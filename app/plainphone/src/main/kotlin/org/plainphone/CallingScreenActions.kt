// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.content.Intent
import org.plainphone.boot.CallingScreen
import org.plainphone.shell.ShellEffect

/** Calling-screen dispatch shared by a tap and crash recovery. Launches keypads, never calls. */
internal class CallingScreenActions(
    private val opened: (CallingScreen) -> Unit,
    private val openPhone: () -> Unit,
    private val openEmergency: () -> Unit,
    private val showInCallScreen: () -> Unit,
    private val launch: (action: String, uri: String?) -> Boolean,
) {
    /** True when this calling action was handled; other effects remain with the shell. */
    fun perform(effect: ShellEffect): Boolean = when (effect) {
        ShellEffect.OpenDialer -> true.also { openPhone() }
        ShellEffect.OpenEmergencyDialer -> true.also { openEmergency911() }
        else -> false
    }

    fun reopen(screen: CallingScreen) {
        when (screen) {
            CallingScreen.IN_CALL -> showInCallScreen()
            CallingScreen.PHONE -> openPhone()
            CallingScreen.EMERGENCY_KEYPAD -> openEmergency()
            CallingScreen.EMERGENCY_911 -> openEmergency911()
        }
    }

    private fun openEmergency911() {
        opened(CallingScreen.EMERGENCY_911)
        launch(Intent.ACTION_DIAL, "tel:911")
    }
}
