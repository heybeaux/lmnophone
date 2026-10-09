// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import java.io.File
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.DpmPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.FloorIdentityReader
import org.plainphone.device.facts.LivePackageReader

/**
 * preview-2 device wiring: reads the live phone, builds [Preview2Plan] and applies it through the
 * readback applier. Every apply logs one `PREVIEW2 APPLY` line (tag [TAG]) for the setup script.
 */
class Preview2Device(private val context: Context) {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager
    private val admin = ComponentName(context, PlainphoneAdminReceiver::class.java)
    private val flush = ComponentName(context, FlushMarker::class.java)
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

    /** Null when Plainphone isn't Device Owner or there is no starter list yet: nothing is changed. */
    fun apply(): ApplyResult? {
        val starter = starter()
        if (!isOwner || starter.isEmpty()) {
            Log.i(TAG, "PREVIEW2 APPLY skipped owner=$isOwner starter=${starter.size}")
            return null
        }
        val port = AndroidPackageSystemPort(context)
        val packages = LivePackageReader(port).read()
        val floor = FloorIdentityReader(port).read()
        val plan = Preview2Plan.decide(plainphone, packages.installed, floor.identities, launchable(), starter,
            keepVisible(), setOf(PackageName(SAFETY_HUB)))
        val result = ReadbackPolicyApplier(DpmPort(context, admin, flush)).apply(plan)
        Log.i(TAG, "PREVIEW2 APPLY complete=${result.complete} failed=${result.failedStage} " +
            "applied=${result.applied} hidden=${plan.hidden.size} lockTask=${plan.lockTaskPackages.map { it.value }.sorted()} " +
            "absent=${result.absent.map { it.value }} flipFailures=${result.flipFailures.map { it.value }} " +
            "floorUnreadable=${floor.unreadable}")
        return result
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

    private fun launchable(): Set<PackageName> =
        pm.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS,
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

/** The no-op component the applier flips after every unhide (mitigation B). Never receives anything. */
class FlushMarker : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = Unit
}
