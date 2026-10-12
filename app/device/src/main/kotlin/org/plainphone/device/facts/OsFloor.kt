// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.plainphone.core.PackageName
import org.plainphone.policy.EmergencyFloor
import org.plainphone.policy.FloorResult
import org.plainphone.policy.FloorRole
import org.plainphone.policy.InstalledPackage

/**
 * The emergency floor from OS-managed sources only, for the plan-independent repair (PLA-241
 * finding 2): [FloorIdentityReader]'s role holders, kept only when Android reports them as system
 * packages. No policy file, inventory or classifier is read. A throw is the caller's to handle.
 */
fun osFloor(port: PackageSystemPort): FloorResult = osFloorRead(port).floor

/** [osFloor] with the roles whose OS source failed: with any, the floor is incomplete (PLA-399 finding 1). */
data class OsFloorRead(val floor: FloorResult, val unreadable: Set<FloorRole>)

fun osFloorRead(port: PackageSystemPort): OsFloorRead {
    val read = FloorIdentityReader(port).read()
    val names = read.identities.mapTo(mutableSetOf()) { it.name.value }
    val installed = port.packages().filter { it.name in names }
        .map { InstalledPackage(PackageName(it.name), it.system, it.versionCode) }
    return OsFloorRead(EmergencyFloor.compute(installed, read.identities), read.unreadable)
}

/**
 * Whether the emergency floor behind this pass's keep set was read from every OS source (PLA-399
 * finding 1). Each source resets it before reading, so a throw leaves it unknown, and the later source
 * decides: the policy facts for the plan, then the OS floor when no plan could be built. Run on the one
 * serialized policy dispatcher.
 */
class FloorCompleteness {
    @Volatile var complete: Boolean = false
        private set

    /** The policy facts read for the plan; [read] returns the floor roles it could not read. */
    fun facts(read: () -> Set<FloorRole>) {
        complete = false
        complete = read().isEmpty()
    }

    fun osFloor(read: () -> OsFloorRead): FloorResult {
        complete = false
        return read().also { complete = it.unreadable.isEmpty() }.floor
    }
}
