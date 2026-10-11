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
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.plainphone.MainActivity
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.core.PackageName
import org.plainphone.device.DpmPort
import org.plainphone.device.KeyguardFirst
import org.plainphone.device.DpmResetProtectionPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.ResetProtectionEffects
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.authority.AuthorityPass
import org.plainphone.device.authority.DeviceReconciliationEffects
import org.plainphone.device.authority.DpmDeviceOwnerReader
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.authority.waitingReconciler
import org.plainphone.device.boot.BootOutcome
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.LivePolicyInputAssembler
import org.plainphone.device.facts.osFloor
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.device.storage.FilePolicyStore
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.ReconcileOutcome
import org.plainphone.ui.home.HomeNotice

/** What Home may show. Only [READY] lets Home draw; the others show the neutral checking screen. */
enum class HomeGate { CHECKING, READY, NOT_READY }

/**
 * The app's one policy runtime: the serialized dispatcher, the DPM port and device-protected stores,
 * and the boot re-apply. Everything here works before the first unlock: the stores live in
 * device-protected storage, and a locked run applies only the locked-boot plan.
 *
 * Re-apply triggers ([reapply]): boot (locked and unlocked), every Plainphone start, package events,
 * the 15-minute alarm and, from the wiring slice, [afterTrustedPersonAction]. Each run re-reads the
 * live phone; repeated runs are harmless.
 *
 * Every run also reads the current waiting operation fresh from the one authority store ([authority])
 * and, once unlocked, reconciles a confirmed lost waiting record without a PIN. Nothing writes that
 * store until trusted people are set up, so on a phone without them both read nothing and change nothing.
 */
class PolicyRuntime private constructor(private val context: Context) {
    private val dispatcher = SerialDispatcher()
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val admin = ComponentName(context, PlainphoneAdminReceiver::class.java)
    private val port = DpmPort(context, admin, ComponentName(context, FlushMarker::class.java))
    private val protectedDir = context.createDeviceProtectedStorageContext().filesDir
    private val packagesPort = AndroidPackageSystemPort(context)
    private val assembler = LivePolicyInputAssembler(
        packagesPort, FilePolicyStore(File(protectedDir, "policy")), emptyList(),
        PackageName(context.packageName), MESSAGES,
    )
    /** One build/SDK restriction filter for every policy write: the boot re-apply and reconciliation (PLA-350 R1). */
    private val restrictionFilter = BuildRestrictions.filter.forSdk(Build.VERSION.SDK_INT)
    /** Credentials, the waiting operation and its reconciliation, in device-protected storage. */
    private val authorityStore = JointAuthorityStore(File(protectedDir, "authority"))
    private val authority: AuthorityPass by lazy {
        AuthorityPass(authorityStore, authorityStore.waitingReconciler(
            { assembler.assemble().input },
            DeviceReconciliationEffects(
                // The common startup revocation: the session journal's revoke and protected-state check.
                revoke = { boot.sessions.onStart() },
                owner = DpmDeviceOwnerReader(context),
                applier = ReadbackPolicyApplier(port),
                reset = ResetProtectionEffects(DpmResetProtectionPort(context, admin)),
                store = authorityStore,
                filter = restrictionFilter,
            ),
            // PIN Repair is not wired yet, so no grant is ever accepted. Automatic reconciliation needs none.
            PersonAuthorityConsumer { _, _, _ -> false },
        ))
    }
    private val boot = BootReapply(
        FileJournalStore(File(protectedDir, "journal"), null, Build.MODEL, Build.FINGERPRINT),
        port, SESSION_PACKAGES,
        { locked -> PolicyEngine.decide(assembler.assemble().input.copy(lockedBoot = locked, waitingOperation = authority.waitingOperation())) },
        restrictionFilter,
        { stage, error -> Log.w(TAG, "stage $stage failed: ${error ?: "readback mismatch"}", error) },
        floor = { osFloor(packagesPort) },
        home = PackageName(context.packageName),
        turnOffCommitting = { authority.turnOffCommitting() },
        flipFailures = FileFlipFailureStore(File(protectedDir, "flip-failures")),
    )
    private val gateState = MutableStateFlow(HomeGate.CHECKING)
    @Volatile private var lastGate = HomeGate.CHECKING
    @Volatile private var checkingHeld = false
    private val noticeState = MutableStateFlow<HomeNotice?>(null)
    private val packageEventQueued = AtomicBoolean(false)
    private val runsState = MutableStateFlow(0)
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
    /** Counts finished re-apply runs, locked ones included: Home retries lock-task entry after each. */
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
        phoneStateReadable().also { if (it) telecom.showInCallScreen(false) }
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

    /** Package events come in bursts: at most one queued run for them. */
    fun onPackageEvent(reason: String) {
        if (packageEventQueued.compareAndSet(false, true)) dispatcher.post {
            packageEventQueued.set(false)
            run(reason)
        }
    }

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
            Log.w(TAG, "reapply $reason skipped: not Device Owner")
            lastGate = HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
            return
        }
        val locked = !users.isUserUnlocked
        val started = SystemClock.elapsedRealtime()
        val outcome = try {
            boot.run(locked)
        } catch (e: RuntimeException) {
            Log.e(TAG, "reapply $reason threw", e)
            null
        }
        log(reason, outcome, SystemClock.elapsedRealtime() - started)
        // Full policy input exists only after unlock, so a lost record is reconciled only then.
        val reconciled = if (locked) null else try {
            authority.reconcileIfLost()?.also { Log.i(TAG, "lost waiting record: outcome=${it.outcome} failure=${it.failure}") }
        } catch (e: RuntimeException) {
            Log.e(TAG, "lost-record reconciliation threw", e)
            null
        }
        // Set up but unreadable needs attention even when no reconciliation can start (PLA-350 R2).
        val authorityAttention = !locked && try {
            authority.needsAttention()
        } catch (e: RuntimeException) {
            Log.e(TAG, "authority attention check threw", e)
            true
        }
        BuildControl.control.afterReapply(context, dpm, admin)
        runsState.value++
        // A locked run never opens Home: the full plan has not run yet.
        if (!locked) {
            val action = recovery.afterPass(locked, outcome?.ready == true)
            // PINNED is not containment (PLA-322 finding 3): not ready, and needs attention.
            val pinned = action == RecoveryAction.PINNED_ATTENTION
            // Only the notice: Home and calling stay available either way.
            val held = reconciled?.outcome == ReconcileOutcome.NEEDS_ATTENTION || authorityAttention
            noticeState.value = if (pinned || held) HomeNotice.NeedsAttention else homeNotice(outcome)
            lastGate = if (outcome?.ready == true && !pinned) HomeGate.READY else HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
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
        if (phoneStateReadable()) return telecom.isInCall
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

    private fun log(reason: String, outcome: BootOutcome?, millis: Long) {
        val apply = outcome?.apply
        Log.i(TAG, "reapply reason=$reason control=${BuildControl.control.name} locked=${outcome?.locked} ready=${outcome?.ready} " +
            "applied=${apply?.applied} failed=${apply?.failedStage} flipFailures=${apply?.flipFailures?.size} " +
            "lockTask=${lockTaskState()} withheld=${outcome?.withheld?.sorted()} attention=${outcome?.needsAttention} disabledFloor=${outcome?.disabledFloor} ms=$millis")
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
