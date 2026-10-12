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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import org.plainphone.boot.BuildControl
import org.plainphone.boot.CallingScreen
import org.plainphone.boot.CheckingScreen
import org.plainphone.boot.HomeGate
import org.plainphone.boot.LockTaskState
import org.plainphone.boot.PolicyRuntime
import org.plainphone.boot.cancelStage
import org.plainphone.boot.confirmStage
import org.plainphone.boot.checkingShowsAttention
import org.plainphone.policy.NoticeTap
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.ui.home.HomeEvent
import org.plainphone.ui.home.HomeNotice
import org.plainphone.ui.home.HomeScreen
import org.plainphone.ui.home.HomeUiState
import org.plainphone.ui.theme.PlainphoneTheme
import org.plainphone.ui.trusted.WaitingChangeEvent
import org.plainphone.ui.trusted.WaitingChangeScreen
import org.plainphone.ui.trusted.WaitingChangeUiState
import org.plainphone.ui.trusted.WaitingStage

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
                    val cancel = cancelScreen
                    if (cancel != null) {
                        // The open confirmation follows each published read of the operation (PLA-371 R1).
                        val wait by runtime.waitingNotice.collectAsState()
                        LaunchedEffect(wait) { followOperation() }
                        WaitingChangeScreen(cancel.state) { onCancelEvent(cancel, it) }
                    } else Home(notice)
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
        runtime.onUserUnlocked()
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
            if (event == HomeEvent.OpenNotice) openNotice()
        }
    }

    /** A pending-change line: anyone holding the phone may cancel it, without a PIN (wiring 3). */
    private fun openNotice() {
        val tap = runtime.noticeTap.value as? NoticeTap.ConfirmCancel ?: return
        val wait = runtime.waitingNotice.value?.takeIf { it.requestId == tap.requestId } ?: return
        val change = when (wait.kind) {
            WaitingChangeKind.TURN_OFF -> getString(R.string.wait_change_turn_off, getString(R.string.app_name))
            WaitingChangeKind.ADD_PERSON, WaitingChangeKind.HAND_OVER, WaitingChangeKind.REMOVE_PERSON ->
                getString(R.string.wait_change_people)
            else -> getString(R.string.wait_change_setup)
        }
        cancelScreen = CancelScreen(tap.requestId, WaitingChangeUiState(stage = WaitingStage.ConfirmCancel, change = change,
            turningOff = wait.kind == WaitingChangeKind.TURN_OFF))
    }

    /**
     * Yes: cancel, then show what was written ([cancelStage]): "Cancelled" only after the cancelled state
     * is saved. Any other choice, or OK afterwards, goes back to Home.
     */
    private fun onCancelEvent(screen: CancelScreen, event: WaitingChangeEvent) {
        if (event != WaitingChangeEvent.ConfirmCancel || screen.state.stage != WaitingStage.ConfirmCancel) {
            cancelScreen = null
            return
        }
        if (screen.state.sending) return
        cancelScreen = screen.copy(state = screen.state.copy(sending = true))
        runtime.cancelWait(screen.requestId) { result ->
            runOnUiThread {
                cancelScreen = cancelStage(result)?.let { screen.copy(state = screen.state.copy(stage = it, sending = false)) }
            }
        }
    }

    /**
     * While "Cancel this change?" waits for a choice, it tracks the current operation: committing shows
     * "Already in progress" with no Cancel, and a finished or replaced request closes it. Once Yes is
     * pressed, the cancel's own result decides.
     */
    private fun followOperation() {
        val screen = cancelScreen ?: return
        if (screen.state.stage != WaitingStage.ConfirmCancel || screen.state.sending) return
        val stage = confirmStage(screen.requestId, runtime.waitingNotice.value)
        cancelScreen = when (stage) {
            null -> null
            WaitingStage.ConfirmCancel -> screen
            else -> screen.copy(state = screen.state.copy(stage = stage))
        }
    }

    private data class CancelScreen(val requestId: String, val state: WaitingChangeUiState)
    private var cancelScreen by mutableStateOf<CancelScreen?>(null)

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
