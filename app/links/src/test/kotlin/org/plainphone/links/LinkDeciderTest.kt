// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.links

import org.plainphone.core.PackageName
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkDeciderTest {
    @Test
    fun openInCarriesTheTargetPackage() {
        val maps = PackageName("com.example.maps")
        val decider = LinkDecider { _, _ -> LinkDecision.OpenIn(maps) }
        val intent = IntentFacts("android.intent.action.VIEW", "https", "example.com", emptyList())
        assertEquals(LinkDecision.OpenIn(maps), decider.decide(intent, setOf(maps)))
    }
}
