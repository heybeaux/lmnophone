// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.Manifest
import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.telecom.TelecomManager
import android.util.Log
import java.io.File
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.plainphone.MainActivity
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.core.PackageName
import org.plainphone.device.PackageChangeReadback
import org.plainphone.device.facts.PackageChangeFacts
import org.plainphone.policy.SessionJournal
import org.plainphone.device.DpmPort
import org.plainphone.device.KeyguardFirst
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.ReapplyTimings
import org.plainphone.device.boot.BootOutcome
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.osFloor
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.preview.Preview2Device
import org.plainphone.ui.home.HomeNotice

/** What Home may show. Only [READY] lets Home draw; the others show the neutral checking screen. */
enum class HomeGate { CHECKING, READY, NOT_READY }

/**
 * The app's one policy runtime: the serialized dispatcher, the DPM port and device-protected stores,
 * and the boot re-apply. Everything here works before the first unlock: the stores live in
 * device-protected storage, and a locked run applies only the locked-boot plan.
 *
 * Re-apply triggers ([reapply]): boot (locked and unlocked), every Plainphone start, package events,
 * user unlock, the 15-minute alarm and, from the wiring slice, [afterTrustedPersonAction]. Each run re-reads the
 * live phone. A single unchanged system-package event may use targeted readback after a verified
 * full unlocked pass; all errors fall back to the full pass.
 */
class PolicyRuntime private constructor(private val context: Context) {
    private val dispatcher = SerialDispatcher()
    private val bootCoverage = BootPassCoverage()
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val userUnlocked = UserUnlockedReapply(dispatcher, { users.isUserUnlocked }) {
        run("user-unlocked")
    }
    private val admin = ComponentName(context, PlainphoneAdminReceiver::class.java)
    private val port = DpmPort(context, admin, ComponentName(context, FlushMarker::class.java))
    private val protectedDir = context.createDeviceProtectedStorageContext().filesDir
    private val packagesPort = AndroidPackageSystemPort(context)
    // Preserve the preview starter list and restriction-free plan during this in-place upgrade.
    private val preview = Preview2Device(context)
    private val timings = ReapplyTimings(SystemClock::elapsedRealtime)
    private val journal = FileJournalStore(File(protectedDir, "journal"), null, Build.MODEL, Build.FINGERPRINT)
    private val packageFacts = PackageChangeFacts(context)
    private val packageReadback = PackageChangeReadback(port, packageFacts::protected, packageFacts::launchable) {
        !journal.revocationPending() && journal.read() == SessionJournal.Empty
    }
    private val boot = BootReapply(
        journal,
        port, SESSION_PACKAGES,
        { locked -> preview.plan(locked, timings) },
        BuildRestrictions.filter.forSdk(Build.VERSION.SDK_INT),
        { stage, error -> Log.w(TAG, "stage $stage failed: ${error ?: "readback mismatch"}", error) },
        floor = { osFloor(packagesPort) },
        home = PackageName(context.packageName),
        // No waiting-change store is wired yet, so no turn-off can be committing. The wiring slice
        // that adds turn-off passes its committing read here.
        turnOffCommitting = { false },
        flipFailures = FileFlipFailureStore(File(protectedDir, "flip-failures")),
        timings = timings,
    )
    private val gateState = MutableStateFlow(HomeGate.CHECKING)
    @Volatile private var lastGate = HomeGate.CHECKING
    @Volatile private var checkingHeld = false
    private val noticeState = MutableStateFlow<HomeNotice?>(null)
    private val packageEvents = PackageEventBurst(dispatcher::post, ::run)
    private val runsState = MutableStateFlow(0)
    private val refreshState = MutableStateFlow(HomeRefresh(null, 0))
    val refresh: StateFlow<HomeRefresh> = refreshState
    private val home = PackageName(context.packageName)
    private val activities = context.getSystemService(ActivityManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val audio = context.getSystemService(AudioManager::class.java)
    private val recovery = LockTaskRecovery(
        ::lockTaskState,
        ::inCall,
        {
            context.startActivity(Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT))
        },
        SystemClock::elapsedRealtime,
        FileOpenedScreenRecord(File(protectedDir, "opened-screen"), SystemClock::elapsedRealtime),
    )

    /** Test builds only (debug `TestHook` IN_CALL; release never sets it): stands in for an ongoing call. */
    @Volatile var inCallStandIn: Boolean? = null

