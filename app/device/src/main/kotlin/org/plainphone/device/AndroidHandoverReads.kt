// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyPlan

/** Resolve-only counterpart of EmergencyGate's emergencyDialPad/keyguardDialPad queries. */
class AndroidHandoverReads(private val context: Context, private val port: DevicePolicyPort) {
    fun debugging(kind: DebuggingKind): Int? = Settings.Global.getInt(context.contentResolver,
        when (kind) {
            DebuggingKind.USB -> Settings.Global.ADB_ENABLED
            // AOSP Settings.Global.ADB_WIFI_ENABLED is hidden from the public SDK.
            DebuggingKind.WIRELESS -> "adb_wifi_enabled"
        })

    fun emergencyRoute(plan: PolicyPlan): Boolean? {
        val pm = context.packageManager
        val routes = listOf("com.android.phone.EmergencyDialer.DIAL", "android.intent.action.DIAL_EMERGENCY")
        return routes.map { action ->
            val intent = Intent(action)
            val flags = PackageManager.MATCH_SYSTEM_ONLY
            val candidates = pm.queryIntentActivities(intent, flags)
            // Include hidden/disabled activities as well, so a disabled manufacturer component
            // cannot disappear from this check merely because another resolver remains enabled.
            val all = pm.queryIntentActivities(intent, flags or PackageManager.MATCH_UNINSTALLED_PACKAGES or
                PackageManager.MATCH_DISABLED_COMPONENTS or PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS)
            if (all.isEmpty()) return@map false
            if (candidates.isEmpty()) return@map false
            // More uses this unscoped action. Its selected target must be a real emergency UI.
            // The generic keyguard action may have several candidates: Telecom selects its target.
            // EmergencyGate expects that candidate set, not an unscoped resolveActivity result.
            if (action == "com.android.phone.EmergencyDialer.DIAL") {
                val selected = pm.resolveActivity(intent, flags)?.activityInfo
                if (selected == null || candidates.none {
                        it.activityInfo.packageName == selected.packageName && it.activityInfo.name == selected.name
                    }) return@map false
            }
            all.map { candidate ->
                val activity = candidate.activityInfo
                val name = PackageName(activity.packageName)
                val state = port.packageState(name) ?: return null
                val enabled = pm.getComponentEnabledSetting(ComponentName(activity.packageName, activity.name))
                name in plan.neverHide && name in plan.lockTaskPackages && !state.hidden && !state.suspended &&
                    state.enabled && when (enabled) {
                        PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> activity.enabled
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                        else -> false
                    }
            }.all { it }
        }.all { it }
    }
}
