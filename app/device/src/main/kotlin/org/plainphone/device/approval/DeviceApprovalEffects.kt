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

/** An access path containment could not verify as closed for a package. */
enum class Exposure {
    /** Not read back hidden. */
    VISIBLE,
    /** Not read back out of the applied lock-task list. */
    LOCK_TASK,
    /** Not read back off Home's list. */
    HOME,
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
 * applied list, open refuses, before any unhide, unless the candidate and every package left in that
 * list resolve to a UID and none equals the candidate's. A UID that reads as absent (null) or throws
 * proves nothing, so it refuses too (PLA-229). It never removes the sibling or the floor.
 *
 * One window at a time. Open claims the window for its identity before its first write, and keeps it
 * even when it fails part-way; a second open is refused and touches nothing. Only the owner's close or
 * hide, read back hidden, frees it. A close for another identity still hides its own package but
 * returns false and leaves the window with its owner.
 *
 * A failed [applyPolicy] leaves no discretionary package visible and in lock task that was not both
 * before it: each such package is taken out of lock task and Home, hidden, and all three are read back.
 * Floor packages (never-hide and emergency UI) are left as the apply repaired them. What could not be
 * verified stays in [unresolvedCleanup]; a failed apply alone does not prove containment.
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

    /**
     * After the last failed apply, each contained package whose exclusion did not read back, with the
     * paths still open; empty when all were verified. A complete apply, read back, clears it.
     */
    @Volatile var unresolvedCleanup: Map<PackageName, Set<Exposure>> = emptyMap()
        private set

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
            // Effective exclusion: every package left in lock task has a known UID, and none is the candidate's.
            val uid = port.uid(name) ?: return@attempt false
            val others = applied.map { port.uid(it) ?: return@attempt false }
            if (uid in others) return@attempt false
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
        unresolvedCleanup = if (done) emptyMap() else contain(discretionary - active)
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
     * Takes [names] out of lock task and Home and hides them, then reads all three back for each name.
     * Every step is attempted even after an earlier one fails. Returns the exposures that did not read
     * back as closed; empty means containment is verified. The caller excludes floor packages; [hide]
     * also checks the live never-hide list, so a protected name is reported VISIBLE, never hidden.
     */
    private fun contain(names: Set<PackageName>): Map<PackageName, Set<Exposure>> {
        if (names.isEmpty()) return emptyMap()
        attempt {
            val now = port.lockTaskPackages()
            if (now.any { it in names }) port.setLockTaskPackages(now - names)
        }
        val lockTask = attempt { port.lockTaskPackages() }
        return names.associateWith { name ->
            attempt { home.remove(name) }
            buildSet {
                if (lockTask == null || name in lockTask) add(Exposure.LOCK_TASK)
                if (attempt { home.lists(name) } != false) add(Exposure.HOME)
                if (!hide(name)) add(Exposure.VISIBLE)
            }
        }.filterValues { it.isNotEmpty() }
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
