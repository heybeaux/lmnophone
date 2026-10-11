// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import org.plainphone.core.PackageName
import org.plainphone.device.DpmPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.SweepMode

/**
 * PLA-233 finding 3, on the emulator: can a Device Owner clear a user-disabled state? Uses the
 * non-floor system package Chrome, never an emergency package. This test APK must be a testOnly
 * Device Owner (see [DpmPortCheck]). Run as `-e phase disabled`.
 *
 * 1. `pm disable-user --user 0` Chrome; its state reads DISABLED_USER (3).
 * 2. [DpmPort.enable] (enableSystemApp) and DevicePolicyManager.installExistingPackage leave it at 3.
 * 3. PackageManager.setApplicationEnabledSetting on Chrome throws SecurityException.
 * 4. [ReadbackPolicyApplier] with a plan that needs Chrome visible completes and reports Chrome in
 *    ApplyResult.unrepairable.
 * Cleanup runs `pm enable --user 0` and fails the run if Chrome is not enabled again.
 */
class DisabledPackageCheck(
    private val ctx: Context,
    private val log: (String) -> Unit,
    private val shell: (String) -> String,
) {
    private val chrome = PackageName("com.android.chrome")
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val pm get() = ctx.packageManager
    private val port by lazy { DpmPort(ctx, admin, ComponentName(ctx, FlushMarker::class.java)) }

    fun check() {
        try {
            run()
        } finally {
            log("cleanup: ${shell("pm enable --user 0 ${chrome.value}")}")
            expect("cleanup: Chrome enabled again", pm.getApplicationEnabledSetting(chrome.value) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED)
        }
    }

    private fun state() = pm.getApplicationEnabledSetting(chrome.value)

    private fun run() {
        log("api=${Build.VERSION.SDK_INT} fingerprint=${Build.FINGERPRINT}")
        expect("this APK is Device Owner", dpm.isDeviceOwnerApp(ctx.packageName))
        expect("Chrome is visible before the check", dpm.isApplicationHidden(admin, chrome.value).not())

        log(shell("pm disable-user --user 0 ${chrome.value}"))
        expect("1. Chrome reads DISABLED_USER (3): ${state()}", state() == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)

        port.enable(chrome)
        expect("2a. after DpmPort.enable (enableSystemApp) still 3: ${state()}", state() == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)
        expect("2b. DpmPort reads it as not enabled", port.packageState(chrome)?.enabled == false)
        val installed = runCatching { dpm.installExistingPackage(admin, chrome.value) }
        log("installExistingPackage -> $installed")
        expect("2c. after installExistingPackage still 3: ${state()}", state() == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)

        val direct = runCatching { pm.setApplicationEnabledSetting(chrome.value, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0) }
        log("setApplicationEnabledSetting -> $direct")
        expect("3. setApplicationEnabledSetting on another package throws SecurityException", direct.exceptionOrNull() is SecurityException)
        expect("3. still 3: ${state()}", state() == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER)

        val plan = PolicyPlan(
            hidden = emptySet(), unhide = setOf(chrome), neverHide = emptySet(), emergencyUi = emptySet(),
            lockTaskPackages = emptySet(), features = emptySet(), restrictions = emptySet(),
            steps = listOf(ApplyStep(ApplyStage.HIDES)),
            expected = ExpectedPolicyState(null, setOf(chrome), emptySet(), null, null, null, null, null, null),
            sweepMode = SweepMode.NONE, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
            classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false,
        )
        val result = ReadbackPolicyApplier(port).apply(plan)
        log("apply -> $result")
        expect("4. apply completes", result.complete)
        expect("4. Chrome is reported unrepairable", result.unrepairable == setOf(chrome))
        expect("4. needsAttention", result.needsAttention)
    }

    private fun expect(what: String, ok: Boolean) {
        log("${if (ok) "ok  " else "FAIL"} $what")
        if (!ok) throw IllegalStateException(what)
    }
}
