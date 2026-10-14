// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import android.content.Context
import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.os.Build
import org.plainphone.core.PackageName
import org.plainphone.device.facts.CertDigests
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionName
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import java.util.concurrent.TimeUnit

/** Invoke off the main thread. PermissionInfo.group is not the runtime platform grouping on API 31+.
 * https://developer.android.com/reference/android/content/pm/PermissionInfo#group
 * Query the OS grouping; don't infer it from names or use it to guess grant capability.
 */
class AndroidAppPermissions(context: Context, admin: ComponentName) {
    private val pm = context.packageManager
    val grants = PermissionGrants(DpmPermissionGrantPort(context, admin))
    private val reader = AppPermissionReader(::snapshot, grants)
    fun read(id: String): AppPermissionRead? = reader.read(id)

    fun snapshot(id: String): AppPermissionSnapshot? = try {
        val info = pm.getPackageInfo(id, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNING_CERTIFICATES or
            PackageManager.MATCH_UNINSTALLED_PACKAGES or PackageManager.MATCH_DISABLED_COMPONENTS)
        check((info.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_INSTALLED != 0)
        val signers = checkNotNull(info.signingInfo).apkContentsSigners.map { CertDigests.sha256(it.toByteArray()) }.toSet()
        val permissions = info.requestedPermissions.orEmpty().mapNotNull {
            // Apps can request vendor permissions whose defining app is absent. Only that
            // missing entry is omitted; other lookup failures still invalidate the snapshot.
            try { pm.getPermissionInfo(it, 0) }
            catch (_: PackageManager.NameNotFoundException) { null }
        }
            .filter { it.protection == PermissionInfo.PROTECTION_DANGEROUS }
        val groupQueries = permissions.associateWith { permission ->
            CompletableFuture<String?>().also { result ->
                if (Build.VERSION.SDK_INT >= 31 && permission.packageName == "android") {
                    pm.getGroupOfPlatformPermission(permission.name, Executor { it.run() }) { result.complete(it) }
                } else result.complete(permission.group)
            }
        }
        // All requests are already in flight. One deadline bounds the whole read, not each permission.
        CompletableFuture.allOf(*groupQueries.values.toTypedArray()).get(3, TimeUnit.SECONDS)
        val groups = permissions.groupBy { groupQueries.getValue(it).getNow(null) ?: it.name }.map { (id, members) ->
            val label = if (id == members.first().name) members.first().loadLabel(pm).toString()
                else pm.getPermissionGroupInfo(id, 0).loadLabel(pm).toString()
            RequestedPermissionGroup(id, label, members.map { PermissionName(it.name) }.sortedBy { it.value })
        }.sortedBy { it.id }
        AppPermissionSnapshot(ApprovalBinding(PackageName(id), signers), info.longVersionCode, groups)
    } catch (_: Exception) { null }
}
