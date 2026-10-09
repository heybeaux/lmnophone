// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.util.Log
import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.DpmPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.SweepMode

/**
 * On-device check of [ReadbackPolicyApplier] over [DpmPort], run by this test APK as a testOnly
 * Device Owner (`dpm set-device-owner org.plainphone.device.test/org.plainphone.device.apply.CheckAdmin`).
 *
 * 1. Full apply (stages 2–7) with Dialer (floor), YouTube and Photos hidden first: three unhides, three
 *    alternating SYNCHRONOUS flips, then an independent readback through DPM/UserManager/PackageManager.
 * 2. Restriction lost in a power cut, applier half: clear one restriction and the lock-task list,
 *    re-hide an approved app, re-apply, read back before Home.
 * 3. A real readback failure at LOCK_TASK stops before HOME.
 * 4. Turn-off shape: null expected fields cause no restriction, lock-task or Home calls.
 *
 * DISALLOW_DEBUGGING_FEATURES is left out: on the emulator it drops adb at once. Everything is undone
 * at the end. Lines go to logcat (tag PLA117) and the `am instrument` output.
 *
 * The test APK can declare only one instrumentation, so this runs as `-e phase apply` of
 * [org.plainphone.device.storage.DirectBootJournalCheck]. Throws on the first unmet expectation.
 */
class DpmPortCheck(private val ctx: Context, private val log: (String) -> Unit) {

    fun check() {
        try {
            run()
        } finally {
            runCatching { cleanUp() }.onFailure { note("cleanup failed: $it") }
        }
    }

