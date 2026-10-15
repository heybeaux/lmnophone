// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

import android.app.ActivityManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Bundle
import android.telecom.TelecomManager
import org.plainphone.device.DpmPort
import org.plainphone.policy.*

/** The preview's actual DPM writes; see tools/give-back/inventory.md for deliberately untouched settings. */
class DpmGiveBackPort(private val context: Context, private val admin: ComponentName) : GiveBackPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager
    private val am = context.getSystemService(ActivityManager::class.java)
    private val telecom = context.getSystemService(TelecomManager::class.java)
    private val self = context.packageName
    private val flags = PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS

    override fun isDeviceOwner() = dpm.isDeviceOwnerApp(self)
    override fun inventory(): List<GiveBackAction> = buildList {
        val restrictions = dpm.getUserRestrictions(admin)
        restrictions.keySet().sorted().filter { restrictions.getBoolean(it) }.forEach { add(GiveBackAction(GiveBackKind.RESTRICTION, it)) }
        for (info in pm.getInstalledPackages(flags or PackageManager.GET_PERMISSIONS).sortedBy { it.packageName }) {
            if (info.applicationInfo?.flags?.and(ApplicationInfo.FLAG_INSTALLED) == 0) continue
            val name = info.packageName
            if (dpm.isApplicationHidden(admin, name)) add(GiveBackAction(GiveBackKind.VISIBLE, name))
            if (dpm.isPackageSuspended(admin, name)) add(GiveBackAction(GiveBackKind.UNSUSPENDED, name))
            if (dpm.isUninstallBlocked(admin, name)) add(GiveBackAction(GiveBackKind.UNINSTALLABLE, name))
            for (permission in info.requestedPermissions.orEmpty().sorted()) {
                val p = try { pm.getPermissionInfo(permission, 0) } catch (_: PackageManager.NameNotFoundException) { continue }
                if (p.protectionLevel and PermissionInfo.PROTECTION_MASK_BASE != PermissionInfo.PROTECTION_DANGEROUS) continue
                if (dpm.getPermissionGrantState(admin, name, permission) != DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT)
                    add(GiveBackAction(GiveBackKind.PERMISSION, name, permission))
            }
        }
        add(GiveBackAction(GiveBackKind.PERMISSION_POLICY))
        add(GiveBackAction(GiveBackKind.INPUT_METHODS))
        add(GiveBackAction(GiveBackKind.ACCESSIBILITY))
        add(GiveBackAction(GiveBackKind.KEYGUARD_FEATURES))
        // This API throws UnsupportedOperationException on devices with no FRP agent. Such devices
        // cannot have accepted our FRP write either; other failures must abort the inventory.
        try { dpm.getFactoryResetProtectionPolicy(admin); add(GiveBackAction(GiveBackKind.RESET_PROTECTION)) }
        catch (_: UnsupportedOperationException) { }
        if (installed("com.android.vending")) add(GiveBackAction(GiveBackKind.PLAY_RESTRICTIONS, "com.android.vending"))
        add(GiveBackAction(GiveBackKind.SYSTEM_UPDATE))
        add(GiveBackAction(GiveBackKind.LOCK_TASK))
        add(GiveBackAction(GiveBackKind.PREFERRED_HOME, self))
    }

    override fun restore(action: GiveBackAction) {
        when (action.kind) {
            GiveBackKind.RESTRICTION -> dpm.clearUserRestriction(admin, action.target)
            GiveBackKind.UNINSTALLABLE -> if (installed(action.target)) dpm.setUninstallBlocked(admin, action.target, false)
            GiveBackKind.PERMISSION -> if (installed(action.target)) {
                check(!dpm.isApplicationHidden(admin, action.target))
                check(dpm.setPermissionGrantState(admin, action.target, action.detail, DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT))
            }
            GiveBackKind.PERMISSION_POLICY -> dpm.setPermissionPolicy(admin, DevicePolicyManager.PERMISSION_POLICY_PROMPT)
            GiveBackKind.INPUT_METHODS -> check(dpm.setPermittedInputMethods(admin, null))
            GiveBackKind.ACCESSIBILITY -> check(dpm.setPermittedAccessibilityServices(admin, null))
            GiveBackKind.KEYGUARD_FEATURES -> if (dpm.getKeyguardDisabledFeatures(admin) != 0)
                dpm.setKeyguardDisabledFeatures(admin, DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE)
            GiveBackKind.RESET_PROTECTION -> {
                dpm.setFactoryResetProtectionPolicy(admin, null)
                context.sendBroadcast(Intent("com.google.android.gms.auth.FRP_CONFIG_CHANGED").setPackage("com.google.android.gms"))
            }
            GiveBackKind.PLAY_RESTRICTIONS -> dpm.setApplicationRestrictions(admin, action.target, Bundle())
            GiveBackKind.SYSTEM_UPDATE -> dpm.setSystemUpdatePolicy(admin, null)
            GiveBackKind.LOCK_TASK -> {
                // The root that started lock task must stop it; a joined HOME instance cannot.
                GiveBackLockTask.stopRoots()
                dpm.setLockTaskPackages(admin, emptyArray())
                dpm.setLockTaskFeatures(admin, DevicePolicyManager.LOCK_TASK_FEATURE_NONE)
            }
            GiveBackKind.PREFERRED_HOME -> dpm.clearPackagePersistentPreferredActivities(admin, action.target)
            GiveBackKind.VISIBLE, GiveBackKind.UNSUSPENDED -> error("package effects must use ReadbackGiveBackPort")
        }
    }

    override fun readBack(action: GiveBackAction): Boolean = when (action.kind) {
        GiveBackKind.RESTRICTION -> !dpm.getUserRestrictions(admin).getBoolean(action.target)
        GiveBackKind.UNINSTALLABLE -> !installed(action.target) || !dpm.isUninstallBlocked(admin, action.target)
        GiveBackKind.PERMISSION -> !installed(action.target) || dpm.getPermissionGrantState(admin, action.target, action.detail) == DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
        GiveBackKind.PERMISSION_POLICY -> dpm.getPermissionPolicy(admin) == DevicePolicyManager.PERMISSION_POLICY_PROMPT
        GiveBackKind.INPUT_METHODS -> dpm.getPermittedInputMethods(admin) == null
        GiveBackKind.ACCESSIBILITY -> dpm.getPermittedAccessibilityServices(admin) == null
        GiveBackKind.KEYGUARD_FEATURES -> dpm.getKeyguardDisabledFeatures(admin) == DevicePolicyManager.KEYGUARD_DISABLE_FEATURES_NONE
        GiveBackKind.RESET_PROTECTION -> dpm.getFactoryResetProtectionPolicy(admin) == null
        GiveBackKind.PLAY_RESTRICTIONS -> dpm.getApplicationRestrictions(admin, action.target).isEmpty
        GiveBackKind.SYSTEM_UPDATE -> dpm.systemUpdatePolicy == null
        GiveBackKind.LOCK_TASK -> dpm.getLockTaskPackages(admin).isEmpty() && dpm.getLockTaskFeatures(admin) == 0 &&
            am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE
        // A resolver/chooser is acceptable: it lets the person choose a normal launcher.
        GiveBackKind.PREFERRED_HOME -> pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName != self && anotherHomeResolves()
        GiveBackKind.VISIBLE, GiveBackKind.UNSUSPENDED -> error("package effects must use ReadbackGiveBackPort")
    }
    override fun anotherHomeResolves(): Boolean = homeComponent() != null
    override fun systemDialerResolves(): Boolean {
        val name = telecom.systemDialerPackage ?: return false
        val info = pm.getApplicationInfo(name, 0)
        return info.enabled && info.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            pm.resolveActivity(Intent(Intent.ACTION_DIAL).setPackage(name), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.enabled == true
    }
    @Suppress("DEPRECATION")
    override fun removeDeviceOwner() { dpm.clearDeviceOwnerApp(self) }
    fun openSuccessor() {
        val component = homeComponent() ?: return
        context.startActivity(homeIntent().setComponent(component).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
    private fun homeComponent(): ComponentName? = pm.queryIntentActivities(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)
        .map { it.activityInfo }.firstOrNull { it.packageName != self && it.enabled && it.applicationInfo.enabled }
        ?.let { ComponentName(it.packageName, it.name) }
    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
    private fun installed(name: String): Boolean = try { pm.getApplicationInfo(name, flags).flags and ApplicationInfo.FLAG_INSTALLED != 0 }
        catch (_: PackageManager.NameNotFoundException) { false }
}
