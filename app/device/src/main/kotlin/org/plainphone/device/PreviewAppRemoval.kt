// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.device.authority.PinAccessResult

sealed interface AppRemovalResult {
    data object Removed : AppRemovalResult
    data object Cancelled : AppRemovalResult
    data object SaveFailed : AppRemovalResult
    data object CheckFailed : AppRemovalResult
    data class PinRejected(val result: PinAccessResult) : AppRemovalResult
}

/** Runs on the common policy dispatcher. No menu authentication result is accepted as authority. */
class PreviewAppRemoval(
    private val authenticate: (String, CharArray) -> PinAccessResult,
    private val remove: (String) -> Boolean,
    private val apply: () -> Boolean,
) {
    fun remove(id: String, pin: CharArray, active: () -> Boolean): AppRemovalResult {
        try {
            if (!active()) return AppRemovalResult.Cancelled
            val auth = authenticate(id, pin)
            if (!active()) return AppRemovalResult.Cancelled
            if (auth != PinAccessResult.Open) return AppRemovalResult.PinRejected(auth)
            if (!remove(id)) return AppRemovalResult.SaveFailed
        } catch (_: Exception) { return AppRemovalResult.SaveFailed }
        finally { pin.fill('\u0000') }
        return try { if (apply()) AppRemovalResult.Removed else AppRemovalResult.CheckFailed }
        catch (_: Exception) { AppRemovalResult.CheckFailed }
    }
}
