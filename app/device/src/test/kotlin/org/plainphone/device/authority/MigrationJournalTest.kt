// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.storage.RecordCodec
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingState
import org.plainphone.device.authority.legacy.JointAuthorityStore as Schema1Store
import org.plainphone.device.authority.legacy3.JointAuthorityStore as Schema3Store

/**
 * PLA-135 fix round. The cancel-intent journal written by the real schema 3 store (4501f7f) is
 * migrated with the record; true from migrate() means every file holds the readable current record;
 * migration re-encodes sections canonically, so several people come out sorted.
 */
class MigrationJournalTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun fixture(schema: Int): File {
        val dir = tmp.newFolder()
        val source = File(checkNotNull(javaClass.classLoader!!.getResource("legacy-authority/schema$schema/expected.txt")).toURI()).parentFile
        source.listFiles()!!.forEach { it.copyTo(File(dir, it.name)) }
        return dir
    }
    private fun hash(id: String) = MessageDigest.getInstance("SHA-256").digest(id.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun entry(dir: File, id: String) = File(dir, "intent-clear-${hash(id)}.rec")
    private fun marker(dir: File, id: String) = File(dir, "intent-requested-${hash(id)}.rec")
    private fun schemaOf(file: File) = (1..4).firstOrNull { RecordCodec.decode(file.readBytes(), it) != null }
    private fun liveRequest(dir: File) = Schema3Store(dir).waitingStore.read()!!.operation!!.identity.requestId

    @Test fun clearIntentIsRewrittenInTheCurrentFormat() {
        val dir = fixture(3)
        val id = liveRequest(dir)
        assertTrue(Schema3Store(dir).cancelIntents.initialize(id))
        assertTrue(JointAuthorityStore(dir).migrate())
        assertEquals(4, schemaOf(entry(dir, id)))
        assertFalse(marker(dir, id).exists())
        assertEquals(CancelIntent.CLEAR, JointAuthorityStore(dir).cancelIntents.read(id))
        // A later cancel request works on the migrated entry.
        assertTrue(JointAuthorityStore(dir).cancelIntents.request(id))
        assertEquals(CancelIntent.REQUESTED, JointAuthorityStore(dir).cancelIntents.read(id))
    }

    @Test fun requestedIntentSurvivesLosingEitherFileAfterMigration() {
        for (lose in listOf("entry", "marker")) {
            val dir = fixture(3)
            val id = liveRequest(dir)
            val old = Schema3Store(dir)
            assertTrue(old.cancelIntents.initialize(id))
            assertTrue(old.cancelIntents.request(id))
            assertTrue(JointAuthorityStore(dir).migrate())
            assertTrue(if (lose == "entry") entry(dir, id).delete() else marker(dir, id).delete())
            assertEquals("lost $lose", CancelIntent.REQUESTED, JointAuthorityStore(dir).cancelIntents.read(id))
        }
    }

    @Test fun requestedMarkerWithoutAnEntryStillReadsRequested() {
        val dir = fixture(3)
        val id = liveRequest(dir)
        assertTrue(Schema3Store(dir).cancelIntents.request(id))
        assertFalse(entry(dir, id).exists())
        assertTrue(JointAuthorityStore(dir).migrate())
        assertEquals(CancelIntent.REQUESTED, JointAuthorityStore(dir).cancelIntents.read(id))
        assertTrue(marker(dir, id).delete())
        assertEquals(CancelIntent.REQUESTED, JointAuthorityStore(dir).cancelIntents.read(id))
    }

    @Test fun damagedOrMisnamedOldEntryIsNeverGuessedClear() {
        val dir = fixture(3)
        val id = liveRequest(dir)
        assertTrue(Schema3Store(dir).cancelIntents.initialize(id))
        // A CLEAR entry under another request's name, and a damaged entry.
        entry(dir, id).copyTo(entry(dir, "other"))
        assertTrue(Schema3Store(dir).cancelIntents.initialize("third"))
        entry(dir, "third").writeBytes(entry(dir, "third").readBytes().also { it[it.lastIndex] = (it.last() + 1).toByte() })
        assertTrue(JointAuthorityStore(dir).migrate())
        val store = JointAuthorityStore(dir)
        assertEquals(CancelIntent.CLEAR, store.cancelIntents.read(id))
        assertEquals(CancelIntent.UNKNOWN, store.cancelIntents.read("other"))
        assertEquals(CancelIntent.UNKNOWN, store.cancelIntents.read("third"))
        // Initialize never overwrites them back to CLEAR.
        assertFalse(store.cancelIntents.initialize("other"))
        assertFalse(store.cancelIntents.initialize("third"))
    }

    @Test fun failedIntentRewriteIsNotCompletionAndARetryFinishesIt() {
        val dir = fixture(3)
        val id = liveRequest(dir)
        assertTrue(Schema3Store(dir).cancelIntents.initialize(id))
        val blocked = File(dir, "${entry(dir, id).name}.tmp").also { assertTrue(it.mkdir()) }
        assertFalse(JointAuthorityStore(dir).migrate())
        // Until the rewrite lands the intent is not CLEAR, so nothing can commit on it.
        assertEquals(CancelIntent.UNKNOWN, JointAuthorityStore(dir).cancelIntents.read(id))
        assertTrue(blocked.delete())
        assertTrue(JointAuthorityStore(dir).migrate())
        assertEquals(CancelIntent.CLEAR, JointAuthorityStore(dir).cancelIntents.read(id))
    }

    @Test fun migrationIsTrueOnlyWhenEveryFileHoldsTheCurrentRecord() {
        for (blockedNames in listOf(listOf("fence-a"), listOf("fence-b"), listOf("fence-a", "fence-b"), listOf("authority-b"))) {
            val dir = fixture(3)
            val blocked = blockedNames.map { File(dir, "$it.rec.tmp").also { f -> assertTrue(f.mkdir()) } }
            assertFalse("blocked $blockedNames", JointAuthorityStore(dir).migrate())
            assertFalse("retry, blocked $blockedNames", JointAuthorityStore(dir).migrate())
            blocked.forEach { assertTrue(it.delete()) }
            assertTrue("unblocked $blockedNames", JointAuthorityStore(dir).migrate())
            for (name in listOf("authority-a", "authority-b", "fence-a", "fence-b")) assertEquals(name, 4, schemaOf(File(dir, "$name.rec")))
            assertNull(LegacyAuthority.load(dir))
        }
    }

    @Test fun currentRecordWithAnUnreadableSectionIsNotReportedMigrated() {
        val dir = fixture(3)
        assertTrue(JointAuthorityStore(dir).migrate())
        val payload = RecordCodec.decode(File(dir, "authority-a.rec").readBytes(), 4)!!
        val (sequence, sections) = LegacyAuthority.decode(payload, Section.entries.size)!!
        val bad = sections.toMutableList().also { it[Section.APPROVALS.ordinal] = byteArrayOf(9) }
        val encoded = RecordCodec.encode(4, LegacyAuthority.encode(sequence, bad))
        listOf("authority-a", "authority-b").forEach { File(dir, "$it.rec").writeBytes(encoded) }
        val store = JointAuthorityStore(dir)
        assertFalse(store.migrate())
        assertFalse(store.migrate())
    }

    @Test fun twoPeopleFromTheRealSchemaOneWriterMigrateToSortedCanonicalBytes() {
        val dir = tmp.newFolder()
        val ids = listOf("zoe", "amir").map { TrustedPersonId(it) }
        fun person(n: Int) = PersonCredential(SecretVerifier(ByteArray(16) { n.toByte() }, ByteArray(32) { (n + 1).toByte() }, 600_000),
            SecretVerifier(ByteArray(16) { (n + 2).toByte() }, ByteArray(32) { (n + 3).toByte() }, 600_000), FailureCounter(n))
        val people = LinkedHashMap<TrustedPersonId, PersonCredential>().apply { ids.forEachIndexed { i, id -> put(id, person(i)) } }
        assertTrue(Schema1Store(dir).initialize(CredentialState(Mode.TRUSTED, people), WaitingState(storageGeneration = "legacy"), ElevationRecord()))
        val before = LegacyAuthority.load(dir)!!.sections[Section.CREDENTIALS.ordinal]
        assertTrue(JointAuthorityStore(dir).migrate())
        val store = JointAuthorityStore(dir)
        val migrated = store.credentials.read()!!
        assertEquals(ids.sortedBy { it.value }, migrated.people.keys.toList())
        ids.forEachIndexed { i, id ->
            val p = migrated.people.getValue(id)
            assertEquals(FailureCounter(i), p.pinCounter)
            assertArrayEquals(ByteArray(16) { i.toByte() }, p.pin.salt)
            assertArrayEquals(ByteArray(32) { (i + 3).toByte() }, p.recovery.hash)
        }
        val canonical = AuthorityCodec.encodeCredentials(AuthorityCodec.decodeCredentials(before)!!)!!
        assertArrayEquals(canonical, store.sectionBytesForTest(Section.CREDENTIALS))
        assertFalse(before.contentEquals(canonical))
        // Canonical bytes keep compare-and-set exact: re-encoding what was read gives the stored bytes.
        assertArrayEquals(store.sectionBytesForTest(Section.CREDENTIALS), AuthorityCodec.encodeCredentials(migrated))
    }
}
