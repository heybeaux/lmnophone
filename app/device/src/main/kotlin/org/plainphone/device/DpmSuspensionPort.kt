// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import org.plainphone.core.PackageName
import org.plainphone.device.boot.SuspensionPort
import org.plainphone.device.facts.PackageSystemPort

/**
 * [SuspensionPort] over `DevicePolicyManager.setPackagesSuspended` for the Device Owner [admin], used
 * only by fallback containment. Installed packages come from [packages] (hidden ones included,
 * uninstalled-for-this-user ones left out).
 */
class DpmSuspensionPort(
    context: Context,
    private val admin: ComponentName,
    private val packages: PackageSystemPort,
) : SuspensionPort {
    private val dpm = context.getSystemService(DevicePolicyManager::class.java)
    private val pm = context.packageManager

    override fun installed(): Set<PackageName> =
        packages.packages().mapNotNullTo(mutableSetOf()) { runCatching { PackageName(it.name) }.getOrNull() }

    override fun suspended(name: PackageName): Boolean? {
        val info = try {
            pm.getApplicationInfo(name.value, PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        if (info.flags and ApplicationInfo.FLAG_INSTALLED == 0) return null
        return try {
            dpm.isPackageSuspended(admin, name.value)
        } catch (e: PackageManager.NameNotFoundException) {
            null
        }
    }

    override fun setSuspended(names: Set<PackageName>, suspended: Boolean): Set<PackageName> =
        dpm.setPackagesSuspended(admin, names.map { it.value }.toTypedArray(), suspended)
            .mapNotNullTo(mutableSetOf()) { runCatching { PackageName(it) }.getOrNull() }
}
