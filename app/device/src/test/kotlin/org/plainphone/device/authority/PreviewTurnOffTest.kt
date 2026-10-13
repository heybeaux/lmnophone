// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*

class PreviewTurnOffTest {
    @get:Rule val tmp = TemporaryFolder()
    private var sequence = 0
    private val clock = RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) }
    private fun pin() = CharArray(6) { '1' }
    private inner class Rig {
        val store = JointAuthorityStore(tmp.newFolder())
        val vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        val menu = PreviewPinAccess(store, vault)
        val turnOff = PreviewTurnOff(store, vault, clock)
        init { assertTrue(menu.enroll(pin()) is PinEnrollmentResult.Created) }
    }
    @Test fun menuPinCannotStartAndBothConfirmationsAreRequired() {
        val r = Rig()
        assertEquals(PinAccessResult.Open, r.menu.authenticate(pin()))
        assertEquals(WaitingOutcome.REFUSED, r.turnOff.start("flow", true, true))
        for ((first, second) in listOf(false to true, true to false, false to false)) {
            assertEquals(PinAccessResult.Open, r.turnOff.authenticate("flow", pin()))
            assertEquals(WaitingOutcome.REFUSED, r.turnOff.start("flow", first, second))
            assertNull(r.store.waitingStore.read()!!.operation)
        }
        assertEquals(PinAccessResult.Open, r.turnOff.authenticate("flow", pin()))
        assertEquals(WaitingOutcome.PENDING, r.turnOff.start("flow", true, true))
    }
    @Test fun wrongCancelledStaleAndRecreatedPinsStartNothing() {
        val r = Rig()
        assertTrue(r.turnOff.authenticate("wrong", CharArray(6) { '2' }) is PinAccessResult.Wrong)
        assertEquals(WaitingOutcome.REFUSED, r.turnOff.start("wrong", true, true))
        r.turnOff.authenticate("cancelled", pin()); r.turnOff.close("cancelled")
        assertEquals(WaitingOutcome.REFUSED, r.turnOff.start("cancelled", true, true))
        r.turnOff.authenticate("old", pin()); r.turnOff.authenticate("new", pin())
        assertEquals(WaitingOutcome.REFUSED, r.turnOff.start("old", true, true))
        assertEquals(WaitingOutcome.REFUSED, PreviewTurnOff(r.store, r.vault, clock).start("new", true, true))
        assertNull(r.store.waitingStore.read()!!.operation)
    }
    @Test fun secondStartIsBusyAndNeverReplacesTheWait() {
        val r = Rig()
        r.turnOff.authenticate("one", pin())
        assertEquals(WaitingOutcome.PENDING, r.turnOff.start("one", true, true))
        val id = r.store.waitingStore.read()!!.operation!!.identity.requestId
        r.turnOff.authenticate("two", pin())
        assertEquals(WaitingOutcome.BUSY, r.turnOff.start("two", true, true))
        assertEquals(id, r.store.waitingStore.read()!!.operation!!.identity.requestId)
    }
}
