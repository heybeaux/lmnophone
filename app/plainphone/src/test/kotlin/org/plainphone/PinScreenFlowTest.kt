// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.authority.*

class PinScreenFlowTest {
    private class Port : PinScreenPort {
        lateinit var entry: (PinEntry) -> Unit
        lateinit var enrolled: (PinEnrollmentResult) -> Unit
        lateinit var checked: (PinAccessResult) -> Unit
        lateinit var recovered: (RecoveryAccessResult) -> Unit
        var recoveries = 0
        var rotations = 0
        override fun recoveryWaits(done: (org.plainphone.policy.CredentialWaits?) -> Unit) { done(org.plainphone.policy.CredentialWaits(0, 0)) }
        override fun rotateRecovery(pin: CharArray, done: (RecoveryAccessResult) -> Unit) { rotations++; pin.fill('\u0000'); recovered = done }
        override fun recover(code: CharArray, newPin: CharArray, done: (RecoveryAccessResult) -> Unit) {
            recoveries++; code.fill('\u0000'); newPin.fill('\u0000'); recovered = done
        }
        var checks = 0
        var starts = 0
        lateinit var started: (org.plainphone.policy.WaitingOutcome) -> Unit
        override fun authenticateTurnOff(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit) = authenticate(pin, done)
        override fun startTurnOff(flow: String, first: Boolean, second: Boolean, done: (org.plainphone.policy.WaitingOutcome) -> Unit) {
            assertTrue(first && second); starts++; started = done
        }
        override fun closeTurnOff(flow: String) {}
        val changeFlows = mutableListOf<String>()
        val closedChanges = mutableListOf<String>()
        var changes = 0
        lateinit var changed: (PinChangeOutcome) -> Unit
        override fun authenticateChange(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit) { changeFlows += flow; authenticate(pin, done) }
        override fun changePin(flow: String, newPin: CharArray, done: (PinChangeOutcome) -> Unit) {
            assertEquals(changeFlows.last(), flow); changes++; newPin.fill('\u0000'); changed = done
        }
        override fun closeChange(flow: String) { closedChanges += flow }

