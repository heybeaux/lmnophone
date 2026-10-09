// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*
import org.plainphone.device.storage.RecordCodec

/** The PLA-135 Verifier's independent migration probes, unchanged except the F3 byte claim (see that test). */
class VerifierMigrationTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun fixture(schema: Int): File {
        val dir = tmp.newFolder()
        val source = File(javaClass.classLoader!!.getResource("legacy-authority/schema$schema/expected.txt")!!.toURI()).parentFile!!
        source.listFiles()!!.forEach { it.copyTo(File(dir, it.name)) }
        return dir
    }
    private fun payload(dir: File, name: String, schema: Int) = AtomicRecord(dir, name, schema).read()

    @Test fun damagedLegacyCopyOrFenceCombinedWithEveryCrashPointRecovers() {
        var cases = 0
        for (schema in 1..3) for (name in listOf("authority-a", "authority-b", "fence-a", "fence-b"))
            for (missing in listOf(true, false)) for (point in WritePoint.entries) {
                val dir = fixture(schema)
                val old = LegacyAuthority.load(dir)!!
                val want = LegacyAuthority.convert(old, null)!!
                val file = File(dir, "$name.rec")
                if (missing) assertTrue(file.delete()) else file.writeBytes(byteArrayOf(0))
                try {
                    JointAuthorityStore(dir, StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }).migrate()
                } catch (_: SimulatedCrash) { }
                val recovered = JointAuthorityStore(dir)
                assertTrue("schema=$schema damaged=$name missing=$missing point=$point", recovered.migrate())
                Section.entries.forEach { assertArrayEquals(want[it.ordinal], recovered.sectionBytesForTest(it)) }
                cases++
            }
        assertEquals(120, cases)
    }

    @Test fun invalidNewestEnvelopeFallsBackToSurvivingOlderLegacyCopy() {
        for (schema in 1..3) {
            val dir = fixture(schema)
            val old = LegacyAuthority.load(dir)!!
            val newer = LegacyAuthority.encode(old.sequence + 1, old.sections)
            val damaged = RecordCodec.encode(schema, newer).also { it[it.lastIndex] = (it.last() + 1).toByte() }
            File(dir, "authority-a.rec").writeBytes(damaged)
            assertTrue(JointAuthorityStore(dir).migrate())
        }
    }

    @Test fun credentialsFromRealOldWriterKeepTheirValuesInCanonicalBytes() {
        val dir = tmp.newFolder()
        val z = TrustedPersonId("z"); val a = TrustedPersonId("a")
        val person = PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000),
            SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000), FailureCounter())
        val old = org.plainphone.device.authority.legacy.JointAuthorityStore(dir)
        assertTrue(old.initialize(CredentialState(Mode.TRUSTED, linkedMapOf(z to person, a to person)), WaitingState(storageGeneration = "legacy"), ElevationRecord()))
        val before = LegacyAuthority.load(dir)!!.sections[Section.CREDENTIALS.ordinal]
        assertEquals(listOf(z, a), old.credentials.read()!!.people.keys.toList())
        val oldRevision = old.credentials.read()!!.revision
        val current = JointAuthorityStore(dir)
        assertTrue(current.migrate())
        assertEquals(setOf(z, a), current.credentials.read()!!.people.keys)
        // PLA-135 F3: migration re-encodes canonically (people sorted), so the values survive but these
        // bytes differ from the z,a insertion order. The section is exactly the canonical encoding.
        val canonical = AuthorityCodec.encodeCredentials(AuthorityCodec.decodeCredentials(before)!!)!!
        assertArrayEquals(canonical, current.sectionBytesForTest(Section.CREDENTIALS))
        assertFalse("z,a insertion order is not canonical", before.contentEquals(canonical))
        assertEquals(oldRevision, current.credentials.read()!!.revision)
    }

    @Test fun failedTailWritesMustNotReportFinishedMigrationWithLegacyFallback() {
        val dir = fixture(3)
        // Force the two tail writes to fail before rename; original files remain intact.
        File(dir, "fence-b.rec.tmp").mkdir()
        File(dir, "authority-b.rec.tmp").mkdir()
        val first = JointAuthorityStore(dir).migrate()
        val retry = JointAuthorityStore(dir).migrate()
        println("TAIL: first=$first retry=$retry legacyCopy=${payload(dir, "authority-b", 3) != null} legacyFence=${payload(dir, "fence-b", 3) != null}")
        // A caller that sees true would move on; losing its only current fence now exposes schema 3 again.
        assertTrue(File(dir, "fence-a.rec").delete())
        assertNotNull(LegacyAuthority.load(dir))
        assertFalse("migrate must not acknowledge completion while old copy/fence remain", first)
    }

    @Test fun currentSchemaWithUndecodableSectionCannotReportReadableMigration() {
        val dir = fixture(3)
        val store = JointAuthorityStore(dir)
        assertTrue(store.migrate())
        val current = LegacyAuthority.decode(payload(dir, "authority-a", 4)!!, 5)!!
        val sections = current.second.toMutableList().also { it[Section.CREDENTIALS.ordinal] = byteArrayOf(1) }
        val bad = RecordCodec.encode(4, LegacyAuthority.encode(current.first, sections))
        listOf("authority-a", "authority-b").forEach { File(dir, "$it.rec").writeBytes(bad) }
        assertNull(store.credentials.read())
        assertFalse("migrate claims true means readable current record", store.migrate())
    }
    @Test fun schemaThreePendingIntentFromActualOldStoreSurvivesMigration() {
        val dir = fixture(3)
        val old = org.plainphone.device.authority.legacy3.JointAuthorityStore(dir)
        val id = old.waitingStore.read()!!.operation!!.identity.requestId
        assertTrue(old.cancelIntents.initialize(id))
        assertEquals(CancelIntent.CLEAR, old.cancelIntents.read(id))
        val current = JointAuthorityStore(dir)
        assertTrue(current.migrate())
        assertEquals(WaitingPhase.PENDING, current.waitingStore.read()!!.operation!!.phase)
        assertEquals("migrated live waiting request must retain CLEAR intent", CancelIntent.CLEAR, current.cancelIntents.read(id))
    }
    @Test fun realWaitingControllerCanAdvanceBeforeUpgradeButNotAfterMigration() {
        fun controller(waiting: WaitingChangeStore, creds: () -> CredentialState?, intents: CancelIntentStore): WaitingChangeController {
            val start = waiting.read()!!.operation!!.startedAt
            return WaitingChangeController(waiting, creds, RunningClock {
                ClockReading(RunningTime(start.epoch, start.totalMillis + 86_400_000), BootTime("boot", 86_400_000))
            }, PersonAuthorityConsumer { _, _, _ -> false }, WaitingIdentitySource { error("no new request") }, intents)
        }
        val beforeDir = fixture(3)
        val before = org.plainphone.device.authority.legacy3.JointAuthorityStore(beforeDir)
        val id = before.waitingStore.read()!!.operation!!.identity.requestId
        assertTrue(before.cancelIntents.initialize(id))
        assertEquals(WaitingOutcome.COMMITTING, controller(before.waitingStore, { before.credentials.read() }, before.cancelIntents).tick(id, BatteryState(50, false)))
        val afterDir = fixture(3)
        val old = org.plainphone.device.authority.legacy3.JointAuthorityStore(afterDir)
        assertTrue(old.cancelIntents.initialize(id))
        val current = JointAuthorityStore(afterDir)
        assertTrue(current.migrate())
        assertTrue(current.migrate())
        val outcome = controller(current.waitingStore, { current.credentials.read() }, current.cancelIntents).tick(id, BatteryState(50, false))
        println("WAIT: old=COMMITTING migrated=$outcome intent=${current.cancelIntents.read(id)}")
        assertEquals("pending wait must still mature after successful migration", WaitingOutcome.COMMITTING, outcome)
    }

    @Test fun legacyRequestedCancelMarkerRemainsRequestedAfterMigration() {
        val dir = fixture(3)
        val old = org.plainphone.device.authority.legacy3.JointAuthorityStore(dir)
        val id = old.waitingStore.read()!!.operation!!.identity.requestId
        assertTrue(old.cancelIntents.initialize(id))
        assertTrue(old.cancelIntents.request(id))
        val current = JointAuthorityStore(dir)
        assertTrue(current.migrate())
        assertEquals(CancelIntent.REQUESTED, current.cancelIntents.read(id))
    }
}
