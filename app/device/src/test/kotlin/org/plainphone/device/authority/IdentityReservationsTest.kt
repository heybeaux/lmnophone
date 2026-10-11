// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class IdentityReservationsTest {
    @get:Rule val tmp = TemporaryFolder()

    /** Repeats each scripted draw, then falls back to a counter, so collisions can be forced. */
    private class Script(vararg draws: Int) {
        val queue = ArrayDeque(draws.toList())
        var next = 1_000
        fun bytes(n: Int): ByteArray {
            val seed = queue.removeFirstOrNull() ?: next++
            return ByteArray(n) { i -> (seed shr (8 * (i % 4))).toByte() }
        }
    }

    private fun open(dir: File, script: Script = Script()) = JointAuthorityStore(dir).withEntropy(script::bytes)

    @Test fun requestIdsAndCancelCodesAreDistinctAndCodesAreEightDigits() {
        val store = open(tmp.newFolder())
        val ids = List(50) { store.identities.reserve() }
        assertEquals(50, ids.map { it.requestId }.toSet().size)
        assertEquals(50, ids.map { it.cancelCode }.toSet().size)
        ids.forEach { assertTrue(it.cancelCode, it.cancelCode.length == 8 && it.cancelCode.all(Char::isDigit)) }
    }

    @Test fun aForcedCollisionDrawsAgainEvenAfterRestart() {
        val dir = tmp.newFolder()
        val first = open(dir, Script(7, 7)).identities.reserve()
        // A fresh process with the same entropy must not hand out the same request ID or code.
        val again = open(dir, Script(7, 7, 7, 7)).identities.reserve()
        assertNotEquals(first.requestId, again.requestId)
        assertNotEquals(first.cancelCode, again.cancelCode)
    }

    @Test fun abandonedReservationsAreNeverReused() {
        val dir = tmp.newFolder()
        val abandoned = open(dir, Script(42, 42)).identities.reserve()
        repeat(3) {
            val next = open(dir, Script(42, 42)).identities.reserve()
            assertNotEquals(abandoned.cancelCode, next.cancelCode)
            assertNotEquals(abandoned.requestId, next.requestId)
        }
    }

    @Test fun aDamagedOrEmptyReservationStillCountsAsReserved() {
        val dir = tmp.newFolder()
        val first = open(dir, Script(9, 9)).identities.reserve()
        File(dir, "reserved").listFiles()!!.forEach { it.writeBytes(ByteArray(0)) }
        val next = open(dir, Script(9, 9)).identities.reserve()
        assertNotEquals(first.cancelCode, next.cancelCode)
        assertNotEquals(first.requestId, next.requestId)
    }

    @Test fun personIdsAreDistinctAndNeverReused() {
        val dir = tmp.newFolder()
        val first = open(dir, Script(5)).reservePerson()!!
        val second = open(dir, Script(5)).reservePerson()!!
        assertNotEquals(first, second)
        assertTrue(first.value.startsWith("person-"))
    }

    @Test fun unwritableStorageRefusesInsteadOfHandingOutAnUnreservedId() {
        val dir = tmp.newFolder()
        File(dir, "reserved").writeText("not a directory")
        val store = open(dir)
        assertNull(store.reservePerson())
        assertTrue(runCatching { store.identities.reserve() }.exceptionOrNull() is IdentityUnavailable)
    }

    @Test fun exhaustedDrawsRefuse() {
        val dir = tmp.newFolder()
        // Every draw is the same value: the first reservation takes it, the next can never succeed.
        val stuck = JointAuthorityStore(dir).withEntropy { n -> ByteArray(n) { 3 } }
        assertTrue(runCatching { stuck.identities.reserve() }.isSuccess)
        assertFalse(runCatching { stuck.identities.reserve() }.isSuccess)
        assertNull(stuck.reservePerson().let { stuck.reservePerson() })
    }

    // ---- PLA-143 R1: a reservation is handed out only after its directory entries are synced ----

    private class FailingSync(val failing: (File) -> Boolean) : DirectorySync {
        val synced = mutableListOf<String>()
        override fun sync(dir: File) {
            if (failing(dir)) throw java.io.IOException("sync failed: ${dir.name}")
            synced += dir.name
        }
    }

    @Test fun reservedDirectorySyncFailureRefusesAndTheValueStaysTaken() {
        val dir = tmp.newFolder()
        val store = open(dir, Script(11, 11)).withDirectorySync(FailingSync { it.name == "reserved" })
        assertTrue(runCatching { store.identities.reserve() }.exceptionOrNull() is IdentityUnavailable)
        assertNull(store.reservePerson())
        // The refused draw was still taken: a healthy store never hands it out later.
        val healthy = open(dir, Script(11, 11))
        val next = healthy.identities.reserve()
        assertFalse(File(dir, "reserved").listFiles()!!.count { it.name.startsWith("request-") } < 2)
        assertNotEquals("req-" + "0b000000".repeat(4), next.requestId)
    }

    @Test fun parentDirectorySyncFailureRefusesSoANewReservedFolderIsNeverTrustedUnsynced() {
        val dir = tmp.newFolder()
        val store = open(dir).withDirectorySync(FailingSync { it == dir })
        assertTrue(runCatching { store.identities.reserve() }.exceptionOrNull() is IdentityUnavailable)
        assertNull(store.reservePerson())
    }

    @Test fun successSyncsTheReservedFolderAndItsParent() {
        val dir = tmp.newFolder()
        val sync = FailingSync { false }
        open(dir).withDirectorySync(sync).identities.reserve()
        assertTrue(sync.synced.toString(), "reserved" in sync.synced && dir.name in sync.synced)
    }

    /** The reviewer's case: the folder can be written but not opened for reading, so it cannot be synced. */
    @Test fun unreadableReservedFolderRefusesWithTheRealPlatformSync() {
        org.junit.Assume.assumeFalse(System.getProperty("user.name") == "root")
        val dir = tmp.newFolder()
        val folder = File(dir, "reserved").apply { mkdirs() }
        val path = folder.toPath()
        val before = java.nio.file.Files.getPosixFilePermissions(path)
        java.nio.file.Files.setPosixFilePermissions(path, java.nio.file.attribute.PosixFilePermissions.fromString("-wx------"))
        try {
            org.junit.Assume.assumeTrue(runCatching {
                java.nio.channels.FileChannel.open(path, java.nio.file.StandardOpenOption.READ).close()
            }.isFailure)
            val store = open(dir)
            assertTrue(runCatching { store.identities.reserve() }.exceptionOrNull() is IdentityUnavailable)
            assertNull(store.reservePerson())
        } finally {
            java.nio.file.Files.setPosixFilePermissions(path, before)
        }
    }
}
