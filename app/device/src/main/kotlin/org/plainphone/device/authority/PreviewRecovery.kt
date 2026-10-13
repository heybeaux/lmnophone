// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.util.UUID
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.*

/** Transfers ownership once, and wipes any unclaimed value when a callback is stale. */
class OneTimeRecoveryCode(private var value: CharArray?) {
    @Synchronized fun take(): CharArray? = value.also { value = null }
    @Synchronized fun clear() { value?.fill('\u0000'); value = null }
    override fun toString() = "OneTimeRecoveryCode(redacted)"
}
sealed interface RecoveryAccessResult {
    class Issued(val code: OneTimeRecoveryCode) : RecoveryAccessResult {
        override fun toString() = "Issued(redacted)"
    }
    data class WrongPin(val remainingMillis: Long) : RecoveryAccessResult
    data class PinDelayed(val remainingMillis: Long) : RecoveryAccessResult
    data class WrongCode(val remainingMillis: Long, val pinWaitMillis: Long) : RecoveryAccessResult
    data class CodeDelayed(val remainingMillis: Long, val pinWaitMillis: Long) : RecoveryAccessResult
    data object Unavailable : RecoveryAccessResult
}

/** Common policy dispatcher only. Recovery grants no menu or privileged-action authority. */
class PreviewRecovery(private val store: JointAuthorityStore, private val vault: PinVault) {
    private val access = PreviewPinAccess(store, vault)
    fun waits(): CredentialWaits? = try {
        if (access.entry() == PinEntry.ENTER) vault.credentialWaits(SELF) else null
    } catch (_: RuntimeException) { null }
    fun rotate(pin: CharArray): RecoveryAccessResult = try {
        if (access.entry() != PinEntry.ENTER) RecoveryAccessResult.Unavailable else {
            val action = PrivilegedAction("preview:new-recovery-code", UUID.randomUUID().toString())
            when (val result = vault.authenticate(SELF, pin, action)) {
                is PinResult.Authorized -> mapped(vault.replaceRecoveryCode(SELF, result.authority, action))
                is PinResult.Wrong -> RecoveryAccessResult.WrongPin(result.remainingMillis)
                is PinResult.Delayed -> RecoveryAccessResult.PinDelayed(result.remainingMillis)
                PinResult.Unavailable -> RecoveryAccessResult.Unavailable
            }
        }
    } catch (_: RuntimeException) { RecoveryAccessResult.Unavailable } finally { pin.fill('\u0000') }

    fun recover(code: CharArray, newPin: CharArray): RecoveryAccessResult = try {
        if (access.entry() != PinEntry.ENTER) RecoveryAccessResult.Unavailable
        else mapped(vault.recover(SELF, code, newPin))
    } catch (_: RuntimeException) { RecoveryAccessResult.Unavailable }
    finally { code.fill('\u0000'); newPin.fill('\u0000') }

    private fun mapped(result: RecoveryResult): RecoveryAccessResult = when (result) {
        is RecoveryResult.Replaced -> RecoveryAccessResult.Issued(OneTimeRecoveryCode(result.recoveryCode.toCharArray()))
        is RecoveryResult.Wrong -> waits()?.let { RecoveryAccessResult.WrongCode(result.remainingMillis, it.pinMillis) } ?: RecoveryAccessResult.Unavailable
        is RecoveryResult.Delayed -> waits()?.let { RecoveryAccessResult.CodeDelayed(result.remainingMillis, it.pinMillis) } ?: RecoveryAccessResult.Unavailable
        RecoveryResult.Unavailable -> RecoveryAccessResult.Unavailable
    }
    companion object { private val SELF = TrustedPersonId("self") }
}
