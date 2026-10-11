// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.SideEffect
import org.plainphone.counts.CommunicationEntryIntents
import org.plainphone.counts.AndroidCommunicationCounts
import org.plainphone.counts.CountAvailability
import org.plainphone.counts.CountKind
import org.plainphone.counts.CommunicationCounts
import org.plainphone.shell.ShellRoute
import org.plainphone.ui.user.PhoneSettingsUiState
import org.plainphone.ui.user.PhoneSettingsEvent
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
import org.plainphone.boot.BuildControl
import org.plainphone.boot.CallingScreen
import org.plainphone.boot.LockTaskState
import org.plainphone.boot.checkingShowsAttention
import org.plainphone.boot.CheckingScreen
import org.plainphone.boot.HomeGate
import org.plainphone.boot.PolicyRuntime
import org.plainphone.policy.NoticeInput
import org.plainphone.preview.Preview2Device
import org.plainphone.shell.ApprovedApp
import org.plainphone.shell.PlainphoneShell
import org.plainphone.shell.PreviewPhone
import org.plainphone.shell.ShellEffect
import org.plainphone.shell.ShellNavigator
import org.plainphone.shell.ShellState
import org.plainphone.shell.ShellUiMapper
import org.plainphone.shell.Step
import org.plainphone.ui.theme.PlainphoneColors
import org.plainphone.ui.theme.PlainphoneTheme

