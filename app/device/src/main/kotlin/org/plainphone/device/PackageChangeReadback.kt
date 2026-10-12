// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName
import org.plainphone.policy.PolicyPlan

/**
 * Read-only, process-local evidence. Capture, invalidation and targeted reads are dispatcher-confined.
 * The immutable snapshot is published atomically; broadcast receipt may read [hasSnapshot] off-thread.
 * Caller must separately require a verified full unlocked pass.
 */
class PackageChangeReadback(
    private val port: DevicePolicyPort,
    private val liveProtected: () -> Set<PackageName>,
    private val launchable: (PackageName) -> Boolean,
    private val journalClear: () -> Boolean,
) {
    private data class Snapshot(val states: Map<PackageName, PackageState>, val lockTask: Set<PackageName>)
    @Volatile private var snapshot: Snapshot? = null
    val hasSnapshot: Boolean get() = snapshot != null

    fun invalidate() { snapshot = null }

    fun capture(systemPackages: Set<PackageName>, plan: PolicyPlan, sessionPackages: Set<PackageName>) =
        capture(systemPackages, plan.hidden,
            plan.neverHide + plan.emergencyUi + plan.lockTaskPackages + sessionPackages +
                listOfNotNull(plan.expected.home), plan.lockTaskPackages)

    /** Only system packages from the fresh plan inputs, excluding every protected/session package. */
    fun capture(systemPackages: Set<PackageName>, hidden: Set<PackageName>,
                excluded: Set<PackageName>, expectedLockTask: Set<PackageName>) {
        invalidate()
        try {
            if (!journalClear() || port.lockTaskPackages() != expectedLockTask) return
            val protected = excluded + liveProtected() + expectedLockTask
            val snapshot = mutableMapOf<PackageName, PackageState>()
            for (name in systemPackages - protected) {
                // One unreadable package loses only its own eligibility.
                val state = try { port.packageState(name) } catch (_: Exception) { null }
                if (state != null && state.hidden == (name in hidden)) snapshot[name] = state
            }
            this.snapshot = Snapshot(snapshot.toMap(), expectedLockTask.toSet())
        } catch (_: Exception) { invalidate() }
    }

    /** Null/throw/unknown/mismatch is not proof. This never writes or grants access. */
    fun unchanged(name: PackageName): Boolean = try {
        val captured = snapshot
        val before = captured?.states?.get(name)
        before != null && journalClear() && name !in liveProtected() &&
            port.lockTaskPackages() == captured.lockTask && name !in captured.lockTask &&
            port.packageState(name) == before &&
            // Component enable changes can create a new launcher without changing app-level flags.
            // A hidden app remains contained regardless of launcher changes.
            (before.hidden || !launchable(name))
    } catch (_: Exception) { false }
}
