// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.requests

import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import org.plainphone.boot.PolicyRuntime
import org.plainphone.device.authority.PinAccessResult
import org.plainphone.device.requests.RequestEngine
import org.plainphone.policy.*
import org.plainphone.ui.requests.*
import org.plainphone.ui.trusted.*
import org.plainphone.ui.theme.*

/** A fresh PIN before reading the number and before each commit. Pause discards fields and authority. */
class RequestRecipientActivity : ComponentActivity() {
    private var ui by mutableStateOf(RequestRecipientUiState())
    private var needsPin by mutableStateOf(true)
    private var changing = false
    private var pin by mutableStateOf("")
    private var busy by mutableStateOf(false)
    private var wrong by mutableStateOf(false)
    private var wait by mutableStateOf(0L)
    @Volatile private var generation = 0
    @Volatile private var active = false
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        setContent {
            LaunchedEffect(needsPin, wait > 0) {
                while (needsPin && wait > 0) { kotlinx.coroutines.delay(1_000); wait = (wait - 1_000).coerceAtLeast(0) }
            }
            BackHandler { finish() }
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    if (needsPin) PinScreen(PinUiState(AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)),
                        value = pin, canContinue = pin.length >= 6 && pin.all { it in '0'..'9' }, sending = busy,
                        wrongPin = wrong, freshExplanation = true,
                        stage = if (wait > 0) PinStage.Locked else PinStage.Enter,
                        wait = DateUtils.formatElapsedTime((wait + 999) / 1000))) { event -> when(event) {
                            is PinEvent.ValueChanged -> if (!busy) pin = event.value
                            PinEvent.Continue -> authenticate()
                            else -> finish()
                        } }
                    else RequestRecipientScreen(ui, ::event)
                }
            }
        }
    }
    private fun event(event: RecipientEvent) {
        if (!active || needsPin || busy) return
        when(event) {
            RecipientEvent.Back -> finish()
            RecipientEvent.Change -> ui = ui.copy(editing = true, canSave = valid())
            RecipientEvent.Remove -> ui = ui.copy(removing = true)
            is RecipientEvent.Name -> if (ui.editing && event.text.length <= 80) { ui = ui.copy(name = event.text); ui = ui.copy(canSave = valid()) }
            is RecipientEvent.Number -> if (ui.editing && event.text.length <= 25) { ui = ui.copy(number = event.text); ui = ui.copy(canSave = valid()) }
            RecipientEvent.Continue -> when {
                ui.editing && valid() -> ui = ui.copy(editing = false, confirming = true)
                ui.confirming || ui.removing -> { changing = true; needsPin = true; wrong = false; wait = 0; pin = "" }
            }
        }
    }
    private fun valid() = runCatching { RequestRecipient(ui.name.trim(), ui.number.trim()) }.isSuccess
    private fun authenticate() {
        if (!active || busy || !needsPin) return
        busy = true
        val secret = pin.toCharArray(); pin = ""
        val token = generation
        val next = if (changing && !ui.removing) RequestRecipient(ui.name.trim(), ui.number.trim()) else null
        PolicyRuntime.get(this).requestRecipientAction(secret, changing, next, { active && token == generation }) { result, saved, recipient ->
            runOnUiThread {
                if (!active || token != generation) return@runOnUiThread
                busy = false
                when(result) {
                    PinAccessResult.Open -> if (saved) {
                        changing = false; needsPin = false; wrong = false; wait = 0
                        ui = RequestRecipientUiState(recipient?.name.orEmpty(), recipient?.number.orEmpty())
                    } else { needsPin = false; ui = ui.copy(failed = true) }
                    is PinAccessResult.Wrong -> { wrong = true; wait = result.remainingMillis }
                    is PinAccessResult.Delayed -> wait = result.remainingMillis
                    PinAccessResult.Unavailable -> finish()
                }
            }
        }
    }
    override fun onResume() { super.onResume(); active = true }
    override fun onPause() {
        active = false; generation++; pin = ""; busy = false; needsPin = true; changing = false
        ui = RequestRecipientUiState(); wrong = false; wait = 0
        super.onPause()
    }
}
