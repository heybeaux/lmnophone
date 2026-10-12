// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import org.plainphone.device.authority.*

interface PinScreenPort {
    fun entry(done: (PinEntry) -> Unit)
    fun enroll(pin: CharArray, done: (PinEnrollmentResult) -> Unit)
    fun authenticate(pin: CharArray, done: (PinAccessResult) -> Unit)
}
enum class PinPage { LOADING, SETUP, ENTER, LOCKED, MENU, NOT_IN_BUILD, UNAVAILABLE }
data class PinPageState(val page: PinPage = PinPage.LOADING, val busy: Boolean = false,
    val wrong: Boolean = false, val waitMillis: Long = 0)

/** UI-thread navigation only. No PIN, grant or authenticated state survives close or recreation. */
class PinScreenFlow(private val port: PinScreenPort, private val changed: (PinPageState) -> Unit) {
    var state = PinPageState()
        private set
    private var generation = 0L
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
    fun close() { generation++; returnPage = PinPage.ENTER; show(PinPageState()) }
    fun submit(pin: CharArray) {
        if (state.busy || state.page !in setOf(PinPage.SETUP, PinPage.ENTER)) { pin.fill('\u0000'); return }
        val page = state.page
        val request = ++generation
        show(state.copy(busy = true, wrong = false))
        if (page == PinPage.SETUP) port.enroll(pin) { result ->
            if (generation == request) show(PinPageState(when (result) {
                PinEnrollmentResult.CREATED -> PinPage.ENTER
                PinEnrollmentResult.INVALID_PIN -> PinPage.SETUP
                PinEnrollmentResult.UNAVAILABLE -> PinPage.UNAVAILABLE
            }))
        } else port.authenticate(pin) { result -> if (generation == request) show(when (result) {
            PinAccessResult.Open -> PinPageState(PinPage.MENU)
            PinAccessResult.Unavailable -> PinPageState(PinPage.UNAVAILABLE)
            is PinAccessResult.Delayed -> PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis)
            is PinAccessResult.Wrong -> if (result.remainingMillis > 0)
                PinPageState(PinPage.LOCKED, waitMillis = result.remainingMillis)
                else PinPageState(PinPage.ENTER, wrong = true)
        }) }
    }
    fun unsupported() {
        if (state.busy || state.page !in setOf(PinPage.MENU, PinPage.ENTER, PinPage.LOCKED)) return
        returnPage = if (state.page == PinPage.MENU) PinPage.MENU else PinPage.ENTER
        show(PinPageState(PinPage.NOT_IN_BUILD))
    }
    fun backFromUnsupported() {
        if (state.page == PinPage.NOT_IN_BUILD) show(PinPageState(returnPage))
    }
}
