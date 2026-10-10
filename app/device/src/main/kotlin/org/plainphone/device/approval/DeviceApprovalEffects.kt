// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.ReadbackPolicyApplier
import org.plainphone.device.SerialDispatcher
import org.plainphone.policy.AppApprovalEffects
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionReadback
import org.plainphone.policy.PermissionWrite
import org.plainphone.policy.PolicyPlan

/**
 * The apps Plainphone Home lists. Removal is the only change outside a committed policy: only
 * [publish], from a complete policy apply, can list an app again.
 */
interface HomeAppList {
    fun remove(name: PackageName)
    /** Whether Home lists [name] now; null when it cannot be read. */
    fun lists(name: PackageName): Boolean?
    /** Lists exactly [names] (the committed policy's lock-task packages). */
    fun publish(names: Set<PackageName>)
}

/** The in-process [HomeAppList] that Home reads. */
class PublishedHomeApps : HomeAppList {
    @Volatile var listed: Set<PackageName> = emptySet()
        private set
    override fun remove(name: PackageName) { listed = listed - name }
    override fun lists(name: PackageName): Boolean = name in listed
    override fun publish(names: Set<PackageName>) { listed = names.toSet() }
}

/**
 * [AppApprovalEffects] on the device (PLA-186). API 35 refuses grant writes to a hidden app
 * (`app/device/evidence/pla86/hidden-permissions-api35.txt`), so permissions are written in a short
 * permission window: the app is taken out of applied lock task and Home's list, both read back, and
 * only then unhidden. It is visible for that moment but cannot be opened from Plainphone; its own
 * background code may run (for example on `ACTION_PACKAGE_ADDED`). Closing re-hides it and never gives
 * lock task or Home back; only the committed [applyPolicy] does that.
 *
 * Every write runs only on [dispatcher] (otherwise it returns false and touches nothing), so a package
 * event handler on the same dispatcher cannot re-hide the app inside the window. Every hide, window
 * open and window close checks the live never-hide list first; unreadable means refuse. Nothing is
 * suspended: a suspended app misreports sensor grants, so open fails for one.
 *
 * Effective exclusion (PLA-218). Android lets every package that shares a UID with an allowed lock-task
 * package start lock task too (`DevicePolicyManager.setLockTaskPackages`). After the name is out of the
 * applied list, open refuses, before any unhide, while a package left in that list shares the
 * candidate's UID or any of those UIDs cannot be read. It never removes the sibling or the floor.
 *
 * One window at a time. Open claims the window for its identity before its first write, and keeps it
 * even when it fails part-way; a second open is refused and touches nothing. Only the owner's close or
 * hide, read back hidden, frees it. A close for another identity still hides its own package but
 * returns false and leaves the window with its owner.
 *
 * A failed [applyPolicy] leaves no discretionary package visible and in lock task that was not both
 * before it: each such package is taken out of lock task and Home, hidden, and read back. Floor
 * packages (never-hide and emergency UI) are left as the apply repaired them.
 */
