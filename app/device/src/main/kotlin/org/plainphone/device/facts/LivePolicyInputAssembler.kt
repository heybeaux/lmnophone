// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.facts

import org.plainphone.core.BuildInventory
import org.plainphone.core.PackageName
import org.plainphone.links.PackageFacts
import org.plainphone.policy.FloorRole
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PolicyStore

/**
 * @property input the live part of the policy input. Callers add journal, session and approval state with `copy`.
 * @property packages one [PackageFacts] per package for the browser classifier.
 * @property unreadableFloorRoles floor roles whose OS source failed this pass.
 */
data class LivePolicyFacts(val input: PolicyInput, val packages: List<PackageFacts>, val unreadableFloorRoles: Set<FloorRole>)

/**
 * Builds a [PolicyInput] from the live phone: package facts and signer bindings, floor identities from
 * OS-managed sources, the policy store's health, the running build, and the one compiled-in inventory
 * for that exact model and fingerprint (none if no inventory matches, or if two of the same revision do).
 * The stored approved list is not copied: approvals come from approval state plus fresh signer bindings.
 */
class LivePolicyInputAssembler(
    private val port: PackageSystemPort,
    private val store: PolicyStore,
    private val inventories: List<BuildInventory>,
    private val plainphone: PackageName,
    private val messages: PackageName,
) {
    fun assemble(): LivePolicyFacts {
        lateinit var floor: FloorRead
        val packages = LivePackageReader(port).read { floor = FloorIdentityReader(port).read() }
        val model = port.model()
        val fingerprint = port.fingerprint()
        val input = PolicyInput(
            model = model,
            fingerprint = fingerprint,
            plainphone = plainphone,
            messages = messages,
            installed = packages.installed,
            identities = floor.identities,
            inventory = inventoryFor(model, fingerprint),
            health = store.read().health,
            installedIdentities = packages.identities,
        )
        return LivePolicyFacts(input, packages.facts, floor.unreadable)
    }

    private fun inventoryFor(model: String, fingerprint: String): BuildInventory? {
        val matching = inventories.filter { it.model == model && it.fingerprint == fingerprint }
        val newest = matching.maxOfOrNull { it.revision } ?: return null
        return matching.filter { it.revision == newest }.singleOrNull()
    }
}
