// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.PackageName

/** Facts are supplied by lane D from the live OS, never from intent extras or stored policy. */
data class InstalledPackage(val name: PackageName, val isSystem: Boolean, val versionCode: Long)
enum class IdentitySource { OS_MANAGED, SYSTEM_ONLY_QUERY, INTENT_DECLARATION }
enum class FloorRole(val source: IdentitySource, val hasEmergencyUi: Boolean = false) {
    SYSTEM_DIALER(IdentitySource.OS_MANAGED, true),
    DEFAULT_DIALER(IdentitySource.OS_MANAGED),
    EMERGENCY_DIALER(IdentitySource.SYSTEM_ONLY_QUERY, true),
    IN_CALL_UI(IdentitySource.OS_MANAGED, true),
    SMS(IdentitySource.OS_MANAGED),
    CELL_BROADCAST_RECEIVER(IdentitySource.SYSTEM_ONLY_QUERY),
    CELL_BROADCAST_DISPLAY(IdentitySource.SYSTEM_ONLY_QUERY, true),
    WEBVIEW(IdentitySource.OS_MANAGED),
    KEYBOARD(IdentitySource.OS_MANAGED),
    PERMISSION_CONTROLLER(IdentitySource.SYSTEM_ONLY_QUERY),
}
data class FloorIdentity(val name: PackageName, val role: FloorRole, val source: IdentitySource)
data class FloorResult(val neverHide: Set<PackageName>, val emergencyUi: Set<PackageName>, val hasSystemDialer: Boolean)

object EmergencyFloor {
    fun compute(installed: List<InstalledPackage>, identities: List<FloorIdentity>): FloorResult {
        // Conflicting facts must not confer system identity.
        val system = installed.groupBy { it.name }.filterValues { facts -> facts.all { it.isSystem } }.keys
        val trusted = identities.filter { it.name in system && it.source == it.role.source }
        return FloorResult(
            immutableSet(trusted.map { it.name }),
            immutableSet(trusted.filter { it.role.hasEmergencyUi }.map { it.name }),
            trusted.any { it.role == FloorRole.SYSTEM_DIALER },
        )
    }
}