class DeviceApprovalEffects(
    private val dispatcher: SerialDispatcher,
    private val port: DevicePolicyPort,
    grantPort: PermissionGrantPort,
    private val home: HomeAppList,
    private val neverHide: () -> Set<PackageName>?,
    private val manifest: (PackageName) -> PermissionManifest?,
) : AppApprovalEffects {
    private val grants = PermissionGrants(grantPort)
    private val signers: (PackageName) -> Set<org.plainphone.core.CertDigest>? = { attempt { grantPort.signers(it) } }
    private val applier = ReadbackPolicyApplier(port)
    /** The identity that owns the window, from its open until its close; written only on the dispatcher. */
    private var owner: ApprovalBinding? = null
    /** Whether the owner's open completed; only then may it write permissions. */
    private var opened = false

    override fun hideAndReadBack(identity: ApprovalBinding): Boolean = serialized {
        hide(identity.packageName).also { if (it && identity == owner) release() }
    }

    override fun openPermissionWindow(identity: ApprovalBinding): Boolean = serialized {
        if (owner != null) return@serialized false
        val name = identity.packageName
        if (!mayHide(name)) return@serialized false
        owner = identity
        opened = attempt {
            val before = port.lockTaskPackages()
            if (name in before) port.setLockTaskPackages(before - name)
            val applied = port.lockTaskPackages()
            if (applied != before - name) return@attempt false
            // Effective exclusion: no package left in lock task may share the candidate's UID.
            val uid = port.uid(name) ?: return@attempt false
            if (applied.any { port.uid(it) == uid }) return@attempt false
            home.remove(name)
            if (home.lists(name) != false) return@attempt false
            // Only the exact identity becomes visible.
            if (signers(name)?.let { identity.matches(name, it) } != true) return@attempt false
            val now = port.packageState(name) ?: return@attempt false
            if (now.suspended) return@attempt false
            if (now.hidden) {
                port.setHidden(name, false)
                if (!flush()) return@attempt false
            }
            port.packageState(name)?.let { !it.hidden && !it.suspended } == true
        } == true
        opened
    }

    override fun closePermissionWindow(identity: ApprovalBinding): Boolean = serialized {
        val hidden = hide(identity.packageName)
        when (owner) {
            null -> hidden
            identity -> hidden.also { if (it) release() }
            else -> false
        }
    }

    override fun setPermission(identity: ApprovalBinding, write: PermissionWrite): Boolean = serialized {
        owner == identity && opened && grants.set(identity, write)
    }

    /** Works hidden or visible; the permissions are the live manifest's. */
    override fun readPermissions(identity: ApprovalBinding): Map<PermissionName, PermissionReadback> {
        val live = attempt { manifest(identity.packageName) } ?: return emptyMap()
        return grants.read(identity, live.permissions.map { it.name })
    }

    override fun applyPolicy(plan: PolicyPlan): Boolean = serialized {
        // Discretionary packages this apply could newly make visible and lock-task permitted.
        val discretionary = plan.unhide - plan.neverHide - plan.emergencyUi
        val active = attempt {
            val permitted = port.lockTaskPackages()
            discretionary.filter { it in permitted && port.packageState(it)?.hidden == false }.toSet()
        } ?: emptySet()
        val result = attempt { applier.apply(plan) }
        val done = result != null && !result.needsAttention &&
            plan.expected.lockTaskPackages.let { it == null || attempt { home.publish(it) } != null }
        if (!done) contain(discretionary - active)
        done
    }

    override fun readBackPolicy(plan: PolicyPlan): Boolean = attempt { applier.readBack(plan) } == true

    private fun hide(name: PackageName): Boolean {
        if (!mayHide(name)) return false
        return attempt {
            if (port.packageState(name)?.hidden == false) port.setHidden(name, true)
            port.packageState(name)?.hidden != false
        } == true
    }

    private fun release() {
        owner = null
        opened = false
    }

    /**
     * After a failed apply: takes [names] out of lock task and Home and hides them, reading both back.
     * Best effort; the apply already failed, so the result only says whether containment read back.
     */
    private fun contain(names: Set<PackageName>): Boolean {
        if (names.isEmpty()) return true
        val excluded = attempt {
            val now = port.lockTaskPackages()
            if (now.any { it in names }) port.setLockTaskPackages(now - names)
            port.lockTaskPackages().none { it in names }
        } == true
        names.forEach { attempt { home.remove(it) } }
        return names.map { hide(it) }.all { it } && excluded
    }

    private fun mayHide(name: PackageName): Boolean = attempt { neverHide() }?.let { name !in it } == true

    /** One real flip of the flush component, read back, retried once (mitigation B, as in the applier). */
    private fun flush(): Boolean {
        repeat(2) {
            val before = attempt { port.flushComponentEnabled() } ?: return false
            if (attempt { port.setFlushComponentEnabled(!before); port.flushComponentEnabled() == !before } == true) return true
        }
        return false
    }

    private inline fun serialized(block: () -> Boolean): Boolean = dispatcher.onDispatcher && block()

    private inline fun <T> attempt(call: () -> T): T? = try {
        call()
    } catch (_: RuntimeException) {
        null
    }
}
