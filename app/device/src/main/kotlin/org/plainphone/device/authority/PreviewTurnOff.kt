// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.core.RunningClock
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.*

/** Serialized by PolicyRuntime. Only opaque, process-local action grants are kept, never a PIN. */
class PreviewTurnOff(store: JointAuthorityStore, private val vault: PinVault, clock: RunningClock) {
    private val controller = WaitingChangeController(store.waitingStore, store.credentials::read, clock,
        PersonAuthorityConsumer(vault::consumeFor), store.identities, store.cancelIntents)
    private val access = PreviewPinAccess(store, vault)
    private data class Prepared(val flow: String, val request: WaitingStart, val grant: FreshAuthority)
    private var prepared: Prepared? = null

    fun authenticate(flow: String, pin: CharArray): PinAccessResult = try {
        prepared = null
        if (access.entry() != PinEntry.ENTER) PinAccessResult.Unavailable else {
            val request = controller.prepareTurnOff(SELF)
            if (request == null) PinAccessResult.Unavailable else when (val result = vault.authenticate(SELF, pin, request.action)) {
                is PinResult.Authorized -> {
                    prepared = Prepared(flow, request, result.authority)
                    PinAccessResult.Open
                }
                is PinResult.Wrong -> PinAccessResult.Wrong(result.remainingMillis)
                is PinResult.Delayed -> PinAccessResult.Delayed(result.remainingMillis)
                PinResult.Unavailable -> PinAccessResult.Unavailable
            }
        }
    } finally { pin.fill('\u0000') }

    fun start(flow: String, first: Boolean, second: Boolean): WaitingOutcome {
        val ready = prepared?.takeIf { it.flow == flow } ?: return WaitingOutcome.REFUSED
        prepared = null
        return controller.startTurnOff(ready.request, ready.grant, first, second)
    }
    fun close(flow: String) { if (prepared?.flow == flow) prepared = null }
    companion object { private val SELF = TrustedPersonId("self") }
}
