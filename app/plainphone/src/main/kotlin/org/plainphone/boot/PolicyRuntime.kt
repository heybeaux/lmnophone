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
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.UUID
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
import org.plainphone.device.approval.AndroidManifestFacts
import org.plainphone.device.approval.ApprovalRecovery
import org.plainphone.device.approval.DeviceApprovalEffects
import org.plainphone.device.approval.DpmPermissionGrantPort
import org.plainphone.device.approval.PublishedHomeApps
import org.plainphone.device.approval.RecordedPermissionManifests
import org.plainphone.device.authority.AuthorityPass
import org.plainphone.device.authority.DeviceReconciliationEffects
import org.plainphone.device.authority.DpmDeviceOwnerReader
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.authority.WaitingCheckpoints
import org.plainphone.device.authority.waitingReconciler
import org.plainphone.device.boot.BootOutcome
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.LivePolicyInputAssembler
import org.plainphone.device.facts.osFloor
import org.plainphone.device.runtime.AndroidRuntimeFacts
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.device.storage.FilePolicyStore
import org.plainphone.policy.AppApprovalController
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.BatteryState
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput
import org.plainphone.policy.NoticeLine
import org.plainphone.policy.NoticeTap
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.ProvenRunningClock
import org.plainphone.policy.ReconcileOutcome
import org.plainphone.policy.WaitingNotice
import org.plainphone.policy.WaitingOutcome
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
 * live phone; repeated runs are harmless.
 *
 * Every run first checkpoints a pending wait ([waiting]): the proven clock, a recorded cancel or changed
 * dependency, and maturity to COMMITTING, which the same run then applies. While a wait is pending the
 * checkpoint alarm runs every 5 minutes. [cancelWait] is the phone's PIN-free cancel.
 *
 * Every run also reads the current waiting operation fresh from the one authority store ([authority])
 * and, once unlocked, reconciles a confirmed lost waiting record without a PIN. Nothing writes that
 * store until trusted people are set up, so on a phone without them both read nothing and change nothing.
 *
 * App approvals ([approvals], PLA-281): every unlocked run first gives the approval controller one retry,
 * and every policy input (the boot re-apply and waiting reconciliation) carries the stored approval state,
 * none while an approval operation is pending. Home is ready only if the approvals the run used are still
 * current. While one is pending the alarm runs every 5 minutes.
 */