        override fun entry(done: (PinEntry) -> Unit) { entry = done }
        override fun enroll(pin: CharArray, done: (PinEnrollmentResult) -> Unit) { pin.fill('\u0000'); enrolled = done }
        override fun authenticate(pin: CharArray, done: (PinAccessResult) -> Unit) { checks++; pin.fill('\u0000'); checked = done }
    }
    @Test fun turnOffUsesFreshPinAndBothConfirmationsAndBusyHasItsOwnPage() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        f.turnOff(); assertEquals(PinPage.OFF_INTRO, f.state.page)
        f.continueTurnOff(); assertEquals(PinPage.OFF_FIRST, f.state.page)
        f.continueTurnOff(); assertEquals(PinPage.OFF_PIN, f.state.page)
        f.submit(CharArray(6) { '2' }); p.checked(PinAccessResult.Wrong(0))
        assertEquals(PinPage.OFF_PIN, f.state.page); assertEquals(0, p.starts)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.OFF_LAST, f.state.page); assertEquals(0, p.starts)
        f.continueTurnOff(); f.continueTurnOff()
        assertEquals(1, p.starts)
        p.started(org.plainphone.policy.WaitingOutcome.BUSY)
        assertEquals(PinPage.WAIT_BUSY, f.state.page)
    }
    @Test fun uncertainStartDoesNotClaimNothingChanged() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        f.turnOff(); f.continueTurnOff(); f.continueTurnOff()
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        f.continueTurnOff(); p.started(org.plainphone.policy.WaitingOutcome.NOT_READY)
        assertEquals(PinPage.UNAVAILABLE, f.state.page)
    }
    @Test fun cancelledFreshPinCannotReopenLastConfirmation() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        f.turnOff(); f.continueTurnOff(); f.continueTurnOff()
        f.submit(CharArray(6) { '1' }); f.close(); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.LOADING, f.state.page); assertEquals(0, p.starts)
    }
    @Test fun enrollmentRequiresFreshEntryAndWrongPinCannotOpenMenu() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.SETUP)
        f.submit(CharArray(6) { '1' }); p.enrolled(PinEnrollmentResult.Created(OneTimeRecoveryCode(CharArray(32) { 'a' })))
        assertEquals(PinPage.RECOVERY_ISSUED, f.state.page)
        f.codeWritten()
        assertEquals(PinPage.ENTER, f.state.page)
        f.submit(CharArray(6) { '2' }); p.checked(PinAccessResult.Wrong(0))
        assertEquals(PinPage.ENTER, f.state.page); assertTrue(f.state.wrong)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.MENU, f.state.page)
    }
    @Test fun leavingInvalidatesAnInFlightSuccessfulPin() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' }); f.close(); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.LOADING, f.state.page)
        f.open(); p.entry(PinEntry.ENTER)
        assertEquals(PinPage.ENTER, f.state.page)
    }
    @Test fun rapidDoubleSubmitDoesNotConsumeTwoPinAttempts() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' })
        val second = CharArray(6) { '2' }; f.submit(second)
        assertEquals(1, p.checks); assertTrue(second.all { it == '\u0000' })
    }
    @Test fun unsupportedActionsStayUnavailableAndCannotRestoreAccessAfterLeaving() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        f.unsupported(); assertEquals(PinPage.NOT_IN_BUILD, f.state.page)
        f.close(); f.backFromUnsupported()
        assertEquals(PinPage.LOADING, f.state.page)
    }

    private fun menu(): Pair<Port, PinScreenFlow> {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.submit(CharArray(6) { '1' }); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.MENU, f.state.page)
        return p to f
    }
    private fun pin(c: Char, n: Int = 6) = CharArray(n) { c }
    @Test fun changePinAsksOldThenNewTwiceAndShowsTheTrueResult() {
        val (p, f) = menu()
        f.changePin(); assertEquals(PinPage.CHANGE_OLD, f.state.page)
        f.submit(pin('2')); p.checked(PinAccessResult.Wrong(0))
        assertEquals(PinPage.CHANGE_OLD, f.state.page); assertTrue(f.state.wrong); assertEquals(0, p.changes)
        f.submit(pin('1')); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.CHANGE_NEW, f.state.page)
        f.submitNewPin(pin('3', 7), pin('3', 7))
        assertTrue(f.state.busy); assertEquals(1, p.changes)
        p.changed(PinChangeOutcome.CHANGED); assertEquals(PinPage.CHANGE_DONE, f.state.page)
        f.back(); assertEquals(PinPage.MENU, f.state.page)
    }
    @Test fun failedChangeHasItsOwnPage() {
        val (p, f) = menu()
        f.changePin(); f.submit(pin('1')); p.checked(PinAccessResult.Open)
        f.submitNewPin(pin('3'), pin('3')); p.changed(PinChangeOutcome.FAILED)
        assertEquals(PinPage.CHANGE_FAILED, f.state.page)
    }
    @Test fun mismatchOrInvalidNewPinWritesNothingAndZeroesBoth() {
        val (p, f) = menu()
        f.changePin(); f.submit(pin('1')); p.checked(PinAccessResult.Open)
        val a = pin('3'); val b = pin('4')
        f.submitNewPin(a, b)
        assertEquals(PinPage.CHANGE_NEW, f.state.page); assertTrue(f.state.mismatch); assertEquals(0, p.changes)
        assertTrue(a.all { it == '\u0000' } && b.all { it == '\u0000' })
        f.submitNewPin(pin('3', 5), pin('3', 5)); assertEquals(0, p.changes)
        f.submitNewPin("abcdef".toCharArray(), "abcdef".toCharArray()); assertEquals(0, p.changes)
    }
    @Test fun lockoutDuringOldPinShowsTheLockedPage() {
        val (p, f) = menu()
        f.changePin(); f.submit(pin('2')); p.checked(PinAccessResult.Wrong(30_000))
        assertEquals(PinPage.LOCKED, f.state.page)
        f.changePin(); assertEquals(PinPage.LOCKED, f.state.page)
        val (q, g) = menu()
        g.changePin(); g.submit(pin('1')); q.checked(PinAccessResult.Delayed(10_000))
        assertEquals(PinPage.LOCKED, g.state.page); assertEquals(0, q.changes)
    }
    @Test fun backAtEachStepWritesNothingAndDropsTheGrant() {
        val (p, f) = menu()
        f.changePin(); f.back()
        assertEquals(PinPage.MENU, f.state.page); assertEquals(1, p.closedChanges.size)
        f.changePin(); f.submit(pin('1')); p.checked(PinAccessResult.Open)
        f.back(); assertEquals(PinPage.MENU, f.state.page); assertEquals(0, p.changes)
        assertEquals(p.changeFlows.last(), p.closedChanges.last())
        // A new attempt uses a new flow, so the dropped grant can't be reused.
        f.changePin(); f.submit(pin('1')); p.checked(PinAccessResult.Open)
        assertNotEquals(p.changeFlows[0], p.changeFlows[1])
        f.submitNewPin(pin('3'), pin('3'))
        f.back(); assertTrue(f.state.busy); assertEquals(PinPage.CHANGE_NEW, f.state.page)
        p.changed(PinChangeOutcome.CHANGED); assertEquals(PinPage.CHANGE_DONE, f.state.page)
    }
    @Test fun leavingTheScreenDropsTheGrantAndAPendingCheckIsIgnored() {
        val (p, f) = menu()
        f.changePin(); f.submit(pin('1')); f.close(); p.checked(PinAccessResult.Open)
        assertEquals(PinPage.LOADING, f.state.page)
        assertTrue(p.changeFlows.last() in p.closedChanges)
        f.submitNewPin(pin('3'), pin('3')); assertEquals(0, p.changes)
    }
    @Test fun changePinOnlyFromTheMenu() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.ENTER)
        f.changePin(); assertEquals(PinPage.ENTER, f.state.page)
    }
    @Test fun recoveryDropsChangeGrantRequiresNewPinTwiceAndDoesNotOpenMenu() {
        val (p, f) = menu()
        f.changePin(); f.useRecovery()
        assertEquals(PinPage.RECOVERY, f.state.page)
        assertTrue(p.closedChanges.isNotEmpty())
        f.submitRecovery(CharArray(32) { 'a' })
        assertEquals(PinPage.RECOVERY_NEW, f.state.page)
        f.submitNewPin(pin('2'), pin('3')); assertEquals(0, p.recoveries)
        f.submitNewPin(pin('2'), pin('2')); f.submitNewPin(pin('2'), pin('2'))
        assertEquals(1, p.recoveries)
        p.recovered(RecoveryAccessResult.WrongCode(30_000, 20_000))
        assertEquals(PinPage.RECOVERY_LOCKED, f.state.page)
        assertEquals(20_000L, f.state.pinWaitMillis)
        f.enterPin(); assertEquals(PinPage.ENTER, f.state.page)
    }
    @Test fun rotationRequiresItsOwnPinAndCodeIsClearedOnExitAndStaleCallbacks() {
        val (p, f) = menu()
        f.newRecoveryCode(); assertEquals(PinPage.RECOVERY_PIN, f.state.page)
        f.submit(pin('1')); assertEquals(1, p.rotations)
        val raw = CharArray(32) { 'b' }; val once = OneTimeRecoveryCode(raw)
        p.recovered(RecoveryAccessResult.Issued(once))
        assertEquals(PinPage.RECOVERY_ISSUED, f.state.page)
        assertNull(once.take())
        assertFalse(f.state.toString().contains(raw.concatToString()))
        f.codeWritten(); assertTrue(raw.all { it == '\u0000' })
        assertEquals(PinPage.ENTER, f.state.page)
        val lateRaw = CharArray(32) { 'c' }
        f.close(); p.recovered(RecoveryAccessResult.Issued(OneTimeRecoveryCode(lateRaw)))
        assertTrue(lateRaw.all { it == '\u0000' })
        assertEquals(PinPage.LOADING, f.state.page)
    }

}
