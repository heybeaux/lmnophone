// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.device.AppAdditionResult
import org.plainphone.device.authority.PinAccessResult
import org.plainphone.policy.*

data class PermissionAttempt(val result: GrantWrite, val refusedAllow: Boolean = false)

/** Observes an actual Android refusal, not NOT_DONE from an identity or uninstall-block failure. */
class PermissionGrantAttempts(port: PermissionGrantPort) {
    private val refused = mutableSetOf<PermissionName>()
    private val observed = object : PermissionGrantPort by port {
        override fun setPolicyState(name: org.plainphone.core.PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
            val accepted = port.setPolicyState(name, permission, state)
            if (!accepted && state == PolicyGrant.GRANTED) refused += permission
            return accepted
        }
    }
    private val grants = PermissionGrants(observed)
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
) {
    fun add(selected: AppPermissionSnapshot, choices: Map<String, PermissionChoice>, pin: CharArray,
        active: () -> Boolean): AppAdditionResult {
        val id = selected.identity.packageName.value
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
                // Foreground location and sensors must precede their background companion on Android.
                .thenBy { it.second.permission.value.contains("BACKGROUND") })
            val failed = linkedSetOf<String>()
            val refused = linkedMapOf<String, PermissionName>()
            if (!active()) return AppAdditionResult.Cancelled
            var windowReady = false
            var didClose = false
            try {
                windowReady = open(selected.identity)
                if (windowReady) for ((group, item) in writes) {
                    if (selected != snapshot(id) || !eligible(selected) || !active()) { failed += group; break }
                    val attempt = write(selected.identity, item)
                    if (attempt.result != GrantWrite.DONE) {
                        failed += group
                        if (attempt.refusedAllow && attempt.result == GrantWrite.NOT_DONE) refused[group] = item.permission
                        break
                    }
                }
            } catch (_: Exception) { failed += selected.groups.map { it.id } }
            finally { didClose = try { close(selected.identity) } catch (_: Exception) { false } }
            if (!didClose) return AppAdditionResult.CheckFailed
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
            return if (apply()) AppAdditionResult.Added else AppAdditionResult.CheckFailed
        } catch (_: Exception) { return AppAdditionResult.SaveFailed }
        finally { pin.fill('\u0000') }
    }
}