    private val self by lazy { PackageName(ctx.packageName) }
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val flush by lazy { ComponentName(ctx, FlushMarker::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val um by lazy { ctx.getSystemService(UserManager::class.java) }

    private val dialer = PackageName("com.google.android.dialer")
    private val messages = PackageName("com.google.android.apps.messaging")
    private val gms = PackageName("com.google.android.gms")
    private val youtube = PackageName("com.google.android.youtube")
    private val photos = PackageName("com.google.android.apps.photos")
    private val chrome = PackageName("com.android.chrome")
    private val maps = PackageName("com.google.android.apps.maps")
    private val touched = listOf(dialer, messages, gms, youtube, photos, chrome, maps)
    private val restrictions = Restriction.entries.toSet() - Restriction.DISALLOW_DEBUGGING_FEATURES
    private val originalFeatures by lazy { dpm.getLockTaskFeatures(admin) }

    private fun plan(
        stages: List<ApplyStage> = ApplyStage.entries,
        lockTaskExpected: Set<PackageName>? = null,
    ): PolicyPlan {
        val neverHide = setOf(self, dialer, messages, gms)
        val unhide = neverHide + setOf(youtube, photos)
        val hidden = setOf(chrome, maps)
        val ui = setOf(self, dialer, messages, youtube, photos)
        val features = LockTaskFeature.entries.toSet()
        return PolicyPlan(
            hidden = hidden, unhide = unhide, neverHide = neverHide, emergencyUi = setOf(dialer),
            lockTaskPackages = ui, features = features, restrictions = restrictions,
            steps = stages.map { ApplyStep(it) },
            expected = ExpectedPolicyState(hidden, unhide, neverHide, lockTaskExpected ?: ui, features, restrictions, self),
            sweepMode = SweepMode.FULL, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
            classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false,
        )
    }

    private fun run() {
        note("api=${Build.VERSION.SDK_INT} model=${Build.MODEL} deviceOwner=${dpm.isDeviceOwnerApp(ctx.packageName)}")
        expect("this APK is Device Owner", dpm.isDeviceOwnerApp(ctx.packageName))
        note("lockTaskFeatures before=$originalFeatures")

        // 1. Full apply from a messy start.
        for (p in listOf(dialer, youtube, photos)) dpm.setApplicationHidden(admin, p.value, true)
        expect("pre: dialer hidden", dpm.isApplicationHidden(admin, dialer.value))
        val port1 = RecordingPort(DpmPort(ctx, admin, flush))
        val r1 = ReadbackPolicyApplier(port1).apply(plan())
        report("full apply", r1, port1)
        expect("full apply complete", r1.complete && !r1.needsAttention)
        expect("stages 2-7 applied", r1.applied == ApplyStage.entries.drop(1))
        val unhides = port1.ops.withIndex().filter { it.value.startsWith("unhide ") }
        expect("three unhides", unhides.size == 3)
        expect("each unhide followed by its own flip", unhides.all { port1.ops.getOrNull(it.index + 1)?.startsWith("flip ") == true })
        expect("three flips", port1.flips.size == 3)
        expect("no same-state flip", port1.flips.all { (before, target) -> before != target })
        expect("flips alternate", port1.flips.zipWithNext().all { (a, b) -> a.second != b.second })
        independentReadback()

        // 2. Power-cut loss, applier half.
        dpm.clearUserRestriction(admin, UserManager.DISALLOW_FACTORY_RESET)
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.setApplicationHidden(admin, youtube.value, true)
        expect("pre: factory reset restriction lost", !um.hasUserRestriction(UserManager.DISALLOW_FACTORY_RESET))
        val port2 = RecordingPort(DpmPort(ctx, admin, flush))
        val r2 = ReadbackPolicyApplier(port2).apply(plan())
        report("re-apply after loss", r2, port2)
        expect("re-apply complete", r2.complete)
        val restoreAt = port2.ops.indexOf("restrict DISALLOW_FACTORY_RESET")
        expect("restriction re-added before Home", restoreAt >= 0 && restoreAt < port2.ops.indexOfFirst { it.startsWith("home") })
        expect("one unhide, one flip", port2.flips.size == 1 && port2.flips.single().let { it.first != it.second })
        independentReadback()

        // 3. Real readback failure at LOCK_TASK: Home is not written.
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        val port3 = RecordingPort(DpmPort(ctx, admin, flush))
        val r3 = ReadbackPolicyApplier(port3).apply(plan(lockTaskExpected = setOf(self, chrome)))
        report("lock-task mismatch", r3, port3)
        expect("fails at LOCK_TASK", r3.failedStage == ApplyStage.LOCK_TASK && ApplyStage.LOCK_TASK !in r3.applied)
        expect("Home not written", port3.ops.none { it.startsWith("home") })

        // 4. Turn-off shape: only the floor; null fields untouched.
        dpm.setApplicationHidden(admin, dialer.value, true)
        val floor = setOf(dialer, gms)
        val turnOff = plan(stages = listOf(ApplyStage.EMERGENCY_FLOOR)).copy(
            unhide = floor, neverHide = floor, lockTaskPackages = emptySet(), restrictions = emptySet(),
            expected = ExpectedPolicyState(null, floor, floor, null, null, null, null, null, null),
        )
        val port4 = RecordingPort(DpmPort(ctx, admin, flush))
        val r4 = ReadbackPolicyApplier(port4).apply(turnOff)
        report("turn-off floor", r4, port4)
        expect("floor complete", r4.complete && r4.applied == listOf(ApplyStage.EMERGENCY_FLOOR))
        expect("only package reads/writes and one flip",
            port4.ops.all { it.startsWith("unhide ") || it.startsWith("flip ") } && port4.reads.none { it in NON_PACKAGE_READS })
    }

    private fun independentReadback() {
        val missing = restrictions.filterNot { um.hasUserRestriction(DpmPort.key(it)) }
        note("readback: missing restrictions=$missing")
        expect("every planned restriction active (UserManager)", missing.isEmpty())
        expect("chrome and maps hidden", dpm.isApplicationHidden(admin, chrome.value) && dpm.isApplicationHidden(admin, maps.value))
        expect("dialer, youtube, photos visible", listOf(dialer, youtube, photos).none { dpm.isApplicationHidden(admin, it.value) })
        val lockTask = dpm.getLockTaskPackages(admin).toSet()
        note("readback: lockTask=${lockTask.sorted()} features=${dpm.getLockTaskFeatures(admin)}")
        expect("lock-task list", lockTask == setOf(self, dialer, messages, youtube, photos).map { it.value }.toSet())
        val home = ctx.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY)
        note("readback: home=${home?.activityInfo?.packageName}/${home?.activityInfo?.name}")
        expect("Home resolves to this APK", home?.activityInfo?.packageName == ctx.packageName)
        expect("system IMEs only", dpm.getPermittedInputMethods(admin)?.isEmpty() == true)
        note("readback: flush component state=${ctx.packageManager.getComponentEnabledSetting(flush)}")
    }

    private fun cleanUp() {
        for (r in Restriction.entries) dpm.clearUserRestriction(admin, DpmPort.key(r))
        for (p in touched) dpm.setApplicationHidden(admin, p.value, false)
        dpm.setLockTaskPackages(admin, emptyArray())
        dpm.setLockTaskFeatures(admin, originalFeatures)
        dpm.clearPackagePersistentPreferredActivities(admin, ctx.packageName)
        dpm.setPermittedInputMethods(admin, null)
        dpm.setPermittedAccessibilityServices(admin, null)
        val hiddenLeft = touched.filter { dpm.isApplicationHidden(admin, it.value) }
        note("cleanup: restrictions left=${Restriction.entries.filter { um.hasUserRestriction(DpmPort.key(it)) }} hidden left=$hiddenLeft")
    }

    private fun report(label: String, r: ApplyResult, port: RecordingPort) {
        note("$label: applied=${r.applied} failed=${r.failedStage} absent=${r.absent} flipFailures=${r.flipFailures}")
        note("$label: writes=${port.ops}")
        note("$label: flips(before->target)=${port.flips}")
    }

    private fun expect(what: String, ok: Boolean) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw IllegalStateException(what)
    }

