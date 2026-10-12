// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.util.UUID
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.PinVault
import org.plainphone.policy.WaitingState

enum class PinEnrollmentResult { CREATED, INVALID_PIN, UNAVAILABLE }

/**
 * Real first-use PIN enrollment, invoked only after the user confirms a PIN in the UI.
 * Existing or damaged authority is never replaced. Genesis uses the store's reviewed atomic
 * initialization; later authentication uses CredentialStore.compareAndSet through PinVault.
 * This creates no Google account profile, grants no authority, and cannot start a waiting change.
 * The owner runs this and the vault on the common policy dispatcher, away from the UI thread.
 */
class PinEnrollment(private val store: JointAuthorityStore, private val vault: PinVault) {
    fun create(person: TrustedPersonId, pin: CharArray): PinEnrollmentResult = try {
        if (pin.size < 6 || pin.any { it !in '0'..'9' }) PinEnrollmentResult.INVALID_PIN
        else store.serialized(PinEnrollmentResult.UNAVAILABLE) {
            if (store.recordPresent()) return@serialized PinEnrollmentResult.UNAVAILABLE
            val prepared = vault.prepareCredential(pin)
            val credentials = CredentialState(Mode.SELF_MANAGED, mapOf(person to prepared.credential))
            val saved = store.initialize(credentials,
                WaitingState(storageGeneration = UUID.randomUUID().toString()), ElevationRecord())
            // A failed write can have landed. Never report success from a read after a false write.
            val read = store.credentials.read()
            if (saved && store.migrate() && read?.mode == Mode.SELF_MANAGED &&
                read.people.keys == setOf(person) && read.recoveryProfile == null &&
                read.people.getValue(person).pin.hash.contentEquals(prepared.credential.pin.hash))
                PinEnrollmentResult.CREATED else PinEnrollmentResult.UNAVAILABLE
        }
    } catch (_: RuntimeException) {
        PinEnrollmentResult.UNAVAILABLE
    } finally {
        pin.fill('\u0000')
    }
}
