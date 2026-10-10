// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.Restriction

/**
 * In-memory [DevicePolicyPort]. Every write is logged in [ops] before it changes state, so tests
 * can check order. Knobs make writes silently not stick (a failed readback) or throw.
 */
class FakeDevicePolicyPort(
    packages: Map<String, PackageState> = emptyMap(),
    var flushComponent: Boolean? = false,
) : DevicePolicyPort {
    val state = packages.mapKeys { PackageName(it.key) }.toMutableMap()
    val restrictionSet = mutableSetOf<Restriction>()
    var inputMethodsSystemOnly = false
    var accessibilitySystemOnly = false
    var lockTask = setOf<PackageName>()
    var features = setOf<LockTaskFeature>()
    var homePackage: PackageName? = null

    val ops = mutableListOf<String>()
    /** Flip targets in call order, with the component state seen just before each call. */
    val flips = mutableListOf<Pair<Boolean?, Boolean>>()

    var restrictionsStick = true
    /** Restriction keys this simulated build does not know: adding them is silently ignored. */
    val unknownRestrictions = mutableSetOf<Restriction>()
    var hidesStick = true
    var lockTaskSticks = true
    /** How many flip calls in a row are ignored (the state does not change). */
    var flipsIgnored = 0
    /** Areas whose reads or writes throw, to prove a step never touched them. */
    val forbidden = mutableSetOf<String>()
    var throwOnWrite: String? = null

    private fun touch(area: String) {
        if (area in forbidden) throw AssertionError("touched $area")
    }
    private fun write(area: String, op: String) {
        touch(area)
        ops += op
        if (throwOnWrite == area) throw IllegalStateException("write $area failed")
    }

    override fun packageState(name: PackageName): PackageState? { touch("packages"); return state[name] }
    override fun setHidden(name: PackageName, hidden: Boolean) {
        write("packages", (if (hidden) "hide " else "unhide ") + name.value)
        val now = state[name] ?: return
        if (hidesStick || !hidden) state[name] = now.copy(hidden = hidden)
    }
    override fun unsuspend(name: PackageName) {
        write("packages", "unsuspend ${name.value}")
        state[name]?.let { state[name] = it.copy(suspended = false) }
    }
    /**
     * Whether [enable] clears a disabled state. Off by default, matching what PLA-232 observed on API 35
     * (enableSystemApp left a user-disabled Chrome at state 3). A fake cannot prove Android behaviour;
     * that is checked on the emulator (DisabledPackageCheck).
     */
    var enableRepairs = false
    override fun enable(name: PackageName) {
        write("packages", "enable ${name.value}")
        if (enableRepairs) state[name]?.let { state[name] = it.copy(enabled = true) }
    }

    override fun restrictions(): Set<Restriction> { touch("restrictions"); return restrictionSet.toSet() }
    override fun addRestriction(restriction: Restriction) {
        write("restrictions", "restrict ${restriction.name}")
        if (restrictionsStick && restriction !in unknownRestrictions) restrictionSet += restriction
    }
    override fun systemInputMethodsOnly(): Boolean { touch("inputMethods"); return inputMethodsSystemOnly }
    override fun setSystemInputMethodsOnly(only: Boolean) {
        write("inputMethods", "inputMethods $only"); inputMethodsSystemOnly = only
    }
    override fun systemAccessibilityServicesOnly(): Boolean { touch("accessibility"); return accessibilitySystemOnly }
    override fun setSystemAccessibilityServicesOnly(only: Boolean) {
        write("accessibility", "accessibility $only"); accessibilitySystemOnly = only
    }

    override fun lockTaskPackages(): Set<PackageName> { touch("lockTask"); return lockTask }
    override fun setLockTaskPackages(packages: Set<PackageName>) {
        write("lockTask", "lockTask " + packages.map { it.value }.sorted().joinToString(","))
        if (lockTaskSticks) lockTask = packages.toSet()
    }
    override fun lockTaskFeatures(): Set<LockTaskFeature> { touch("features"); return features }
    override fun setLockTaskFeatures(features: Set<LockTaskFeature>) {
        write("features", "features ${features.size}"); this.features = features.toSet()
    }

    override fun home(): PackageName? { touch("home"); return homePackage }
    override fun setHome(name: PackageName) { write("home", "home ${name.value}"); homePackage = name }

    /**
     * App UIDs; a package without an entry gets its own. Packages in [uidUnreadable] throw; packages in
     * [uidAbsent] read as not installed (null), as `DpmPort.uid` does for an absent package.
     */
    val uids = mutableMapOf<PackageName, Int>()
    val uidUnreadable = mutableSetOf<PackageName>()
    val uidAbsent = mutableSetOf<PackageName>()
    private val assigned = mutableMapOf<PackageName, Int>()
    override fun uid(name: PackageName): Int? {
        touch("uid")
        if (name in uidUnreadable) throw IllegalStateException("uid of ${name.value} unreadable")
        if (name !in state || name in uidAbsent) return null
        return uids[name] ?: assigned.getOrPut(name) { 10_000 + assigned.size }
    }

    override fun flushComponentEnabled(): Boolean? { touch("flush"); return flushComponent }
    override fun setFlushComponentEnabled(enabled: Boolean) {
        write("flush", "flip $enabled")
        flips += flushComponent to enabled
        if (flipsIgnored > 0) flipsIgnored-- else flushComponent = enabled
    }

    fun hidden(name: String) = state.getValue(PackageName(name)).hidden
}

fun visible() = PackageState(hidden = false, suspended = false, enabled = true)
fun hiddenApp() = PackageState(hidden = true, suspended = false, enabled = true)
