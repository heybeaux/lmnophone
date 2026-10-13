// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.os.Bundle
import android.content.Intent
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
import org.plainphone.boot.PolicyRuntime
import org.plainphone.device.authority.*
import org.plainphone.shell.PreviewPhone
import org.plainphone.shell.NotInBuildScreen
import org.plainphone.ui.home.*
import org.plainphone.ui.theme.*
import org.plainphone.ui.trusted.*

/** Private preview controls. This activity grants no access to Android settings or other apps. */
class PinAccessActivity : ComponentActivity() {
    private var page by mutableStateOf(PinPageState())
    private var value by mutableStateOf("")
    private var family by mutableStateOf<FamilyUiState?>(null)
    private var familyEditor: org.plainphone.voice.FamilyEditor? = null
    private val familyStore by lazy { org.plainphone.device.voice.AndroidFamilyStore.open(this) }
    private val familySpeaker by lazy { org.plainphone.device.voice.AndroidVoiceSpeaker(this) { _, person -> person?.spokenName.orEmpty() } }
    private fun openFamily() {
        if (page.page != PinPage.MENU) return
        familyEditor = org.plainphone.voice.FamilyEditor(familyStore, AdminContext(PreviewPhone.sample().userName),
            { page.page == PinPage.MENU && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) },
            { org.plainphone.device.voice.AndroidMicAccess(this, android.content.ComponentName(this, PlainphoneAdminReceiver::class.java)).selfGrant() },
            { name -> familySpeaker.speakOnRequest(org.plainphone.device.voice.VoicePrompt.Name, org.plainphone.device.voice.VoicePerson("name", name)) },
            { family = null; familyEditor = null }, { family = it }).also { it.open() }
    }
    private var confirmation by mutableStateOf("")
    private val runtime by lazy { PolicyRuntime.get(this) }
    private val flow by lazy {
        PinScreenFlow(object : PinScreenPort {
            override fun authenticateTurnOff(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit) =
                runtime.checkTurnOffPin(flow, pin) { runOnUiThread { done(it) } }
            override fun startTurnOff(flow: String, first: Boolean, second: Boolean, done: (org.plainphone.policy.WaitingOutcome) -> Unit) =
                runtime.startTurnOff(flow, first, second) { runOnUiThread { done(it) } }
            override fun closeTurnOff(flow: String) = runtime.closeTurnOff(flow)
            override fun entry(done: (PinEntry) -> Unit) = runtime.pinEntry { runOnUiThread { done(it) } }
            override fun enroll(pin: CharArray, done: (PinEnrollmentResult) -> Unit) =
                runtime.enrollPin(pin) { runOnUiThread { done(it) } }
            override fun authenticate(pin: CharArray, done: (PinAccessResult) -> Unit) =
                runtime.checkPin(pin) { runOnUiThread { done(it) } }
        }) { page = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            BackHandler { if (family != null) familyEditor?.event(FamilyEvent.Back) else if (page.page == PinPage.NOT_IN_BUILD) flow.backFromUnsupported() else finish() }
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    when (page.page) {
                        PinPage.LOADING -> Unit
                        PinPage.SETUP, PinPage.ENTER, PinPage.LOCKED, PinPage.OFF_PIN -> PinScreen(PinUiState(
                            context = AdminContext(PreviewPhone.sample().userName),
                            stage = when (page.page) {
                                PinPage.SETUP -> PinStage.NewPin
                                PinPage.LOCKED -> PinStage.Locked
                                else -> PinStage.Enter
                            },
                            value = value, confirmation = confirmation,
                            canContinue = value.length >= 6 && value.all { it in '0'..'9' } &&
                                (page.page != PinPage.SETUP || value == confirmation),
                            sending = page.busy, wrongPin = page.wrong,
                            wait = DateUtils.formatElapsedTime((page.waitMillis + 999) / 1000),
                        )) { event -> when (event) {
                            is PinEvent.ValueChanged -> value = event.value
                            is PinEvent.ConfirmationChanged -> confirmation = event.value
                            PinEvent.Continue -> {
                                val pin = value.toCharArray()
                                value = ""; confirmation = ""
                                flow.submit(pin)
                            }
                            PinEvent.UseRecoveryCode -> { value = ""; confirmation = ""; flow.unsupported() }
                            else -> finish()
                        } }
                        PinPage.MENU -> if (family != null) FamilyScreen(family!!) { familyEditor?.event(it) } else AdminMenuScreen(AdminMenuUiState(
                            AdminContext(PreviewPhone.sample().userName), AdminDestination.entries.map { AdminMenuItem(it) },
                        )) { when (it) {
                            AdminMenuEvent.Done -> finish()
                            is AdminMenuEvent.OpenNotice -> flow.unsupported()
                            is AdminMenuEvent.Open -> when (it.destination) { AdminDestination.TurnOff -> flow.turnOff(); AdminDestination.VoiceMessages -> openFamily(); else -> flow.unsupported() }
                        } }
                        PinPage.OFF_INTRO, PinPage.OFF_FIRST, PinPage.OFF_LAST -> TurnOffScreen(TurnOffUiState(
                            context = AdminContext(PreviewPhone.sample().userName),
                            stage = when (page.page) {
                                PinPage.OFF_INTRO -> TurnOffStage.Intro
                                PinPage.OFF_FIRST -> TurnOffStage.FirstConfirmation
                                else -> TurnOffStage.LastConfirmation
                            }, helper = "Sam", sending = page.busy,
                            testBuild = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0,
                        )) { if (it == TurnOffEvent.Continue) flow.continueTurnOff() else finish() }
                        PinPage.WAIT_STARTED, PinPage.WAIT_BUSY, PinPage.WAIT_FAILED -> {
                            val waiting = org.plainphone.boot.pendingChange(runtime.waitingRead.value,
                                java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
                            val off = waiting?.kind as? PendingChangeKind.TurnOff
                            WaitingChangeScreen(WaitingChangeUiState(
                            context = AdminContext(PreviewPhone.sample().userName),
                            stage = when (page.page) {
                                PinPage.WAIT_STARTED -> WaitingStage.Started
                                PinPage.WAIT_BUSY -> WaitingStage.Another
                                else -> WaitingStage.SaveFailed
                            }, change = getString(org.plainphone.ui.R.string.admin_turn_off),
                            happensAt = off?.time.orEmpty(), happensAtDay = off?.day,
                        )) { event ->
                                startActivity(Intent(this@PinAccessActivity, MainActivity::class.java)
                                    .addCategory(Intent.CATEGORY_HOME)
                                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    .putExtra("open_waiting_change", event == WaitingChangeEvent.SeeWaiting))
                                finish()
                            }
                        }
                        PinPage.NOT_IN_BUILD -> NotInBuildScreen(flow::backFromUnsupported)
                        PinPage.UNAVAILABLE -> NeedsAttentionScreen(NeedsAttentionUiState(null)) { finish() }
                    }
                }
            }
        }
    }

    override fun onDestroy() { familySpeaker.shutdown(); super.onDestroy() }

    override fun onResume() { super.onResume(); flow.open() }
    override fun onPause() {
        value = ""; confirmation = ""
        family = null; familyEditor = null; familySpeaker.stop()
        flow.close()
        super.onPause()
    }
}
