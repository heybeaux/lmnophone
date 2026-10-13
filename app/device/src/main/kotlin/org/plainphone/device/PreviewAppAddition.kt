// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.device.authority.PinAccessResult

sealed interface AppAdditionResult {
    data object Added : AppAdditionResult
    data object Cancelled : AppAdditionResult
    data object SaveFailed : AppAdditionResult
    data object CheckFailed : AppAdditionResult
    data class PinRejected(val result: PinAccessResult) : AppAdditionResult
}

/** Called only on the policy dispatcher. Save re-reads eligibility before its commit. */
class PreviewAppAddition(private val authenticate: (String, CharArray) -> PinAccessResult,
    private val save: (String) -> Boolean, private val apply: () -> Boolean) {
    fun add(id: String, pin: CharArray, active: () -> Boolean): AppAdditionResult {
        try {
            if (!active()) return AppAdditionResult.Cancelled
            val auth = authenticate(id, pin)
            if (!active()) return AppAdditionResult.Cancelled
            if (auth != PinAccessResult.Open) return AppAdditionResult.PinRejected(auth)
            if (!save(id)) return AppAdditionResult.SaveFailed
        } catch (_: Exception) { return AppAdditionResult.SaveFailed }
        finally { pin.fill('\u0000') }
        return try { if (apply()) AppAdditionResult.Added else AppAdditionResult.CheckFailed }
        catch (_: Exception) { AppAdditionResult.CheckFailed }
    }
}
