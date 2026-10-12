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
 *
 * A package disabled in both reads is left out of `installed`, so no sweep tries to hide it: it cannot
 * open, and a system-disabled app never reads back as hidden (PLA-144 spare finding). A disabled package
 * that a floor identity names stays in, so the floor still enables and unhides it.
 *
 * Confirmed absence (PLA-293). After both list reads, each minimum-UI name of the matching inventory and
 * each [assemble] `previousLockTask` name is asked about on its own ([PackageSystemPort.installedForUser]).
 * Only a positive "not installed for this user" makes it absent. A name listed in either read (hidden,
 * disabled and suspended packages are listed), a floor name, Plainphone, or a query that throws is never
 * absent. Nothing is kept between passes: each pass asks again.
 */
class LivePolicyInputAssembler(
    private val port: PackageSystemPort,
    private val store: PolicyStore,
    private val inventories: List<BuildInventory>,
    private val plainphone: PackageName,
    private val messages: PackageName,
) {
    /**
     * @param previousLockTask the protected UI state the caller would otherwise add with `copy`. It is set
     *   on the input, and its names are checked for absence like the minimum UI.
     */
    fun assemble(previousLockTask: Set<PackageName> = emptySet()): LivePolicyFacts {
        lateinit var floor: FloorRead
        val packages = LivePackageReader(port).read { floor = FloorIdentityReader(port).read() }
        val floorNames = floor.identities.map { it.name }.toSet()
        val model = port.model()
        val fingerprint = port.fingerprint()
        val inventory = inventoryFor(model, fingerprint)
        val listed = packages.installed.map { it.name }.toSet()
        val absent = ((inventory?.minimumUi.orEmpty() + previousLockTask) - listed - floorNames - plainphone)
            .filterTo(mutableSetOf()) { name -> runCatching { !port.installedForUser(name.value) }.getOrDefault(false) }
        val input = PolicyInput(
            model = model,
            fingerprint = fingerprint,
            plainphone = plainphone,
            messages = messages,
            installed = packages.installed.filter { it.name !in packages.disabled || it.name in floorNames },
            identities = floor.identities,
            inventory = inventory,
            health = store.read().health,
            previousLockTask = previousLockTask,
            installedIdentities = packages.identities,
            confirmedAbsentPackages = absent,
        )
        return LivePolicyFacts(input, packages.facts, floor.unreadable)
    }

    private fun inventoryFor(model: String, fingerprint: String): BuildInventory? {
        val matching = inventories.filter { it.model == model && it.fingerprint == fingerprint }
        val newest = matching.maxOfOrNull { it.revision } ?: return null
        return matching.filter { it.revision == newest }.singleOrNull()
    }
}
