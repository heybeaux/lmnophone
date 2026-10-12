// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import org.plainphone.core.PackageName

/**
 * DevicePolicyManager suspension, for [FallbackContainment] only. It is separate from
 * [org.plainphone.device.DevicePolicyPort], which deliberately has no suspend call, so the policy
 * applier still cannot suspend anything.
 */
interface SuspensionPort {
    /** Packages installed for this user, hidden ones included. Throws when it cannot be read. */
    fun installed(): Set<PackageName>
    /** Whether [name] is suspended now (by anyone); null when it is not installed. Throws when unreadable. */
    fun suspended(name: PackageName): Boolean?
    /** `setPackagesSuspended`; returns the packages Android refused. The caller reads the result back. */
    fun setSuspended(names: Set<PackageName>, suspended: Boolean): Set<PackageName>
}

/** The packages fallback containment suspended, in device-protected storage. */
interface SuspensionRecord {
    /** Empty when nothing was ever saved. Throws when a saved record cannot be read. */
    fun read(): Set<PackageName>
    /** True when durably saved. False is not acknowledged. */
    fun save(names: Set<PackageName>): Boolean
}

/**
 * One containment pass.
 * @property suspended packages this containment suspended and read back suspended.
 * @property refused packages it tried to suspend that do not read back suspended: Android refused them
 *   (on API 35, Settings and other `OP_SYSTEM_EXEMPT_FROM_SUSPENSION` packages) or their read failed.
 * @property recorded false when the record could not be read or a new entry was not acknowledged; then
 *   nothing new was suspended.
 */
data class Containment(val suspended: Set<PackageName>, val refused: Set<PackageName>, val recorded: Boolean)

/**
 * device-lockdown, *Fallback containment* (PLA-344; board card `da306a1a` Q1). When the lock-task
 * features cannot be written or do not read back with KEYGUARD, Home stays out of lock task, so other
 * apps are contained by suspension instead:
 * - [contain] suspends every installed package outside `keep` (the lock-task UI list, the emergency
 *   floor, the never-hide dependencies and Home). A package is added to the [record] and the record is
 *   acknowledged before it is suspended; then each is read back. A package already suspended by someone
 *   else is not ours and is never recorded. A recorded package that is now in `keep` is lifted at once.
 * - [liftIfLocked] lifts exactly the recorded set, and only while lock task reads LOCKED and the
 *   features read back with KEYGUARD ([locked] and [keyguard], read right before). An entry leaves the
 *   record only once its lift reads back.
 *
 * A crash between the record and the suspension leaves recorded packages that are not suspended yet:
 * the next [contain] suspends them, and a lift just finds them unsuspended. If the record cannot be
 * read, nothing new is suspended and nothing is lifted (the caller shows attention).
 * Known limit: a package that someone else suspends and that was already in the record is lifted with
 * our suspension; Android keeps a suspension per suspending package, so another suspender's own stays.
 * Run on the one serialized policy dispatcher.
 */
class FallbackContainment(
    private val port: SuspensionPort,
    private val record: SuspensionRecord,
    private val locked: () -> Boolean,
    private val keyguard: () -> Boolean,
) {
    /** False only after a read or a lift found the record empty: a cheap check before queueing a lift. */
    @Volatile var mayHoldSuspensions: Boolean = true
        private set

    fun contain(keep: Set<PackageName>): Containment {
        val saved = attempt { record.read() } ?: return Containment(emptySet(), emptySet(), recorded = false)
        mayHoldSuspensions = true
        // A recorded package that is kept now (newly approved or on the floor) must not stay suspended.
        val release = saved intersect keep
        val ours = (saved - lift(release)).filterNotTo(mutableSetOf(), ::isGone)
        val installed = attempt { port.installed() }
        val fresh = mutableSetOf<PackageName>()
        val unread = mutableSetOf<PackageName>()
        for (name in installed.orEmpty() - keep - ours) when (attempt { port.suspended(name) }) {
            false -> fresh += name
            true -> {} // Suspended by someone else: not ours, never recorded, never lifted.
            null -> unread += name
        }
        // Record first. Without an acknowledged record nothing new is suspended.
        val recorded = (ours == saved && fresh.isEmpty()) || attempt { record.save(ours + fresh) } == true
        // Never (re)suspend a kept package, even one whose lift above did not read back.
        val target = (if (recorded) ours + fresh else ours) - keep
        if (target.isNotEmpty()) attempt { port.setSuspended(target, true) }
        val suspended = target.filterTo(mutableSetOf()) { attempt { port.suspended(it) } == true }
        // A refused package was never suspended by us, so a later lift must not touch it.
        val refusedFresh = (fresh intersect target) - suspended
        if (refusedFresh.isNotEmpty()) attempt { record.save(ours + fresh - refusedFresh) }
        return Containment(suspended, target - suspended + unread, recorded && installed != null)
    }

    /** Null when lock task is not LOCKED with KEYGUARD, the record cannot be read, or a lift does not read back. */
    fun liftIfLocked(): Set<PackageName>? {
        if (attempt { locked() } != true || attempt { keyguard() } != true) return null
        val ours = attempt { record.read() } ?: return null
        if (ours.isEmpty()) {
            mayHoldSuspensions = false
            return emptySet()
        }
        val lifted = lift(ours)
        val left = ours - lifted
        if (attempt { record.save(left) } != true) return null
        mayHoldSuspensions = left.isNotEmpty()
        return lifted.takeIf { left.isEmpty() }
    }

    /** Unsuspends [names]; returns the ones that read back unsuspended or are no longer installed. */
    private fun lift(names: Set<PackageName>): Set<PackageName> {
        if (names.isEmpty()) return emptySet()
        attempt { port.setSuspended(names, false) }
        return names.filterTo(mutableSetOf()) { attempt { port.suspended(it) }.let { it == false } || isGone(it) }
    }

    private fun isGone(name: PackageName): Boolean = try {
        port.suspended(name) == null
    } catch (e: Exception) {
        false
    }

    private inline fun <T> attempt(call: () -> T): T? = try {
        call()
    } catch (e: Exception) {
        null
    }
}
