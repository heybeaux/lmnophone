// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
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
import org.plainphone.boot.CheckingScreen
import org.plainphone.boot.HomeGate
import org.plainphone.boot.PolicyRuntime
import org.plainphone.ui.home.HomeEvent
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme

/**
 * Plainphone Home. The manifest's `lockTaskMode="if_whitelisted"` puts this task in lock task as it
 * launches, before any frame, whenever the package is on the lock-task list. Home itself draws only
 * when [PolicyRuntime.gate] is READY (the full re-apply read back) and the task is in lock task;
 * until then the neutral checking screen shows, with only Phone and Emergency.
 */
class MainActivity : ComponentActivity() {
    private val dpm by lazy { getSystemService(DevicePolicyManager::class.java) }
    private val activities by lazy { getSystemService(ActivityManager::class.java) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterLockTask()
        val runtime = PolicyRuntime.get(this)
        setContent {
            PlainphoneTheme {
                val gate by runtime.gate.collectAsState()
                if (gate == HomeGate.READY && enterLockTask()) {
                    LaunchedEffect(Unit) {
                        Log.i(PolicyRuntime.TAG, "home shown lockTask=${activities.lockTaskModeState} at=${SystemClock.elapsedRealtime()}")
                    }
                    Home()
                } else {
                    CheckingScreen(onPhone = ::openPhone, onEmergency = ::openEmergency)
                }
            }
        }
    }

    /** True when this task is in lock task, starting it first if the package is allowed. */
    private fun enterLockTask(): Boolean {
        if (activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE) return true
        if (!dpm.isLockTaskPermitted(packageName)) return false
        return try {
            startLockTask()
            activities.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (e: RuntimeException) {
            false
        }
    }

    @Composable
    private fun Home() {
        val now = LocalDateTime.now()
        HomeScreen(
            HomeUiState(
                time = now.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)),
                date = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)),
                shortDate = now.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)),
            ),
        ) { event ->
            // The wiring slice adds apps, Messages and More.
            if (event == HomeEvent.OpenPhone) openPhone()
        }
    }

    private fun openPhone() = launch(Intent(Intent.ACTION_DIAL))

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
