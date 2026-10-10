// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionWrite

/** `DevicePolicyManager.PERMISSION_GRANT_STATE_*`. */
enum class PolicyGrant { DEFAULT, GRANTED, DENIED }

/** The Android reads and writes behind [PermissionGrants]. Every call must also work while the app is hidden. */
interface PermissionGrantPort {
    /** Current signer digests of [name] in this user, hidden included; null when not installed or unreadable. */
    fun signers(name: PackageName): Set<CertDigest>?
    /** The Device Owner's grant state for [permission]; null when the read failed. */
    fun policyState(name: PackageName, permission: PermissionName): PolicyGrant?
    /** Sets the Device Owner's grant state; false when Android refused it. */
    fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean
    /** Whether [permission] is actually granted to [name] now; null when the read failed. */
    fun granted(name: PackageName, permission: PermissionName): Boolean?
}

/**
 * Runtime permission writes and readbacks for one exact app (package and complete current signer
 * set), as the approval flow does them while the app is still hidden. ALLOW and DENY become fixed
 * Device Owner grant states, so the person cannot change them in Settings. A readback is GRANTED or
 * DENIED only when the fixed state and the actual grant agree; DEFAULT stays DEFAULT, never DENIED.
 * A wrong or missing identity writes nothing and reads UNKNOWN.
 */
class PermissionGrants(private val port: PermissionGrantPort) {
    fun set(identity: ApprovalBinding, write: PermissionWrite): Boolean {
        if (!current(identity)) return false
        val state = if (write.choice == PermissionChoice.ALLOW) PolicyGrant.GRANTED else PolicyGrant.DENIED
        if (!port.setPolicyState(identity.packageName, write.permission, state)) return false
        val expected = if (state == PolicyGrant.GRANTED) PermissionReadback.GRANTED else PermissionReadback.DENIED
        return read(identity, listOf(write.permission))[write.permission] == expected
    }

    fun read(identity: ApprovalBinding, permissions: Collection<PermissionName>): Map<PermissionName, PermissionReadback> {
        if (!current(identity)) return permissions.associateWith { PermissionReadback.UNKNOWN }
        val result = permissions.associateWith { readback(port.policyState(identity.packageName, it), port.granted(identity.packageName, it)) }
        // The package could have been replaced while reading; then nothing read is about this identity.
        return if (current(identity)) result else permissions.associateWith { PermissionReadback.UNKNOWN }
    }

    private fun current(identity: ApprovalBinding): Boolean =
        port.signers(identity.packageName)?.let { identity.matches(identity.packageName, it) } == true

    private fun readback(policy: PolicyGrant?, granted: Boolean?): PermissionReadback = when {
        policy == PolicyGrant.GRANTED && granted == true -> PermissionReadback.GRANTED
        policy == PolicyGrant.DENIED && granted == false -> PermissionReadback.DENIED
        policy == PolicyGrant.DEFAULT -> PermissionReadback.DEFAULT
        else -> PermissionReadback.UNKNOWN
    }
}
