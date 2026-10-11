// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.runtime

import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.authority.DirectorySync
import org.plainphone.core.BootTime
import org.plainphone.core.RunningTime
import org.plainphone.policy.ClockRecord
import org.plainphone.policy.ProvenRunningClock
import org.plainphone.policy.SecretVerifier

/** The PLA-248 review probes, kept as regressions. Two are adapted to the fix; each says how. */
class VaultRuntimeReviewTest {
    @get:Rule val tmp = TemporaryFolder()
    private val hour = 3_600_000L

    @Test fun damagedPrimaryMustStartAFreshZeroEpoch() {
        val dir = tmp.newFolder()
        var now = BootTime("boot-1", 0)
        var epoch = 0
        val clock = ProvenRunningClock(FileClockStateStore(dir), { now }, { "epoch-${++epoch}" })
        clock.checkpoint()!!
        now = BootTime("boot-1", 30 * hour)
        val before = clock.checkpoint()!!
        File(dir, "clock.rec").writeBytes(byteArrayOf(1, 2, 3))
        val after = clock.checkpoint()!!
        println("damaged primary: before=${before.proven} after=${after.proven}")
        assertNotEquals("TPC damaged-P requirement: fresh epoch", before.proven.epoch, after.proven.epoch)
        assertEquals(0L, after.proven.totalMillis)
    }

    @Test fun fallbackMustNotResurrectAnEpochAndMatureANewWait() {
        val dir = tmp.newFolder()
        var now = BootTime("boot-1", 0)
        var epoch = 0
        val clock = ProvenRunningClock(FileClockStateStore(dir), { now }, { "epoch-${++epoch}" })
        clock.checkpoint()!!
        now = BootTime("boot-1", 30 * hour)
        clock.checkpoint()!!
        // The approved spec explicitly requires repair for a backwards same-boot reading.
        now = BootTime("boot-1", 30 * hour - 1_000)
        val newWait = clock.checkpoint()!!.proven
        assertEquals("epoch-2", newWait.epoch)
        assertEquals(0L, newWait.totalMillis)
        // Only two seconds pass after that repair. The primary is damaged before another checkpoint.
        now = BootTime("boot-1", 30 * hour + 1_000)
        File(dir, "clock.rec").writeBytes(byteArrayOf(1, 2, 3))
        val after = clock.checkpoint()!!.proven
        println("epoch resurrection: wait=$newWait after=$after age=${after.since(newWait)} ms")
        assertTrue("a two-second-old wait must not have 24 hours of authority", after.since(newWait) < 24 * hour)
    }

