// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.shell

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.ui.user.WifiForgetConfirmUiState
import org.plainphone.ui.user.WifiSettingsUiState

/** PLA-488: Forget runs only for the network the question named, while it is still current and forgettable. */
class WifiForgetAskTest {
    private val sam = WifiSettingsUiState(connected = true, network = "Sam", canForget = true)
    @Test fun sameForgettableNetworkIsStillAsked() {
        assertTrue(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), sam))
        assertTrue(wifiForgetStillAsked(WifiForgetConfirmUiState(null), sam.copy(network = null)))
    }
    @Test fun anotherNetworkOrAChangedNameEndsTheQuestion() {
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), sam.copy(network = "Jo")))
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), sam.copy(network = null)))
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState(null), sam))
    }
    @Test fun disconnectedOrNotForgettableEndsTheQuestion() {
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), sam.copy(connected = false)))
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), sam.copy(canForget = false)))
    }
}
