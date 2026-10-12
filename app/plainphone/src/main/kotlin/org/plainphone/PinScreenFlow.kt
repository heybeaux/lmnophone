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
}
enum class PinPage { LOADING, SETUP, ENTER, LOCKED, MENU, NOT_IN_BUILD, UNAVAILABLE, OFF_INTRO, OFF_FIRST, OFF_PIN, OFF_LAST, WAIT_STARTED, WAIT_BUSY, WAIT_FAILED }
data class PinPageState(val page: PinPage = PinPage.LOADING, val busy: Boolean = false,
    val wrong: Boolean = false, val waitMillis: Long = 0)

/** UI-thread navigation only. No PIN, grant or authenticated state survives close or recreation. */
class PinScreenFlow(private val port: PinScreenPort, private val changed: (PinPageState) -> Unit) {
    var state = PinPageState()
        private set
    private var generation = 0L
    private var turnOffId = UUID.randomUUID().toString()
    private var firstConfirmed = false
    private var returnPage = PinPage.ENTER
    private fun show(next: PinPageState) { state = next; changed(next) }
    fun open() {
        val request = ++generation
        show(PinPageState())
        port.entry { entry -> if (generation == request) show(PinPageState(when (entry) {
            PinEntry.SETUP -> PinPage.SETUP
            PinEntry.ENTER -> PinPage.ENTER
            PinEntry.UNAVAILABLE -> PinPage.UNAVAILABLE
        })) }
    }
    fun close() { port.closeTurnOff(turnOffId); turnOffId = UUID.randomUUID().toString(); firstConfirmed = false; generation++; returnPage = PinPage.ENTER; show(PinPageState()) }
    fun submit(pin: CharArray) {
        if (state.busy || state.page !in setOf(PinPage.SETUP, PinPage.ENTER, PinPage.OFF_PIN)) { pin.fill('\u0000'); return }
        val page = state.page
        val request = ++generation
        show(state.copy(busy = true, wrong = false))
        if (page == PinPage.SETUP) port.enroll(pin) { result ->
            if (generation == request) show(PinPageState(when (result) {
                PinEnrollmentResult.CREATED -> PinPage.ENTER
                PinEnrollmentResult.INVALID_PIN -> PinPage.SETUP
                PinEnrollmentResult.UNAVAILABLE -> PinPage.UNAVAILABLE
            }))
        } else {
            val done: (PinAccessResult) -> Unit = { result -> if (generation == request) show(when (result) {
                PinAccessResult.Open -> PinPageState(if (page == PinPage.OFF_PIN) PinPage.OFF_LAST else PinPage.MENU)
                PinAccessResult.Unavailable -> PinPageState(PinPage.UNAVAILABLE)
                is PinAccessResult.Delayed -> PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis)
                is PinAccessResult.Wrong -> if (result.remainingMillis > 0)
                    PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis)
                    else PinPageState(page, wrong = true)
            }) }
            if (page == PinPage.OFF_PIN) port.authenticateTurnOff(turnOffId, pin, done) else port.authenticate(pin, done)
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

    fun unsupported() {
        if (state.busy || state.page !in setOf(PinPage.MENU, PinPage.ENTER, PinPage.LOCKED, PinPage.OFF_PIN)) return
        returnPage = if (state.page in setOf(PinPage.MENU, PinPage.OFF_PIN)) state.page else PinPage.ENTER
        show(PinPageState(PinPage.NOT_IN_BUILD))
    }
    fun backFromUnsupported() {
        if (state.page == PinPage.NOT_IN_BUILD) show(PinPageState(returnPage))
    }
}
