// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.io.File
import org.plainphone.core.PackageName
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.FloorIdentityReader
import org.plainphone.device.facts.FloorRead
import org.plainphone.device.facts.LivePackageReader
import org.plainphone.policy.PolicyPlan

/**
 * preview-2 device wiring: reads the live phone and builds [Preview2Plan] for the boot runtime, which
 * applies it through the readback applier on every re-apply (boot, start, setup, alarm, package events).
 */
class Preview2Device(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager
    private val plainphone = PackageName(context.packageName)
    private val starterFile = File(context.filesDir, "starter-list.txt")

    val isOwner: Boolean get() = dpm.isDeviceOwnerApp(context.packageName)

    /** The setup script's list, one package per line. Empty until the script has run. */
    fun starter(): Set<PackageName> =
        if (starterFile.exists()) starterFile.readLines().map(String::trim).filter(String::isNotEmpty).map(::PackageName).toSet()
        else emptySet()

    fun saveStarter(packages: List<String>) {
        val tmp = File(starterFile.path + ".tmp")
        tmp.writeText(packages.joinToString("\n", postfix = "\n"))
        check(tmp.renameTo(starterFile)) { "could not save the starter list" }
    }

    /**
     * The plan for one re-apply ([org.plainphone.boot.PolicyRuntime]). Before the first unlock the
     * starter list can't be read, so the locked plan only restores the floor and lock task.
     * Unlocked, it throws when there is no starter list yet: the run is then not ready and Home stays on
     * the checking screen.
     */
    fun plan(locked: Boolean): PolicyPlan {
        lateinit var floor: FloorRead
        val port = AndroidPackageSystemPort(context)
        val read = LivePackageReader(port).read { floor = FloorIdentityReader(port).read() }
        val floorNames = floor.identities.map { it.name }.toSet()
        val installed = read.installed.filter { it.name !in read.disabled || it.name in floorNames }
        if (locked) return Preview2Plan.decideLocked(plainphone, installed, floor.identities)
        val starter = starter()
        check(starter.isNotEmpty()) { "no starter list yet" }
        val plan = Preview2Plan.decide(plainphone, installed, floor.identities, launchable(), starter,
            keepVisible(), setOf(PackageName(SAFETY_HUB)))
        Log.i(TAG, "PREVIEW2 PLAN hidden=${plan.hidden.size} lockTask=${plan.lockTaskPackages.map { it.value }.sorted()} " +
            "floorUnreadable=${floor.unreadable}")
        return plan
    }

    /** Starter apps to list on Home, with their launcher labels. Phone and Messages have their own rows. */
    fun homeApps(): List<Pair<String, String>> {
        val own = setOf(dialer(), sms(), context.packageName)
        return starter().map { it.value }.filter { it !in own }.mapNotNull { name ->
            val intent = pm.getLaunchIntentForPackage(name) ?: return@mapNotNull null
            val label = pm.resolveActivity(intent, 0)?.loadLabel(pm)?.toString() ?: return@mapNotNull null
            name to label
        }.sortedBy { it.second.lowercase() }
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
