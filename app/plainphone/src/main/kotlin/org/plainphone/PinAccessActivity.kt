// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.text.format.DateUtils
import android.os.Bundle
import android.content.Intent
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
import androidx.compose.ui.graphics.asImageBitmap
import org.plainphone.preview.*
import org.plainphone.boot.PolicyRuntime
import org.plainphone.device.authority.*
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
        familyEditor = org.plainphone.voice.FamilyEditor(familyStore, AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)),
            { page.page == PinPage.MENU && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED) },
            { org.plainphone.device.voice.AndroidMicAccess(this, android.content.ComponentName(this, PlainphoneAdminReceiver::class.java)).selfGrant() },
            { name -> familySpeaker.speakOnRequest(org.plainphone.device.voice.VoicePrompt.Name, org.plainphone.device.voice.VoicePerson("name", name)) },
            { family = null; familyEditor = null }, { family = it }, helperName = getString(org.plainphone.ui.R.string.setup_helper)).also { it.open() }
    }
    private var apps by mutableStateOf<PreviewAppsState?>(null)
    private var appsFlow: PreviewAppsFlow? = null
    private val appsContext get() = AdminContext(getString(org.plainphone.ui.R.string.current_phone_user), null)
    private fun openApps() {
        if (page.page != PinPage.MENU) return
        appsFlow = PreviewAppsFlow(object : PreviewAppsPort {
            override fun hidden(done: (List<org.plainphone.device.PreviewHiddenApp>?) -> Unit) =
                runtime.previewHiddenApps { rows -> runOnUiThread { done(rows) } }
            override fun add(app: org.plainphone.device.PreviewHiddenApp, pin: CharArray, active: () -> Boolean, done: (org.plainphone.device.AppAdditionResult) -> Unit) =
                runtime.addPreviewApp(app.id, app.identity, pin, active) { result -> runOnUiThread { done(result) } }
            override fun read(done: (List<PreviewAppRow>?) -> Unit) = runtime.previewApps { rows -> runOnUiThread { done(rows) } }
            override fun move(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit) =
                runtime.movePreviewApp(id, delta, active) { result -> runOnUiThread { done(result) } }
            override fun remove(id: String, pin: CharArray, active: () -> Boolean, done: (org.plainphone.device.AppRemovalResult) -> Unit) =
                runtime.removePreviewApp(id, pin, active) { result -> runOnUiThread { done(result) } }
        }) { apps = it }
        appsFlow?.open()
    }
    private fun backApps() {
        value = ""
        if (apps?.stage == PreviewAppsStage.List) { appsFlow?.close(); appsFlow = null; apps = null }
        else appsFlow?.back()
    }
    @Composable private fun AppsContent(state: PreviewAppsState) {
        val editor = appsFlow ?: return
        val app = state.selected?.let { InstalledApp(it.id, it.name, "") }
        when (state.stage) {
            PreviewAppsStage.List -> if (state.loadFailed) NeedsAttentionScreen(NeedsAttentionUiState(null)) { backApps() }
            else AppsOnPhoneScreen(AppsOnPhoneUiState(appsContext,
                state.rows.mapIndexed { index, row -> AppOnPhone(InstalledApp(row.id, row.name, ""), row.protected,
                    canMoveUp = index > 0 && !state.rows[index - 1].protected,
                    canMoveDown = index < state.rows.lastIndex && !state.rows[index + 1].protected) } + listOf(
                    AppOnPhone(InstalledApp("@sms", getString(org.plainphone.ui.R.string.home_messages), ""), alwaysOn = true),
                    AppOnPhone(InstalledApp("@phone", getString(org.plainphone.ui.R.string.home_phone), ""), alwaysOn = true)),
                sending = state.busy, canAddHidden = true)) { event -> when (event) {
                    AppsOnPhoneEvent.AddHidden -> editor.hidden()
                    AppsOnPhoneEvent.Back -> backApps()
                    is AppsOnPhoneEvent.Open -> editor.select(event.id)
                    is AppsOnPhoneEvent.MoveUp -> editor.move(event.id, -1)
                    is AppsOnPhoneEvent.MoveDown -> editor.move(event.id, 1)
                } }
            PreviewAppsStage.Details -> if (app != null) PreviewAppDetailScreen(appsContext, app.name, state.selected.version, onBack = ::backApps) { editor.askRemove() }
            PreviewAppsStage.Confirm, PreviewAppsStage.Removed -> if (app != null)
                AppReviewScreen(AppReviewUiState(appsContext, app, stage = if (state.stage == PreviewAppsStage.Confirm)
                    AppReviewStage.Remove else AppReviewStage.Removed, sending = state.busy, previewSubset = true)) {
                    if (it == AppReviewEvent.Continue) { value = ""; editor.confirm() } else if (it == AppReviewEvent.Back) backApps() else editor.open()
                }
            PreviewAppsStage.HiddenList -> PreviewHiddenAppsScreen(appsContext,
                state.hidden.map { HiddenAppRow(it.id, it.name, it.icon?.asImageBitmap()) },
                state.loadFailed, state.busy, editor::selectHidden, ::backApps)
            PreviewAppsStage.AddReview, PreviewAppsStage.AddUnreviewed, PreviewAppsStage.AddConfirm,
            PreviewAppsStage.Added, PreviewAppsStage.Hidden -> state.selectedHidden?.let { selected ->
                val stage = when (state.stage) {
                    PreviewAppsStage.AddReview -> AppReviewStage.Review
                    PreviewAppsStage.AddUnreviewed -> AppReviewStage.Unreviewed
                    PreviewAppsStage.AddConfirm -> AppReviewStage.Confirm
                    PreviewAppsStage.Added -> AppReviewStage.Added
                    else -> AppReviewStage.Hidden
                }
                AppReviewScreen(AppReviewUiState(appsContext, InstalledApp(selected.id, selected.name, ""),
                    stage = stage, helper = getString(org.plainphone.ui.R.string.setup_helper), sending = state.busy, previewSubset = true)) { event -> when (event) {
                    AppReviewEvent.Continue -> { value = ""; editor.nextAdd() }
                    AppReviewEvent.DontAdd -> editor.dontAdd()
                    AppReviewEvent.Back -> backApps()
                    else -> editor.open()
                } }
            }
            PreviewAppsStage.Unsupported -> NotInBuildScreen { editor.open() }
            PreviewAppsStage.Pin, PreviewAppsStage.AddPin -> PinScreen(PinUiState(appsContext,
                stage = if (state.waitMillis > 0) PinStage.Locked else PinStage.Enter,
                value = value, canContinue = value.length >= 6 && value.all { it in '0'..'9' },
                sending = state.busy, wrongPin = state.wrong, freshExplanation = true,
                wait = DateUtils.formatElapsedTime((state.waitMillis + 999) / 1000))) { event -> when (event) {
                    is PinEvent.ValueChanged -> value = event.value
                    PinEvent.Continue -> { val pin = value.toCharArray(); value = ""; editor.submit(pin) }
                    PinEvent.UseRecoveryCode -> { value = ""; editor.unsupported() }
                    else -> backApps()
                } }
            PreviewAppsStage.SaveFailed -> PreviewAppsFailureScreen(appsContext) { editor.open() }
            PreviewAppsStage.CheckFailed -> NeedsAttentionScreen(NeedsAttentionUiState(null)) { finish() }
        }
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
            override fun authenticateChange(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit) =
                runtime.checkChangePin(flow, pin) { runOnUiThread { done(it) } }
            override fun changePin(flow: String, newPin: CharArray, done: (PinChangeOutcome) -> Unit) =
                runtime.changePin(flow, newPin) { runOnUiThread { done(it) } }
            override fun recoveryWaits(done: (org.plainphone.policy.CredentialWaits?) -> Unit) =
                runtime.recoveryWaits { runOnUiThread { done(it) } }
            override fun rotateRecovery(pin: CharArray, done: (RecoveryAccessResult) -> Unit) =
                runtime.rotateRecovery(pin) { runOnUiThread { done(it) } }
            override fun recover(code: CharArray, newPin: CharArray, done: (RecoveryAccessResult) -> Unit) =
                runtime.recoverPin(code, newPin) { runOnUiThread { done(it) } }
            override fun closeChange(flow: String) = runtime.closeChangePin(flow)
        }) { page = it }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent {
            LaunchedEffect(page.page) {
                while (page.page in setOf(PinPage.LOCKED, PinPage.RECOVERY_LOCKED)) {
                    kotlinx.coroutines.delay(1_000)
                    flow.refreshWaits()
                }
            }
            BackHandler { if (apps != null) backApps() else if (family != null) familyEditor?.event(FamilyEvent.Back) else when (page.page) {
                PinPage.NOT_IN_BUILD -> flow.backFromUnsupported()
                PinPage.CHANGE_OLD, PinPage.CHANGE_NEW, PinPage.CHANGE_DONE, PinPage.CHANGE_FAILED -> {
                    value = ""; confirmation = ""; flow.back()
                }
                else -> finish()
            } }
            val waitingNow by runtime.waitingRead.collectAsState()
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    when (page.page) {
                        PinPage.LOADING -> Unit
                        PinPage.SETUP, PinPage.ENTER, PinPage.LOCKED, PinPage.OFF_PIN, PinPage.CHANGE_OLD,
                        PinPage.CHANGE_NEW, PinPage.RECOVERY_PIN, PinPage.RECOVERY, PinPage.RECOVERY_NEW,
                        PinPage.RECOVERY_LOCKED, PinPage.RECOVERY_ISSUED -> PinScreen(pinUiState(
                            page, AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)), value, confirmation, flow.displayedCode(),
                            waitText(page.waitMillis), waitText(page.pinWaitMillis), waitingChangeName(waitingNow),
                            helper = getString(org.plainphone.ui.R.string.setup_helper),
                        )) { event -> when (event) {
                            is PinEvent.ValueChanged -> value = event.value
                            is PinEvent.ConfirmationChanged -> confirmation = event.value
                            PinEvent.Continue -> {
                                val secret = value.toCharArray(); val again = confirmation.toCharArray()
                                value = ""; confirmation = ""
                                when (page.page) {
                                    PinPage.RECOVERY -> { again.fill('\u0000'); flow.submitRecovery(secret) }
                                    PinPage.CHANGE_NEW, PinPage.RECOVERY_NEW -> flow.submitNewPin(secret, again)
                                    else -> { again.fill('\u0000'); flow.submit(secret) }
                                }
                            }
                            PinEvent.UseRecoveryCode -> { value = ""; confirmation = ""; flow.useRecovery() }
                            PinEvent.EnterPin -> { value = ""; confirmation = ""; flow.enterPin() }
                            PinEvent.Ok -> if (page.page == PinPage.RECOVERY_ISSUED) flow.codeWritten() else finish()
                            else -> finish()
                        } }
                        PinPage.MENU -> if (apps != null) AppsContent(apps!!) else if (family != null) FamilyScreen(family!!) { familyEditor?.event(it) } else AdminMenuScreen(pinMenuUiState(AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)))) { when (it) {
                            AdminMenuEvent.Done -> finish()
                            is AdminMenuEvent.OpenNotice -> flow.unsupported()
                            is AdminMenuEvent.Open -> when (it.destination) {
                                AdminDestination.TurnOff -> flow.turnOff()
                                AdminDestination.ChangePin -> flow.changePin()
                                AdminDestination.NewRecoveryCode -> flow.newRecoveryCode()
                                AdminDestination.VoiceMessages -> openFamily()
                                AdminDestination.Apps -> openApps()
                                AdminDestination.Requests -> startActivity(Intent(this@PinAccessActivity, org.plainphone.requests.RequestsActivity::class.java)
                                    .putExtra(org.plainphone.requests.RequestsActivity.HISTORY, true))
                                AdminDestination.RequestRecipient -> startActivity(Intent(this@PinAccessActivity, org.plainphone.requests.RequestRecipientActivity::class.java))
                                else -> flow.unsupported()
                            }
                        } }
                        PinPage.OFF_INTRO, PinPage.OFF_FIRST, PinPage.OFF_LAST -> TurnOffScreen(TurnOffUiState(
                            context = AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)),
                            stage = when (page.page) {
                                PinPage.OFF_INTRO -> TurnOffStage.Intro
                                PinPage.OFF_FIRST -> TurnOffStage.FirstConfirmation
                                else -> TurnOffStage.LastConfirmation
                            }, helper = getString(org.plainphone.ui.R.string.setup_helper), sending = page.busy,
                            testBuild = applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0,
                        )) { if (it == TurnOffEvent.Continue) flow.continueTurnOff() else finish() }
                        PinPage.WAIT_STARTED, PinPage.WAIT_BUSY, PinPage.WAIT_FAILED -> {
                            val waiting = org.plainphone.boot.pendingChange(runtime.waitingRead.value,
                                java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT))
                            val off = waiting?.kind as? PendingChangeKind.TurnOff
                            WaitingChangeScreen(WaitingChangeUiState(
                            context = AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)),
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
                        PinPage.CHANGE_DONE, PinPage.CHANGE_FAILED -> PinChangeResultScreen(
                            AdminContext(getString(org.plainphone.ui.R.string.current_phone_user)), page.page == PinPage.CHANGE_DONE) { flow.back() }
                        PinPage.NOT_IN_BUILD -> NotInBuildScreen(flow::backFromUnsupported)
                        PinPage.UNAVAILABLE -> NeedsAttentionScreen(NeedsAttentionUiState(null)) { finish() }
                    }
                }
            }
        }
    }

    private fun waitText(millis: Long): String {
        val seconds = ((millis + 999) / 1000).coerceAtLeast(1).toInt()
        return if (seconds < 60) resources.getQuantityString(org.plainphone.ui.R.plurals.recovery_wait_seconds, seconds, seconds)
        else if (seconds < 3600) ((seconds + 59) / 60).let { resources.getQuantityString(org.plainphone.ui.R.plurals.recovery_wait_minutes, it, it) }
        else getString(org.plainphone.ui.R.string.recovery_wait_hour)
    }

    private fun waitingChangeName(read: org.plainphone.boot.WaitingRead?): String? {
        val waiting = org.plainphone.boot.pendingChange(read,
            java.time.format.DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.SHORT)) ?: return null
        return if (waiting.kind is PendingChangeKind.TurnOff) getString(org.plainphone.ui.R.string.admin_turn_off) else null
    }

    override fun onDestroy() { familySpeaker.shutdown(); super.onDestroy() }

    override fun onResume() { super.onResume(); flow.open() }
    override fun onPause() {
        value = ""; confirmation = ""
        appsFlow?.close(); appsFlow = null; apps = null
        family = null; familyEditor = null; familySpeaker.stop()
        flow.close()
        super.onPause()
    }
}
