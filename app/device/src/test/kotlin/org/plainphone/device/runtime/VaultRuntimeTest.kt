// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.runtime

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.policy.ClockRecord
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PrivilegedAction
import org.plainphone.policy.ProvenRunningClock
import org.plainphone.policy.WaitingState

/** The device pieces every trusted-person flow needs before wiring: key derivation, entropy and the running clock. */
class VaultRuntimeTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun hex(b: ByteArray) = b.joinToString("") { "%02x".format(it) }

    /** RFC 7914 section 11, PBKDF2-HMAC-SHA256 P="passwd" S="salt" c=1: the first 32 bytes of its 64-byte output. */
    @Test fun pbkdf2MatchesThePublishedVector() {
        val out = Pbkdf2KeyDerivation.derive("passwd".toCharArray(), "salt".toByteArray(), 1)
        assertEquals("55ac046e56e3089fec1691c22544b605f94185216dde0465e68b9d57c20dacbc", hex(out))
    }

    @Test fun pbkdf2LeavesTheCallersSecretAloneAndDependsOnEveryInput() {
        val pin = "123456".toCharArray()
        val salt = ByteArray(16) { 1 }
        val a = Pbkdf2KeyDerivation.derive(pin, salt, 2)
        assertArrayEquals("123456".toCharArray(), pin)
        assertArrayEquals(a, Pbkdf2KeyDerivation.derive(pin, salt, 2))
        assertFalse(a.contentEquals(Pbkdf2KeyDerivation.derive("123457".toCharArray(), salt, 2)))
        assertFalse(a.contentEquals(Pbkdf2KeyDerivation.derive(pin, ByteArray(16) { 2 }, 2)))
        assertFalse(a.contentEquals(Pbkdf2KeyDerivation.derive(pin, salt, 3)))
        assertEquals(32, a.size)
    }

    @Test fun secureEntropyGivesFreshBytesOfTheRequestedSize() {
        val a = SecureEntropy.bytes(16); val b = SecureEntropy.bytes(16)
        assertEquals(16, a.size)
        assertFalse(a.contentEquals(b))
    }

    /** The real vault with the real derivation at the policy's minimum iteration count. */
    @Test fun aPinPreparedWithPbkdf2AuthenticatesAndAWrongPinDoesNot() {
        val dir = tmp.newFolder()
        val store = JointAuthorityStore(dir)
        val clock = ProvenRunningClock(FileClockStateStore(File(dir, "clock")), { BootTime("boot-1", 1_000) }, { store.reserveEpoch() })
        val vault = PinVault(store.credentials, Pbkdf2KeyDerivation, SecureEntropy, clock)
        val person = TrustedPersonId("synthetic")
        val credential = vault.prepareCredential("246810".toCharArray()).credential
        assertEquals(600_000, credential.pin.iterations)
        assertTrue(store.initialize(CredentialState(Mode.SELF_MANAGED, mapOf(person to credential)), WaitingState(storageGeneration = "live"), ElevationRecord()))
        val action = PrivilegedAction("probe", "probe")
        assertTrue(vault.authenticate(person, "246810".toCharArray(), action) is PinResult.Authorized)
        assertTrue(vault.authenticate(person, "246811".toCharArray(), action) is PinResult.Wrong)
    }

    @Test fun clockStoreComparesAndSetsAndSurvivesReopen() {
        val dir = tmp.newFolder()
        val store = FileClockStateStore(dir)
        assertNull(store.read())
        val first = ClockRecord(RunningTime("epoch-a", 0), BootTime("boot-1", 10))
        val second = ClockRecord(RunningTime("epoch-a", 500), BootTime("boot-1", 510))
        assertFalse(store.compareAndSet(second, first))
        assertTrue(store.compareAndSet(null, first))
        assertFalse(store.compareAndSet(null, second))
        assertTrue(store.compareAndSet(first, second))
        assertEquals(second, FileClockStateStore(dir).read())
        assertFalse(FileClockStateStore(dir).compareAndSet(first, second))
    }

    /** A damaged or missing primary reads null, even with an older good copy beside it, so the clock repairs to a fresh epoch. */
    @Test fun aDamagedClockReadsNullAndNeverFallsBackToAnOlderCopy() {
        val dir = tmp.newFolder()
        val store = FileClockStateStore(dir)
        val first = ClockRecord(RunningTime("epoch-a", 0), BootTime("boot-1", 10))
        val second = ClockRecord(RunningTime("epoch-a", 500), BootTime("boot-1", 510))
        assertTrue(store.compareAndSet(null, first)); assertTrue(store.compareAndSet(first, second))
        val good = File(dir, "clock.rec").readBytes()
        File(dir, "clock.lkg").writeBytes(good)
        File(dir, "clock.rec").writeBytes(byteArrayOf(1, 2, 3))
        assertNull(FileClockStateStore(dir).read())
        File(dir, "clock.rec").delete()
        assertNull(FileClockStateStore(dir).read())
        val fresh = ClockRecord(RunningTime("epoch-b", 0), BootTime("boot-1", 600))
        assertFalse(store.compareAndSet(second, fresh))
        assertTrue(store.compareAndSet(null, fresh))
        assertEquals(fresh, FileClockStateStore(dir).read())
    }

    /** Two instances for one directory, one reached through a symlink, share one lock: the slower writer sees the faster one's record. */
    @Test fun storesForOneDirectoryShareOneCompareAndSet() {
        val dir = tmp.newFolder()
        val alias = File(tmp.root, "alias")
        java.nio.file.Files.createSymbolicLink(alias.toPath(), dir.toPath())
        val a = FileClockStateStore(dir); val b = FileClockStateStore(alias)
        val original = ClockRecord(RunningTime("epoch-a", 0), BootTime("boot-1", 0))
        val next = ClockRecord(RunningTime("epoch-a", 100), BootTime("boot-1", 100))
        assertTrue(a.compareAndSet(null, original))
        val results = java.util.Collections.synchronizedList(mutableListOf<Boolean>())
        val start = java.util.concurrent.CountDownLatch(1)
        val writers = List(8) { i ->
            val store = if (i % 2 == 0) a else b
            Thread { start.await(); results += store.compareAndSet(original, next.copy(proven = RunningTime("epoch-a", 100L + i))) }
        }
        writers.forEach { it.start() }; start.countDown(); writers.forEach { it.join(10_000) }
        assertEquals(1, results.count { it })
        assertEquals(7, results.count { !it })
        assertEquals(a.read(), b.read())
    }

    @Test fun provenClockAdvancesWithinABootAndCreditsOnlyTheNewBootAfterARestart() {
        val dir = tmp.newFolder()
        val ids = JointAuthorityStore(tmp.newFolder())
        var now = BootTime("boot-1", 1_000)
        val clock = ProvenRunningClock(FileClockStateStore(dir), { now }, { ids.reserveEpoch() })
        val start = clock.checkpoint()!!
        assertEquals(0L, start.proven.totalMillis)
        now = BootTime("boot-1", 61_000)
        assertEquals(60_000L, clock.checkpoint()!!.proven.totalMillis)
        now = BootTime("boot-2", 5_000)
        val afterReboot = ProvenRunningClock(FileClockStateStore(dir), { now }, { ids.reserveEpoch() }).checkpoint()!!
        assertEquals(65_000L, afterReboot.proven.totalMillis)
        assertEquals(start.proven.epoch, afterReboot.proven.epoch)
    }

    @Test fun repairedClocksGetNewNeverReusedEpochs() {
        val ids = JointAuthorityStore(tmp.newFolder())
        val epochs = List(5) { ids.reserveEpoch() }
        assertEquals(5, epochs.toSet().size)
        epochs.forEach { assertTrue(it, it.startsWith("epoch-")) }
        val dir = tmp.newFolder()
        val clock = ProvenRunningClock(FileClockStateStore(dir), { BootTime("boot-1", 0) }, { ids.reserveEpoch() })
        val a = clock.checkpoint()!!.proven.epoch
        File(dir, "clock.rec").delete(); File(dir, "clock.lkg").delete()
        val b = clock.checkpoint()!!.proven.epoch
        assertNotEquals(a, b)
        assertTrue(b !in epochs)
    }

    @Test fun anUnreservableEpochGrantsNoTime() {
        val idsDir = tmp.newFolder()
        File(idsDir, "reserved").writeText("not a directory")
        val ids = JointAuthorityStore(idsDir)
        assertEquals("", ids.reserveEpoch())
        assertNull(ProvenRunningClock(FileClockStateStore(tmp.newFolder()), { BootTime("boot-1", 0) }, { ids.reserveEpoch() }).checkpoint())
    }
}
