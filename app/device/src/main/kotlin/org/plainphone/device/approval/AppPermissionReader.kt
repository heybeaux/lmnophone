// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.policy.*

data class RequestedPermissionGroup(val id: String, val label: String, val permissions: List<PermissionName>)
data class AppPermissionSnapshot(val identity: ApprovalBinding, val versionCode: Long, val groups: List<RequestedPermissionGroup>) {
    val permissions: List<PermissionName> get() = groups.flatMap { it.permissions }
    val selectionIdentity: String get() = "$versionCode:" + identity.signingCerts.map { it.value }.sorted().joinToString(",")
}
data class AppPermissionRead(val snapshot: AppPermissionSnapshot, val states: Map<PermissionName, PermissionReadback>)

/** Read-only, including when hidden. Missing inventory is not an empty inventory. */
class AppPermissionReader(private val manifest: (String) -> AppPermissionSnapshot?, private val grants: PermissionGrants) {
    fun read(id: String): AppPermissionRead? = try {
        manifest(id)?.let { before ->
            val states = grants.read(before.identity, before.permissions)
            if (before == manifest(id)) AppPermissionRead(before, states) else null
        }
    } catch (_: Exception) { null }
}
