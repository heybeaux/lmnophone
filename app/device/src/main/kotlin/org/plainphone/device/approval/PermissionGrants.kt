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

/**
 * The Android reads and writes behind [PermissionGrants]. Reads work while the app is hidden.
 * Grant writes need the app visible and not suspended: on API 35 Android refuses every grant write
 * to a hidden app, and a suspended app misreports sensor grants
 * (`app/device/evidence/pla86/hidden-permissions-api35.txt`). A call may also throw; [PermissionGrants]
 * treats that as an unavailable value.
 */
interface PermissionGrantPort {
    /** Current signer digests of [name] in this user, hidden included; null when not installed or unreadable. */
    fun signers(name: PackageName): Set<CertDigest>?
    /** The Device Owner's grant state for [permission]; null when the read failed. */
    fun policyState(name: PackageName, permission: PermissionName): PolicyGrant?
    /** Sets the Device Owner's grant state; false when Android refused it. */
    fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean
    /** Whether [permission] is actually granted to [name] now; null when the read failed. */
    fun granted(name: PackageName, permission: PermissionName): Boolean?
    /** Whether the Device Owner blocks uninstalling [name]; null when the read failed. */
    fun uninstallBlocked(name: PackageName): Boolean?
    /** Sets the Device Owner's uninstall block for [name]; false when Android refused it. */
    fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean
}

/**
 * What [PermissionGrants.write] did. Only [DONE] means the grant was in place for the identity, and only
 * as of the last signer read; see "What DONE means" on [PermissionGrants].
 */
enum class GrantWrite {
    /** Written and read back for the exact identity, which was still installed at the last signer read. */
    DONE,
    /** Not done for the identity; the installed app is still that identity, or no write was tried. */
    NOT_DONE,
    /** Another identity was installed by the end of the write; its grant was fixed DENIED and read back so. */
    REPLACED_CONTAINED,
    /** Another identity was installed by the end of the write, and fixing its grant DENIED did not read back. */
    REPLACED_NOT_CONTAINED,
}

/**
 * Runtime permission writes and readbacks for one exact app (package and complete current signer
 * set). ALLOW and DENY become fixed Device Owner grant states, so the person cannot change them in
 * Settings. A readback is GRANTED or DENIED only when the fixed state and the actual grant agree;
 * DEFAULT stays DEFAULT, never DENIED. Any failed or throwing read gives UNKNOWN, and a wrong or
 * missing identity reads UNKNOWN.
 *
 * Reads work while the app is hidden. Writes need the app visible and not suspended, so the approval
 * flow writes in its short visible window (PLA-176) and reads back again once the app is hidden.
 *
 * Write boundary. `setPermissionGrantState` takes a package name, not a signer, so a check before it
 * cannot prove which app receives the grant. [write] therefore:
 * 1. blocks uninstall of the package (keeping a block that was already there) and reads the block
 *    back, before the identity check. Android refuses an update signed by an unrelated key, so with
 *    the block held another signer cannot take the package name during the write. API 35 evidence
 *    that `pm uninstall` is refused while blocked, hidden or visible, and that a different-signer
 *    install is refused: `app/device/evidence/pla182/uninstall-block-api35.txt`;
 * 2. writes only when the exact identity is installed, then reads back;
 * 3. checks the identity again after the readback. If another identity is installed by then, its
 *    grant is fixed DENIED (not DEFAULT: DEFAULT would leave a granted permission granted and
 *    changeable) and read back, whatever the requested choice was;
 * 4. puts back an uninstall block it added, in every case. If the process dies, or Android refuses
 *    that write, the block stays and the app cannot be uninstalled until it is cleared.
 *
 * A key-rotation update (APK Signature Scheme v3) is a normal update, so the block does not stop it
 * and the permission state carries over (`pla182` evidence: "rotated update installs while uninstall
 * is blocked", "the rotated app kept CAMERA GRANTED"). The binding pins the exact signer set, so the
 * rotated app is another identity. If it lands before the last signer read (step 3), its grant is
 * fixed DENIED and the result is [GrantWrite.REPLACED_CONTAINED].
 *
 * What DONE means. [GrantWrite.DONE] says: the grant was written and read back for the exact approved
 * identity, and that identity was still installed at the last signer read (step 3), while the uninstall
 * block was held. It is a verified snapshot, not a guarantee for the whole call. Nothing is checked from
 * the last signer read, through putting back the block (step 4), to the return. Only a same-lineage v3
 * rotation can land in that window: the block refuses uninstall, and Android refuses an update signed
 * by an unrelated key (`pla182` evidence: DELETE_FAILED_OWNER_BLOCKED and
 * INSTALL_FAILED_UPDATE_INCOMPATIBLE, hidden and visible). A rotation needs the app developer's own
 * signing lineage. If one lands there, [write] still returns DONE and the rotated app keeps the grant;
 * no containment runs. Another signer check would only move the window. The `pla182` emulator check
 * injects its rotation before the first grant write; host tests cover the late window.
 *
 * Callers' duty. Before relying on a grant, or committing an approval, re-read it with [read] by the
 * exact identity. [read] gives UNKNOWN for an identity that is no longer installed, so a rotated app
 * fails that check (`:policy` re-reads after its writes and again in its commit check). The rotated app
 * may still hold the grant while it is hidden and unapproved. Plainphone clears it only at the next
 * approval for that package, which writes and reads back every requested permission; withdrawal and
 * removal keep the app hidden and do not rewrite its grants.
 *
 * [set] is true only for [GrantWrite.DONE], with the same meaning. False means "not done", also when a
 * side effect on a replacement was contained.
 */
