// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SystemUpdatePostponeTest {
    private class FakePort(
        var owner: Boolean = true,
        var held: UpdatePolicyRead = UpdatePolicyRead(UpdatePolicyKind.NONE, 0),
        /** What a write leaves behind; null holds exactly what was written. */
        var afterWrite: UpdatePolicyRead? = null,
        var failRead: Boolean = false,
        var failWrite: Boolean = false,
        var failClear: Boolean = false,
    ) : SystemUpdatePolicyPort {
        var clears = 0
        val writes = mutableListOf<UpdatePolicyKind>()
        var reads = 0
        override fun isDeviceOwner() = owner
        override fun read(): UpdatePolicyRead {
            reads++
            if (failRead) throw SecurityException(DETAIL)
            return held
        }
        override fun write(kind: UpdatePolicyKind) {
            if (failWrite) throw IllegalStateException(DETAIL)
            writes += kind
            held = afterWrite ?: UpdatePolicyRead(kind, 0)
        }
        override fun clear() {
            if (failClear) throw IllegalStateException(DETAIL)
            clears++
            held = UpdatePolicyRead(UpdatePolicyKind.NONE, 0)
        }
    }

    private val logs = mutableListOf<String>()

    private fun postpone(port: FakePort) = SystemUpdatePostpone(port) { logs += it }

    @Test fun writesPostponeNotAutomaticOrWindowedAndNoFreezePeriods() {
        val port = FakePort()
        assertEquals(PostponeStatus.SET, postpone(port).apply())
        assertEquals(listOf(UpdatePolicyKind.POSTPONE), port.writes)
        assertEquals(UpdatePolicyRead(UpdatePolicyKind.POSTPONE, 0), SystemUpdatePostpone.TARGET)
    }

    @Test fun replacesAnAutomaticPolicy() {
        val port = FakePort(held = UpdatePolicyRead(UpdatePolicyKind.AUTOMATIC, 0))
        assertEquals(PostponeStatus.SET, postpone(port).apply())
        assertEquals(listOf(UpdatePolicyKind.POSTPONE), port.writes)
    }

    @Test fun replacesAPostponeThatCarriesFreezePeriods() {
        val port = FakePort(held = UpdatePolicyRead(UpdatePolicyKind.POSTPONE, 1))
        assertEquals(PostponeStatus.SET, postpone(port).apply())
        assertEquals(listOf(UpdatePolicyKind.POSTPONE), port.writes)
    }

    @Test fun reapplyIsIdempotent() {
        val port = FakePort()
        val postpone = postpone(port)
        assertEquals(PostponeStatus.SET, postpone.apply())
        assertEquals(PostponeStatus.ALREADY_SET, postpone.apply())
        assertEquals(PostponeStatus.ALREADY_SET, postpone.apply())
        assertEquals(1, port.writes.size)
    }

    @Test fun readbackMismatchIsAPlainStatusAndRetriedNextReapply() {
        val port = FakePort(afterWrite = UpdatePolicyRead(UpdatePolicyKind.AUTOMATIC, 0))
        val postpone = postpone(port)
        assertEquals(PostponeStatus.MISMATCH, postpone.apply())
        assertTrue(logs.last(), logs.last().contains("mismatch"))
        port.afterWrite = null
        assertEquals(PostponeStatus.SET, postpone.apply())
        assertEquals(2, port.writes.size)
    }

    @Test fun failuresNeverThrow() {
        assertEquals(PostponeStatus.FAILED, postpone(FakePort(failRead = true)).apply())
        assertEquals(PostponeStatus.FAILED, postpone(FakePort(failWrite = true)).apply())
    }

    @Test fun notDeviceOwnerTouchesNothing() {
        val port = FakePort(owner = false)
        assertEquals(PostponeStatus.NOT_OWNER, postpone(port).apply())
        assertEquals(0, port.reads)
        assertTrue(port.writes.isEmpty())
    }

    /**
     * The driver's undo (API 35 keeps the policy after `dpm remove-active-admin` and reboot): release clears
     * it and later re-applies in this process leave it cleared, so DO can then be removed.
     */
    @Test fun releaseClearsAndLaterReappliesLeaveItCleared() {
        val port = FakePort()
        val postpone = postpone(port)
        postpone.apply()
        assertEquals(PostponeStatus.RELEASED, postpone.release())
        assertEquals(UpdatePolicyRead(UpdatePolicyKind.NONE, 0), port.held)
        assertEquals(PostponeStatus.RELEASED, postpone.apply())
        assertEquals(1, port.writes.size)
        assertEquals(1, port.clears)
    }

    @Test fun releaseNeedsDeviceOwnerAndNeverThrows() {
        assertEquals(PostponeStatus.NOT_OWNER, postpone(FakePort(owner = false)).release())
        val failing = postpone(FakePort(failClear = true))
        assertEquals(PostponeStatus.FAILED, failing.release())
        assertEquals(PostponeStatus.SET, failing.apply())
    }

    /** Logs carry only the status and policy shape: no build fingerprint, serial or exception message. */
    @Test fun logsCarryNoDeviceDetails() {
        postpone(FakePort(failRead = true)).apply()
        postpone(FakePort(afterWrite = UpdatePolicyRead(UpdatePolicyKind.WINDOWED, 0))).apply()
        assertEquals(2, logs.size)
        logs.forEach { assertFalse(it, it.contains(DETAIL)) }
    }
}

private const val DETAIL = "serial ZX0000 fingerprint brand/product"
