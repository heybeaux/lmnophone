// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.authority.PinAccessResult

class PreviewAppAdditionTest {
    private var saves = 0
    private var applies = 0
    private var eligible = true
    private var active = true
    private var auth: PinAccessResult = PinAccessResult.Open
    private var ready = true
    private val addition = PreviewAppAddition(
        authenticate = { id, _ -> assertEquals("calculator", id); auth },
        save = { if (eligible) { saves++; true } else false },
        apply = { applies++; ready })
    @Test fun freshPinAndEligibilityRequiredEveryTime() {
        assertEquals(AppAdditionResult.Added, addition.add("calculator", charArrayOf('1')) { active })
        auth = PinAccessResult.Wrong(0)
        assertEquals(AppAdditionResult.PinRejected(auth), addition.add("calculator", charArrayOf('1')) { active })
        auth = PinAccessResult.Open; eligible = false
        assertEquals(AppAdditionResult.SaveFailed, addition.add("calculator", charArrayOf('1')) { active })
        assertEquals(1, saves); assertEquals(1, applies)
    }
    @Test fun backAndPauseWriteNothingAndClearPin() {
        active = false
        val pin = charArrayOf('1')
        assertEquals(AppAdditionResult.Cancelled, addition.add("calculator", pin) { active })
        assertEquals('\u0000', pin[0]); assertEquals(0, saves)
    }
    @Test fun cancellationDuringAuthenticationWritesNothing() {
        val subject = PreviewAppAddition({ _, _ -> active = false; PinAccessResult.Open }, { saves++; true }, { true })
        assertEquals(AppAdditionResult.Cancelled, subject.add("calculator", charArrayOf('1')) { active })
        assertEquals(0, saves)
    }
    @Test fun failedSaveAndFailedReadbackNeverSayDone() {
        val failing = PreviewAppAddition({ _, _ -> PinAccessResult.Open }, { throw java.io.IOException() }, { applies++; true })
        assertEquals(AppAdditionResult.SaveFailed, failing.add("calculator", charArrayOf('1')) { true })
        assertEquals(0, applies)
        ready = false
        assertEquals(AppAdditionResult.CheckFailed, addition.add("calculator", charArrayOf('1')) { true })
    }
}
