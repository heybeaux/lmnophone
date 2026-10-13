// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.util.UUID
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.*

enum class PinChangeOutcome { CHANGED, FAILED }

/**
 * Change PIN on the preview controls (PLA-494). The old PIN is a fresh entry with the normal counter and
 * lockout; it yields one process-local grant bound to this flow and to changing the PIN, never the PIN
 * itself. [change] spends it in the vault's single atomic write. A waiting change that the old PIN started
 * depends on the old generation; the next checkpoint (PolicyRuntime runs a pass after a change) cancels it.
 * All methods run on PolicyRuntime's common serial dispatcher.
 */
class PreviewChangePin(private val store: JointAuthorityStore, private val vault: PinVault) {
    private val access = PreviewPinAccess(store, vault)
    private data class Prepared(val flow: String, val action: PrivilegedAction, val grant: FreshAuthority)
    private var prepared: Prepared? = null

    fun authenticate(flow: String, pin: CharArray): PinAccessResult = try {
        prepared = null
        if (access.entry() != PinEntry.ENTER) PinAccessResult.Unavailable else {
            val action = PrivilegedAction("preview:change-pin", UUID.randomUUID().toString())
            when (val result = vault.authenticate(SELF, pin, action)) {
                is PinResult.Authorized -> {
                    prepared = Prepared(flow, action, result.authority)
                    PinAccessResult.Open
                }
                is PinResult.Wrong -> PinAccessResult.Wrong(result.remainingMillis)
                is PinResult.Delayed -> PinAccessResult.Delayed(result.remainingMillis)
                PinResult.Unavailable -> PinAccessResult.Unavailable
            }
        }
    } catch (_: RuntimeException) { PinAccessResult.Unavailable } finally { pin.fill('\u0000') }

    fun change(flow: String, newPin: CharArray): PinChangeOutcome = try {
        val ready = prepared?.takeIf { it.flow == flow }
        prepared = null
        if (ready == null || access.entry() != PinEntry.ENTER) PinChangeOutcome.FAILED
        else if (vault.changePin(SELF, ready.grant, ready.action, newPin) == PinChangeResult.Changed) PinChangeOutcome.CHANGED
        else PinChangeOutcome.FAILED
    } catch (_: RuntimeException) { PinChangeOutcome.FAILED } finally { newPin.fill('\u0000') }

    fun close(flow: String) { if (prepared?.flow == flow) prepared = null }

    /** A pending waiting change exists; a new PIN cancels it (shown before the new-PIN step). */
    fun waitingChange(): Boolean = try {
        store.waitingStore.read()?.operation?.phase == WaitingPhase.PENDING
    } catch (_: RuntimeException) { false }

    companion object { private val SELF = TrustedPersonId("self") }
}
