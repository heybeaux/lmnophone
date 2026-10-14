// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.device.facts.CertDigests
import org.plainphone.policy.PermissionName

/**
 * [PermissionGrantPort] over DevicePolicyManager (Device Owner [admin]) and PackageManager.
 * `MATCH_UNINSTALLED_PACKAGES` keeps a hidden app visible to the signer read. Every framework
 * exception is an unavailable value (null or false), never an escape.
 */
class DpmPermissionGrantPort(context: Context, private val admin: ComponentName) : PermissionGrantPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager

    override fun signers(name: PackageName): Set<CertDigest>? = try {
        // The int-flags overload: PackageInfoFlags needs API 33 and minSdk is 30.
        @Suppress("DEPRECATION")
        val info = pm.getPackageInfo(name.value,
            PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        if ((info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED == 0) null
        else info.signingInfo?.apkContentsSigners?.map { CertDigests.sha256(it.toByteArray()) }?.toSet()?.takeIf { it.isNotEmpty() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    override fun policyState(name: PackageName, permission: PermissionName): PolicyGrant? = try {
        val state = when (dpm.getPermissionGrantState(admin, name.value, permission.value)) {
            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT -> PolicyGrant.DEFAULT
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> PolicyGrant.GRANTED
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> PolicyGrant.DENIED
            else -> null
        }
        val info = pm.getApplicationInfo(name.value, PackageManager.MATCH_UNINSTALLED_PACKAGES)
        locationGrantState(permission.value, state, granted(name, permission), info.flags and ApplicationInfo.FLAG_SUSPENDED != 0)
    } catch (_: PackageManager.NameNotFoundException) {
        null
    } catch (_: RuntimeException) {
        null
    }

    override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean = try {
        dpm.setPermissionGrantState(admin, name.value, permission.value, when (state) {
            PolicyGrant.DEFAULT -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
            PolicyGrant.GRANTED -> DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
            PolicyGrant.DENIED -> DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
        })
    } catch (_: RuntimeException) {
        false
    }

    override fun granted(name: PackageName, permission: PermissionName): Boolean? = try {
        pm.checkPermission(permission.value, name.value) == PackageManager.PERMISSION_GRANTED
    } catch (_: RuntimeException) {
        null
    }

    override fun uninstallBlocked(name: PackageName): Boolean? = try {
        dpm.isUninstallBlocked(admin, name.value)
    } catch (_: RuntimeException) {
        null
    }

    /** `setUninstallBlocked` returns nothing, so this reads the block back. */
    override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean = try {
        dpm.setUninstallBlocked(admin, name.value, blocked)
        dpm.isUninstallBlocked(admin, name.value) == blocked
    } catch (_: RuntimeException) {
        false
    }
}
