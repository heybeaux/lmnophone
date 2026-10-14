// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.device.AppAdditionResult
import org.plainphone.device.authority.PinAccessResult
import org.plainphone.policy.*

data class PermissionAttempt(val result: GrantWrite, val refusedAllow: Boolean = false)

/** Observes an actual Android refusal, not NOT_DONE from an identity or uninstall-block failure. */
class PermissionGrantAttempts(private val port: PermissionGrantPort) {
    private val refused = mutableSetOf<PermissionName>()
    private val observed = object : PermissionGrantPort by port {
        override fun setPolicyState(name: org.plainphone.core.PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            val accepted = port.setPolicyState(name, permission, state)
            if (!accepted && state == PolicyGrant.GRANTED) refused += permission
            return accepted
        }
    }
    private val grants = PermissionGrants(observed)
    private data class Before(val policy: PolicyGrant, val granted: Boolean)
    private var before: Map<PermissionName, Before>? = null
    private var binding: ApprovalBinding? = null

    fun checkpoint(selected: AppPermissionSnapshot): Boolean {
        before = null
        binding = null
        val identity = selected.identity
        if (port.signers(identity.packageName) != identity.signingCerts) return false
        val saved = selected.permissions.associateWith { permission ->
            val policy = port.policyState(identity.packageName, permission) ?: return false
            val granted = port.granted(identity.packageName, permission) ?: return false
            Before(policy, granted)
        }
        if (port.signers(identity.packageName) != identity.signingCerts) return false
        before = saved
        binding = identity
        return true
    }

    /** Restore only the same identity, while visible and excluded. DEFAULT alone does not undo a
     * runtime grant: restore the previous runtime value first, then clear the temporary fixed state.
     * Inspect all entries because Android can change a companion permission in the same group.
     * A failed restore is an attention result, never success. A caller must still re-hide.
     */
    fun rollback(selected: AppPermissionSnapshot): Boolean {
        val identity = selected.identity
        val saved = before ?: return false
        if (binding != identity || saved.keys != selected.permissions.toSet()) return false
        val name = identity.packageName
        val wasBlocked = port.uninstallBlocked(name) ?: return false
        try {
            if (!wasBlocked && (!port.setUninstallBlocked(name, true) || port.uninstallBlocked(name) != true)) return false
            fun current() = port.signers(name) == identity.signingCerts
            if (!current()) return false
            // Revocations before grants; reverse dependency order for revocations.
            val ordered = saved.entries.sortedWith(compareBy<Map.Entry<PermissionName, Before>> { it.value.granted }
                .thenBy { permissionDependencyOrder(it.key, it.value.granted) })
            var restored = true
            for ((permission, state) in ordered) {
                if (!current()) return false
                if (port.granted(name, permission) != state.granted) {
                    val desired = if (state.granted) PolicyGrant.GRANTED else PolicyGrant.DENIED
                    if (!port.setPolicyState(name, permission, desired)) { restored = false; continue }
                }
                if (port.policyState(name, permission) != state.policy &&
                    !port.setPolicyState(name, permission, state.policy)) restored = false
            }
            return saved.all { (permission, state) ->
                port.policyState(name, permission) == state.policy && port.granted(name, permission) == state.granted
            } && current() && restored
        } finally {
            if (!wasBlocked) port.setUninstallBlocked(name, false)
        }
    }

    fun write(identity: ApprovalBinding, write: PermissionWrite): PermissionAttempt {
        refused.clear()
        val result = grants.write(identity, write)
        return PermissionAttempt(result, write.permission in refused)
    }
}

/** One serial add transaction. The existing effect owns exclusion/unhide/rehide; no grant is written
 * before fresh PIN. Membership is last, after every DONE and another exact-identity read while hidden.
 * A process loss before membership leaves this app unapproved; the normal preview plan hides it.
 */
