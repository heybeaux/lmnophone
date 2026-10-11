// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
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
import org.plainphone.boot.CallingScreen
import org.plainphone.boot.CheckingScreen
import org.plainphone.boot.HomeGate
import org.plainphone.boot.LockTaskState
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
 * Only LOCKED counts; PINNED is not lock task (PLA-322 finding 3). Once LOCKED reads back, Home reopens
 * the calling screen recovery displaced, inside lock task (PLA-322 finding 1).
 */
class MainActivity : ComponentActivity() {
    private val runtime by lazy { PolicyRuntime.get(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enterLockTask()
        setContent {
            PlainphoneTheme {
                val gate by runtime.gate.collectAsState()
                val runs by runtime.runs.collectAsState()
                LaunchedEffect(runs) { enterLockTask() }
                if (gate == HomeGate.READY && enterLockTask(followUp = false)) {
                    LaunchedEffect(Unit) {
                        Log.i(PolicyRuntime.TAG, "home shown lockTask=${runtime.lockTaskState()} at=${SystemClock.elapsedRealtime()}")
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
        runtime.homeResumed()
        enterLockTask()
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
        when (screen) {
            CallingScreen.IN_CALL -> runtime.showInCallScreen()
            CallingScreen.PHONE -> openPhone()
            CallingScreen.EMERGENCY_KEYPAD -> openEmergency()
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
        /** The emergency-dialer screen of `com.android.phone` (PLA-5 A3). */
        const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"
    }
}
