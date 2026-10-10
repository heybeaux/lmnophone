// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.plainphone.core.PackageName
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.Restriction

/** A package's policy-relevant state in the current user. */
data class PackageState(val hidden: Boolean, val suspended: Boolean, val enabled: Boolean)

/**
 * The DevicePolicyManager calls [ReadbackPolicyApplier] needs, as a Device Owner in the current user.
 * Writes may throw; reads report what Android holds now. The port has no hide-only, suspend,
 * disable or uninstall call beyond [setHidden], so the applier cannot run destruction.
 */
interface DevicePolicyPort {
    /** Null when the package is not installed for this user. */
    fun packageState(name: PackageName): PackageState?
    fun setHidden(name: PackageName, hidden: Boolean)
    fun unsuspend(name: PackageName)
    /** Best-effort enable. It may not clear a user-disabled state; the caller reads the state again. */
    fun enable(name: PackageName)

    fun restrictions(): Set<Restriction>
    fun addRestriction(restriction: Restriction)
    fun systemInputMethodsOnly(): Boolean
    fun setSystemInputMethodsOnly(only: Boolean)
    fun systemAccessibilityServicesOnly(): Boolean
    fun setSystemAccessibilityServicesOnly(only: Boolean)

    /**
     * The app UID of [name] in this user, hidden packages included (`MATCH_UNINSTALLED_PACKAGES`); null
     * when it is not installed for this user. Throws when it cannot be read. Android lets every package
     * that shares a UID with an allowed lock-task package start lock task too.
     */
    fun uid(name: PackageName): Int?

    fun lockTaskPackages(): Set<PackageName>
    fun setLockTaskPackages(packages: Set<PackageName>)
    fun lockTaskFeatures(): Set<LockTaskFeature>
    fun setLockTaskFeatures(features: Set<LockTaskFeature>)

    /** The package that answers the Home intent now, or null if none or a chooser would show. */
    fun home(): PackageName?
    fun setHome(name: PackageName)

    /** Enabled state of Plainphone's own no-op flush component; null if it cannot be read. */
    fun flushComponentEnabled(): Boolean?
    /** Sets it with `DONT_KILL_APP | SYNCHRONOUS` in the same user (mitigation B). */
    fun setFlushComponentEnabled(enabled: Boolean)
}
