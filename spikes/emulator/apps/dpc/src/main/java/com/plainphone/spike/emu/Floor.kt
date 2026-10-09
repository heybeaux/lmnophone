// THROWAWAY SPIKE (PLA-11). Runtime emergency floor from OS identities and MATCH_SYSTEM_ONLY (spec device-lockdown, T1).
// `naive = true` is the negative control: plain queries with no system-only filter.
package com.plainphone.spike.emu

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Telephony
import android.telecom.InCallService
import android.telecom.TelecomManager
import android.view.inputmethod.InputMethodManager
import android.webkit.WebView

object Floor {
    const val EMERGENCY_DIAL = "com.android.phone.EmergencyDialer.DIAL"

    data class Result(val members: Map<String, Set<String>>, val uiSubset: Set<String>, val rejected: Map<String, String>)

    fun compute(c: Context, naive: Boolean = false): Result {
        val pm = c.packageManager
        val sysOnly = if (naive) 0 else PackageManager.MATCH_SYSTEM_ONLY
        val rejected = linkedMapOf<String, String>()
        fun isSystem(p: String?) = p != null && runCatching {
            pm.getApplicationInfo(p, 0).flags and ApplicationInfo.FLAG_SYSTEM != 0
        }.getOrDefault(false)
        fun keep(role: String, p: String?): Set<String> {
            if (p == null) return emptySet()
            if (!naive && !isSystem(p)) { rejected[p] = "$role: not a system app"; return emptySet() }
            return setOf(p)
        }
        fun acts(i: Intent) = pm.queryIntentActivities(i, sysOnly).map { it.activityInfo.packageName }.toSet()
            .flatMap { keep("activity ${i.action}", it) }.toSet()

        val tm = c.getSystemService(TelecomManager::class.java)
        val systemDialer = keep("system dialer", tm.systemDialerPackage)
        val defaultDialer = keep("default dialer", tm.defaultDialerPackage)
        val emergency = acts(Intent(EMERGENCY_DIAL)) +
            acts(Intent("android.intent.action.CALL_EMERGENCY", Uri.parse("tel:"))) +
            acts(Intent("android.intent.action.DIAL_EMERGENCY"))
        val inCall = pm.queryIntentServices(Intent(InCallService.SERVICE_INTERFACE), sysOnly)
            .map { it.serviceInfo.packageName }.filter { it in systemDialer || naive }.flatMap { keep("in-call", it) }.toSet()
        val sms = keep("sms", Telephony.Sms.getDefaultSmsPackage(c))
        val cb = pm.queryBroadcastReceivers(Intent("android.provider.Telephony.SMS_CB_RECEIVED"), sysOnly)
            .map { it.activityInfo.packageName }.flatMap { keep("cell broadcast", it) }.toSet() +
            acts(Intent("android.intent.action.MAIN").setPackage("com.google.android.cellbroadcastreceiver")) +
            acts(Intent("android.intent.action.MAIN").setPackage("com.android.cellbroadcastreceiver"))
        val webview = keep("webview", WebView.getCurrentWebViewPackage()?.packageName)
        val imes = c.getSystemService(InputMethodManager::class.java).enabledInputMethodList
            .map { it.packageName }.flatMap { keep("keyboard", it) }.toSet()
        val permCtl = acts(Intent("android.content.pm.action.REQUEST_PERMISSIONS"))

        val members = linkedMapOf(
            "systemDialer" to systemDialer, "defaultDialer" to defaultDialer, "emergencyDialer" to emergency,
            "inCallUi" to inCall, "sms" to sms, "cellBroadcast" to cb, "webview" to webview,
            "keyboards" to imes, "permissionController" to permCtl,
        )
        return Result(members, systemDialer + emergency + inCall, rejected)
    }

    /** System-only package that handles EmergencyDialer.DIAL (com.android.phone on these images). */
    fun emergencyPackage(c: Context): String? = c.packageManager
        .queryIntentActivities(Intent(EMERGENCY_DIAL), PackageManager.MATCH_SYSTEM_ONLY)
        .map { it.activityInfo.applicationInfo }
        .firstOrNull { it.flags and ApplicationInfo.FLAG_SYSTEM != 0 }?.packageName

    fun all(r: Result) = r.members.values.flatten().toSet()
}
