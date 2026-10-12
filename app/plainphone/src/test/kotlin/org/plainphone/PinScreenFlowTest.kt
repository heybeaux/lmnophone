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
        var checks = 0
        override fun entry(done: (PinEntry) -> Unit) { entry = done }
        override fun enroll(pin: CharArray, done: (PinEnrollmentResult) -> Unit) { pin.fill('\u0000'); enrolled = done }
        override fun authenticate(pin: CharArray, done: (PinAccessResult) -> Unit) { checks++; pin.fill('\u0000'); checked = done }
    }
    @Test fun enrollmentRequiresFreshEntryAndWrongPinCannotOpenMenu() {
        val p = Port(); val f = PinScreenFlow(p) {}
        f.open(); p.entry(PinEntry.SETUP)
        f.submit(CharArray(6) { '1' }); p.enrolled(PinEnrollmentResult.CREATED)
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
}
