// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

/** Packages that must never be hidden, because emergency calling and texting depend on them. */
class NeverHideList(val packages: Set<PackageName>) {
    operator fun contains(packageName: PackageName): Boolean = packageName in packages
}

/** Reviewed inventory for one exact phone model and build fingerprint, compiled into the APK. */
class BuildInventory(
    val model: String,
    val fingerprint: String,
    val neverHide: NeverHideList,
    val settingsSessionPackages: Set<PackageName>,
)
