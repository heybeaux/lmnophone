// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName

/** Which packages to hide as browsers. Never-hide conflicts are flagged, never hidden. */
class Classification(val hide: Set<PackageName>, val conflicts: Set<PackageName>)

fun interface BrowserClassifier {
    fun classify(
        facts: List<PackageFacts>,
        catalog: BrowserCatalog,
        neverHide: NeverHideList,
        inventoryKnown: Boolean,
    ): Classification
}
