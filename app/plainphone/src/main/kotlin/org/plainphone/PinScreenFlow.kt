// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.plainphone.device.authority.*
import org.plainphone.policy.WaitingOutcome
import java.util.UUID

interface PinScreenPort {
    fun authenticateTurnOff(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit)
    fun startTurnOff(flow: String, first: Boolean, second: Boolean, done: (WaitingOutcome) -> Unit)
    fun closeTurnOff(flow: String)
    fun entry(done: (PinEntry) -> Unit)
    fun enroll(pin: CharArray, done: (PinEnrollmentResult) -> Unit)
    fun authenticate(pin: CharArray, done: (PinAccessResult) -> Unit)
    fun authenticateChange(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit)
    fun changePin(flow: String, newPin: CharArray, done: (PinChangeOutcome) -> Unit)
    fun recoveryWaits(done: (org.plainphone.policy.CredentialWaits?) -> Unit)
    fun rotateRecovery(pin: CharArray, done: (RecoveryAccessResult) -> Unit)
    fun recover(code: CharArray, newPin: CharArray, done: (RecoveryAccessResult) -> Unit)
    fun closeChange(flow: String)
}
enum class PinPage { LOADING, SETUP, ENTER, LOCKED, MENU, NOT_IN_BUILD, UNAVAILABLE, OFF_INTRO, OFF_FIRST, OFF_PIN, OFF_LAST, WAIT_STARTED, WAIT_BUSY, WAIT_FAILED,
    CHANGE_OLD, CHANGE_NEW, CHANGE_DONE, CHANGE_FAILED, RECOVERY_PIN, RECOVERY, RECOVERY_NEW, RECOVERY_LOCKED, RECOVERY_ISSUED }
data class PinPageState(val page: PinPage = PinPage.LOADING, val busy: Boolean = false,
    val wrong: Boolean = false, val waitMillis: Long = 0, val mismatch: Boolean = false, val pinWaitMillis: Long = 0)

