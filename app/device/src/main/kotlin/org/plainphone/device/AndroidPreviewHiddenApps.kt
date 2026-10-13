// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.LivePackageReader
import org.plainphone.links.*
import org.plainphone.policy.PreviewAddFacts
import org.plainphone.policy.PreviewAppRules

data class PreviewHiddenApp(val id: String, val name: String, val version: String,
    val identity: String, val icon: Bitmap? = null)

/** Read-only package inspection. Hidden queries use the same flags as AndroidPackageSystemPort.
 * Any incomplete source aborts the list, rather than treating missing facts as permission to add.
 */
class AndroidPreviewHiddenApps(private val context: Context, private val admin: ComponentName) {
    private val pm = context.packageManager
    private val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS
    private fun handlers(intent: Intent): Set<String> = pm.queryIntentActivities(intent, flags or PackageManager.MATCH_ALL)
        .map { it.activityInfo.packageName }.toSet()

    fun read(starter: Set<String>, protected: Set<String>): List<PreviewHiddenApp> {
        val facts = LivePackageReader(AndroidPackageSystemPort(context)).read().facts
        val self = facts.single { it.packageName.value == context.packageName }
        val classifications = BrowserClassifier(PlainphoneIdentity(self.packageName, self.signingCerts))
            .classify(facts, BrowserCatalog.bundled(), NeverHideList(emptySet()), inventoryKnown = false)
        val launchers = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), flags)
            .filter { it.activityInfo.enabled && it.activityInfo.exported && it.activityInfo.applicationInfo.enabled }
            .groupBy { it.activityInfo.packageName }
        val homes = handlers(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
        val settings = handlers(Intent(android.provider.Settings.ACTION_SETTINGS))
        val installers = handlers(Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse("content://org.plainphone.test/app.apk"), "application/vnd.android.package-archive")) +
            handlers(Intent(Intent.ACTION_INSTALL_PACKAGE).setData(Uri.parse("content://org.plainphone.test/app.apk")))
        val stores = handlers(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=org.plainphone"))) + "com.android.vending"
        val dpm = context.getSystemService(DevicePolicyManager::class.java)
        return facts.mapNotNull { fact ->
            val id = fact.packageName.value
            if (id in starter || id !in launchers) return@mapNotNull null
            val info = pm.getPackageInfo(id, flags or PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES)
            val app = checkNotNull(info.applicationInfo)
            val permissions = info.requestedPermissions.orEmpty().toSet()
            val allowed = classifications[PackageName(id)]?.verdict in setOf(Verdict.ALLOWED, Verdict.FLAG_REVIEW)
            val factsForAdd = PreviewAddFacts(
                installed = app.flags and ApplicationInfo.FLAG_INSTALLED != 0,
                launchable = app.enabled, hidden = dpm.isApplicationHidden(admin, id),
                readable = fact.signingCerts.isNotEmpty(), browserAllowed = allowed,
                protected = id in protected || id in settings || id == "com.android.systemui",
                installer = id in installers || permissions.any { it in setOf("android.permission.INSTALL_PACKAGES", "android.permission.REQUEST_INSTALL_PACKAGES") },
                store = id in stores, home = id in homes, self = id == context.packageName)
            if (!PreviewAppRules.canAdd(id, starter, factsForAdd)) return@mapNotNull null
            // Bind the selection to the installed version and current signer set; no catalog notes are invented.
            val currentSigners = info.signingInfo?.apkContentsSigners?.map { org.plainphone.device.facts.CertDigests.sha256(it.toByteArray()) }?.toSet()
            check(currentSigners == fact.signingCerts) { "identity changed" }
            val identity = "${info.longVersionCode}:" + fact.signingCerts.map { it.value }.sorted().joinToString(",")
            val launcher = launchers.getValue(id).first()
            val icon = launcher.loadIcon(pm).let { drawable ->
                Bitmap.createBitmap(48, 48, Bitmap.Config.ARGB_8888).also { bitmap ->
                    drawable.setBounds(0, 0, 48, 48); drawable.draw(Canvas(bitmap))
                }
            }
            PreviewHiddenApp(id, launcher.loadLabel(pm).toString(), info.versionName.orEmpty(), identity, icon)
        }.sortedWith(compareBy<PreviewHiddenApp> { it.name.lowercase() }.thenBy { it.id })
    }
}
