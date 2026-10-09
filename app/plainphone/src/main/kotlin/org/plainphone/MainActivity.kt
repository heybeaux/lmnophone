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
import android.util.Log
import android.text.format.DateFormat
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import kotlin.concurrent.thread
import kotlinx.coroutines.delay
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

/**
 * preview-2 (PLA-144): Home and Device Owner. Before Home shows, the starter-list policy is applied and
 * read back ([Preview2Device]); then the activity enters lock task. Screens still read [PreviewPhone]
 * for everything except the app list, and every unwired action says "Not in this test build yet".
 */
class MainActivity : ComponentActivity() {
    private val shell: ShellViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        val paper = PlainphoneColors.Paper.toArgb()
        enableEdgeToEdge(SystemBarStyle.light(paper, paper), SystemBarStyle.light(paper, paper))
        super.onCreate(savedInstanceState)
        val debuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        shell.start(if (debuggable) intent.getStringExtra(EXTRA_PREVIEW_SCREEN) else null)
        shell.prepare(Preview2Device(applicationContext)) { runOnUiThread(::enterLockTask) }
        val mapper = ShellUiMapper(resources.configuration.locales[0], DateFormat.is24HourFormat(this))
        setContent {
            // Nothing but paper until the policy has been applied and read back.
            val phone = shell.phone ?: return@setContent
            var snapshot by remember { mutableStateOf(phone.snapshot()) }
            LaunchedEffect(shell.state) {
                while (true) {
                    snapshot = phone.snapshot()
                    delay(1_000)
                }
            }
            PlainphoneShell(shell.state, snapshot, mapper, shell.navigator ?: return@setContent) { step ->
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
        if (shell.phone != null) enterLockTask()
    }

    /** Lock task only as Device Owner with this package allowed; otherwise Plainphone is an ordinary app. */
    private fun enterLockTask() {
        val dpm = getSystemService(DevicePolicyManager::class.java)
        val am = getSystemService(ActivityManager::class.java)
        if (!dpm.isLockTaskPermitted(packageName) || am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return
        try {
            startLockTask()
        } catch (e: IllegalStateException) {
            Log.w(Preview2Device.TAG, "PREVIEW2 lock task not started", e)
        }
    }

    private fun perform(effect: ShellEffect) {
        val intent = when (effect) {
            ShellEffect.OpenDialer -> Intent(Intent.ACTION_DIAL)
            ShellEffect.OpenMessages ->
                Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
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
    }
}

/** Keeps the back stack and the phone across configuration changes such as text size. */
class ShellViewModel : ViewModel() {
    /** Null until [prepare] has applied the policy and read it back. */
    var phone by mutableStateOf<PreviewPhone?>(null)
        private set
    var navigator: ShellNavigator? = null
        private set
    var state by mutableStateOf(ShellState())
    private var started = false
    private var preparing = false

    /** Applies and reads back the policy off the main thread, then shows Home with the real app list. */
    fun prepare(device: Preview2Device, onReady: () -> Unit) {
        if (preparing) return
        preparing = true
        thread(name = "preview2-apply") {
            val result = runCatching { device.apply() }
                .onFailure { Log.e(Preview2Device.TAG, "PREVIEW2 APPLY threw", it) }
            val attention = result.isFailure || result.getOrNull()?.needsAttention == true
            val apps = runCatching { device.homeApps() }.getOrDefault(emptyList()).map { (id, label) -> ApprovedApp(id, label) }
            val sample = PreviewPhone.sample()
            val ready = PreviewPhone(sample.copy(apps = apps, notices = NoticeInput(sample.mode, needsAttention = attention)))
            Handler(Looper.getMainLooper()).post {
                navigator = ShellNavigator(ready, ready, testBuild = true)
                phone = ready
                onReady()
            }
        }
    }

    fun start(entry: String?) {
        if (started) return
        started = true
        state = ShellState.forPreviewEntry(entry)
    }
}
