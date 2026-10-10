// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.ClockReading
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.WindowKind
import org.plainphone.device.authority.SimulatedCrash
import org.plainphone.policy.AuthorityConsumer
import org.plainphone.policy.SessionController
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.SessionPhase

class FileJournalStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dir: File by lazy { tmp.newFolder("journal") }
    private val settings = PackageName("test.settings")
    private val sibling = PackageName("test.settings.sibling")
    private val inventory = BuildInventory("model", "build-1", 1, NeverHideList(emptySet()), emptySet(), setOf(settings, sibling))
    private val deadline = BootTime("boot-1", 600_000)

    private fun store(inv: BuildInventory? = inventory, fingerprint: String = "build-1") =
        FileJournalStore(dir, inv, "model", fingerprint)
    private fun record() = File(dir, "journal.rec")

    private fun active(kind: WindowKind = WindowKind.PLAY, phase: SessionPhase = SessionPhase.OPENING): SessionJournal.Active {
        val packages = if (kind == WindowKind.PLAY) setOf(PackageName("com.android.vending")) else setOf(settings, sibling)
        return SessionJournal.restore("session-1", kind, packages, deadline, phase, inventory) as SessionJournal.Active
    }

    /** A store that has been started once, so it holds Empty with the latch cleared. */
    private fun startedStore(): FileJournalStore = store().also {
        it.beginRevocation()
        assertTrue(it.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
    }

    private fun savedActiveBytes(kind: WindowKind = WindowKind.SETTINGS): ByteArray {
        val s = startedStore()
        assertTrue(s.compareAndSet(SessionJournal.Empty, active(kind)))
        return record().readBytes()
    }

    // --- Reading: anything not exactly a valid entry is Damaged ---

    @Test fun noJournalYetIsDamagedAndPending() {
        val s = store()
        assertEquals(SessionJournal.Damaged, s.read())
        assertTrue(s.revocationPending())
    }

    @Test fun emptyAndActiveEntriesSurviveANewInstance() {
        startedStore()
        assertEquals(SessionJournal.Empty, store().read())
        assertFalse(store().revocationPending())
        for (kind in listOf(WindowKind.PLAY, WindowKind.SETTINGS)) {
            for (phase in SessionPhase.entries) {
                val before = store().read()
                val entry = active(kind, phase)
                assertTrue(store().compareAndSet(before, entry))
                assertEquals(entry, store().read())
            }
        }
    }

    @Test fun everyTruncationIsDamaged() {
        val good = savedActiveBytes()
        for (length in 0 until good.size) {
            record().writeBytes(good.copyOf(length))
            assertEquals("length $length", SessionJournal.Damaged, store().read())
            assertTrue("length $length", store().revocationPending())
        }
        record().writeBytes(good)
        assertEquals(active(WindowKind.SETTINGS), store().read())
    }

    @Test fun everySingleBitFlipIsDamaged() {
        val good = savedActiveBytes()
        for (i in good.indices) {
            for (bit in 0 until 8) {
                val bad = good.copyOf().also { it[i] = (it[i].toInt() xor (1 shl bit)).toByte() }
                record().writeBytes(bad)
                assertEquals("byte $i bit $bit", SessionJournal.Damaged, store().read())
                assertTrue("byte $i bit $bit", store().revocationPending())
            }
        }
    }

    @Test fun paddedRecordIsDamaged() {
        val good = savedActiveBytes()
        record().writeBytes(good + byteArrayOf(0))
        assertEquals(SessionJournal.Damaged, store().read())
    }

    @Test fun anotherSchemaIsDamaged() {
        val payload = RecordCodec.decode(savedActiveBytes(), FileJournalStore.SCHEMA_VERSION)!!
        record().writeBytes(RecordCodec.encode(FileJournalStore.SCHEMA_VERSION + 1, payload))
        assertEquals(SessionJournal.Damaged, store().read())
    }

    @Test fun settingsEntryNeedsAnInventoryForTheLiveBuild() {
        savedActiveBytes(WindowKind.SETTINGS)
        assertEquals(active(WindowKind.SETTINGS), store().read())
        assertEquals(SessionJournal.Damaged, store(fingerprint = "build-2").read())
        assertEquals(SessionJournal.Damaged, store(inv = null).read())
        val moved = BuildInventory("model", "build-1", 2, NeverHideList(emptySet()), emptySet(), setOf(settings))
        assertEquals(SessionJournal.Damaged, store(inv = moved).read())
    }

    @Test fun playEntryDoesNotNeedAnInventory() {
        savedActiveBytes(WindowKind.PLAY)
        assertEquals(active(WindowKind.PLAY), store(inv = null).read())
        assertEquals(active(WindowKind.PLAY), store(fingerprint = "build-2").read())
    }

    @Test fun validChecksumOverAnInvalidEntryIsDamaged() {
        val cases = mapOf(
            "unknown state tag" to payload(0, 9),
            "latch byte out of range" to payload(2, 0),
            "trailing byte" to payload(0, 0, extra = 1),
            "unsupported kind" to activePayload(kind = "WEB", packages = listOf("com.android.vending")),
            "unknown kind" to activePayload(kind = "NOPE", packages = listOf("com.android.vending")),
            "unknown phase" to activePayload(phase = "LATER"),
            "blank id" to activePayload(id = ""),
            "wrong package" to activePayload(packages = listOf("com.example.other")),
            "unsorted packages" to activePayload(kind = "SETTINGS", packages = listOf(sibling.value, settings.value)),
            "duplicate package" to activePayload(packages = listOf("com.android.vending", "com.android.vending")),
            "negative count" to activePayload(packages = emptyList(), count = -1),
            "negative deadline" to activePayload(elapsed = -1),
            "blank boot id" to activePayload(bootId = ""),
        )
        for ((name, bytes) in cases) {
            record().writeBytes(RecordCodec.encode(FileJournalStore.SCHEMA_VERSION, bytes))
            assertEquals(name, SessionJournal.Damaged, store().read())
        }
        // The same writer with valid fields is accepted, so the cases above fail on their one defect.
        record().writeBytes(RecordCodec.encode(FileJournalStore.SCHEMA_VERSION,
            activePayload(kind = "SETTINGS", packages = listOf(settings.value, sibling.value))))
        assertEquals(active(WindowKind.SETTINGS, SessionPhase.OPEN), store().read())
    }

    @Test fun lastKnownGoodIsNeverServed() {
        val s = startedStore()
        assertTrue(s.compareAndSet(SessionJournal.Empty, active()))
        assertTrue(s.compareAndSet(active(), active(phase = SessionPhase.OPEN)))
        record().writeBytes(record().readBytes().copyOf(5))
        assertEquals(SessionJournal.Damaged, store().read())
        assertFalse(File(dir, "journal.lkg").exists())
    }

    // --- Compare-and-set ---

    @Test fun staleExpectedValueFailsAndChangesNothing() {
        val s = startedStore()
        assertTrue(s.compareAndSet(SessionJournal.Empty, active()))
        val bytes = record().readBytes()
        assertFalse(s.compareAndSet(SessionJournal.Empty, active(phase = SessionPhase.OPEN)))
        assertFalse(s.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        assertFalse(s.compareAndSet(active(phase = SessionPhase.CLOSING), SessionJournal.Empty))
        assertArrayEquals(bytes, record().readBytes())
        assertEquals(active(), s.read())
    }

    @Test fun staleValueFromAnotherInstanceFails() {
        val a = startedStore()
        val b = store()
        assertTrue(a.compareAndSet(SessionJournal.Empty, active()))
        assertFalse(b.compareAndSet(SessionJournal.Empty, active(WindowKind.SETTINGS)))
        assertEquals(active(), b.read())
    }

    @Test fun damagedIsNeverWritten() {
        val s = startedStore()
        assertFalse(s.compareAndSet(SessionJournal.Empty, SessionJournal.Damaged))
        assertEquals(SessionJournal.Empty, s.read())
    }

    @Test fun damagedEntryCanOnlyBeReplacedFromDamaged() {
        val good = savedActiveBytes()
        record().writeBytes(good.copyOf(good.size - 1))
        val s = store()
        assertFalse(s.compareAndSet(SessionJournal.Empty, SessionJournal.Empty))
        assertFalse(s.compareAndSet(active(WindowKind.SETTINGS), SessionJournal.Empty))
        assertTrue(s.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        assertEquals(SessionJournal.Empty, store().read())
    }

    @Test fun racingWritersFromTheSameValueHaveOneWinner() {
        startedStore()
        val pool = Executors.newFixedThreadPool(8)
        val go = CountDownLatch(1)
        val results = (0 until 8).map { i ->
            pool.submit<Boolean> {
                go.await()
                store().compareAndSet(SessionJournal.Empty,
                    SessionJournal.restore("session-$i", WindowKind.PLAY, setOf(PackageName("com.android.vending")), deadline, SessionPhase.OPENING, null))
            }
        }
        go.countDown()
        val wins = results.count { it.get(10, TimeUnit.SECONDS) }
        pool.shutdown()
        assertEquals(1, wins)
    }

    @Test fun crashBetweenTempWriteAndRenameKeepsTheOldJournal() {
        val s = startedStore()
        assertTrue(s.compareAndSet(SessionJournal.Empty, active()))
        val crashing = FileJournalStore(dir, inventory, "model", "build-1") { throw SimulatedCrash() }
        try {
            crashing.compareAndSet(active(), active(phase = SessionPhase.OPEN))
            throw AssertionError("expected the simulated crash")
        } catch (_: SimulatedCrash) {
        }
        assertTrue(File(dir, "journal.rec.tmp").exists())
        // After the "restart": the old entry is still there, and the next write succeeds over the leftover temp file.
        val restarted = store()
        assertEquals(active(), restarted.read())
        assertTrue(restarted.compareAndSet(active(), active(phase = SessionPhase.OPEN)))
        assertEquals(active(phase = SessionPhase.OPEN), store().read())
    }

    @Test fun writeThatDoesNotReadBackIsNotAcknowledged() {
        val s = startedStore()
        val corrupting = FileJournalStore(dir, inventory, "model", "build-1") { temp ->
            val bytes = temp.readBytes()
            bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 1).toByte()
            temp.writeBytes(bytes)
        }
        assertFalse(corrupting.compareAndSet(SessionJournal.Empty, active()))
        assertEquals(SessionJournal.Damaged, s.read())
        assertTrue(s.revocationPending())
    }

    @Test fun unwritableDirectoryIsNotAcknowledged() {
        val notADir = tmp.newFile("blocked")
        val s = FileJournalStore(File(notADir, "journal"), inventory, "model", "build-1")
        s.beginRevocation()
        assertTrue(s.revocationPending())
        assertFalse(s.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        assertTrue(s.revocationPending())
    }

    // --- Revocation latch ---

    @Test fun latchIsOnDiskWhenBeginRevocationReturns() {
        startedStore()
        assertFalse(store().revocationPending())
        store().beginRevocation()
        assertTrue(store().revocationPending())
        assertEquals(SessionJournal.Empty, store().read())
    }

    @Test fun latchKeepsTheActiveEntryAndSurvivesNonEmptyWrites() {
        val s = startedStore()
        assertTrue(s.compareAndSet(SessionJournal.Empty, active(phase = SessionPhase.OPEN)))
        s.beginRevocation()
        assertEquals(active(phase = SessionPhase.OPEN), store().read())
        assertTrue(s.compareAndSet(active(phase = SessionPhase.OPEN), active(phase = SessionPhase.CLOSING)))
        assertTrue(store().revocationPending())
        assertTrue(s.compareAndSet(active(phase = SessionPhase.CLOSING), SessionJournal.Empty))
        assertFalse(store().revocationPending())
        assertFalse(s.revocationPending())
    }

    @Test fun latchOverADamagedEntryKeepsItDamaged() {
        val good = savedActiveBytes()
        record().writeBytes(good.copyOf(20))
        store().beginRevocation()
        assertEquals(SessionJournal.Damaged, store().read())
        assertTrue(store().revocationPending())
    }

    @Test fun latchStaysPendingInMemoryWhenItCannotBeWritten() {
        val notADir = tmp.newFile("blocked")
        val s = FileJournalStore(File(notADir, "journal"), inventory, "model", "build-1")
        s.beginRevocation()
        assertTrue(s.revocationPending())
    }

    // --- Through the reviewed SessionController ---

    private class Effects(private val dir: File, private val revokes: Boolean = true) : SessionEffects {
        val calls = mutableListOf<String>()
        override fun revokeAllRelaxations(): Boolean {
            // The latch must already be on disk when the first effect runs.
            calls += "revoke(latchOnDisk=${FileJournalStore(dir, null, "model", "build-1").revocationPending()})"
            return revokes
        }
        override fun open(access: org.plainphone.policy.SessionAccess) = error("not used")
        override fun protectedStateCheck(): Boolean { calls += "check"; return true }
        override fun readBackPolicy(): Boolean { calls += "readback"; return true }
    }

    private fun controller(effects: SessionEffects) = SessionController(store(), effects,
        RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot-2", 0)) }, inventory, AuthorityConsumer { _, _ -> false })

    @Test fun everyStartRevokesAndClearsAJournaledSession() {
        val s = startedStore()
        assertTrue(s.compareAndSet(SessionJournal.Empty, active(phase = SessionPhase.OPEN)))
        val effects = Effects(dir)
        assertTrue(controller(effects).onStart())
        assertEquals(listOf("revoke(latchOnDisk=true)", "check", "readback"), effects.calls)
        assertEquals(SessionJournal.Empty, store().read())
        assertFalse(store().revocationPending())
    }

    @Test fun damagedJournalRevokesEverythingOnStart() {
        val good = savedActiveBytes()
        record().writeBytes(good.copyOf().also { it[30] = (it[30].toInt() xor 4).toByte() })
        val effects = Effects(dir)
        val c = controller(effects)
        assertTrue(c.onStart())
        assertEquals(listOf("revoke(latchOnDisk=true)", "check", "readback"), effects.calls)
        assertTrue(c.handbackReady)
        assertEquals(SessionJournal.Empty, store().read())
    }

    @Test fun failedRevocationLeavesTheDamagedJournalAndTheLatch() {
        val good = savedActiveBytes()
        record().writeBytes(good.copyOf(good.size - 3))
        val c = controller(Effects(dir, revokes = false))
        assertFalse(c.onStart())
        assertFalse(c.handbackReady)
        assertEquals(SessionJournal.Damaged, store().read())
        assertTrue(store().revocationPending())
    }

    @Test fun firstEverStartRepairsTheMissingJournal() {
        val effects = Effects(dir)
        assertTrue(controller(effects).onStart())
        assertEquals("revoke(latchOnDisk=true)", effects.calls.first())
        assertEquals(SessionJournal.Empty, store().read())
        assertFalse(store().revocationPending())
    }

    // --- Raw payload writer mirroring the v1 layout, for checksum-valid but invalid entries ---

    private fun payload(latch: Int, tag: Int, extra: Int? = null): ByteArray = bytes {
        writeByte(latch); writeByte(tag); extra?.let { writeByte(it) }
    }

    private fun activePayload(
        id: String = "session-1", kind: String = "PLAY", packages: List<String> = listOf("com.android.vending"),
        count: Int = packages.size, bootId: String = "boot-1", elapsed: Long = 600_000, phase: String = "OPEN",
    ): ByteArray = bytes {
        writeByte(0); writeByte(1)
        writeUTF(id); writeUTF(kind)
        writeInt(count); packages.forEach { writeUTF(it) }
        writeUTF(bootId); writeLong(elapsed); writeUTF(phase)
    }

    private fun bytes(block: DataOutputStream.() -> Unit): ByteArray {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use(block)
        return out.toByteArray()
    }

    // --- A failed directory sync is never acknowledged (PLA-232 finding 1) ---

    private fun denySync(d: File) {
        assertTrue(d.setReadable(false, false))
        try {
            FileChannel.open(d.toPath(), StandardOpenOption.READ).use { }
            fail("control: opening the directory for sync must fail")
        } catch (_: IOException) { }
    }

    @Test fun failedDirectorySyncKeepsTheLatchAndIsNotAcknowledged() {
        startedStore().compareAndSet(SessionJournal.Empty, active()).let(::assertTrue)
        val s = FileJournalStore(dir, inventory, "model", "build-1") { denySync(dir) }
        s.beginRevocation()
        try { assertFalse(s.compareAndSet(active(), SessionJournal.Empty)) } finally { dir.setReadable(true, true) }
        // The in-memory latch survives an unacknowledged clear.
        assertTrue(s.revocationPending())
    }

    @Test fun failedSyncOfANewJournalDirectoryIsNotAcknowledged() {
        val parent = tmp.newFolder("parent")
        denySync(parent)
        val s = FileJournalStore(File(parent, "journal"), inventory, "model", "build-1")
        try { assertFalse(s.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty)) }
        finally { parent.setReadable(true, true) }
    }
}
