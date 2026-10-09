// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BuildInventory
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.device.storage.RecordCodec
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.WaitingState

/**
 * Records written by the real schema 1, 2 and 3 stores (commits 6899dba, b88de2b, 4501f7f) are
 * kept in test resources with the facts those stores read back. Migration is an explicit startup
 * step: before it runs, nothing reads; after it, everything reads exactly as the old store did.
 */
class LegacyMigrationTest {
    @get:Rule val tmp = TemporaryFolder()
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(PackageName("com.android.vending"))),
        emptySet(), setOf(PackageName("test.settings")))
    private var faults = StorageFaults { _, _ -> }
    private fun open(dir: File) = JointAuthorityStore(dir, { point, d -> faults.at(point, d) }, inventory)

    private fun fixture(schema: Int): File {
        val dir = tmp.newFolder("schema$schema-${System.nanoTime()}")
        val source = File(checkNotNull(javaClass.classLoader!!.getResource("legacy-authority/schema$schema/expected.txt")).toURI()).parentFile
        source.listFiles()!!.forEach { it.copyTo(File(dir, it.name)) }
        return dir
    }
    private fun expected(dir: File) = File(dir, "expected.txt").readLines().filter { it.isNotBlank() }
        .associate { it.substringBefore('=') to it.substringAfter('=') }
    private fun recFiles(dir: File) = dir.listFiles()!!.filter { it.name.endsWith(".rec") }.associate { it.name to it.readBytes() }

    /** The same facts, formatted the same way, as the old store's own dump. */
    private fun facts(store: JointAuthorityStore): Map<String, String> {
        val facts = sortedMapOf<String, String>()
        store.approvals.read()?.takeIf { it.revision > 0 }?.let { ap ->
            facts["approvals"] = "${ap.revision} ${ap.apps.keys.joinToString(",")} ${ap.receipts.joinToString(",") { it.id }}"
        }
        val c = checkNotNull(store.credentials.read())
        facts["credentials"] = "${c.revision} ${c.mode} ${c.recoveryProfile!!.friction.countdownMillis} ${c.recoveryProfile!!.friction.dailyLimit} ${c.people.keys.joinToString(",") { it.value }}"
        val w = checkNotNull(store.waitingStore.read())
        facts["waiting"] = "${w.revision} ${w.storageGeneration} ${w.operation!!.identity.requestId} ${w.operation!!.phase} ${w.operation!!.frictionPatch!!.target.countdownMillis} ${w.texts.size}"
        facts["second"] = w.operation!!.identity.requestId
        val e = checkNotNull(store.elevationStore.read())
        facts["elevation"] = "${e.revision} ${e.state.ledger.debits.joinToString(",") { it.id }}"
        val r = checkNotNull(store.frictionReceipts())
        facts["receipts"] = "${r.size} ${r.joinToString(",") { it.key }}"
        return facts
    }

    private fun allCurrentSchema(dir: File) = listOf("authority-a", "authority-b", "fence-a", "fence-b")
        .all { RecordCodec.decode(File(dir, "$it.rec").readBytes(), CURRENT) != null }

    @Test fun everyOldSchemaMigratesToTheSameFactsTheOldStoreRead() {
        for (schema in 1..3) {
            val dir = fixture(schema)
            val store = open(dir)
            assertNull("schema $schema reads before migration", store.credentials.read())
            assertTrue("schema $schema", store.migrate())
            assertEquals("schema $schema", expected(dir), facts(open(dir)))
            assertTrue(allCurrentSchema(dir))
            if (schema == 1) assertEquals(0L, open(dir).approvals.read()!!.revision)
        }
    }

    /** One-person fixtures are already canonical, so their bytes survive; several people come out sorted (MigrationJournalTest). */
    @Test fun canonicalOneSectionFormatsAreKeptByteForByte() {
        for (schema in 1..3) {
            val dir = fixture(schema)
            val legacy = LegacyAuthority.load(dir)!!
            assertTrue(open(dir).migrate())
            val store = open(dir)
            assertArrayEquals(legacy.sections[Section.CREDENTIALS.ordinal], store.sectionBytesForTest(Section.CREDENTIALS))
            assertArrayEquals(legacy.sections[Section.RECEIPTS.ordinal], store.sectionBytesForTest(Section.RECEIPTS))
            if (schema == 3) assertArrayEquals(legacy.sections[Section.WAITING.ordinal], store.sectionBytesForTest(Section.WAITING))
            if (schema >= 2) {
                assertArrayEquals(legacy.sections[Section.ELEVATION.ordinal], store.sectionBytesForTest(Section.ELEVATION))
                assertArrayEquals(legacy.sections[Section.APPROVALS.ordinal], store.sectionBytesForTest(Section.APPROVALS))
            }
        }
    }

    @Test fun migrationIsIdempotentAndOrdinaryWritesFollow() {
        val dir = fixture(1)
        assertTrue(open(dir).migrate())
        val once = recFiles(dir)
        assertTrue(open(dir).migrate())
        assertEquals(once.keys, recFiles(dir).keys)
        once.forEach { (name, bytes) -> assertArrayEquals(name, bytes, File(dir, name).readBytes()) }
        val store = open(dir)
        val before = store.credentials.read()!!
        assertTrue(store.credentials.compareAndSet(before, CredentialState(before.mode, before.people, before.revision + 1,
            before.structureGeneration, before.pendingNotices, before.recoveryProfile, before.appliedPeopleChange)))
        assertEquals(before.revision + 1, open(dir).credentials.read()!!.revision)
    }

    @Test fun crashAtEveryMigrationWritePointThenRestartCompletesIt() {
        for (schema in 1..3) for (point in WritePoint.entries) {
            val dir = fixture(schema)
            val want = expected(dir)
            faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            try { open(dir).migrate() } catch (_: SimulatedCrash) { }
            faults = StorageFaults { _, _ -> }
            // Old record or migrated record, never a third state; a restart's migrate always finishes.
            val reopened = open(dir)
            if (reopened.credentials.read() != null) assertEquals("schema $schema at $point", want, facts(reopened))
            assertTrue("schema $schema at $point", open(dir).migrate())
            assertEquals("schema $schema at $point", want, facts(open(dir)))
            assertTrue(allCurrentSchema(dir))
        }
    }

    @Test fun eitherSurvivingOldCopyIsEnough() {
        for (damaged in listOf("authority-a.rec", "authority-b.rec")) for (schema in 1..3) {
            val dir = fixture(schema)
            File(dir, damaged).writeBytes(ByteArray(5))
            val want = expected(dir)
            assertTrue("$damaged schema $schema", open(dir).migrate())
            assertEquals(want, facts(open(dir)))
            // A crash right after the first new copy still leaves the old surviving copy readable.
        }
    }

    @Test fun crashAfterTheFirstNewCopyKeepsTheOnlySurvivingOldCopy() {
        for (damaged in listOf("authority-a.rec", "authority-b.rec")) {
            val dir = fixture(2)
            File(dir, damaged).writeBytes(ByteArray(5))
            val want = expected(dir)
            faults = StorageFaults { at, _ -> if (at == WritePoint.AFTER_COPY_A) throw SimulatedCrash() }
            try { open(dir).migrate() } catch (_: SimulatedCrash) { }
            faults = StorageFaults { _, _ -> }
            assertTrue(LegacyAuthority.load(dir) != null)
            assertTrue(open(dir).migrate())
            assertEquals(want, facts(open(dir)))
        }
    }

    @Test fun undecodableOldRecordIsLeftUntouchedAndRefused() {
        val dir = fixture(2)
        // Corrupt one section inside both copies, keeping valid checksums: the elevation ledger count.
        val legacy = LegacyAuthority.load(dir)!!
        val sections = legacy.sections.toMutableList()
        sections[Section.ELEVATION.ordinal] = byteArrayOf(1, 2, 3)
        val bytes = LegacyAuthority.encode(legacy.sequence, sections)
        listOf("authority-a", "authority-b").forEach { File(dir, "$it.rec").writeBytes(RecordCodec.encode(2, bytes)) }
        val before = recFiles(dir)
        assertFalse(open(dir).migrate())
        before.forEach { (name, b) -> assertArrayEquals(name, b, File(dir, name).readBytes()) }
        assertNull(open(dir).credentials.read())
    }

    @Test fun oldRecordIsNeverOverwrittenByInitialize() {
        val dir = fixture(1)
        val before = recFiles(dir)
        val credentials = AuthorityCodec.decodeCredentials(LegacyAuthority.load(dir)!!.sections[Section.CREDENTIALS.ordinal])!!
        assertFalse(open(dir).initialize(credentials, WaitingState(storageGeneration = "fresh"), ElevationRecord()))
        before.forEach { (name, b) -> assertArrayEquals(name, b, File(dir, name).readBytes()) }
    }

    @Test fun onceACurrentFenceExistsTheOldRecordIsNeverReadAgain() {
        val dir = fixture(3)
        val oldCopyB = File(dir, "authority-b.rec").readBytes()
        val oldFenceB = File(dir, "fence-b.rec").readBytes()
        assertTrue(open(dir).migrate())
        // Damage every current copy and put stale old files back next to the current fence.
        File(dir, "authority-a.rec").writeBytes(ByteArray(5))
        File(dir, "authority-b.rec").writeBytes(oldCopyB)
        File(dir, "fence-b.rec").writeBytes(oldFenceB)
        assertFalse(open(dir).migrate())
        assertNull(open(dir).credentials.read())
    }

    private companion object { const val CURRENT = 4 }
}
