// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.core.PackageName
import org.plainphone.device.DpmPort
import org.plainphone.device.SerialDispatcher
import org.plainphone.device.boot.BootOutcome
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.LivePolicyInputAssembler
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.device.storage.FilePolicyStore
import org.plainphone.policy.PolicyEngine

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
    private val boot = BootReapply(
        FileJournalStore(File(protectedDir, "journal"), null, Build.MODEL, Build.FINGERPRINT),
        port, SESSION_PACKAGES,
        { locked -> PolicyEngine.decide(assembler.assemble().input.copy(lockedBoot = locked)) },
        BuildRestrictions.filter.forSdk(Build.VERSION.SDK_INT),
    )
    private val gateState = MutableStateFlow(HomeGate.CHECKING)
    private val packageEventQueued = AtomicBoolean(false)

    val gate: StateFlow<HomeGate> = gateState

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

    /** Hook for the wiring slice: run after every trusted-person action commits. */
    fun afterTrustedPersonAction() = reapply("trusted-person")

    private fun run(reason: String) {
        if (!dpm.isDeviceOwnerApp(context.packageName)) {
            Log.w(TAG, "reapply $reason skipped: not Device Owner")
            gateState.value = HomeGate.NOT_READY
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
        BuildControl.control.afterReapply(context, dpm, admin)
        // A locked run never opens Home: the full plan has not run yet.
        if (!locked) gateState.value = if (outcome?.ready == true) HomeGate.READY else HomeGate.NOT_READY
    }

    private fun log(reason: String, outcome: BootOutcome?, millis: Long) {
        val apply = outcome?.apply
        Log.i(TAG, "reapply reason=$reason control=${BuildControl.control.name} locked=${outcome?.locked} ready=${outcome?.ready} " +
            "applied=${apply?.applied} failed=${apply?.failedStage} flipFailures=${apply?.flipFailures?.size} " +
            "withheld=${outcome?.withheld?.sorted()} ms=$millis")
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