    /**
     * Adapted: writer B now waits up to 10 s for A's transaction and then gives up (not
     * acknowledged), so A is held for up to 30 s instead of 10 s. [aWaitingWriterSeesTheFirstWritersRecord]
     * covers B waiting it out.
     */
    @Test fun distinctStoreInstancesMustNotBothAcceptTheSameExpectedRecord() {
        val realDir = tmp.newFolder()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val armed = AtomicBoolean(false)
        // Pause writer A at the first filesystem operation in save(), after its CAS comparison.
        // This models ordinary thread scheduling without changing any production code.
        val pausingDir = object : File(realDir.path) {
            override fun isDirectory(): Boolean {
                if (armed.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.await(30, TimeUnit.SECONDS))
                }
                return super.isDirectory()
            }
        }
        val a = FileClockStateStore(pausingDir)
        val b = FileClockStateStore(realDir)
        val original = ClockRecord(RunningTime("epoch-1", 0), BootTime("boot-1", 0))
        val first = ClockRecord(RunningTime("epoch-1", 100), BootTime("boot-1", 100))
        val second = ClockRecord(RunningTime("epoch-1", 200), BootTime("boot-1", 200))
        assertTrue(a.compareAndSet(null, original))
        armed.set(true)
        val result = AtomicReference<Boolean>()
        val error = AtomicReference<Throwable>()
        val worker = Thread { try { result.set(a.compareAndSet(original, first)) } catch (t: Throwable) { error.set(t) } }
        worker.start()
        var secondAccepted = false
        try {
            assertTrue(entered.await(10, TimeUnit.SECONDS))
            secondAccepted = b.compareAndSet(original, second)
        } finally {
            release.countDown()
            worker.join(30_000)
        }
        error.get()?.let { throw it }
        assertFalse(worker.isAlive)
        println("CAS: writer A=${result.get()} writer B=$secondAccepted final=${b.read()}")
        assertFalse("two writers may not both acknowledge the same expected record", result.get() == true && secondAccepted)
        assertEquals(if (secondAccepted) second else first, b.read())
    }

    /** B waits while A is paused after its comparison; once A lands, B's stale expectation is refused. */
    @Test fun aWaitingWriterSeesTheFirstWritersRecord() {
        val realDir = tmp.newFolder()
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val armed = AtomicBoolean(false)
        val pausingDir = object : File(realDir.path) {
            override fun isDirectory(): Boolean {
                if (armed.compareAndSet(true, false)) {
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
                return super.isDirectory()
            }
        }
        val a = FileClockStateStore(pausingDir)
        val b = FileClockStateStore(realDir)
        val original = ClockRecord(RunningTime("epoch-1", 0), BootTime("boot-1", 0))
        val first = ClockRecord(RunningTime("epoch-1", 100), BootTime("boot-1", 100))
        val second = ClockRecord(RunningTime("epoch-1", 200), BootTime("boot-1", 200))
        assertTrue(a.compareAndSet(null, original))
        armed.set(true)
        val resultA = AtomicReference<Boolean>(); val resultB = AtomicReference<Boolean>()
        val writerA = Thread { resultA.set(a.compareAndSet(original, first)) }
        writerA.start()
        assertTrue(entered.await(10, TimeUnit.SECONDS))
        val writerB = Thread { resultB.set(b.compareAndSet(original, second)) }
        writerB.start()
        Thread.sleep(200)
        assertTrue("B waits for A's transaction", writerB.isAlive)
        release.countDown()
        writerA.join(10_000); writerB.join(10_000)
        assertEquals(true, resultA.get())
        assertEquals(false, resultB.get())
        assertEquals(first, b.read())
    }

    @Test fun failedClockSaveGrantsNoReadingAndCanRecover() {
        val dir = tmp.newFolder()
        var now = BootTime("boot-1", 0)
        val clock = ProvenRunningClock(FileClockStateStore(dir), { now }, { "epoch-1" })
        val initial = clock.checkpoint()!!
        val temp = File(dir, "clock.rec.tmp")
        assertTrue(temp.mkdir())
        now = BootTime("boot-1", 1_000)
        assertNull(clock.checkpoint())
        assertEquals(initial.proven, FileClockStateStore(dir).read()!!.proven)
        assertTrue(temp.delete())
        assertEquals(1_000L, clock.checkpoint()!!.proven.totalMillis)
    }

    /**
     * Adapted: the probe expected the older copy to keep 12 h after a damaged primary. Finding 1
     * requires a fresh zero epoch instead, so the damaged primary now restarts proven time at zero.
     */
    @Test fun fallbackWithinOneEpochAcrossRebootDoesNotAddUnprovenTime() {
        val dir = tmp.newFolder()
        var now = BootTime("boot-1", 0)
        var epoch = 0
        val clock = ProvenRunningClock(FileClockStateStore(dir), { now }, { "epoch-${++epoch}" })
        clock.checkpoint()!!
        now = BootTime("boot-1", 10 * hour)
        clock.checkpoint()!!
        now = BootTime("boot-2", hour)
        assertEquals(11 * hour, clock.checkpoint()!!.proven.totalMillis)
        now = BootTime("boot-2", 2 * hour)
        File(dir, "clock.rec").writeBytes(byteArrayOf(1, 2, 3))
        assertEquals(RunningTime("epoch-2", 0), clock.checkpoint()!!.proven)
    }

    @Test fun refusedEpochReservationCannotBeReusedAfterReopen() {
        val dir = tmp.newFolder()
        val failed = JointAuthorityStore(dir).withEntropy { n -> ByteArray(n) { 7 } }
            .withDirectorySync(DirectorySync { throw java.io.IOException("injected sync failure") })
        assertEquals("", failed.reserveEpoch())
        val reopened = JointAuthorityStore(dir).withEntropy { n -> ByteArray(n) { 7 } }
        assertEquals("", reopened.reserveEpoch())
        val fresh = JointAuthorityStore(dir).withEntropy { n -> ByteArray(n) { 8 } }
        assertEquals("epoch-" + "08".repeat(16), fresh.reserveEpoch())
    }

    @Test fun verifierRejectsWeakParameters() {
        for (iterations in listOf(1, 599_999)) {
            assertThrows(IllegalArgumentException::class.java) { SecretVerifier(ByteArray(16), ByteArray(32), iterations) }
        }
        assertThrows(IllegalArgumentException::class.java) { SecretVerifier(ByteArray(16), ByteArray(31), 600_000) }
        assertThrows(IllegalArgumentException::class.java) { SecretVerifier(ByteArray(15), ByteArray(32), 600_000) }
    }
}
