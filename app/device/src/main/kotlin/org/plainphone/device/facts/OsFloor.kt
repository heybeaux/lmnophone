// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.plainphone.core.PackageName
import org.plainphone.policy.EmergencyFloor
import org.plainphone.policy.FloorResult
import org.plainphone.policy.InstalledPackage

/**
 * The emergency floor from OS-managed sources only, for the plan-independent repair (PLA-241
 * finding 2): [FloorIdentityReader]'s role holders, kept only when Android reports them as system
 * packages. No policy file, inventory or classifier is read. A throw is the caller's to handle.
 */
fun osFloor(port: PackageSystemPort): FloorResult {
    val identities = FloorIdentityReader(port).read().identities
    val names = identities.mapTo(mutableSetOf()) { it.name.value }
    val installed = port.packages().filter { it.name in names }
        .map { InstalledPackage(PackageName(it.name), it.system, it.versionCode) }
    return EmergencyFloor.compute(installed, identities)
}
