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
 * [publish], from a complete policy apply, can list an app again. The one exception is [relist].
 */
interface HomeAppList {
    fun remove(name: PackageName)
    /** Whether Home lists [name] now; null when it cannot be read. */
    fun lists(name: PackageName): Boolean?
    /** Lists exactly [names] (the committed policy's lock-task packages). */
    fun publish(names: Set<PackageName>)
    /**
     * Lists [name] again after a call of [DeviceApprovalEffects] removed it and the live never-hide list
     * then turned out to protect it (PLA-276 finding 2). Throws where the list cannot do that.
     */
    fun relist(name: PackageName): Unit = throw UnsupportedOperationException("relist")
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
    override fun relist(name: PackageName) { listed = listed + name }
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
 * event handler on the same dispatcher cannot re-hide the app inside the window. Nothing is suspended:
 * a suspended app misreports sensor grants, so open fails for one.
 *
 * Floor consistency (PLA-276 finding 2). Within one call (window open, hide or close, containment) the
 * live never-hide list is read again before every destructive write (lock-task list, Home removal, hide)
 * and once after the last one. A name it protects at any of those reads is not touched from then on,
 * and what this call took from it (lock-task membership, Home listing, visibility) is given back; nothing
 * else is added, so the rest of the list never enters lock task or Home this way. The call then fails.
 * An unreadable read refuses every later write and fails the call, and only names a read proved protected
 * are given anything back. A change after the final read is for the next call: the next apply repairs the
 * floor, and every later hide reads the list again.
 *
 * Positive evidence only (PLA-313 finding 4). A step records something as taken only after a read proved
 * the name had it: in the read lock-task list, read back visible, or read listed on Home. An unreadable
 * prior read (null or throwing) is never recorded, so the repair cannot add what the call never removed.
 * Home is the one step whose prior read can be unknown while the write would still go ahead, so it skips
 * the removal instead: open fails before it, and containment reports [Exposure.HOME]. A removal that throws
 * after a positive read stays recorded, since it may have taken effect.
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
 * verified stays in [unresolvedCleanup]; a failed apply alone does not prove containment. Each later
 * failed apply contains those names again, even when its plan no longer names them, and drops a name
 * only when all three read back closed (PLA-276 finding 3).
 * [containFailedActivation] runs the same containment for the packages policy names after a failed
 * final activation (PLA-234), including approved apps that were active before it. Policy also calls it
 * before its retry and startup gates with the durable pending target alone, with no plan and possibly
 * without policy facts (PLA-278), so the live floor guard is the only guard there: a protected name is
 * never touched and the call fails.
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
     * Each contained package whose exclusion has not read back since a failed apply left it open, with
     * the paths still open; empty when all were verified. Carried through later failed applies until it
     * reads back closed; a complete apply, read back, clears it.
     */
    @Volatile var unresolvedCleanup: Map<PackageName, Set<Exposure>> = emptyMap()
        private set

    override fun hideAndReadBack(identity: ApprovalBinding): Boolean = serialized {
        hide(identity.packageName).also { if (it && identity == owner) release() }
    }

