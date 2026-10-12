// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import org.plainphone.core.PackageName

/** Live exclusions for a targeted package read; errors must escape to the fail-closed caller. */
class PackageChangeFacts(context: Context) {
    private val pm = context.packageManager
    private val floor = FloorIdentityReader(AndroidPackageSystemPort(context))
    private val own = PackageName(context.packageName)

    fun protected(): Set<PackageName> {
        val read = floor.read()
        check(read.unreadable.isEmpty()) { "floor unreadable" }
        val homes = pm.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        return read.identities.mapTo(mutableSetOf()) { it.name } + own +
            homes.map { PackageName(it.activityInfo.packageName) }
    }

    fun launchable(name: PackageName): Boolean = pm.queryIntentActivities(
        Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(name.value),
        PackageManager.MATCH_UNINSTALLED_PACKAGES).isNotEmpty()
}