class PolicyRuntime private constructor(private val context: Context) {
    private val dispatcher = SerialDispatcher()
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val users = context.getSystemService(UserManager::class.java)
    private val userUnlocked = UserUnlockedReapply(dispatcher, { users.isUserUnlocked }) {
        run("user-unlocked")
    }
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
            { assembler.assemble().input.copy(approvalState = approvals.forPass()) },
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
    private val runtimeFacts = AndroidRuntimeFacts(context)
    /** The one proven clock in device-protected storage, shared by waiting checkpoints and app approvals. */
    private val clock by lazy {
        ProvenRunningClock(FileClockStateStore(File(protectedDir, "clock")), runtimeFacts::bootTime, authorityStore::reserveEpoch)
    }
    /**
     * The pending wait's checkpoints and PIN-free cancel: one per process, on the same store, with the
     * proven clock in device-protected storage. No grant is accepted; nothing here starts or skips a wait.
     */
    private val waiting: WaitingCheckpoints by lazy {
        WaitingCheckpoints(authorityStore, clock, runtimeFacts::battery, PersonAuthorityConsumer { _, _, _ -> false })
    }
    /**
     * The one app-approval controller on [dispatcher], over the same authority store. No grant is accepted
     * yet (PIN entry is not wired), so only recovery of an already-recorded operation can change anything.
     */
    private val approvals: ApprovalRecovery by lazy {
        val store = authorityStore.approvals
        val grants = DpmPermissionGrantPort(context, admin)
        val manifests = RecordedPermissionManifests(store::read, AndroidManifestFacts(context, grants, packagesPort::fingerprint))
        val neverHide = {
            try {
                PolicyEngine.decide(assembler.assemble().input).neverHide
            } catch (_: RuntimeException) {
                null
            }
        }
        ApprovalRecovery(dispatcher, store, AppApprovalController(
            store, authorityStore.credentials::read, PersonAuthorityConsumer { _, _, _ -> false }, boot.sessions, clock,
            { assembler.assemble().input.copy(waitingOperation = authority.waitingOperation()) },
            manifests, { runtimeFacts.battery() ?: UNKNOWN_BATTERY },
            DeviceApprovalEffects(dispatcher, port, grants, PublishedHomeApps(), neverHide, manifests),
            AppApprovalIdSource { UUID.randomUUID().toString() },
        ))
    }
    /** The approval state the current run's plan used; read by its hand-back check. Dispatcher only. */
    private var passApprovals: AppApprovalState? = null
    private val boot = BootReapply(
        FileJournalStore(File(protectedDir, "journal"), null, Build.MODEL, Build.FINGERPRINT),
        port, SESSION_PACKAGES,
        { locked ->
            passApprovals = approvals.forPass()
            PolicyEngine.decide(assembler.assemble().input.copy(lockedBoot = locked, waitingOperation = authority.waitingOperation(),
                approvalState = passApprovals))
        },
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
    private val noticeTapState = MutableStateFlow<NoticeTap?>(null)
    private val waitingNoticeState = MutableStateFlow<WaitingNotice?>(null)
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
    /** What tapping [notice] does (policy `NoticeLine`); controllers re-check before any effect. */
    val noticeTap: StateFlow<NoticeTap?> = noticeTapState
    /** The current waiting operation as the last unlocked re-apply read it; null with none. */
    val waitingNotice: StateFlow<WaitingNotice?> = waitingNoticeState
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

    /** Unlock broadcast or Home resume: queue the full pass only if the last pass was locked/absent. */
    fun onUserUnlocked(done: () -> Unit = {}) = userUnlocked.request(done)

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

    /** One whole app-approval operation as one dispatcher task, then the pass after a trusted-person action. */
    fun <T> approvalOperation(block: AppApprovalController.() -> T, done: (T?) -> Unit = {}) = dispatcher.post {
        val result = try {
            approvals.operation(block)
        } catch (e: RuntimeException) {
            Log.e(TAG, "approval operation threw", e)
            null
        }
        try {
            run("trusted-person")
        } finally {
            done(result)
        }
    }

    /** The phone's own cancel of the pending wait it shows; no PIN. The pass that follows reads the result. */
    fun cancelWait(requestId: String, done: (WaitingOutcome?) -> Unit = {}) = dispatcher.post {
        val result = try {
            waiting.cancel(requestId)
        } catch (e: RuntimeException) {
            Log.e(TAG, "waiting cancel threw", e)
            null
        }
        Log.i(TAG, "waiting cancel: $result")
        try {
            run("waiting-cancel")
        } finally {
            done(result)
        }
    }

    private fun run(reason: String) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "reapply $reason skipped: not Device Owner")
            lastGate = HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
            return
        }
        val locked = !users.isUserUnlocked
        val started = SystemClock.elapsedRealtime()
        // Before deciding: a matured wait commits here and this same pass applies it (wiring 2).
        val waited = try {
            waiting.checkpoint()?.also { Log.i(TAG, "waiting checkpoint after $reason: $it") }
        } catch (e: RuntimeException) {
            Log.e(TAG, "waiting checkpoint threw", e)
            null
        }
        // One approval retry before deciding; a recovered activation is in this pass's approvals (PLA-281).
        val approval = approvals.beforePass(locked)?.also { Log.i(TAG, "approval recovery after $reason: $it") }
        passApprovals = null
        val outcome = try {
            boot.run(locked)
        } catch (e: RuntimeException) {
            Log.e(TAG, "reapply $reason threw", e)
            null
        }
        userUnlocked.recordPass(locked)
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
        // While a wait or an approval operation is pending, the alarm runs every 5 minutes; otherwise it is left as it was.
        val approvalPending = approval != null && approvals.pending() == true
        if (waited != null || approvalPending) try {
            val waitPeriod = if (waited != null) waiting.periodMillis(ReapplyAlarm.PERIOD_MILLIS) else ReapplyAlarm.PERIOD_MILLIS
            val period = if (approvalPending) minOf(waitPeriod, APPROVAL_RETRY_MILLIS) else waitPeriod
            if (period != ReapplyAlarm.PERIOD_MILLIS) ReapplyAlarm.schedule(context, period)
        } catch (e: RuntimeException) {
            Log.e(TAG, "checkpoint alarm threw", e)
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
            val attention = pinned || held || homeNotice(outcome) != null
            // One line, in the spec's order (policy NoticeLine): attention, then the pending change (wiring 3).
            val wait = try {
                waiting.notice()
            } catch (e: RuntimeException) {
                Log.e(TAG, "waiting notice threw", e)
                null
            }
            val mode = if (wait == null) Mode.TRUSTED else try {
                authorityStore.credentials.read()?.mode
            } catch (_: RuntimeException) {
                null
            } ?: Mode.TRUSTED
            val line = NoticeLine.decide(NoticeInput(mode, attention, wait)).line
            waitingNoticeState.value = wait
            noticeTapState.value = line?.tap
            noticeState.value = homeLine(line, ZonedDateTime.now(), DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
            // Hand-back: the approvals this pass decided with must still be current (PLA-281).
            val handback = approvals.handback(passApprovals)
            lastGate = if (outcome?.ready == true && !pinned && handback) HomeGate.READY else HomeGate.NOT_READY
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
        /** The alarm period while an approval operation stays pending. */
        private const val APPROVAL_RETRY_MILLIS = 5 * 60 * 1000L
        /** An unreadable battery allows no discretionary unhide. */
        private val UNKNOWN_BATTERY = BatteryState(0, false)

        @Volatile private var instance: PolicyRuntime? = null

        fun get(context: Context): PolicyRuntime = instance ?: synchronized(this) {
            instance ?: PolicyRuntime(context.applicationContext).also { instance = it }
        }
    }
}