    override fun openPermissionWindow(identity: ApprovalBinding): Boolean = serialized {
        if (owner != null) return@serialized false
        val name = identity.packageName
        val floor = Floor()
        if (!floor.allows(name)) return@serialized false
        owner = identity
        val taken = Taken()
        opened = attempt {
            val before = port.lockTaskPackages()
            if (name in before) {
                if (!floor.allows(name)) return@attempt false
                taken.lockTask += name
                port.setLockTaskPackages(before - name)
            }
            val applied = port.lockTaskPackages()
            if (applied != before - name) return@attempt false
            // Effective exclusion: every package left in lock task has a known UID, and none is the candidate's.
            val uid = port.uid(name) ?: return@attempt false
            val others = applied.map { port.uid(it) ?: return@attempt false }
            if (uid in others) return@attempt false
            if (!floor.allows(name)) return@attempt false
            // Only a positive read is a removal this call can give back; unknown is never a listing (PLA-313).
            val listed = attempt { home.lists(name) } ?: return@attempt false
            if (listed) {
                taken.home += name
                home.remove(name)
            }
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
        if (!settle(floor, taken) || name in floor.protected) opened = false
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
        // What an earlier failed apply left open is never already active, and is contained again even
        // when this plan no longer names it.
        val carried = unresolvedCleanup.keys
        val active = attempt {
            val permitted = port.lockTaskPackages()
            discretionary.filter { it in permitted && port.packageState(it)?.hidden == false }.toSet()
        }.orEmpty() - carried
        val result = attempt { applier.apply(plan) }
        val done = result != null && !result.needsAttention &&
            plan.expected.lockTaskPackages.let { it == null || attempt { home.publish(it) } != null }
        unresolvedCleanup = if (done) emptyMap() else contain(discretionary - active + carried)
        done
    }

    override fun readBackPolicy(plan: PolicyPlan): Boolean = attempt { applier.readBack(plan) } == true

    /**
     * True only when every name in [packages] reads back hidden, out of lock task and off Home. A name the
     * live never-hide list protects (it holds the emergency UI) at any read during the call is not touched,
     * or gets back what the call took from it, and makes this false.
     */
    override fun containFailedActivation(packages: Set<PackageName>): Boolean = serialized {
        attempt { contain(packages).isEmpty() } == true
    }

    /** Hides [name] and reads it back, as one call under the floor-consistency boundary. */
    private fun hide(name: PackageName): Boolean {
        val floor = Floor()
        val taken = Taken()
        val hidden = hide(name, floor, taken)
        return settle(floor, taken) && name !in floor.protected && hidden
    }

    /** The hide step of a larger call: [floor] is read just before the write, which is recorded in [taken]. */
    private fun hide(name: PackageName, floor: Floor, taken: Taken): Boolean {
        if (!floor.allows(name)) return false
        return attempt {
            if (port.packageState(name)?.hidden == false) {
                taken.visibility += name
                port.setHidden(name, true)
            }
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
     * back as closed; empty means containment is verified. The live never-hide list is read before each
     * write and once after the last (see [Floor]): a name it protects at any read, or every name when a
     * read fails, is reported with all three exposures, and a protected name gets back what this took.
     */
    private fun contain(names: Set<PackageName>): Map<PackageName, Set<Exposure>> {
        if (names.isEmpty()) return emptyMap()
        val floor = Floor()
        val taken = Taken()
        attempt {
            val now = port.lockTaskPackages()
            val out = floor.allowed(names) intersect now
            if (out.isNotEmpty()) {
                taken.lockTask += out
                port.setLockTaskPackages(now - out)
            }
        }
        val lockTask = attempt { port.lockTaskPackages() }
        val open = names.associateWith { name ->
            if (!floor.allows(name)) return@associateWith Exposure.entries.toSet()
            // An unreadable listing skips the removal (nothing to give back) and stays reported (PLA-313).
            val listed = attempt { home.lists(name) }
            if (listed == true) {
                taken.home += name
                attempt { home.remove(name) }
            }
            buildSet {
                if (lockTask == null || name in lockTask) add(Exposure.LOCK_TASK)
                if (listed == null || attempt { home.lists(name) } != false) add(Exposure.HOME)
                if (!hide(name, floor, taken)) add(Exposure.VISIBLE)
            }
        }
        val settled = settle(floor, taken)
        return open.mapValues { (name, paths) ->
            if (!settled || name in floor.protected) Exposure.entries.toSet() else paths
        }.filterValues { it.isNotEmpty() }
    }

    /**
     * The live never-hide list during one call (the floor-consistency boundary, PLA-276 finding 2). It is
     * read again by every [allows] and [allowed], just before a destructive write. A name it protected at
     * any read stays in [protected] for the rest of the call; after one unreadable read nothing is allowed.
     */
    private inner class Floor {
        val protected = mutableSetOf<PackageName>()
        var readable = true
            private set

        fun read() {
            val live = attempt { neverHide() }
            if (live == null) readable = false else protected += live
        }
        fun allowed(names: Set<PackageName>): Set<PackageName> {
            read()
            return if (readable) names - protected else emptySet()
        }
        fun allows(name: PackageName): Boolean = name in allowed(setOf(name))
    }

    /** What one call took from each name, so a name the floor turns out to protect gets exactly that back. */
    private class Taken {
        val lockTask = mutableSetOf<PackageName>()
        val home = mutableSetOf<PackageName>()
        val visibility = mutableSetOf<PackageName>()
    }

    /**
     * The end of a call: reads the floor once more, then gives each name it protects what [taken] holds for
     * it, and nothing more. False when any read in the call failed.
     */
    private fun settle(floor: Floor, taken: Taken): Boolean {
        floor.read()
        val lockTask = taken.lockTask intersect floor.protected
        if (lockTask.isNotEmpty()) attempt {
            val now = port.lockTaskPackages()
            if (!now.containsAll(lockTask)) port.setLockTaskPackages(now + lockTask)
        }
        (taken.home intersect floor.protected).forEach { attempt { home.relist(it) } }
        val shown = taken.visibility intersect floor.protected
        shown.forEach { attempt { port.setHidden(it, false) } }
        if (shown.isNotEmpty()) flush()
        return floor.readable
    }

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
