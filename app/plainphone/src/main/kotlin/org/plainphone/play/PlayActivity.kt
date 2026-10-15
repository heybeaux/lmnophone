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
import org.plainphone.preview.*
import org.plainphone.MainActivity
import org.plainphone.boot.PolicyRuntime
import org.plainphone.device.session.PlayRequestResult
import org.plainphone.policy.SessionEnd
import org.plainphone.ui.theme.*
import org.plainphone.ui.home.*
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
    private var apps by mutableStateOf<PreviewAppsState?>(null)
    private var appsFlow: PreviewAppsFlow? = null
    private fun approvals() {
        if (appsFlow != null) return
        appsFlow = PreviewAppsFlow(object : PreviewAppsPort {
            override fun hidden(done: (List<org.plainphone.device.PreviewHiddenApp>?) -> Unit) =
                runtime.playCandidates { runOnUiThread { done(it) } }
            override fun permissions(id: String, hidden: Boolean, done: (org.plainphone.device.approval.AppPermissionRead?) -> Unit) =
                runtime.previewAppPermissions(id, true) { runOnUiThread { done(it) } }
            override fun add(app: org.plainphone.device.PreviewHiddenApp,
                selection: org.plainphone.device.approval.AppPermissionSnapshot,
                choices: Map<String, org.plainphone.policy.PermissionChoice>, pin: CharArray,
                active: () -> Boolean, done: (org.plainphone.device.AppAdditionResult) -> Unit) {
                pin.fill('\u0000')
                runtime.addPlayApp(app.id, app.identity, selection, choices, { this@PlayActivity.active && active() }) {
                    runOnUiThread { done(it) }
                }
            }
            override fun read(done: (List<PreviewAppRow>?) -> Unit) = done(emptyList())
            override fun move(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit) = done(false)
            override fun remove(id: String, pin: CharArray, active: () -> Boolean, done: (org.plainphone.device.AppRemovalResult) -> Unit) {
                pin.fill('\u0000'); done(org.plainphone.device.AppRemovalResult.Cancelled)
            }
        }, playApproval = true) { apps = it }.also { it.openHidden() }
    }
    @Composable private fun ApprovalContent(state: PreviewAppsState, context: AdminContext, live: Boolean) {
        val editor = appsFlow ?: return
        if (!live && state.stage !in setOf(PreviewAppsStage.Added, PreviewAppsStage.CheckFailed)) {
            PlayApprovalListScreen(playApprovalListUi(context, emptyList(), expired = true), {}, ::home)
            return
        }
        when (state.stage) {
            PreviewAppsStage.HiddenList, PreviewAppsStage.List -> PlayApprovalListScreen(
                playApprovalListUi(context, state.hidden.map { HiddenAppRow(it.id, it.name) },
                    unavailable = state.loadFailed, busy = state.busy), editor::selectHidden, ::home)
            PreviewAppsStage.AddReview, PreviewAppsStage.AddUnreviewed, PreviewAppsStage.AddConfirm,
            PreviewAppsStage.Added, PreviewAppsStage.Hidden -> state.selectedHidden?.let { app ->
                val stage = when (state.stage) {
                    PreviewAppsStage.AddReview -> AppReviewStage.Review
                    PreviewAppsStage.AddUnreviewed -> AppReviewStage.Unreviewed
                    PreviewAppsStage.AddConfirm -> AppReviewStage.Confirm
                    PreviewAppsStage.Added -> AppReviewStage.Added
                    else -> AppReviewStage.Hidden
                }
                AppReviewScreen(AppReviewUiState(context, InstalledApp(app.id, app.name, ""), stage = stage,
                    helper = getString(org.plainphone.ui.R.string.setup_helper), sending = state.busy,
                    previewSubset = true)) { event -> when (event) {
                    AppReviewEvent.Continue -> editor.nextAdd()
                    AppReviewEvent.DontAdd -> editor.dontAdd()
                    AppReviewEvent.Back -> home()
                    else -> editor.hidden()
                } }
            }
            PreviewAppsStage.AddPermissions -> PermissionsScreen(appPermissionChoicesUi(state, context)) { event -> when (event) {
                is PermissionsEvent.Choose -> editor.choosePermission(event.id, event.decision)
                PermissionsEvent.Continue -> editor.nextAdd()
            } }
            PreviewAppsStage.PermissionFailed -> AppPermissionFailureScreen(context, state.selectedHidden?.name.orEmpty(),
                state.failedRows, appPermissionsUi(state.permissions)) { editor.nextAdd() }
            PreviewAppsStage.CheckFailed -> NeedsAttentionScreen(NeedsAttentionUiState(null)) { home() }
            else -> PreviewAppsFailureScreen(context) { editor.hidden() }
        }
    }
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
            val closure = playSessionStage(view, failed, returned, busy)
            val context = AdminContext(getString(org.plainphone.ui.R.string.current_phone_user))
            LaunchedEffect(view.approvalAvailable) { if (view.approvalAvailable) approvals() }
            LaunchedEffect(wait > 0) {
                while (wait > 0) { kotlinx.coroutines.delay(1000); wait = (wait - 1000).coerceAtLeast(0) }
            }
            BackHandler { home() }
            PlainphoneTheme {
                Box(Modifier.fillMaxSize().background(PlainphoneColors.Paper).systemBarsPadding().displayCutoutPadding()) {
                    when {
                        apps != null -> ApprovalContent(apps!!, context, view.approvalAvailable)
                        needsPin -> PinScreen(PinUiState(context, value = pin,
                            canContinue = pin.length >= 6 && pin.all { it in '0'..'9' }, sending = busy,
                            freshExplanation = true, wrongPin = wrong,
                            stage = if (wait > 0) PinStage.Locked else PinStage.Enter,
                            wait = DateUtils.formatElapsedTime((wait + 999) / 1000))) { event -> when (event) {
                                is PinEvent.ValueChanged -> if (!busy) pin = event.value
                                PinEvent.Continue -> request()
                                else -> home()
                            } }
                        closure != null -> SessionScreen(SessionUiState(context, closure,
                            helper = getString(org.plainphone.ui.R.string.setup_helper), canShowDetails = false)) { event ->
                            if (event == SessionEvent.Continue) {
                                if (!view.handbackReady) {
                                    busy = true
                                    runtime.tickPlay { runOnUiThread { busy = false } }
                                } else home()
                            }
                        }
                        else -> OpeningScreen(playOpeningUi(view, context, if (failed) OpeningStage.OpenFailed else overrideStage,
                            view.nextTurnMillis?.let { LocalTime.now().plusNanos(it * 1_000_000).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)) }.orEmpty(), busy)) { event ->
                            when(event) {
                                OpeningEvent.Stop -> { view.id?.let(runtime::cancelPlay); home() }
                                OpeningEvent.NotNow -> home()
                                OpeningEvent.See -> overrideStage = null
                                OpeningEvent.Continue -> when {
                                    failed || view.left <= 0 && view.countdownMillis == null && view.readyMillis == null -> home()
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
        appsFlow?.close(); appsFlow = null; apps = null
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
                launched = false; failed = true; busy = true
                runtime.endPlay(SessionEnd.DONE) { runOnUiThread { busy = false } }
            }
        } }
    }
    private fun home() {
        appsFlow?.close()
        runtime.endPlay(SessionEnd.DONE)
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