    private fun note(line: String) {
        Log.i("PLA117", line)
        log(line)
    }

    /** Logs every write (and which reads happen) in call order, then delegates. */
    private class RecordingPort(private val d: DevicePolicyPort) : DevicePolicyPort by d {
        val ops = mutableListOf<String>()
        val reads = mutableSetOf<String>()
        val flips = mutableListOf<Pair<Boolean?, Boolean>>()
        override fun setHidden(name: PackageName, hidden: Boolean) {
            ops += (if (hidden) "hide " else "unhide ") + name.value; d.setHidden(name, hidden)
        }
        override fun unsuspend(name: PackageName) { ops += "unsuspend ${name.value}"; d.unsuspend(name) }
        override fun enable(name: PackageName) { ops += "enable ${name.value}"; d.enable(name) }
        override fun restrictions() = d.restrictions().also { reads += "restrictions" }
        override fun addRestriction(restriction: Restriction) { ops += "restrict ${restriction.name}"; d.addRestriction(restriction) }
        override fun systemInputMethodsOnly() = d.systemInputMethodsOnly().also { reads += "inputMethods" }
        override fun setSystemInputMethodsOnly(only: Boolean) { ops += "inputMethods $only"; d.setSystemInputMethodsOnly(only) }
        override fun systemAccessibilityServicesOnly() = d.systemAccessibilityServicesOnly().also { reads += "accessibility" }
        override fun setSystemAccessibilityServicesOnly(only: Boolean) { ops += "accessibility $only"; d.setSystemAccessibilityServicesOnly(only) }
        override fun lockTaskPackages() = d.lockTaskPackages().also { reads += "lockTask" }
        override fun setLockTaskPackages(packages: Set<PackageName>) { ops += "lockTask ${packages.size}"; d.setLockTaskPackages(packages) }
        override fun lockTaskFeatures() = d.lockTaskFeatures().also { reads += "features" }
        override fun setLockTaskFeatures(features: Set<LockTaskFeature>) { ops += "features ${features.size}"; d.setLockTaskFeatures(features) }
        override fun home() = d.home().also { reads += "home" }
        override fun setHome(name: PackageName) { ops += "home ${name.value}"; d.setHome(name) }
        override fun setFlushComponentEnabled(enabled: Boolean) {
            ops += "flip $enabled"
            flips += d.flushComponentEnabled() to enabled
            d.setFlushComponentEnabled(enabled)
        }
    }

    private companion object {
        val NON_PACKAGE_READS = setOf("restrictions", "inputMethods", "accessibility", "lockTask", "features", "home")
    }
}
