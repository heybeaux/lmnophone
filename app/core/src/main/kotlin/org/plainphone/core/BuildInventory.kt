// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

import java.util.Collections

/** Dependencies protected from every destructive package/component operation. */
class NeverHideList(packages: Set<PackageName>) {
    val packages: Set<PackageName> = Collections.unmodifiableSet(packages.toSet())
    operator fun contains(packageName: PackageName): Boolean = packageName in packages
}

/** Compiled, reviewed inventory for one exact model and fingerprint. Not runtime app declarations. */
class BuildInventory(
    val model: String,
    val fingerprint: String,
    val revision: Int,
    val neverHide: NeverHideList,
    minimumUi: Set<PackageName>,
    settingsSessionPackages: Set<PackageName>,
) {
    val minimumUi: Set<PackageName> = Collections.unmodifiableSet(minimumUi.toSet())
    val settingsSessionPackages: Set<PackageName> = Collections.unmodifiableSet(settingsSessionPackages.toSet())
    init {
        require(model.isNotBlank() && fingerprint.isNotBlank() && revision > 0)
        require(neverHide.packages.containsAll(minimumUi)) { "Minimum UI must be protected from sweeping" }
    }
}
