// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
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
 * preview-2 (PLA-144): Home and Device Owner. The manifest's `lockTaskMode="if_whitelisted"` puts this
 * task in lock task as it launches. Home draws only when [PolicyRuntime.gate] is READY (the starter-list
 * plan re-applied and read back) and the task is in lock task; until then the neutral checking screen
 * shows, with only Phone and Emergency. Screens still read [PreviewPhone] for everything except the app
 * list, and every unwired action says "Not in this test build yet".
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

    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val activities by lazy { getSystemService(ActivityManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        communication = AndroidCommunicationCounts(this, ::syncCounts)
        syncCounts()
        enterLockTask()
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        shell.start(if (debuggable) intent.getStringExtra(EXTRA_PREVIEW_SCREEN) else null)
        val runtime = PolicyRuntime.get(this)
        val device = Preview2Device(applicationContext)
        val mapper = ShellUiMapper(resources.configuration.locales[0], DateFormat.is24HourFormat(this))
        setContent {
            val gate by runtime.gate.collectAsState()
            val notice by runtime.notice.collectAsState()
            val homeVisible = resumed && gate == HomeGate.READY && shell.phone != null && shell.state.current == ShellRoute.Home
            val settingsVisible = resumed && gate == HomeGate.READY && shell.phone != null && shell.state.current == ShellRoute.PhoneSettings
            SideEffect {
                communication.controller.setVisible(homeVisible)
                communication.controller.setSettingsVisible(settingsVisible)
            }
            if (gate != HomeGate.READY || !enterLockTask()) {
                PlainphoneTheme { CheckingScreen(onPhone = { perform(ShellEffect.OpenDialer) }, onEmergency = ::openEmergency) }
                return@setContent
            }
            // Each read-back run refreshes the app list and the attention notice.
            LaunchedEffect(notice) { shell.prepare(device, attention = notice != null) }
            // Nothing but paper until the app list is read.
            val phone = shell.phone ?: return@setContent
            LaunchedEffect(phone) {
                Log.i(PolicyRuntime.TAG, "home shown lockTask=${activities.lockTaskModeState} at=${SystemClock.elapsedRealtime()}")
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
    }

    override fun onResume() {
        super.onResume()
        resumed = true
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

    /** True when this task is in lock task, starting it first if the package is allowed. */
    private fun enterLockTask(): Boolean {
        if (activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return true
        if (!dpm.isLockTaskPermitted(packageName)) return false
        return try {
            startLockTask()
            activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: RuntimeException) {
            Log.w(PolicyRuntime.TAG, "lock task not started", e)
            false
        }
    }

    /** The emergency-dialer screen of `com.android.phone` (PLA-5 A3), from the checking screen. */
    private fun openEmergency() {
        try {
            startActivity(Intent(EMERGENCY_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            perform(ShellEffect.OpenEmergencyDialer)
        }
    }

    private fun perform(effect: ShellEffect) {
        val intent = when (effect) {
            ShellEffect.OpenDialer -> CommunicationEntryIntents.phone()
            ShellEffect.OpenMessages ->
                CommunicationEntryIntents.messages()
            ShellEffect.OpenEmergencyDialer -> Intent(Intent.ACTION_DIAL, Uri.parse("tel:911"))
            is ShellEffect.OpenApp -> packageManager.getLaunchIntentForPackage(effect.id)
            ShellEffect.Finish -> null.also { finish() }
        } ?: return
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: ActivityNotFoundException) {
            // Preview: an app missing from this emulator just doesn't open.
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
    fun prepare(device: Preview2Device, attention: Boolean) {
        val generation = ++prepared
        thread(name = "preview2-home") {
            val apps = runCatching { device.homeApps() }.getOrDefault(emptyList()).map { (id, label) -> ApprovedApp(id, label) }
            val sample = PreviewPhone.sample()
            val ready = PreviewPhone(sample.copy(apps = apps, notices = NoticeInput(sample.mode, needsAttention = attention)))
            Handler(Looper.getMainLooper()).post {
                if (generation != prepared) return@post
                navigator = ShellNavigator(ready, ready, testBuild = true)
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
