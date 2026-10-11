// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

/**
 * [PackageSystemPort] over PackageManager, Telecom, the SMS role, WebView and the input method list,
 * in the calling user. Needs QUERY_ALL_PACKAGES (declared in this module's manifest).
 *
 * `MATCH_UNINSTALLED_PACKAGES` keeps hidden packages in every list and query: a hidden package is
 * still installed (`FLAG_INSTALLED`), only invisible to queries without the flag. Packages
 * uninstalled for this user (`FLAG_INSTALLED` unset) are left out.
 */
class AndroidPackageSystemPort(private val context: Context) : PackageSystemPort {
    private val pm = context.packageManager

    override fun model(): String = Build.MODEL
    override fun fingerprint(): String = Build.FINGERPRINT

    override fun packages(): List<RawPackage> = readPackages(includeSigners = true)

    override fun packageStates(): List<PackageState> = readPackages(includeSigners = false).map { it.state() }

    private fun readPackages(includeSigners: Boolean): List<RawPackage> =
        installedPackages(HIDDEN_AND_DISABLED or
            if (includeSigners) PackageManager.GET_SIGNING_CERTIFICATES.toLong() else 0L).mapNotNull { info ->
            val app = info.applicationInfo ?: return@mapNotNull null
            if (app.flags and ApplicationInfo.FLAG_INSTALLED == 0) return@mapNotNull null
            RawPackage(
                name = info.packageName,
                system = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
                versionCode = info.longVersionCode,
                // Current signers only; signingCertificateHistory is deliberately not read.
                signers = if (includeSigners) info.signingInfo?.apkContentsSigners?.map { it.toByteArray() } else null,
                enabled = app.enabled,
            )
        }

    override fun browsableHandlers(url: String): Set<String> =
        activities(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE),
            PackageManager.MATCH_ALL.toLong() or HIDDEN_AND_DISABLED)

    override fun systemDialer(): String? = context.getSystemService(TelecomManager::class.java)?.systemDialerPackage
    override fun defaultDialer(): String? = context.getSystemService(TelecomManager::class.java)?.defaultDialerPackage
    override fun defaultSms(): String? = Telephony.Sms.getDefaultSmsPackage(context)
    override fun webViewProvider(): String? = WebView.getCurrentWebViewPackage()?.packageName
    override fun enabledInputMethods(): Set<String> =
        context.getSystemService(InputMethodManager::class.java)?.enabledInputMethodList.orEmpty().map { it.packageName }.toSet()

    override fun systemInCallUiPackages(): Set<String> =
        services(Intent(InCallService.SERVICE_INTERFACE), SYSTEM_ONLY or PackageManager.GET_META_DATA.toLong())
            .filter { it.serviceInfo.metaData?.getBoolean(TelecomManager.METADATA_IN_CALL_SERVICE_UI) == true }
            .map { it.serviceInfo.packageName }.toSet()

    override fun systemOnlyActivities(action: String, data: String?): Set<String> =
        activities(Intent(action).apply { if (data != null) setData(Uri.parse(data)) }, SYSTEM_ONLY)

    override fun systemOnlyReceivers(action: String): Set<String> =
        receivers(Intent(action), SYSTEM_ONLY).map { it.activityInfo.packageName }.toSet()

    private fun activities(intent: Intent, flags: Long): Set<String> =
        (if (Build.VERSION.SDK_INT >= 33) pm.queryIntentActivities(intent, PackageManager.ResolveInfoFlags.of(flags))
        else @Suppress("DEPRECATION") pm.queryIntentActivities(intent, flags.toInt()))
            .map { it.activityInfo.packageName }.toSet()

    private fun services(intent: Intent, flags: Long): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.queryIntentServices(intent, PackageManager.ResolveInfoFlags.of(flags))
        else @Suppress("DEPRECATION") pm.queryIntentServices(intent, flags.toInt())

    private fun receivers(intent: Intent, flags: Long): List<ResolveInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.queryBroadcastReceivers(intent, PackageManager.ResolveInfoFlags.of(flags))
        else @Suppress("DEPRECATION") pm.queryBroadcastReceivers(intent, flags.toInt())

    private fun installedPackages(flags: Long): List<PackageInfo> =
        if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags))
        else @Suppress("DEPRECATION") pm.getInstalledPackages(flags.toInt())

    companion object {
        private const val HIDDEN_AND_DISABLED: Long = (PackageManager.MATCH_UNINSTALLED_PACKAGES or
            PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS).toLong()
        private const val SYSTEM_ONLY: Long = PackageManager.MATCH_SYSTEM_ONLY.toLong() or HIDDEN_AND_DISABLED
    }
}
