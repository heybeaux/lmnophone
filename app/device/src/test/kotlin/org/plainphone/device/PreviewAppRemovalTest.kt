// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.authority.PinAccessResult

class PreviewAppRemovalTest {
    private var saved = 0
    private var applied = 0
    private var result: PinAccessResult = PinAccessResult.Open
    private var active = true
    private var fail = false
    private var appliedOk = true
    private var protected = false
    private val removal = PreviewAppRemoval(
        authenticate = { id, pin -> assertEquals("calculator", id); pin.fill('\u0000'); result },
        remove = { _ -> if (fail) throw java.io.IOException(); if (protected) false else { saved++; true } },
        apply = { applied++; appliedOk },
    )
    @Test fun freshPinBeforeEverySave() {
        assertEquals(AppRemovalResult.Removed, removal.remove("calculator", charArrayOf('1'), { active }))
        result = PinAccessResult.Wrong(0)
        assertEquals(AppRemovalResult.PinRejected(result), removal.remove("calculator", charArrayOf('2'), { active }))
        assertEquals(1, saved); assertEquals(1, applied)
    }
    @Test fun backOrPauseBeforeCommitWritesNothing() {
        active = false
        assertEquals(AppRemovalResult.Cancelled, removal.remove("calculator", charArrayOf('1'), { active }))
        assertEquals(0, saved); assertEquals(0, applied)
    }
    @Test fun cancellationDuringPinCheckWritesNothing() {
        val delayed = PreviewAppRemoval({ _, _ -> active = false; PinAccessResult.Open }, { saved++; true }, { true })
        assertEquals(AppRemovalResult.Cancelled, delayed.remove("calculator", charArrayOf('1'), { active }))
        assertEquals(0, saved)
    }
    @Test fun saveFailureDoesNotClaimRemovedOrReapply() {
        fail = true
        assertEquals(AppRemovalResult.SaveFailed, removal.remove("calculator", charArrayOf('1'), { active }))
        assertEquals(0, saved); assertEquals(0, applied)
    }
    @Test fun protectedTargetWritesNothing() {
        protected = true
        assertEquals(AppRemovalResult.SaveFailed, removal.remove("calculator", charArrayOf('1'), { active }))
        assertEquals(0, saved)
    }
    @Test fun failedReadbackDoesNotClaimRemoved() {
        appliedOk = false
        assertEquals(AppRemovalResult.CheckFailed, removal.remove("calculator", charArrayOf('1'), { active }))
        assertEquals(1, saved); assertEquals(1, applied)
    }
}
