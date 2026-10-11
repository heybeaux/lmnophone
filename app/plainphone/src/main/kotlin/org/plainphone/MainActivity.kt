// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.ActivityManager
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.plainphone.boot.BuildControl
import org.plainphone.boot.CheckingScreen
import org.plainphone.boot.HomeGate
import org.plainphone.boot.PolicyRuntime
import org.plainphone.boot.checkingShowsAttention
import org.plainphone.ui.home.HomeEvent
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/**
 * Plainphone Home. Keyguard first (PLA-264 finding 1): the manifest never starts lock task; Home calls
 * `startLockTask()` itself, and only when [PolicyRuntime.mayEnterLockTask] reads the features back with
 * KEYGUARD. It tries on create, resume, new intent (the boot receiver's relaunch) and after every
 * re-apply run. Home draws only when [PolicyRuntime.gate] is READY (the full re-apply read back) and
 * the task is in lock task; until then the neutral checking screen shows, with only Phone and Emergency.
 * Lock task must actually be running (PLA-307 finding 2): create and resume read the live state, and
 * the runtime brings Home forward after a ready pass that finds it not running ([LockTaskRecovery]).
 */
class MainActivity : ComponentActivity() {
    private val activities by lazy { getSystemService(ActivityManager::class.java) }
    private val runtime by lazy { PolicyRuntime.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterLockTask()
        setContent {
            PlainphoneTheme {
                val gate by runtime.gate.collectAsState()
                val runs by runtime.runs.collectAsState()
                LaunchedEffect(runs) { enterLockTask() }
                if (gate == HomeGate.READY && enterLockTask()) {
                    LaunchedEffect(Unit) {
                        Log.i(PolicyRuntime.TAG, "home shown lockTask=${activities.lockTaskModeState} at=${SystemClock.elapsedRealtime()}")
                    }
                    val notice by runtime.notice.collectAsState()
                    Home(notice)
                } else {
                    val notice by runtime.notice.collectAsState()
                    CheckingScreen(onPhone = ::openPhone, onEmergency = ::openEmergency, attention = checkingShowsAttention(gate, notice))
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        enterLockTask()
    }

    override fun onResume() {
        super.onResume()
        enterLockTask()
    }

    /** True when this task is in lock task, starting it first only if Keyguard first allows it now. */
    private fun enterLockTask(): Boolean {
        if (activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return true
        if (!runtime.mayEnterLockTask()) return false
        return try {
            startLockTask()
            activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: RuntimeException) {
            false
        }
    }

    @Composable
    private fun Home(notice: HomeNotice?) {
        val now = LocalDateTime.now()
        HomeScreen(
            HomeUiState(
                time = now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                date = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                shortDate = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
                notice = notice,
            ),
        ) { event ->
            // The wiring slice adds apps, Messages and More.
            if (event == HomeEvent.OpenPhone) openPhone()
        }
    }

    private fun openPhone() = launch(BuildControl.control.phoneIntent(Intent(Intent.ACTION_DIAL)))

    private fun openEmergency() = launch(Intent(EMERGENCY_DIAL))

    private fun launch(intent: Intent) {
        try {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            Log.w(PolicyRuntime.TAG, "no activity for ${intent.action}")
        }
    }

    companion object {
        /** The emergency-dialer screen of `com.android.phone` (PLA-5 A3). */
        const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"
    }
}
