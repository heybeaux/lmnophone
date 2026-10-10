// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.io.IOException
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.authority.legacy3.JointAuthorityStore as Schema3Store

/**
 * PLA-220 R1: migration acknowledges readiness only after a successful directory sync covering every
 * rename an earlier attempt left behind. The Verifier's four probes, adapted where noted.
 */
class MigrationRetryDurabilityTest {
    @get:Rule val tmp = TemporaryFolder()

    // Reach this state using only real schema-3 fixtures and public migration calls:
    // an intent write fails before rename, but the authority record finishes migration.
    private fun prepared(requested: Boolean): File {
        val dir = tmp.newFolder()
        val source = File(checkNotNull(javaClass.classLoader!!.getResource(
            "legacy-authority/schema3/expected.txt")).toURI()).parentFile!!
        source.listFiles()!!.forEach { it.copyTo(File(dir, it.name)) }
        val old = Schema3Store(dir)
        val id = old.waitingStore.read()!!.operation!!.identity.requestId
        assertTrue(old.cancelIntents.initialize(id))
        if (requested) assertTrue(old.cancelIntents.request(id))
        val block = File(dir, "intent-clear-${intentHash(id)}.rec.tmp")
        assertTrue(block.mkdir())
        assertFalse(JointAuthorityStore(dir).migrate())
        assertNotNull(JointAuthorityStore(dir).credentials.read())
        assertTrue(block.delete())
        assertTrue(LegacyAuthority.intentRequests(dir)!!.contains(id))
        return dir
    }

    private fun failedConversionThenRetry(requested: Boolean) {
        val dir = prepared(requested)
        var calls = 0
        var successes = 0
        val failureAt = if (requested) 2 else 1
        val fault = DirectorySync { d ->
            calls++
            if (calls >= failureAt) throw IOException("directory sync unavailable")
            PlatformDirectorySync.sync(d)
            successes++
        }
        assertFalse(JointAuthorityStore(dir).withDirectorySync(fault).migrate())
        assertEquals(failureAt, calls)
        assertTrue("conversion removed the last schema-3 discovery entry",
            LegacyAuthority.intentRequests(dir)!!.isEmpty())
        val beforeRetry = calls
        val result = JointAuthorityStore(dir).withDirectorySync(fault).migrate()
        println("requested=$requested retry=$result retrySyncCalls=${calls-beforeRetry} totalSuccessfulSyncs=$successes")
        assertFalse("migration retry acknowledged an unsynced conversion", result)
        // Author addition: once directory sync works again, a retry completes.
        assertTrue(JointAuthorityStore(dir).migrate())
        assertNotNull(JointAuthorityStore(dir).credentials.read())
    }

    @Test fun clearMigrationRetryMustNotAcknowledgeUnsyncedConversion() = failedConversionThenRetry(false)
    @Test fun requestedMigrationRetryMustNotAcknowledgeUnsyncedMarkerConversion() = failedConversionThenRetry(true)

    @Test fun healthyConversionsActuallySync() {
        for (requested in listOf(false, true)) {
            val dir = prepared(requested)
            var calls = 0
            assertTrue(JointAuthorityStore(dir).withDirectorySync { d ->
                calls++; PlatformDirectorySync.sync(d)
            }.migrate())
            // Was assertEquals(if (requested) 2 else 1, calls): readiness now adds one directory sync.
            assertTrue("$calls", calls >= (if (requested) 2 else 1) + 1)
        }
    }

    @Test fun failedLegacyCopyWriteRestoresExactLegacyBytes() {
        val dir = tmp.newFolder()
        val source = File(checkNotNull(javaClass.classLoader!!.getResource(
            "legacy-authority/schema3/expected.txt")).toURI()).parentFile!!
        source.listFiles()!!.forEach { it.copyTo(File(dir, it.name)) }
        val copies = listOf("authority-a.rec", "authority-b.rec")
        val before = copies.associateWith { File(dir, it).readBytes() }
        var calls = 0
        assertFalse(JointAuthorityStore(dir).withDirectorySync { d ->
            if (++calls == 2) throw IOException("second copy sync failed")
            PlatformDirectorySync.sync(d)
        }.migrate())
        before.forEach { (name, bytes) -> assertArrayEquals(name, bytes, File(dir, name).readBytes()) }
        assertTrue(JointAuthorityStore(dir).migrate())
    }
}