class PreviewPermissionAddition(
    private val authenticate: (String, CharArray) -> PinAccessResult,
    private val eligible: (AppPermissionSnapshot) -> Boolean,
    private val snapshot: (String) -> AppPermissionSnapshot?,
    private val open: (ApprovalBinding) -> Boolean,
    private val close: (ApprovalBinding) -> Boolean,
    private val write: (ApprovalBinding, PermissionWrite) -> PermissionAttempt,
    private val read: (String) -> AppPermissionRead?,
    private val save: (AppPermissionSnapshot) -> Boolean,
    private val apply: () -> Boolean,
    private val checkpoint: (AppPermissionSnapshot) -> Boolean,
    private val rollback: (AppPermissionSnapshot) -> Boolean,
) {
    fun add(selected: AppPermissionSnapshot, choices: Map<String, PermissionChoice>, pin: CharArray,
        active: () -> Boolean): AppAdditionResult {
        val id = selected.identity.packageName.value
        var captured = false
        var attempted = false
        var membershipSaved = false
        var windowAttempted = false
        var windowClosed = false
        fun perform(): AppAdditionResult {
            try {
                if (!active()) return AppAdditionResult.Cancelled
                val auth = authenticate(id, pin)
                if (!active()) return AppAdditionResult.Cancelled
                if (auth != PinAccessResult.Open) return AppAdditionResult.PinRejected(auth)
                if (selected.groups.map { it.id }.toSet() != choices.keys ||
                    selected.permissions.distinct().size != selected.permissions.size ||
                    !eligible(selected) || selected != snapshot(id)) return AppAdditionResult.SaveFailed
                val writes = selected.groups.flatMap { group -> group.permissions.map {
                    group.id to PermissionWrite(it, choices.getValue(group.id))
                } }.sortedWith(compareBy<Pair<String, PermissionWrite>> { it.second.choice == PermissionChoice.ALLOW }
                    // Grant dependencies first; revoke dependent permissions first.
                    .thenBy { permissionDependencyOrder(it.second.permission, it.second.choice == PermissionChoice.ALLOW) })
                val failed = linkedSetOf<String>()
                val refused = linkedMapOf<String, PermissionName>()
                if (!active()) return AppAdditionResult.Cancelled
                captured = checkpoint(selected)
                if (!captured) return AppAdditionResult.CheckFailed
                var windowReady = false
                try {
                    windowAttempted = true
                    windowReady = open(selected.identity)
                    if (windowReady) for ((group, item) in writes) {
                        if (selected != snapshot(id) || !eligible(selected) || !active()) { failed += group; break }
                        attempted = true
                        val attempt = write(selected.identity, item)
                        if (attempt.result != GrantWrite.DONE) {
                            failed += group
                            if (attempt.refusedAllow && attempt.result == GrantWrite.NOT_DONE) refused[group] = item.permission
                            break
                        }
                    }
                } catch (_: Exception) { failed += selected.groups.map { it.id } }
                // Failed writes restore while the original exclusion window is still open.
                if (failed.isEmpty() && windowReady) {
                    windowClosed = close(selected.identity)
                    if (!windowClosed) return AppAdditionResult.CheckFailed
                }
                if (!active()) return AppAdditionResult.Cancelled
                val actual = read(id)
                val same = actual?.snapshot == selected
                val mismatches = selected.groups.filter { group -> group.permissions.any {
                    actual?.states?.get(it) != if (choices[group.id] == PermissionChoice.ALLOW) PermissionReadback.GRANTED else PermissionReadback.DENIED
                } }.map { it.id }
                if (!windowReady || failed.isNotEmpty() || !same || mismatches.isNotEmpty()) {
                    // A readback must also show denied before claiming that a refused feature cannot work.
                    val unavailable = if (same) refused.filterValues { actual?.states?.get(it) == PermissionReadback.DENIED }.keys else emptySet()
                    return AppAdditionResult.PermissionFailed((failed + mismatches).toList(), actual, unavailable)
                }
                if (!eligible(selected) || selected != snapshot(id) || !active() || !save(selected)) return AppAdditionResult.SaveFailed
                membershipSaved = true
                return if (apply()) AppAdditionResult.Added else AppAdditionResult.CheckFailed
            } catch (_: Exception) { return AppAdditionResult.SaveFailed }
        }
        val outcome = try { perform() } finally { pin.fill('\u0000') }
        if (!membershipSaved && windowAttempted) {
            var restored = true
            if (captured && attempted) restored = try {
                // A failed hidden read or save needs another contained window. No membership
                // has been saved; open rechecks live Home/lock-task exclusion and the floor.
                (!windowClosed || open(selected.identity)) &&
                    selected == snapshot(id) && eligible(selected) && rollback(selected)
            } catch (_: Exception) { false }
            val hidden = try { close(selected.identity) } catch (_: Exception) { false }
            if (!restored || !hidden) return AppAdditionResult.CheckFailed
            if (outcome is AppAdditionResult.PermissionFailed) {
                val actual = try { read(id) } catch (_: Exception) { null }
                val unavailable = outcome.unavailable.filterTo(mutableSetOf()) { group ->
                    actual?.snapshot == selected && selected.groups.first { it.id == group }.permissions.all {
                        actual.states[it] == PermissionReadback.DENIED
                    }
                }
                return outcome.copy(readback = actual, unavailable = unavailable)
            }
        }
        return outcome
    }
}
