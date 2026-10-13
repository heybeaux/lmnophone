// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import org.plainphone.device.PreviewAppListStore
import org.plainphone.policy.EmergencyFloor
import org.plainphone.policy.PreviewAppRules
import org.plainphone.core.PackageName
import org.plainphone.device.ReapplyTimings
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.policy.FloorRole
import org.plainphone.policy.PolicyPlan

/**
 * preview-2 device wiring: reads the live phone and builds [Preview2Plan] for the boot runtime, which
 * applies it through the readback applier on every re-apply (boot, start, setup, alarm, package events).
 */
class Preview2Device(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager
    private val plainphone = PackageName(context.packageName)
    private val appList = PreviewAppListStore(context.filesDir)

    // Refreshed only by a full plan; used solely for optional post-verification readback evidence.
    internal var lastPlan: PolicyPlan? = null
        private set
    internal var systemPackages: Set<PackageName> = emptySet()
        private set
    /** The floor roles the last [plan] could not read; null until that plan read the floor (PLA-399 F1). */
    @Volatile internal var floorUnreadable: Set<FloorRole>? = null
        private set

    val isOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    /** The setup script's list, one package per line. Empty until the script has run. */
    fun starter(): Set<PackageName> = appList.starter().mapTo(linkedSetOf(), ::PackageName)

    fun saveStarter(packages: List<String>) = appList.saveStarter(packages)

    /** Re-read OS identities before every edit; unreadable floor facts refuse the edit. */
    fun protectedApps(): Set<String> {
        val (installed, floor) = PreviewPackageInput.read(AndroidPackageSystemPort(context), ReapplyTimings())
        check(floor.unreadable.isEmpty()) { "floor unavailable" }
        val protected = EmergencyFloor.compute(installed, floor.identities)
        check(protected.hasSystemDialer) { "dialer unavailable" }
        return (protected.neverHide + keepVisible()).mapTo(mutableSetOf()) { it.value } +
            setOfNotNull(dialer(), sms(), context.packageName, SAFETY_HUB)
    }

    fun moveApp(id: String, delta: Int, active: () -> Boolean): Boolean {
        val protected = protectedApps()
        val next = PreviewAppRules.move(homeApps().map { it.first }, id, delta, protected) ?: return false
        if (!active()) return false
        appList.saveOrder(next)
        return true
    }

    fun removeApp(id: String, active: () -> Boolean): Boolean {
        val protected = protectedApps()
        return active() && appList.remove(id, protected)
    }

    fun appVersion(id: String): String = pm.getPackageInfo(id, 0).versionName.orEmpty()

    fun hiddenApps(): List<org.plainphone.device.PreviewHiddenApp> =
        org.plainphone.device.AndroidPreviewHiddenApps(context,
            android.content.ComponentName(context, org.plainphone.PlainphoneAdminReceiver::class.java))
            .read(appList.starter(), protectedApps())

    fun addApp(id: String, identity: String, active: () -> Boolean): Boolean =
        appList.add(id, homeApps().map { it.first }) {
            val eligible = hiddenApps().any { it.id == id && it.identity == identity }
            eligible && active()
        }

    /**
     * The plan for one re-apply ([org.plainphone.boot.PolicyRuntime]). Before the first unlock the
     * starter list can't be read, so the locked plan only restores the floor and lock task.
     * Unlocked, it throws when there is no starter list yet: the run is then not ready and Home stays on
     * the checking screen.
     */
    fun plan(locked: Boolean, timings: ReapplyTimings = ReapplyTimings()): PolicyPlan {
        lastPlan = null
        systemPackages = emptySet()
        floorUnreadable = null
        val (installed, floor) = PreviewPackageInput.read(AndroidPackageSystemPort(context), timings)
        floorUnreadable = floor.unreadable
        if (locked) return Preview2Plan.decideLocked(plainphone, installed, floor.identities)
        val starter = starter()
        check(appList.configured()) { "no starter list yet" }
        val plan = Preview2Plan.decide(plainphone, installed, floor.identities, launchable(), starter,
            keepVisible(), setOf(PackageName(SAFETY_HUB)))
        Log.i(TAG, "PREVIEW2 PLAN hidden=${plan.hidden.size} lockTask=${plan.lockTaskPackages.map { it.value }.sorted()} " +
            "floorUnreadable=${floor.unreadable}")
        if (floor.unreadable.isEmpty()) {
            systemPackages = installed.filter { it.isSystem }.mapTo(mutableSetOf()) { it.name }
            lastPlan = plan
        }
        return plan
    }

    /** Starter apps to list on Home, with their launcher labels. Phone and Messages have their own rows. */
    fun homeApps(): List<Pair<String, String>> =
        PreviewHomeApps.read(appList, setOfNotNull(dialer(), sms(), context.packageName)) { name ->
            pm.getLaunchIntentForPackage(name)?.let { pm.resolveActivity(it, 0)?.loadLabel(pm)?.toString() }
        }

    /**
     * Hidden apps stay in the query through MATCH_UNINSTALLED_PACKAGES. Disabled apps are left out: they
     * can't open, and on the spare a system-disabled carrier app never read back as hidden, which
     * stopped the apply at HIDES (PLA-144).
     */
    private fun launchable(): Set<PackageName> =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_UNINSTALLED_PACKAGES,
        ).mapTo(mutableSetOf()) { PackageName(it.activityInfo.packageName) }

    /** Settings and every other Home app stay as they are: they are the way back if Plainphone breaks. */
    private fun keepVisible(): Set<PackageName> {
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
            .map { it.activityInfo.packageName }
        val settings = pm.resolveActivity(Intent(Settings.ACTION_SETTINGS), 0)?.activityInfo?.packageName
        return (homes + listOfNotNull(settings)).filter { it != context.packageName }.mapTo(mutableSetOf(), ::PackageName)
    }

    private fun dialer(): String? = context.getSystemService(android.telecom.TelecomManager::class.java)?.defaultDialerPackage
    private fun sms(): String? = android.provider.Telephony.Sms.getDefaultSmsPackage(context)

    companion object {
        const val TAG = "Plainphone"
        /** Android Safety (SOS). Must be in lock task for the power-button SOS on the spare (PLA-40). */
        const val SAFETY_HUB = "com.google.android.apps.safetyhub"
    }
}
