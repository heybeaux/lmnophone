// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.util.UUID
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PrivilegedAction

enum class PinEntry { SETUP, ENTER, UNAVAILABLE }
sealed interface PinAccessResult {
    data object Open : PinAccessResult
    data class Wrong(val remainingMillis: Long) : PinAccessResult
    data class Delayed(val remainingMillis: Long) : PinAccessResult
    data object Unavailable : PinAccessResult
}

/**
 * Access to the preview's controls screen only. Turn-off requires a separate fresh action-bound PIN.
 * A successful PIN creates and immediately consumes a purpose-bound, single-use vault grant. This
 * result cannot authorize a future privileged action. No access or raw secret is persisted.
 * All methods are called on PolicyRuntime's common serial dispatcher.
 */
class PreviewPinAccess(private val store: JointAuthorityStore, private val vault: PinVault) {
    fun entry(): PinEntry = try {
        if (!store.recordPresent()) PinEntry.SETUP
        else if (store.migrate() && store.credentials.read()?.let {
            it.mode == Mode.SELF_MANAGED && it.people.keys == setOf(SELF)
        } == true) PinEntry.ENTER else PinEntry.UNAVAILABLE
    } catch (_: Exception) { PinEntry.UNAVAILABLE }

    fun enroll(pin: CharArray): PinEnrollmentResult = PinEnrollment(store, vault).create(SELF, pin)

    fun authenticate(pin: CharArray): PinAccessResult = authenticateAction(pin, "preview:controls", UUID.randomUUID().toString())

    /** Removal consumes a fresh grant bound to the selected app, on the policy dispatcher. */
    fun authenticateRemoval(id: String, pin: CharArray): PinAccessResult = authenticateAction(pin, "preview:remove", id)

    fun authenticateAddition(id: String, pin: CharArray): PinAccessResult = authenticateAction(pin, "preview:add", id)

    fun authenticateRequestRecipient(pin: CharArray): PinAccessResult =
        authenticateAction(pin, "preview:request-recipient", UUID.randomUUID().toString())

    private fun authenticateAction(pin: CharArray, purpose: String, target: String): PinAccessResult = try {
        if (entry() != PinEntry.ENTER) PinAccessResult.Unavailable else {
            val action = PrivilegedAction(purpose, target)
            when (val result = vault.authenticate(SELF, pin, action)) {
                is PinResult.Authorized -> if (vault.consumeFor(result.authority, action, SELF))
                    PinAccessResult.Open else PinAccessResult.Unavailable
                is PinResult.Wrong -> PinAccessResult.Wrong(result.remainingMillis)
                is PinResult.Delayed -> PinAccessResult.Delayed(result.remainingMillis)
                PinResult.Unavailable -> PinAccessResult.Unavailable
            }
        }
    } catch (_: Exception) { PinAccessResult.Unavailable }
    finally { pin.fill('\u0000') }

    companion object { private val SELF = TrustedPersonId("self") }
}
