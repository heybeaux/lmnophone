// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.plainphone.update.PendingSystemUpdate
import org.plainphone.update.PostponeStatus
import org.plainphone.update.SystemUpdatePostpone
import org.plainphone.update.buildKey
import org.plainphone.update.withUpdateLine
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.plainphone.MainActivity
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.core.PackageName
import org.plainphone.device.PackageChangeReadback
import org.plainphone.device.facts.PackageChangeFacts
import org.plainphone.policy.SessionJournal
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.DpmPort
import org.plainphone.device.DpmSuspensionPort
import org.plainphone.device.KeyguardFirst
import org.plainphone.device.DpmResetProtectionPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.resetProtectionFor
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.ReapplyTimings
import org.plainphone.device.authority.PinEntry
import org.plainphone.device.authority.PinEnrollmentResult
import org.plainphone.device.authority.PinAccessResult
import org.plainphone.device.authority.PreviewPinAccess
import org.plainphone.device.authority.PreviewTurnOff
import org.plainphone.device.authority.PreviewChangePin
import org.plainphone.device.authority.PinChangeOutcome
import org.plainphone.device.runtime.Pbkdf2KeyDerivation
import org.plainphone.device.runtime.SecureEntropy
import org.plainphone.policy.PinVault
import org.plainphone.device.authority.AuthorityPass
import org.plainphone.device.authority.DeviceReconciliationEffects
import org.plainphone.device.authority.DpmDeviceOwnerReader
import org.plainphone.device.authority.JointAuthorityStore
import org.plainphone.device.authority.PeopleCommitDriver
import org.plainphone.device.authority.WaitingCheckpoints
import org.plainphone.device.authority.waitingReconciler
import org.plainphone.device.boot.BootOutcome
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.boot.FallbackContainment
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.FloorCompleteness
import org.plainphone.device.facts.osFloorRead
import org.plainphone.device.runtime.AndroidRuntimeFacts
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.device.storage.FileFlipFailureStore
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.preview.Preview2Device
import org.plainphone.device.storage.FileSuspensionRecord
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.core.Mode
import org.plainphone.policy.NoticeInput
import org.plainphone.policy.NoticeLine
import org.plainphone.policy.NoticeTap
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PeopleChangeResult
import org.plainphone.policy.PolicyPlan
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
 * live phone. Unchanged system-package events may use targeted readback after a verified
 * full unlocked pass; all errors fall back to the full pass.
 *
 * Every run first checkpoints a pending wait ([waiting]): the proven clock, a recorded cancel or changed
 * dependency, and maturity to COMMITTING. The preview never executes a turn-off. While a wait is pending the
 * checkpoint alarm runs every 5 minutes. [cancelWait] is the phone's PIN-free cancel.
 *
 * Every run also reads the current waiting operation fresh from the one authority store ([authority])
 * and, once unlocked, attempts reconciliation. Without a full enrolled profile/policy, a lost record
 * remains held with attention; it never replaces the preview plan. Nothing writes that
 * store until trusted people are set up, so on a phone without them both read nothing and change nothing.
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
    /** Test builds can drop feature writes ([FeatureFault]); release always writes them. */
    private val port: DevicePolicyPort =
        FeatureFaultPort(DpmPort(context, admin, ComponentName(context, FlushMarker::class.java))) { FeatureFault.on(context) }
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
    /** One build/SDK restriction filter for every policy write: the boot re-apply and reconciliation (PLA-350 R1). */
    private val restrictionFilter = BuildRestrictions.filter.forSdk(Build.VERSION.SDK_INT)
    /**
     * The one reset-protection effect for reconciliation and people changes. Test builds withhold the
     * write under the same filter as DISALLOW_FACTORY_RESET (Lead decision on PLA-92) and log only the
     * account count.
     */
    private val resetEffects by lazy {
        resetProtectionFor(restrictionFilter, DpmResetProtectionPort(context, admin)) { accounts ->
            Log.i(TAG, "reset protection WITHHELD (test build): would set $accounts accounts")
        }
    }
    /** Credentials, the waiting operation and its reconciliation, in device-protected storage. */
    private val authorityStore = JointAuthorityStore(File(protectedDir, "authority"))
    private val authority: AuthorityPass by lazy {
        AuthorityPass(authorityStore, authorityStore.waitingReconciler(
            // The preview has no full-engine policy/profile enrollment. A lost record
            // stays held with attention; never replace the starter-list plan or touch FRP.
            { null },
            DeviceReconciliationEffects(
                // The common startup revocation: the session journal's revoke and protected-state check.
                revoke = { boot.sessions.onStart() },
                owner = DpmDeviceOwnerReader(context),
                applier = ReadbackPolicyApplier(port),
                reset = resetEffects,
                store = authorityStore,
                filter = restrictionFilter,
            ),
            // PIN Repair is not wired yet, so no grant is ever accepted. Automatic reconciliation needs none.
            PersonAuthorityConsumer { _, _, _ -> false },
        ))
    }
    /**
     * The pending wait's checkpoints and PIN-free cancel: one per process, on the same store, with the
     * proven clock in device-protected storage. Starting uses PreviewTurnOff and a separate fresh PIN; skipping is unavailable.
     */
    private val facts by lazy { AndroidRuntimeFacts(context) }
    private val runningClock by lazy {
        ProvenRunningClock(FileClockStateStore(File(protectedDir, "clock")), facts::bootTime, authorityStore::reserveEpoch)
    }
    private val testBuild get() = context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_TEST_ONLY != 0
    private val vault by lazy { PinVault(authorityStore.credentials, Pbkdf2KeyDerivation, SecureEntropy, runningClock) }
    private val pins by lazy { PreviewPinAccess(authorityStore, vault) }
    private val turnOff by lazy { PreviewTurnOff(authorityStore, vault, runningClock) }
    private val pinRecovery by lazy { org.plainphone.device.authority.PreviewRecovery(authorityStore, vault) }
    private val pinChange by lazy { PreviewChangePin(authorityStore, vault) }
    private val waiting: WaitingCheckpoints by lazy {
        WaitingCheckpoints(
            authorityStore,
            runningClock,
            facts::battery,
            PersonAuthorityConsumer { _, _, _ -> false },
            testBuild = testBuild,
        )
    }
    /**
     * The directed callback for a committing or rolling-back people change (wiring 5): one step per pass,
     * in the direction its record holds, through the exact reset-protection set, broadcast and readback.
     * The fresh-PIN stop is not wired, so no grant is accepted.
     */
    private val peopleDriver: PeopleCommitDriver by lazy {
        PeopleCommitDriver(authorityStore, resetEffects, runningClock,
            PersonAuthorityConsumer { _, _, _ -> false })
    }
    /** Whether this pass's keep set rests on a completely read floor (PLA-399 finding 1). */
    private val floorCompleteness = FloorCompleteness()
    private val boot = BootReapply(
        journal,
        port, SESSION_PACKAGES,
        { locked ->
            // Read authority afresh at the decision boundary. Unsupported forward restore
            // cannot authorize replacing the preview plan or removing its owner.
            check(!authority.turnOffCommitting()) { "turn-off execution unavailable in preview" }
            // The preview plan reads the floor from the OS role holders; its completeness gates containment (PLA-399 F1).
            lateinit var plan: PolicyPlan
            floorCompleteness.facts {
                plan = preview.plan(locked, timings)
                checkNotNull(preview.floorUnreadable) { "floor not read" }
            }
            plan
        },
        restrictionFilter,
        { stage, error -> Log.w(TAG, "stage $stage failed: ${error ?: "readback mismatch"}", error) },
        floor = { floorCompleteness.osFloor { osFloorRead(packagesPort) } },
        home = PackageName(context.packageName),
        turnOffCommitting = { authority.turnOffCommitting() },
        flipFailures = FileFlipFailureStore(File(protectedDir, "flip-failures")),
        timings = timings,
        floorComplete = { floorCompleteness.complete },
    )
    /**
     * Fallback containment (PLA-344): suspends what it can while lock task cannot start with KEYGUARD,
     * and lifts exactly that once lock task is LOCKED with KEYGUARD.
     */
    private val containment = FallbackContainment(
        DpmSuspensionPort(context, admin, packagesPort),
        FileSuspensionRecord(File(protectedDir, "fallback")),
        { lockTaskState() == LockTaskState.LOCKED },
        { mayEnterLockTask() },
    )
    private val fallbackState = MutableStateFlow<FallbackNotice?>(null)
    private val gateState = MutableStateFlow(HomeGate.CHECKING)
    @Volatile private var lastGate = HomeGate.CHECKING
    @Volatile private var checkingHeld = false
    private val noticeState = MutableStateFlow<HomeNotice?>(null)
    /** The last pass's line before the pending-update line, which is not attention (PLA-447). */
    @Volatile private var baseNotice: HomeNotice? = null
    private val updatePostpone = SystemUpdatePostpone.forDevice(context)
    private val pendingUpdate = PendingSystemUpdate(File(protectedDir, "system-update-pending"), buildKey(Build.FINGERPRINT))
    private val packageEvents = PackageEventBurst(dispatcher::post, { reasons, maySkip ->
        run(reasons.singleOrNull() ?: "package-burst", maySkip, reasons)
    }) { packageReadback.hasSnapshot }
    private val noticeTapState = MutableStateFlow<NoticeTap?>(null)
    private val waitingNoticeState = MutableStateFlow<WaitingNotice?>(null)
    private val waitingReadState = MutableStateFlow<WaitingRead?>(null)
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
    /** What tapping [notice] does (policy `NoticeLine`); controllers re-check before any effect. */
    val noticeTap: StateFlow<NoticeTap?> = noticeTapState
    /** The current waiting operation as the last unlocked re-apply read it; null with none. */
    val waitingNotice: StateFlow<WaitingNotice?> = waitingNoticeState
    /** The same read with the time it was made: screens time the change from it, as Home's line does. */
    val waitingRead: StateFlow<WaitingRead?> = waitingReadState
    /** While fallback containment holds: what the attention screen discloses. Null otherwise. */
    val fallback: StateFlow<FallbackNotice?> = fallbackState
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
        // Lock task just started with KEYGUARD: lift the fallback suspension, then refresh the gate.
        if (state == LockTaskState.LOCKED && containment.mayHoldSuspensions) dispatcher.post {
            if (fallbackStep(!users.isUserUnlocked, lockTaskState(), mayEnterLockTask()) == FallbackStep.LIFT && lift()) run("fallback-lifted")
        }
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

    /** The setup command's undo of the postpone policy ([SystemUpdatePostpone.release]), on the pass thread. */
    fun releaseUpdatePolicy(done: (PostponeStatus) -> Unit) = dispatcher.post { done(updatePostpone.release()) }

    /**
     * `onSystemUpdatePending` (device-lockdown): records the time Android reports (-1 clears it) and
     * updates Home's line now, without a re-apply pass. Logs no build details. Nothing thrown here may
     * leave the shared dispatcher (Android's uncaught handler would end the app).
     */
    fun onSystemUpdatePending(receivedTime: Long) = dispatcher.post {
        val pending = try { pendingUpdate.onPending(receivedTime) } catch (e: Exception) {
            Log.e(TAG, "system update pending record failed: ${e.javaClass.simpleName}")
            false
        }
        Log.i(SystemUpdatePostpone.TAG, "system update pending=$pending")
        noticeState.value = withUpdateLine(baseNotice, pending)
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

    /** PIN work shares the policy dispatcher; no secret or capability crosses a saved UI state. */
    fun pinEntry(done: (PinEntry) -> Unit) = dispatcher.post { done(pins.entry()) }

    fun enrollPin(pin: CharArray, done: (PinEnrollmentResult) -> Unit) = dispatcher.post {
        val result = pins.enroll(pin)
        try { run("pin-enrollment") } finally { done(result) }
    }

    fun recoveryWaits(done: (org.plainphone.policy.CredentialWaits?) -> Unit) = dispatcher.post { done(pinRecovery.waits()) }
    fun rotateRecovery(pin: CharArray, done: (org.plainphone.device.authority.RecoveryAccessResult) -> Unit) = dispatcher.post {
        done(pinRecovery.rotate(pin))
    }
    fun recoverPin(code: CharArray, newPin: CharArray, done: (org.plainphone.device.authority.RecoveryAccessResult) -> Unit) = dispatcher.post {
        val result = pinRecovery.recover(code, newPin)
        // As for Change PIN, resolve any wait bound to the replaced credential generation.
        try { run("pin-recovery") } finally { done(result) }
    }

    /** Number reads and writes require a fresh PIN on the common dispatcher. */
    fun requestRecipientAction(pin: CharArray, change: Boolean, recipient: org.plainphone.policy.RequestRecipient?,
        active: () -> Boolean, done: (PinAccessResult, Boolean, org.plainphone.policy.RequestRecipient?) -> Unit) = dispatcher.post {
        var result: PinAccessResult = PinAccessResult.Unavailable
        val engine = org.plainphone.device.requests.RequestEngine.get(context)
        val guard = org.plainphone.requests.RecipientChange({ secret ->
            pins.authenticateRequestRecipient(secret).also { result = it } == PinAccessResult.Open
        }, { value -> if (change) engine.setRecipient(value) })
        val saved = guard.apply(pin, recipient, active)
        val current = if (saved) runCatching { engine.recipient() } else null
        done(result, saved && current?.isSuccess == true, current?.getOrNull())
    }

    fun checkPin(pin: CharArray, done: (PinAccessResult) -> Unit) = dispatcher.post {
        done(pins.authenticate(pin))
    }

    private val appPermissions by lazy { org.plainphone.device.approval.AndroidAppPermissions(context, admin) }
    fun previewAppPermissions(id: String, hidden: Boolean = false, done: (org.plainphone.device.approval.AppPermissionRead?) -> Unit) = dispatcher.post {
        done(try {
            if (id in preview.protectedApps() || (if (hidden) preview.hiddenApps().none { it.id == id } else preview.homeApps().none { it.first == id })) null
            else appPermissions.read(id)
        } catch (_: Exception) { null })
    }

    /** T5 preview: all app-list reads/writes share the policy pass dispatcher. */
    fun previewApps(done: (List<org.plainphone.preview.PreviewAppRow>?) -> Unit) = dispatcher.post {
        val rows = try {
            val protected = preview.protectedApps()
            preview.homeApps().map { (id, name) ->
                org.plainphone.preview.PreviewAppRow(id, name, preview.appVersion(id), id in protected)
            }
        } catch (_: Exception) { null }
        done(rows)
    }
    fun movePreviewApp(id: String, delta: Int, active: () -> Boolean, done: (Boolean) -> Unit) = dispatcher.post {
        val saved = try { active() && preview.moveApp(id, delta, active) } catch (_: Exception) { false }
        if (saved) refreshState.value = refreshState.value.copy(revision = refreshState.value.revision + 1)
        done(saved)
    }
    fun removePreviewApp(id: String, pin: CharArray, active: () -> Boolean,
        done: (org.plainphone.device.AppRemovalResult) -> Unit) = dispatcher.post {
        val removal = org.plainphone.device.PreviewAppRemoval(
            authenticate = pins::authenticateRemoval,
            remove = { preview.removeApp(it, active) },
            apply = { run("app-removed", packageMaySkip = false); gate.value == HomeGate.READY },
        )
        done(removal.remove(id, pin, active))
    }

    fun previewHiddenApps(done: (List<org.plainphone.device.PreviewHiddenApp>?) -> Unit) = dispatcher.post {
        done(try { preview.hiddenApps() } catch (_: Exception) { null })
    }
    fun addPreviewApp(id: String, identity: String, selection: org.plainphone.device.approval.AppPermissionSnapshot,
        choices: Map<String, org.plainphone.policy.PermissionChoice>, pin: CharArray, active: () -> Boolean,
        done: (org.plainphone.device.AppAdditionResult) -> Unit) = dispatcher.post {
        val result = try {
            // The hidden-app eligibility gate remains live and version/signer-bound before any unhide.
            if (!active() || id != selection.identity.packageName.value || selection.selectionIdentity != identity ||
                preview.hiddenApps().none { it.id == id && it.identity == identity }) org.plainphone.device.AppAdditionResult.SaveFailed
            else {
                val grantPort = org.plainphone.device.approval.DpmPermissionGrantPort(context, admin)
                val attempts = org.plainphone.device.approval.PermissionGrantAttempts(grantPort)
                val effects = org.plainphone.device.approval.DeviceApprovalEffects(dispatcher, port, grantPort,
                    object : org.plainphone.device.approval.HomeAppList {
                        override fun lists(name: PackageName): Boolean = name in preview.starter()
                        override fun remove(name: PackageName): Unit = error("unexpected approved candidate")
                        override fun publish(names: Set<PackageName>): Unit = error("activation belongs to runtime")
                    }, { preview.protectedApps().mapTo(mutableSetOf(), ::PackageName) }, { null })
                org.plainphone.device.approval.PreviewPermissionAddition(
                    authenticate = pins::authenticateAddition,
                    eligible = { target -> target.identity.packageName.value !in preview.protectedApps() &&
                        target.identity.packageName !in preview.starter() },
                    snapshot = appPermissions::snapshot,
                    open = effects::openPermissionWindow,
                    close = { target -> effects.closePermissionWindow(target) && port.packageState(target.packageName)?.hidden == true },
                    write = attempts::write,
                    checkpoint = attempts::checkpoint,
                    rollback = attempts::rollback,
                    read = appPermissions::read,
                    save = { target -> preview.addApp(id, target.selectionIdentity, active) },
                    apply = { run("app-added", packageMaySkip = false); gate.value == HomeGate.READY },
                ).add(selection, choices, pin, active)
            }
        } catch (_: Exception) { org.plainphone.device.AppAdditionResult.SaveFailed }
        finally { pin.fill('\u0000') }
        done(result)
    }

    fun checkTurnOffPin(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit) = dispatcher.post {
        val result = try { turnOff.authenticate(flow, pin) } catch (_: RuntimeException) { PinAccessResult.Unavailable }
        finally { pin.fill('\u0000') }
        done(result)
    }
    fun closeTurnOff(flow: String) = dispatcher.post { turnOff.close(flow) }

    /** Change PIN (PLA-494): the old PIN's grant stays on this dispatcher, bound to [flow]. */
    fun checkChangePin(flow: String, pin: CharArray, done: (PinAccessResult) -> Unit) = dispatcher.post {
        val result = try { pinChange.authenticate(flow, pin) } catch (_: RuntimeException) { PinAccessResult.Unavailable }
        finally { pin.fill('\u0000') }
        done(result)
    }
    fun closeChangePin(flow: String) = dispatcher.post { pinChange.close(flow) }
    /** A pass always follows, even after a failure: its checkpoint cancels a wait the old PIN started. */
    fun changePin(flow: String, newPin: CharArray, done: (PinChangeOutcome) -> Unit) = dispatcher.post {
        val result = try { pinChange.change(flow, newPin) } catch (_: RuntimeException) { PinChangeOutcome.FAILED }
        finally { newPin.fill('\u0000') }
        Log.i(TAG, "pin change: $result")
        try { run("pin-change") } finally { done(result) }
    }
    fun startTurnOff(flow: String, first: Boolean, second: Boolean, done: (WaitingOutcome) -> Unit) = dispatcher.post {
        val result = try { turnOff.start(flow, first, second) } catch (_: RuntimeException) { WaitingOutcome.NOT_READY }
        try { run("turn-off-start") } finally { done(result) }
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

    private fun run(reason: String, packageMaySkip: Boolean = true, packageReasons: List<String> = listOf(reason)) {
        val loggedReason = reason + if (reason == "package-burst") " groupedReasons=$packageReasons" else ""
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            bootCoverage.invalidate()
            packageReadback.invalidate()
            Log.w(TAG, "reapply $loggedReason skipped: not Device Owner")
            lastGate = HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
            return
        }
        updatePostpone.apply()
        val locked = !users.isUserUnlocked
        timings.reset()
        val started = SystemClock.elapsedRealtime()
        // Checkpoint before deciding whether any earlier package/boot pass can be reused.
        var waitingUncertain = false
        val waited = try { waiting.checkpoint() } catch (e: RuntimeException) {
            waitingUncertain = true
            Log.e(TAG, "waiting checkpoint threw", e)
            null
        }
        val activeWait = try {
            authority.needsAttention() || authority.waitingOperation()?.phase in setOf(
                org.plainphone.policy.WaitingPhase.PENDING,
                org.plainphone.policy.WaitingPhase.COMMITTING,
                org.plainphone.policy.WaitingPhase.ROLLING_BACK)
        } catch (_: RuntimeException) { true }
        if (waited != null) try {
            ReapplyAlarm.schedule(context, waiting.periodMillis(ReapplyAlarm.PERIOD_MILLIS))
        } catch (e: RuntimeException) { Log.e(TAG, "checkpoint alarm threw", e) }
        var passId = 0L
        var bootMillis = 0L
        var wasSkipped = false
        bootCoverage.run(reason, locked,
            // Negative-control builds deliberately break readback state after a pass: never reuse it.
            retryPending = { boot.retryPending || waited != null || activeWait || waitingUncertain || BuildControl.control !== NegativeControl.None },
            apply = { id ->
                passId = id
                packageReadback.invalidate()
                val outcome = try {
                    boot.run(locked)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "reapply $loggedReason threw", e)
                    null
                }
                userUnlocked.recordPass(locked)
                bootMillis = SystemClock.elapsedRealtime() - started
                outcome
            },
            skipped = { id ->
                wasSkipped = true
                val kind = if (reason.startsWith("package:") || reason == "package-burst") "unchanged-since" else "covered-by"
                Log.i(TAG, "reapply reason=$loggedReason skipped=$kind=$id")
            },
            packageReasons = packageReasons,
            packageMaySkip = packageMaySkip,
            packageUnchanged = { name -> packageReadback.unchanged(PackageName(name)) },
            afterPackageSkip = { outcome ->
                publishPass(reason, locked, outcome, refreshHome = false)
                lastGate == HomeGate.READY
            },
            afterPublished = { id ->
                // BootPassCoverage has now checked full verification, publication and retry state.
                // Freeze this pass's inputs; later passes must never supply an older capture's plan.
                preview.lastPlan?.let { plan ->
                    val systemPackages = preview.systemPackages.toSet()
                    bootCoverage.captureAfterPublished(dispatcher::post) {
                        val captureStarted = SystemClock.elapsedRealtime()
                        packageReadback.capture(systemPackages, plan, SESSION_PACKAGES)
                        Log.i(TAG, "packageSnapshot pass=$id ms=${SystemClock.elapsedRealtime() - captureStarted} " +
                            "captured=${packageReadback.hasSnapshot} at=${SystemClock.elapsedRealtime()}")
                    }
                }
            },
            afterPass = { outcome ->
                try {
                    timings.measure("afterReapply") { BuildControl.control.afterReapply(context, dpm, admin) }
                } finally {
                    if (!wasSkipped) log(loggedReason, outcome, bootMillis, SystemClock.elapsedRealtime() - started, passId)
                }
                // A committing or rolling-back people change takes its next step only after this pass's startup
                // revoked every session and read back (PLA-390 R1); a reused pass did not, so it waits.
                val drove = if (wasSkipped) null else try {
                    peopleDriver.driveAfter(outcome)?.also { Log.i(TAG, "people change after $loggedReason: ${it.outcome} failure=${it.failure}") }
                } catch (e: RuntimeException) {
                    Log.e(TAG, "people change step threw", e)
                    null
                }
                publishPass(reason, locked, outcome, drove = drove)
                lastGate == HomeGate.READY
            },
        )
    }

    private fun publishPass(reason: String, locked: Boolean, outcome: BootOutcome?, refreshHome: Boolean = true,
                            drove: PeopleChangeResult? = null) {
        fallbackPass(locked, outcome)
        // Attempt only after unlock. Preview recovery has no full-policy input: loss stays held.
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
        runsState.value++
        // A locked run never opens Home: the full plan has not run yet.
        if (!locked) {
            val action = recovery.afterPass(locked, outcome?.ready == true)
            // PINNED is not containment (PLA-322 finding 3): not ready, and needs attention.
            val pinned = action == RecoveryAction.PINNED_ATTENTION
            // Only the notice: Home and calling stay available either way.
            val held = reconciled?.outcome == ReconcileOutcome.NEEDS_ATTENTION || authorityAttention ||
                drove?.outcome == PeopleChangeOutcome.NEEDS_ATTENTION
            val contained = fallbackState.value != null
            val attention = pinned || held || contained || homeNotice(outcome) != null
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
            val readAt = ZonedDateTime.now()
            waitingReadState.value = WaitingRead(wait, readAt)
            waitingNoticeState.value = wait
            noticeTapState.value = line?.tap
            val base = homeLine(line, readAt, DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                ?: if (waiting.previewTurnOffClosed()) HomeNotice.PreviewTurnOffClosed else null
            baseNotice = base
            noticeState.value = withUpdateLine(base, try { pendingUpdate.pending() } catch (_: RuntimeException) { false })
            lastGate = if (outcome?.ready == true && !pinned && !contained) HomeGate.READY else HomeGate.NOT_READY
            if (!checkingHeld) gateState.value = lastGate
            refreshState.value = HomeRefresh(base,
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

    /**
     * After every pass: contain when lock task cannot start with KEYGUARD, lift when it runs with it
     * ([fallbackStep]). The keep set comes from this pass; without one that holds Home nothing is suspended.
     */
    private fun fallbackPass(userLocked: Boolean, outcome: BootOutcome?) {
        when (fallbackStep(userLocked, lockTaskState(), mayEnterLockTask())) {
            FallbackStep.CONTAIN -> {
                val keep = containmentKeep(outcome, home)
                if (keep == null) {
                    Log.w(TAG, "fallback containment skipped: no complete keep set this pass")
                    fallbackState.value = FallbackNotice(emptyList(), incomplete = true)
                    return
                }
                val result = try {
                    containment.contain(keep)
                } catch (e: RuntimeException) {
                    Log.e(TAG, "fallback containment threw", e)
                    null
                }
                Log.i(TAG, "fallback containment: suspended=${result?.suspended?.size} " +
                    "refused=${result?.refused?.map { it.value }?.sorted()} recorded=${result?.recorded} at=${SystemClock.elapsedRealtime()}")
                fallbackState.value = result?.let { fallbackNotice(it, DISCLOSED, ::label) } ?: FallbackNotice(emptyList(), incomplete = true)
            }
            FallbackStep.LIFT -> lift()
            FallbackStep.NONE -> {}
        }
    }

    /** Lifts the recorded suspension (LOCKED with KEYGUARD is re-read inside); true when the notice cleared. */
    private fun lift(): Boolean {
        val held = fallbackState.value != null
        val lifted = if (containment.mayHoldSuspensions) try {
            containment.liftIfLocked()
        } catch (e: RuntimeException) {
            Log.e(TAG, "fallback lift threw", e)
            null
        } else emptySet()
        if (lifted == null) return false
        if (lifted.isNotEmpty()) Log.i(TAG, "fallback lifted=${lifted.size} at=${SystemClock.elapsedRealtime()}")
        fallbackState.value = null
        return held
    }

    private fun label(name: PackageName): String = try {
        val pm = context.packageManager
        pm.getApplicationInfo(name.value, PackageManager.MATCH_UNINSTALLED_PACKAGES).loadLabel(pm).toString()
    } catch (e: Exception) {
        name.value
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
        /** What the board-approved disclosure names: Settings and the app-permissions screen (card da306a1a). */
        private val DISCLOSED = setOf(PackageName("com.android.settings")) + PERMISSION_CONTROLLERS
        /** Every package a session window can relax: Play, plus a matching inventory's Settings packages (none compiled in yet). */
        private val SESSION_PACKAGES = setOf(PackageName("com.android.vending"))

        @Volatile private var instance: PolicyRuntime? = null

        fun get(context: Context): PolicyRuntime = instance ?: synchronized(this) {
            instance ?: PolicyRuntime(context.applicationContext).also { instance = it }
        }
    }
}