class PermissionGrants(private val port: PermissionGrantPort) {
    fun set(identity: ApprovalBinding, write: PermissionWrite): Boolean = write(identity, write) == GrantWrite.DONE

    fun write(identity: ApprovalBinding, write: PermissionWrite): GrantWrite {
        val name = identity.packageName
        val wasBlocked = attempt { port.uninstallBlocked(name) } ?: return GrantWrite.NOT_DONE
        try {
            if (!wasBlocked && !(attempt { port.setUninstallBlocked(name, true) } == true && attempt { port.uninstallBlocked(name) } == true)) {
                return GrantWrite.NOT_DONE
            }
            return writeWhileBlocked(identity, write)
        } finally {
            if (!wasBlocked) attempt { port.setUninstallBlocked(name, false) }
        }
    }

    fun read(identity: ApprovalBinding, permissions: Collection<PermissionName>): Map<PermissionName, PermissionReadback> {
        if (!current(identity)) return permissions.associateWith { PermissionReadback.UNKNOWN }
        val result = permissions.associateWith {
            readback(attempt { port.policyState(identity.packageName, it) }, attempt { port.granted(identity.packageName, it) })
        }
        // The package could have been replaced while reading; then nothing read is about this identity.
        return if (current(identity)) result else permissions.associateWith { PermissionReadback.UNKNOWN }
    }

    private fun writeWhileBlocked(identity: ApprovalBinding, write: PermissionWrite): GrantWrite {
        if (!current(identity)) return GrantWrite.NOT_DONE
        val state = if (write.choice == PermissionChoice.ALLOW) PolicyGrant.GRANTED else PolicyGrant.DENIED
        val expected = if (state == PolicyGrant.GRANTED) PermissionReadback.GRANTED else PermissionReadback.DENIED
        val done = attempt { port.setPolicyState(identity.packageName, write.permission, state) } == true &&
            read(identity, listOf(write.permission))[write.permission] == expected
        if (current(identity)) return if (done) GrantWrite.DONE else GrantWrite.NOT_DONE
        return if (contain(identity.packageName, write.permission)) GrantWrite.REPLACED_CONTAINED else GrantWrite.REPLACED_NOT_CONTAINED
    }

    /** Fixes [permission] DENIED on whatever is installed as [name] now, and reads it back. */
    private fun contain(name: PackageName, permission: PermissionName): Boolean =
        attempt { port.setPolicyState(name, permission, PolicyGrant.DENIED) } == true &&
            attempt { port.policyState(name, permission) } == PolicyGrant.DENIED &&
            attempt { port.granted(name, permission) } == false

    private fun current(identity: ApprovalBinding): Boolean =
        attempt { port.signers(identity.packageName) }?.let { identity.matches(identity.packageName, it) } == true

    private fun readback(policy: PolicyGrant?, granted: Boolean?): PermissionReadback = when {
        policy == null || granted == null -> PermissionReadback.UNKNOWN
        policy == PolicyGrant.DEFAULT -> PermissionReadback.DEFAULT
        policy == PolicyGrant.GRANTED && granted -> PermissionReadback.GRANTED
        policy == PolicyGrant.DENIED && !granted -> PermissionReadback.DENIED
        else -> PermissionReadback.UNKNOWN
    }

    /** A port call; an exception is an unavailable value. */
    private inline fun <T> attempt(call: () -> T): T? = try {
        call()
    } catch (_: RuntimeException) {
        null
    }
}