/**
 * Plainphone Home. Keyguard first (PLA-264 finding 1): the manifest never starts lock task; Home calls
 * `startLockTask()` itself, and only when [PolicyRuntime.mayEnterLockTask] reads the features back with
 * KEYGUARD. It tries on create, resume, new intent (the boot receiver's relaunch) and after every
 * re-apply run. Home draws only when [PolicyRuntime.gate] is READY (the full re-apply read back) and
 * the task is in lock task; until then the neutral checking screen shows, with only Phone and Emergency.
 * Lock task must actually be running (PLA-307 finding 2): create and resume read the live state, and
 * the runtime brings Home forward after a ready pass that finds it not running ([LockTaskRecovery]).
 * Only LOCKED counts; PINNED is not lock task (PLA-322 finding 3). Once LOCKED reads back, Home reopens
 * the calling screen recovery displaced, inside lock task (PLA-322 finding 1).
 */
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()
    private lateinit var communication: AndroidCommunicationCounts
    private var resumed by mutableStateOf(false)
    private var counts by mutableStateOf(CommunicationCounts())
    private var callsUnavailable by mutableStateOf(false)
    private var messagesUnavailable by mutableStateOf(false)
    private var countsEnabled by mutableStateOf(false)
    private var unsupportedCountApps by mutableStateOf(false)

    private fun syncCounts() {
        counts = communication.controller.counts
        callsUnavailable = communication.controller.availability(CountKind.CALLS) == CountAvailability.UNAVAILABLE
        messagesUnavailable = communication.controller.availability(CountKind.MESSAGES) == CountAvailability.UNAVAILABLE
        countsEnabled = communication.controller.enabled
        unsupportedCountApps = communication.controller.unsupportedApps
    }

    private val runtime by lazy { PolicyRuntime.get(this) }
    private val callingActions by lazy {
        CallingScreenActions(runtime::opened, ::openPhone, ::openEmergency, { runtime.showInCallScreen() }) { action, uri ->
            launch(Intent(action, uri?.let(Uri::parse)))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        communication = AndroidCommunicationCounts(this, ::syncCounts)
        syncCounts()
        enterLockTask()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        shell.start(if (debuggable) intent.getStringExtra(EXTRA_PREVIEW_SCREEN) else null)
        val device = Preview2Device(applicationContext)
        val mapper = ShellUiMapper(resources.configuration.locales[0], DateFormat.is24HourFormat(this))
        setContent {
            val gate by runtime.gate.collectAsState()
            val refresh by runtime.refresh.collectAsState()
            val runs by runtime.runs.collectAsState()
            val notice by runtime.notice.collectAsState()
            LaunchedEffect(runs) { enterLockTask() }
            val homeVisible = resumed && gate == HomeGate.READY && shell.phone != null && shell.state.current == ShellRoute.Home
            val settingsVisible = resumed && gate == HomeGate.READY && shell.phone != null && shell.state.current == ShellRoute.PhoneSettings
            SideEffect {
                communication.controller.setVisible(homeVisible)
                communication.controller.setSettingsVisible(settingsVisible)
            }
            if (gate != HomeGate.READY || !enterLockTask(followUp = false)) {
                PlainphoneTheme { CheckingScreen(onPhone = ::openPhone, onEmergency = ::openEmergency, attention = checkingShowsAttention(gate, notice)) }
                return@setContent
            }
            // Each ready read-back run refreshes the app list and the attention notice.
            LaunchedEffect(refresh) { shell.prepare(device, attention = refresh.notice != null, productName = getString(R.string.product_name)) }
            // Nothing but paper until the app list is read.
            val phone = shell.phone ?: return@setContent
            LaunchedEffect(phone) {
                Log.i(PolicyRuntime.TAG, "home shown lockTask=${runtime.lockTaskState()} at=${SystemClock.elapsedRealtime()}")
            }
            var snapshot by remember(phone) { mutableStateOf(phone.snapshot()) }
            LaunchedEffect(shell.state, phone) {
                while (true) {
                    snapshot = phone.snapshot()
                    delay(1_000)
                }
            }
            PlainphoneShell(shell.state, snapshot, mapper, shell.navigator ?: return@setContent,
                counts = counts,
                settings = PhoneSettingsUiState(countsEnabled, callsUnavailable, messagesUnavailable, unsupportedCountApps),
                onSettings = { event ->
                    when (event) {
                        PhoneSettingsEvent.Back -> shell.state = shell.navigator!!.back(shell.state).state
                        is PhoneSettingsEvent.ShowCommunicationCountsChanged -> {
                            communication.controller.setEnabled(event.enabled)
                            syncCounts()
                        }
                    }
                }) { step ->
                shell.state = step.state
                step.effect?.let(::perform)
            }
        }
    }

    /** The Home key always lands on the home screen, whatever Plainphone screen was open. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) shell.state = ShellState()
        enterLockTask()
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        runtime.homeResumed()
        runtime.onUserUnlocked()
        enterLockTask()
    }

    override fun onPause() {
        resumed = false
        communication.controller.setVisible(false)
        communication.controller.setSettingsVisible(false)
        syncCounts()
        super.onPause()
    }

    override fun onDestroy() {
        communication.close()
        super.onDestroy()
    }

    /**
     * True when lock task is LOCKED, starting it first only if Keyguard first allows it now. PINNED is
     * someone else's pin, not ours to start over. With [followUp], a LOCKED read reopens the calling
     * screen recovery displaced; never from composition.
     */
    private fun enterLockTask(followUp: Boolean = true): Boolean {
        var state = runtime.lockTaskState()
        if ((state == LockTaskState.NONE || state == LockTaskState.UNREADABLE) && runtime.mayEnterLockTask()) {
            try {
                startLockTask()
            } catch (e: RuntimeException) {
                // Read below: still not LOCKED.
            }
            state = runtime.lockTaskState()
        }
        if (followUp) runtime.onHomeLockTaskState(state)?.let(::reopen)
        return state.contained
    }

    /** Brings back a displaced calling screen, now inside lock task. Never ends or touches a call. */
    private fun reopen(screen: CallingScreen) {
        Log.i(PolicyRuntime.TAG, "reopening $screen inside lock task at=${SystemClock.elapsedRealtime()}")
        callingActions.reopen(screen)
    }

    private fun openPhone() {
        runtime.opened(CallingScreen.PHONE)
        launch(BuildControl.control.phoneIntent(Intent(Intent.ACTION_DIAL)))
    }

    private fun openEmergency() {
        runtime.opened(CallingScreen.EMERGENCY_KEYPAD)
        openEmergencyDialer { action, uri ->
            launch(Intent(action, uri?.let(Uri::parse)))
        }
    }

    private fun perform(effect: ShellEffect) {
        if (callingActions.perform(effect)) return
        val intent = when (effect) {
            ShellEffect.OpenDialer, ShellEffect.OpenEmergencyDialer -> null // Handled above.
            ShellEffect.OpenMessages ->
                CommunicationEntryIntents.messages()
            is ShellEffect.OpenApp -> packageManager.getLaunchIntentForPackage(effect.id)
            ShellEffect.Finish -> null.also { finish() }
        } ?: return
        launch(intent)
    }

    private fun launch(intent: Intent): Boolean {
        return try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(PolicyRuntime.TAG, "no activity for ${intent.action}")
            false
        }
    }

    companion object {
        /** Debug builds only: `more`, `blocked_link`, `wifi_sign_in` or `wifi_no_internet`. */
        const val EXTRA_PREVIEW_SCREEN = "org.plainphone.preview.SCREEN"
        const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"
    }
}

/** Keeps the back stack and the phone across configuration changes such as text size. */
class ShellViewModel : ViewModel() {
    /** Null until [prepare] has read the app list after a read-back run. */
    var phone by mutableStateOf<PreviewPhone?>(null)
        private set
    var navigator: ShellNavigator? = null
        private set
    var state by mutableStateOf(ShellState())
    private var started = false
    private var prepared = 0

    /**
     * Reads the starter apps off the main thread, after the runtime has re-applied and read back the
     * policy, then shows Home with the real app list. The latest call wins.
     */
    fun prepare(device: Preview2Device, attention: Boolean, productName: String) {
        val generation = ++prepared
        thread(name = "preview2-home") {
            val apps = runCatching { device.homeApps() }.getOrDefault(emptyList()).map { (id, label) -> ApprovedApp(id, label) }
            val sample = PreviewPhone.sample()
            val ready = PreviewPhone(sample.copy(apps = apps, notices = NoticeInput(sample.mode, needsAttention = attention)))
            Handler(Looper.getMainLooper()).post {
                if (generation != prepared) return@post
                navigator = ShellNavigator(ready, ready, productName, testBuild = true)
                phone = ready
            }
        }
    }

    fun start(entry: String?) {
        if (started) return
        started = true
        state = ShellState.forPreviewEntry(entry)
    }
}
