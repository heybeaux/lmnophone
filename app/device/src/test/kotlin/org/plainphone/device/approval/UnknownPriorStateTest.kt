// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.DevicePolicyPort
import org.plainphone.device.PackageState

/**
 * PLA-313 finding 4: a call gives back only what a positive read proved it took. A prior-state read
 * that is unreadable (null or throwing) is never authority to put a package on Home, into lock task, or
 * on screen, even when the live floor protects the package from that read on.
 *
 * Each case: the target is absent from the path under test, the first read of that path for it is
 * unreadable, and the floor promotes the target at that very read. The call fails, and afterwards the
 * target is still absent from the path. `lockTaskPackages()` returns a non-null set, so lock task has
 * the throwing case only.
 */
class UnknownPriorStateTest {
    private enum class Read { HOME, LOCK_TASK, VISIBILITY }
    private enum class Unreadable { NULL, THROWS }
    private enum class Call { OPEN, CONTAIN, HIDE }

    /** One call on a fresh harness; null when it held, otherwise what went wrong. */
    private fun run(read: Read, unreadable: Unreadable, call: Call): String? = ApprovalHarness().use { h ->
        val target = h.target
        val base = h.neverHide()!!
        var promoted = false
        var pending = true
        /** The first [read] of the target: promotes it and reads as unknown. */
        fun firstRead(path: Read, name: PackageName?): Boolean {
            if (path != read || !pending || (name != null && name != target)) return false
            pending = false
            promoted = true
            if (unreadable == Unreadable.THROWS) error("unreadable")
            return true
        }
        val port = object : DevicePolicyPort by h.port {
            override fun packageState(name: PackageName): PackageState? =
                if (firstRead(Read.VISIBILITY, name)) null else h.port.packageState(name)
            override fun lockTaskPackages(): Set<PackageName> {
                firstRead(Read.LOCK_TASK, null)
                return h.port.lockTaskPackages()
            }
        }
        val home = object : HomeAppList by h.home {
            override fun lists(name: PackageName): Boolean? = if (firstRead(Read.HOME, name)) null else h.home.lists(name)
        }
        // HOME: active but off Home. LOCK_TASK: visible, outside lock task. VISIBILITY: hidden and closed.
        h.port.setHidden(target, read == Read.VISIBILITY)
        h.port.lockTask = if (read == Read.HOME) h.port.lockTask + target else h.port.lockTask - target
        h.home.publish(h.port.lockTask - target)
        val effects = DeviceApprovalEffects(h.dispatcher, port, h.grants, home,
            { base + if (promoted) setOf(target) else emptySet() }) { null }
        val result = h.dispatcher.run {
            when (call) {
                Call.OPEN -> effects.openPermissionWindow(h.identity)
                Call.CONTAIN -> effects.containFailedActivation(setOf(target))
                Call.HIDE -> effects.hideAndReadBack(h.identity)
            }
        }
        val wrong = buildList {
            if (pending) add("the unreadable read never happened")
            if (result) add("the call succeeded")
            if (h.home.lists(target)) add("listed on Home")
            when (read) {
                Read.HOME -> if (target !in h.port.lockTask) add("the lock-task membership it took was not given back")
                else -> if (target in h.port.lockTask) add("added to lock task")
            }
            if (read == Read.VISIBILITY && !h.port.hidden(target.value)) add("shown")
        }
        wrong.takeIf { it.isNotEmpty() }?.let { "$read $unreadable $call: $it (${h.state()})" }
    }

    private fun check(read: Read, vararg calls: Call, kinds: List<Unreadable> = Unreadable.entries) {
        val failures = calls.flatMap { call -> kinds.mapNotNull { run(read, it, call) } }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun anUnreadableHomeReadNeverListsOnOpen() = check(Read.HOME, Call.OPEN)
    @Test fun anUnreadableHomeReadNeverListsOnContainment() = check(Read.HOME, Call.CONTAIN)
    @Test fun anUnreadableLockTaskReadNeverPermitsOnOpen() = check(Read.LOCK_TASK, Call.OPEN, kinds = listOf(Unreadable.THROWS))
    @Test fun anUnreadableLockTaskReadNeverPermitsOnContainment() = check(Read.LOCK_TASK, Call.CONTAIN, kinds = listOf(Unreadable.THROWS))
    @Test fun anUnreadableVisibilityReadNeverShowsOnOpen() = check(Read.VISIBILITY, Call.OPEN)
    @Test fun anUnreadableVisibilityReadNeverShowsOnContainment() = check(Read.VISIBILITY, Call.CONTAIN)
    @Test fun anUnreadableVisibilityReadNeverShowsOnHide() = check(Read.VISIBILITY, Call.HIDE)
}