    val gate: StateFlow<HomeGate> = gateState
    /** The Home notice from the last unlocked re-apply ([homeNotice]). */
    val notice: StateFlow<HomeNotice?> = noticeState
    /** Counts handled re-apply triggers, covered boot triggers included: Home retries lock-task entry after each. */
    val runs: StateFlow<Int> = runsState

    /** The live lock-task state; only [LockTaskState.LOCKED] is containment. */
    fun lockTaskState(): LockTaskState = LockTaskState.read { activities.lockTaskModeState }

    /** LMNOPhone opened a calling screen; recovery brings it back if it displaces it. */
    fun opened(screen: CallingScreen) = recovery.opened(screen)

    /** Home is resumed: nothing it opened is in front any more. */
    fun homeResumed() = recovery.homeResumed()

    /**
     * Home read its own lock-task [state]: returns the displaced calling screen to reopen inside lock
     * task, and queues a fresh pass once a pin that made the last pass not ready has ended.
     */
    fun onHomeLockTaskState(state: LockTaskState): CallingScreen? {
        val followUp = recovery.onHomeLockTaskState(state)
        if (followUp.reapply) reapply("pin-ended")
        return followUp.restore
    }

    /** Brings the ongoing call's screen back. It never answers, ends or changes a call. */
    fun showInCallScreen(): Boolean = try {
        if (inCallStandIn == true) Log.i(TAG, "in-call stand-in: showInCallScreen at=${SystemClock.elapsedRealtime()}")
        phoneStateReadable().also {
            if (it && context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
                telecom.showInCallScreen(false)
        }
    } catch (e: RuntimeException) {
        Log.w(TAG, "showInCallScreen failed", e)
        false
    }

    /** Keyguard first: true only while the features read back with KEYGUARD and Home is on the list. */
    fun mayEnterLockTask(): Boolean = KeyguardFirst.mayEnterLockTask(port, home)

    /** Queues one re-apply; [done] runs on the dispatcher after it. */
    fun reapply(reason: String, done: () -> Unit = {}) = dispatcher.post {
        try {
            run(reason)
        } finally {
            done()
        }
    }

    /** Unlock broadcast or Home resume: queue the full pass only if the last pass was locked/absent. */
    fun onUserUnlocked(done: () -> Unit = {}) = userUnlocked.request(done)

    /** Package events come in bursts: at most one queued run for them. */
    fun onPackageEvent(reason: String) = packageEvents.add(reason)

    /**
     * Test builds only (debug `TestHook` HOLD; release never calls it): keeps the checking screen up so
     * the emergency gate can test its Phone, then restores the last re-apply's gate.
     */
    fun holdChecking(hold: Boolean) {
        checkingHeld = hold
        gateState.value = if (hold) HomeGate.CHECKING else lastGate
    }

    /** Hook for the wiring slice: run after every trusted-person action commits. */
    fun afterTrustedPersonAction() = reapply("trusted-person")

    private fun run(reason: String) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            bootCoverage.invalidate()
            packageReadback.invalidate()
            Log.w(TAG, "reapply $reason skipped: not Device Owner")
            lastGate = HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
            return
        }
        val locked = !users.isUserUnlocked
        timings.reset()
        val started = SystemClock.elapsedRealtime()
        var passId = 0L
        var bootMillis = 0L
        var wasSkipped = false
        bootCoverage.run(reason, locked,
            // Negative-control builds deliberately break readback state after a pass: never reuse it.
            retryPending = { boot.retryPending || BuildControl.control !== NegativeControl.None },
            apply = { id ->
                passId = id
                packageReadback.invalidate()
                val outcome = try {
                    boot.run(locked)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "reapply $reason threw", e)
                    null
                }
                userUnlocked.recordPass(locked)
                if (!locked && outcome?.ready == true && !outcome.needsAttention && !boot.retryPending &&
                    BuildControl.control === NegativeControl.None) {
                    preview.lastPlan?.let { plan ->
                        timings.measure("packageSnapshot") {
                            packageReadback.capture(preview.systemPackages, plan, SESSION_PACKAGES)
                        }
                    }
                }
                bootMillis = SystemClock.elapsedRealtime() - started
                outcome
            },
            skipped = { id ->
                wasSkipped = true
                val kind = if (reason.startsWith("package:PACKAGE_CHANGED:")) "unchanged-since" else "covered-by"
                Log.i(TAG, "reapply reason=$reason skipped=$kind=$id")
            },
            packageUnchanged = {
                val name = reason.removePrefix("package:PACKAGE_CHANGED:")
                packageReadback.unchanged(PackageName(name))
            },
            afterPackageSkip = { outcome ->
                publishPass(reason, locked, outcome, refreshHome = false)
                lastGate == HomeGate.READY
            },
            afterPass = { outcome ->
                try {
                    timings.measure("afterReapply") { BuildControl.control.afterReapply(context, dpm, admin) }
                } finally {
                    if (!wasSkipped) log(reason, outcome, bootMillis, SystemClock.elapsedRealtime() - started, passId)
                }
                publishPass(reason, locked, outcome)
                lastGate == HomeGate.READY
            },
        )
    }

    private fun publishPass(reason: String, locked: Boolean, outcome: BootOutcome?, refreshHome: Boolean = true) {
        runsState.value++
        // A locked run never opens Home: the full plan has not run yet.
        if (!locked) {
            val action = recovery.afterPass(locked, outcome?.ready == true)
            // PINNED is not containment (PLA-322 finding 3): not ready, and needs attention.
            val pinned = action == RecoveryAction.PINNED_ATTENTION
            noticeState.value = if (pinned) HomeNotice.NeedsAttention else homeNotice(outcome)
            lastGate = if (outcome?.ready == true && !pinned) HomeGate.READY else HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
            refreshState.value = HomeRefresh(noticeState.value,
                if (lastGate == HomeGate.READY && refreshHome) refreshState.value.revision + 1 else refreshState.value.revision)
            when (action) {
                RecoveryAction.HOME_FORWARD ->
                    Log.i(TAG, "lock task not running after $reason: Home brought forward at=${SystemClock.elapsedRealtime()}")
                RecoveryAction.PINNED_ATTENTION ->
                    Log.i(TAG, "lock task PINNED after $reason: not ready, needs attention at=${SystemClock.elapsedRealtime()}")
                RecoveryAction.NOTHING -> {}
            }
        }
    }

    /** An ongoing call, from Telecom; the audio mode stands in if READ_PHONE_STATE can't be granted. */
    private fun inCall(): Boolean {
        inCallStandIn?.let { return it }
        if (phoneStateReadable() &&
            context.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) == PackageManager.PERMISSION_GRANTED)
            return telecom.isInCall
        return audio.mode == AudioManager.MODE_IN_CALL || audio.mode == AudioManager.MODE_RINGTONE
    }

    /** READ_PHONE_STATE, which the Device Owner grants itself on first use. */
    private fun phoneStateReadable(): Boolean {
        val permission = Manifest.permission.READ_PHONE_STATE
        if (context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED) return true
        return try {
            dpm.setPermissionGrantState(admin, context.packageName, permission, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED) &&
                context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        } catch (e: RuntimeException) {
            false
        }
    }

    private fun log(reason: String, outcome: BootOutcome?, millis: Long, totalMillis: Long, passId: Long) {
        val apply = outcome?.apply
        Log.i(TAG, "reapply reason=$reason control=${BuildControl.control.name} locked=${outcome?.locked} ready=${outcome?.ready} " +
            "applied=${apply?.applied} failed=${apply?.failedStage} flipFailures=${apply?.flipFailures?.size} " +
            "lockTask=${lockTaskState()} withheld=${outcome?.withheld?.sorted()} attention=${outcome?.needsAttention} disabledFloor=${outcome?.disabledFloor} ms=$millis totalMs=$totalMillis pass=$passId timingsMs=${timings.summary()}")
    }

    companion object {
        const val TAG = "PlainphoneBoot"
        private val MESSAGES = PackageName("com.google.android.apps.messaging")
        /** Every package a session window can relax: Play, plus a matching inventory's Settings packages (none compiled in yet). */
        private val SESSION_PACKAGES = setOf(PackageName("com.android.vending"))

        @Volatile private var instance: PolicyRuntime? = null

        fun get(context: Context): PolicyRuntime = instance ?: synchronized(this) {
            instance ?: PolicyRuntime(context.applicationContext).also { instance = it }
        }
    }
}
