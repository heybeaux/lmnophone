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
    private fun asked(ask: WifiForgetConfirmUiState, wifi: WifiSettingsUiState) = wifiForgetStillAsked(ask, 100L, wifi, 100L)
    @Test fun sameForgettableNetworkIsStillAsked() {
        assertTrue(asked(WifiForgetConfirmUiState("Sam"), sam))
        assertTrue(asked(WifiForgetConfirmUiState(null), sam.copy(network = null)))
    }
    @Test fun anotherNetworkOrAChangedNameEndsTheQuestion() {
        assertFalse(asked(WifiForgetConfirmUiState("Sam"), sam.copy(network = "Jo")))
        assertFalse(asked(WifiForgetConfirmUiState("Sam"), sam.copy(network = null)))
        assertFalse(asked(WifiForgetConfirmUiState(null), sam))
    }
    @Test fun disconnectedOrNotForgettableEndsTheQuestion() {
        assertFalse(asked(WifiForgetConfirmUiState("Sam"), sam.copy(connected = false)))
        assertFalse(asked(WifiForgetConfirmUiState("Sam"), sam.copy(canForget = false)))
    }
    // PLA-491 P1: names alone cannot tell two unnamed or same-label networks apart.
    @Test fun aReplacementConnectionEndsTheQuestionEvenWithTheSameOrNoName() {
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState(null), 100L, sam.copy(network = null), 200L))
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), 100L, sam, 200L))
    }
    @Test fun anUnknownConnectionEndsTheQuestion() {
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), null, sam, null))
        assertFalse(wifiForgetStillAsked(WifiForgetConfirmUiState("Sam"), 100L, sam, null))
    }
}