/** UI-thread navigation only. No PIN, grant or authenticated state survives close or recreation. */
class PinScreenFlow(private val port: PinScreenPort, private val changed: (PinPageState) -> Unit) {
    var state = PinPageState()
        private set
    private var enteredCode: CharArray? = null
    private var issuedCode: CharArray? = null
    fun displayedCode(): String = if (state.page == PinPage.RECOVERY_ISSUED)
        issuedCode?.concatToString()?.chunked(4)?.joinToString("-").orEmpty() else ""
    private fun clearCodes() { enteredCode?.fill('\u0000'); enteredCode = null; issuedCode?.fill('\u0000'); issuedCode = null }
    private var generation = 0L
    private var turnOffId = UUID.randomUUID().toString()
    private var changeId = UUID.randomUUID().toString()
    private var firstConfirmed = false
    private var returnPage = PinPage.ENTER
    private fun show(next: PinPageState) { state = next; changed(next) }
    fun open() {
        clearCodes()
        val request = ++generation
        show(PinPageState())
        port.entry { entry -> if (generation == request) {
            when (entry) {
                PinEntry.SETUP -> show(PinPageState(PinPage.SETUP))
                PinEntry.UNAVAILABLE -> show(PinPageState(PinPage.UNAVAILABLE))
                // Re-read the durable wait after recreation/resume. Never save a UI timer or PIN.
                PinEntry.ENTER -> port.recoveryWaits { waits -> if (generation == request) {
                    show(when {
                        waits == null -> PinPageState(PinPage.UNAVAILABLE)
                        waits.pinMillis > 0 -> PinPageState(PinPage.LOCKED, waitMillis = waits.pinMillis)
                        else -> PinPageState(PinPage.ENTER)
                    })
                } }
            }
        } }
    }
    fun close() { clearCodes(); dropChange(); port.closeTurnOff(turnOffId); turnOffId = UUID.randomUUID().toString(); firstConfirmed = false; generation++; returnPage = PinPage.ENTER; show(PinPageState()) }
    fun submit(pin: CharArray) {
        if (state.busy || state.page !in setOf(PinPage.SETUP, PinPage.ENTER, PinPage.OFF_PIN, PinPage.CHANGE_OLD, PinPage.RECOVERY_PIN)) { pin.fill('\u0000'); return }
        val page = state.page
        val request = ++generation
        show(state.copy(busy = true, wrong = false))
        if (page == PinPage.SETUP) port.enroll(pin) { result ->
            if (generation != request) { (result as? PinEnrollmentResult.Created)?.code?.clear() }
            else when (result) {
                is PinEnrollmentResult.Created -> issued(result.code)
                PinEnrollmentResult.INVALID_PIN -> show(PinPageState(PinPage.SETUP))
                PinEnrollmentResult.UNAVAILABLE -> show(PinPageState(PinPage.UNAVAILABLE))
            }
        } else if (page == PinPage.RECOVERY_PIN) {
            port.rotateRecovery(pin) { recoveryResult(request, it) }
        } else {
            val done: (PinAccessResult) -> Unit = { result -> if (generation == request) show(when (result) {
                PinAccessResult.Open -> PinPageState(when (page) {
                    PinPage.OFF_PIN -> PinPage.OFF_LAST
                    PinPage.CHANGE_OLD -> PinPage.CHANGE_NEW
                    else -> PinPage.MENU
                })
                PinAccessResult.Unavailable -> PinPageState(PinPage.UNAVAILABLE)
                is PinAccessResult.Delayed -> PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis)
                is PinAccessResult.Wrong -> if (result.remainingMillis > 0)
                    PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis)
                    else PinPageState(page, wrong = true)
            }) }
            when (page) {
                PinPage.OFF_PIN -> port.authenticateTurnOff(turnOffId, pin, done)
                PinPage.CHANGE_OLD -> port.authenticateChange(changeId, pin, done)
                else -> port.authenticate(pin, done)
            }
        }
    }
    fun turnOff() {
        if (state.page != PinPage.MENU || state.busy) return
        firstConfirmed = false
        show(PinPageState(PinPage.OFF_INTRO))
    }
    fun continueTurnOff() {
        if (state.busy) return
        when (state.page) {
            PinPage.OFF_INTRO -> show(PinPageState(PinPage.OFF_FIRST))
            PinPage.OFF_FIRST -> { firstConfirmed = true; show(PinPageState(PinPage.OFF_PIN)) }
            PinPage.OFF_LAST -> {
                val request = ++generation
                show(state.copy(busy = true))
                port.startTurnOff(turnOffId, firstConfirmed, true) { result -> if (generation == request)
                    show(PinPageState(when (result) {
                        WaitingOutcome.PENDING -> PinPage.WAIT_STARTED
                        WaitingOutcome.BUSY -> PinPage.WAIT_BUSY
                        WaitingOutcome.NOT_READY -> PinPage.UNAVAILABLE
                        else -> PinPage.WAIT_FAILED
                    }))
                }
            }
            else -> Unit
        }
    }

    /** Change PIN (PLA-494): a fresh old-PIN entry, then the new PIN twice. Only from the menu. */
    fun changePin() {
        if (state.page != PinPage.MENU || state.busy) return
        show(PinPageState(PinPage.CHANGE_OLD))
    }
    /** Both arrays are always zeroed. A mismatch or an invalid PIN writes nothing. */
    fun submitNewPin(pin: CharArray, again: CharArray) {
        try {
            if (state.page !in setOf(PinPage.CHANGE_NEW, PinPage.RECOVERY_NEW) || state.busy) return
            if (pin.size < 6 || pin.any { it !in '0'..'9' } || !pin.contentEquals(again)) {
                show(PinPageState(state.page, mismatch = true)); return
            }
            val request = ++generation
            show(state.copy(busy = true, mismatch = false))
            if (state.page == PinPage.RECOVERY_NEW) {
                val code = enteredCode ?: run { show(PinPageState(PinPage.UNAVAILABLE)); return }
                enteredCode = null
                port.recover(code, pin.copyOf()) { recoveryResult(request, it) }
                return
            }
            port.changePin(changeId, pin.copyOf()) { result -> if (generation == request) {
                dropChange()
                show(PinPageState(if (result == PinChangeOutcome.CHANGED) PinPage.CHANGE_DONE else PinPage.CHANGE_FAILED))
            } }
        } finally { pin.fill('\u0000'); again.fill('\u0000') }
    }
    /** Back on any Change PIN page returns to the menu and drops the grant. Ignored while a write runs. */
    fun back() {
        if (state.busy || state.page !in setOf(PinPage.CHANGE_OLD, PinPage.CHANGE_NEW, PinPage.CHANGE_DONE, PinPage.CHANGE_FAILED)) return
        generation++
        dropChange()
        show(PinPageState(PinPage.MENU))
    }
    private fun dropChange() { port.closeChange(changeId); changeId = UUID.randomUUID().toString() }

    fun newRecoveryCode() {
        if (state.page == PinPage.MENU && !state.busy) show(PinPageState(PinPage.RECOVERY_PIN))
    }
    /** Apps abandons its own operation before this transition; recovery grants no app action. */
    fun useRecoveryFromApps() {
        if (state.busy || state.page != PinPage.MENU) return
        enterPin()
        useRecovery()
    }
    fun useRecovery() {
        if (state.busy || state.page !in setOf(PinPage.ENTER, PinPage.LOCKED, PinPage.CHANGE_OLD, PinPage.OFF_PIN, PinPage.RECOVERY_PIN)) return
        clearCodes(); dropChange(); port.closeTurnOff(turnOffId); firstConfirmed = false
        val request = ++generation
        show(PinPageState(PinPage.RECOVERY, busy = true))
        port.recoveryWaits { waits -> if (generation == request) {
            show(when {
                waits == null -> PinPageState(PinPage.UNAVAILABLE)
                waits.recoveryMillis > 0 -> PinPageState(PinPage.RECOVERY_LOCKED, waitMillis = waits.recoveryMillis, pinWaitMillis = waits.pinMillis)
                else -> PinPageState(PinPage.RECOVERY)
            })
        } }
    }
    /** Refresh lockout presentation from the same proven clock as verification, without an attempt. */
    fun refreshWaits() {
        val page = state.page
        if (state.busy || page !in setOf(PinPage.LOCKED, PinPage.RECOVERY_LOCKED)) return
        val request = generation
        port.recoveryWaits { waits -> if (generation == request && state.page == page) {
            show(when {
                waits == null -> PinPageState(PinPage.UNAVAILABLE)
                page == PinPage.LOCKED && waits.pinMillis > 0 -> state.copy(waitMillis = waits.pinMillis)
                page == PinPage.LOCKED -> PinPageState(PinPage.ENTER)
                waits.recoveryMillis > 0 -> state.copy(waitMillis = waits.recoveryMillis, pinWaitMillis = waits.pinMillis)
                else -> PinPageState(PinPage.RECOVERY)
            })
        } }
    }
    fun submitRecovery(code: CharArray) {
        if (state.page != PinPage.RECOVERY || state.busy || code.isEmpty()) { code.fill('\u0000'); return }
        enteredCode?.fill('\u0000'); enteredCode = code
        show(PinPageState(PinPage.RECOVERY_NEW))
    }
    fun enterPin() {
        if (state.busy) return
        clearCodes(); generation++
        show(PinPageState(PinPage.ENTER))
    }
    fun codeWritten() { if (state.page == PinPage.RECOVERY_ISSUED) enterPin() }
    private fun issued(code: OneTimeRecoveryCode) {
        clearCodes(); issuedCode = code.take()
        show(PinPageState(if (issuedCode == null) PinPage.UNAVAILABLE else PinPage.RECOVERY_ISSUED))
    }
    private fun recoveryResult(request: Long, result: RecoveryAccessResult) {
        if (generation != request) { (result as? RecoveryAccessResult.Issued)?.code?.clear(); return }
        when (result) {
            is RecoveryAccessResult.Issued -> issued(result.code)
            is RecoveryAccessResult.WrongPin -> show(if (result.remainingMillis > 0) PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis) else PinPageState(PinPage.RECOVERY_PIN, wrong = true))
            is RecoveryAccessResult.PinDelayed -> show(PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis))
            is RecoveryAccessResult.WrongCode -> show(if (result.remainingMillis > 0) PinPageState(PinPage.RECOVERY_LOCKED, waitMillis = result.remainingMillis, pinWaitMillis = result.pinWaitMillis) else PinPageState(PinPage.RECOVERY, wrong = true))
            is RecoveryAccessResult.CodeDelayed -> show(PinPageState(PinPage.RECOVERY_LOCKED, waitMillis = result.remainingMillis, pinWaitMillis = result.pinWaitMillis))
            RecoveryAccessResult.Unavailable -> show(PinPageState(PinPage.UNAVAILABLE))
        }
    }

    fun unsupported() {
        if (state.busy || state.page !in setOf(PinPage.MENU, PinPage.ENTER, PinPage.LOCKED, PinPage.OFF_PIN, PinPage.CHANGE_OLD)) return
        returnPage = if (state.page in setOf(PinPage.MENU, PinPage.OFF_PIN)) state.page else if (state.page == PinPage.CHANGE_OLD) PinPage.MENU else PinPage.ENTER
        if (state.page == PinPage.CHANGE_OLD) dropChange()
        show(PinPageState(PinPage.NOT_IN_BUILD))
    }
    fun backFromUnsupported() {
        if (state.page == PinPage.NOT_IN_BUILD) show(PinPageState(returnPage))
    }
}
