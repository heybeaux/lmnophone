// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.UserManager
import org.plainphone.core.PackageName
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.Restriction

/**
 * [DevicePolicyPort] over DevicePolicyManager and PackageManager, for a Device Owner [admin] in the
 * calling user. [flushComponent] is a no-op component of the same app, flipped between explicit
 * ENABLED and DISABLED with `DONT_KILL_APP | SYNCHRONOUS`. SYNCHRONOUS exists from API 30, which is
 * also minSdk, so no API gate is needed.
 * Reading other packages needs QUERY_ALL_PACKAGES (declared in this module's manifest).
 */
class DpmPort(
    context: Context,
    private val admin: ComponentName,
    private val flushComponent: ComponentName,
) : DevicePolicyPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager

    override fun packageState(name: PackageName): PackageState? {
        val info = try {
            pm.getApplicationInfo(name.value, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return null
        val enabled = when (pm.getApplicationEnabledSetting(name.value)) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> info.enabled
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            else -> false
        }
        return PackageState(
            hidden = dpm.isApplicationHidden(admin, name.value),
            suspended = dpm.isPackageSuspended(admin, name.value),
            enabled = enabled,
        )
    }

    override fun uid(name: PackageName): Int? {
        val info = try {
            pm.getApplicationInfo(name.value, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        return if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) null else info.uid
    }

    override fun setHidden(name: PackageName, hidden: Boolean) {
        dpm.setApplicationHidden(admin, name.value, hidden)
    }

    override fun unsuspend(name: PackageName) {
        dpm.setPackagesSuspended(admin, arrayOf(name.value), false)
    }

    override fun enable(name: PackageName) {
        dpm.enableSystemApp(admin, name.value)
    }

    override fun restrictions(): Set<Restriction> {
        val bundle = dpm.getUserRestrictions(admin)
        return Restriction.entries.filterTo(mutableSetOf()) { bundle.getBoolean(key(it)) }
    }

    override fun addRestriction(restriction: Restriction) {
        dpm.addUserRestriction(admin, key(restriction))
    }

    // An empty permitted list means system input methods / accessibility services only; null allows all.
    override fun systemInputMethodsOnly() = dpm.getPermittedInputMethods(admin)?.isEmpty() == true
    override fun setSystemInputMethodsOnly(only: Boolean) {
        dpm.setPermittedInputMethods(admin, if (only) emptyList() else null)
    }
    override fun systemAccessibilityServicesOnly() = dpm.getPermittedAccessibilityServices(admin)?.isEmpty() == true
    override fun setSystemAccessibilityServicesOnly(only: Boolean) {
        dpm.setPermittedAccessibilityServices(admin, if (only) emptyList() else null)
    }

    override fun lockTaskPackages(): Set<PackageName> = dpm.getLockTaskPackages(admin).mapTo(mutableSetOf(), ::PackageName)
    override fun setLockTaskPackages(packages: Set<PackageName>) {
        dpm.setLockTaskPackages(admin, packages.map { it.value }.sorted().toTypedArray())
    }

    override fun lockTaskFeatures(): Set<LockTaskFeature> {
        val bits = dpm.getLockTaskFeatures(admin)
        return LockTaskFeature.entries.filterTo(mutableSetOf()) { bits and flag(it) != 0 }
    }
    override fun setLockTaskFeatures(features: Set<LockTaskFeature>) {
        dpm.setLockTaskFeatures(admin, features.fold(0) { bits, f -> bits or flag(f) })
    }

    /** The package Home resolves to; the system resolver's package when a chooser would show. */
    override fun home(): PackageName? =
        pm.resolveActivity(homeIntent(), PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName?.let(::PackageName)

    override fun setHome(name: PackageName) {
        val activity = pm.queryIntentActivities(homeIntent().setPackage(name.value), 0).firstOrNull()?.activityInfo
            ?: throw IllegalStateException("${name.value} has no Home activity")
        val filter = IntentFilter(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
            addCategory(Intent.CATEGORY_DEFAULT)
        }
        dpm.clearPackagePersistentPreferredActivities(admin, name.value)
        dpm.addPersistentPreferredActivity(admin, filter, ComponentName(activity.packageName, activity.name))
    }

    override fun flushComponentEnabled(): Boolean? {
        return when (pm.getComponentEnabledSetting(flushComponent)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> false
            else -> null
        }
    }

    override fun setFlushComponentEnabled(enabled: Boolean) {
        val state = if (enabled) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        pm.setComponentEnabledSetting(flushComponent, state, PackageManager.DONT_KILL_APP or PackageManager.SYNCHRONOUS)
    }

    private fun homeIntent() = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)

    companion object {
        /** UserManager keys. DISALLOW_CONFIG_DEFAULT_APPS exists from API 34; RestrictionFilter.forSdk leaves it out below. */
        fun key(r: Restriction): String = when (r) {
            Restriction.DISALLOW_SAFE_BOOT -> UserManager.DISALLOW_SAFE_BOOT
            Restriction.DISALLOW_FACTORY_RESET -> UserManager.DISALLOW_FACTORY_RESET
            Restriction.DISALLOW_DEBUGGING_FEATURES -> UserManager.DISALLOW_DEBUGGING_FEATURES
            Restriction.DISALLOW_ADD_USER -> UserManager.DISALLOW_ADD_USER
            Restriction.DISALLOW_USER_SWITCH -> UserManager.DISALLOW_USER_SWITCH
            Restriction.DISALLOW_ADD_MANAGED_PROFILE -> UserManager.DISALLOW_ADD_MANAGED_PROFILE
            Restriction.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY -> UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES_GLOBALLY
            Restriction.DISALLOW_USB_FILE_TRANSFER -> UserManager.DISALLOW_USB_FILE_TRANSFER
            Restriction.DISALLOW_CONFIG_VPN -> UserManager.DISALLOW_CONFIG_VPN
            Restriction.DISALLOW_CONFIG_PRIVATE_DNS -> UserManager.DISALLOW_CONFIG_PRIVATE_DNS
            Restriction.DISALLOW_NETWORK_RESET -> UserManager.DISALLOW_NETWORK_RESET
            Restriction.DISALLOW_CONFIG_DATE_TIME -> UserManager.DISALLOW_CONFIG_DATE_TIME
            Restriction.DISALLOW_SYSTEM_ERROR_DIALOGS -> UserManager.DISALLOW_SYSTEM_ERROR_DIALOGS
            Restriction.DISALLOW_CREATE_WINDOWS -> UserManager.DISALLOW_CREATE_WINDOWS
            Restriction.DISALLOW_MODIFY_ACCOUNTS -> UserManager.DISALLOW_MODIFY_ACCOUNTS
            Restriction.DISALLOW_CONFIG_CELL_BROADCASTS -> UserManager.DISALLOW_CONFIG_CELL_BROADCASTS
            Restriction.DISALLOW_CONFIG_DEFAULT_APPS -> UserManager.DISALLOW_CONFIG_DEFAULT_APPS
        }

        fun flag(f: LockTaskFeature): Int = when (f) {
            LockTaskFeature.HOME -> DevicePolicyManager.LOCK_TASK_FEATURE_HOME
            LockTaskFeature.KEYGUARD -> DevicePolicyManager.LOCK_TASK_FEATURE_KEYGUARD
            LockTaskFeature.GLOBAL_ACTIONS -> DevicePolicyManager.LOCK_TASK_FEATURE_GLOBAL_ACTIONS
            LockTaskFeature.NOTIFICATIONS -> DevicePolicyManager.LOCK_TASK_FEATURE_NOTIFICATIONS
            LockTaskFeature.SYSTEM_INFO -> DevicePolicyManager.LOCK_TASK_FEATURE_SYSTEM_INFO
            LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK -> DevicePolicyManager.LOCK_TASK_FEATURE_BLOCK_ACTIVITY_START_IN_TASK
        }
    }
}
