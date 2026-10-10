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
 * `MATCH_UNINSTALLED_PACKAGES` keeps a hidden app visible to the signer read.
 */
class DpmPermissionGrantPort(context: Context, private val admin: ComponentName) : PermissionGrantPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager

    override fun signers(name: PackageName): Set<CertDigest>? = try {
        val info = pm.getPackageInfo(name.value, PackageManager.PackageInfoFlags.of(
            PackageManager.GET_SIGNING_CERTIFICATES.toLong() or PackageManager.MATCH_UNINSTALLED_PACKAGES.toLong() or
                PackageManager.MATCH_DISABLED_COMPONENTS.toLong()))
        if ((info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED == 0) null
        else info.signingInfo?.apkContentsSigners?.map { CertDigests.sha256(it.toByteArray()) }?.toSet()?.takeIf { it.isNotEmpty() }
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    override fun policyState(name: PackageName, permission: PermissionName): PolicyGrant? = try {
        when (dpm.getPermissionGrantState(admin, name.value, permission.value)) {
            DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT -> PolicyGrant.DEFAULT
            DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED -> PolicyGrant.GRANTED
            DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED -> PolicyGrant.DENIED
            else -> null
        }
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
}
