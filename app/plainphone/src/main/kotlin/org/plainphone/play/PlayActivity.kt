// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import org.plainphone.MainActivity
import org.plainphone.boot.PolicyRuntime
import org.plainphone.device.session.PlayRequestResult
import org.plainphone.policy.SessionEnd
import org.plainphone.ui.theme.*
import org.plainphone.ui.trusted.*
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** No authority in intents or saved state. Every countdown request consumes its own fresh PIN. */
class PlayActivity : ComponentActivity() {
    private val runtime by lazy { PolicyRuntime.get(this) }
    private var pin by mutableStateOf("")
    private var needsPin by mutableStateOf(false)
    private var busy by mutableStateOf(false)
    private var wrong by mutableStateOf(false)
    private var wait by mutableStateOf(0L)
    private var overrideStage by mutableStateOf<OpeningStage?>(null)
    private var failed by mutableStateOf(false)
    private var returned by mutableStateOf(false)
    private var launched = false
    @Volatile private var active = false
    private var generation = 0
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        setContent {
            val view by runtime.playView.collectAsState()
            val context = AdminContext(getString(org.plainphone.ui.R.string.current_phone_user))
            LaunchedEffect(wait > 0) {
                while (wait > 0) { kotlinx.coroutines.delay(1000); wait = (wait - 1000).coerceAtLeast(0) }
            }
            BackHandler { home() }
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    when {
                        needsPin -> PinScreen(PinUiState(context, value = pin,
                            canContinue = pin.length >= 6 && pin.all { it in '0'..'9' }, sending = busy,
                            freshExplanation = true, wrongPin = wrong,
                            stage = if (wait > 0) PinStage.Locked else PinStage.Enter,
                            wait = DateUtils.formatElapsedTime((wait + 999) / 1000))) { event -> when (event) {
                                is PinEvent.ValueChanged -> if (!busy) pin = event.value
                                PinEvent.Continue -> request()
                                else -> home()
                            } }
                        failed || returned || playNeedsClosure(view) -> SessionScreen(SessionUiState(context,
                            if (busy) SessionStage.Checking else if (failed || !view.handbackReady) SessionStage.NotReady else SessionStage.Restored,
                            helper = getString(org.plainphone.ui.R.string.setup_helper), canShowDetails = false)) { event ->
                            if (event == SessionEvent.Continue) {
                                if (failed || !view.handbackReady) {
                                    busy = true
                                    runtime.tickPlay { runOnUiThread { busy = false; failed = !runtime.playView.value.handbackReady } }
                                } else home()
                            }
                        }
                        else -> OpeningScreen(playOpeningUi(view, context, overrideStage,
                            view.nextTurnMillis?.let { LocalTime.now().plusNanos(it * 1_000_000).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }.orEmpty(), busy)) { event ->
                            when(event) {
                                OpeningEvent.Stop -> { view.id?.let(runtime::cancelPlay); home() }
                                OpeningEvent.NotNow -> home()
                                OpeningEvent.See -> overrideStage = null
                                OpeningEvent.Continue -> when {
                                    overrideStage != null -> { overrideStage = null; home() }
                                    view.readyMillis != null -> start(view.id)
                                    view.countdownMillis != null -> home()
                                    else -> { needsPin = true; wrong = false }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    override fun onResume() {
        super.onResume(); active = true
        if (launched) {
            launched = false; returned = true; busy = true
            runtime.endPlay(SessionEnd.LEFT_PACKAGES) { runOnUiThread { busy = false } }
        }
        runtime.tickPlay()
    }
    override fun onPause() {
        active = false; generation++; pin = ""; needsPin = false; busy = false
        super.onPause()
    }
    private fun request() {
        if (!active || busy || wait > 0) return
        busy = true; val token = generation; val secret = pin.toCharArray(); pin = ""
        runtime.requestPlay(secret, { active && token == generation }) { result -> runOnUiThread {
            if (token != generation || !active) return@runOnUiThread
            busy = false
            when (result) {
                PlayRequestResult.Counting -> needsPin = false
                PlayRequestResult.Busy -> { needsPin = false; overrideStage = OpeningStage.AlreadyOpening }
                PlayRequestResult.LimitReached -> { needsPin = false; overrideStage = OpeningStage.NoOpenings }
                PlayRequestResult.Unavailable -> { needsPin = false; failed = true }
                is PlayRequestResult.Wrong -> { wrong = true; wait = result.remainingMillis }
                is PlayRequestResult.Delayed -> wait = result.remainingMillis
            }
        } }
    }
    private fun start(id: String?) {
        if (!active || busy || id == null) return
        busy = true; val token = generation
        runtime.startPlay(id) { opened -> runOnUiThread {
            busy = false
            if (!opened) { failed = true; return@runOnUiThread }
            if (!active || token != generation) { runtime.endPlay(SessionEnd.DONE); return@runOnUiThread }
            try {
                val launch = packageManager.getLaunchIntentForPackage("com.android.vending") ?: error("Play unavailable")
                launched = true
                startActivity(launch)
            } catch (_: Exception) {
                launched = false; failed = true; runtime.endPlay(SessionEnd.DONE)
            }
        } }
    }
    private fun home() {
        runtime.endPlay(SessionEnd.DONE)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
