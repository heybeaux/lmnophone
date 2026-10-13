// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.links.*
import org.plainphone.policy.*

class PreviewAddEligibilityTest {
    @get:Rule val tmp = TemporaryFolder()
    private val good = PreviewAddFacts(true, true, true, true, true)
    @Test fun everyRefusalIsRecheckedInsideStoreBeforeWriting() {
        val dir = tmp.newFolder(); val store = PreviewAppListStore(dir)
        store.saveStarter(listOf("camera")); store.saveOrder(listOf("camera"))
        val refusals = mapOf("absent" to good.copy(installed = false),
            "not launchable" to good.copy(launchable = false), "not hidden" to good.copy(hidden = false),
            "unreadable identity" to good.copy(readable = false), "browser" to good.copy(browserAllowed = false),
            "settings floor permission controller system UI" to good.copy(protected = true),
            "installer" to good.copy(installer = true), "store" to good.copy(store = true),
            "home" to good.copy(home = true), "LMNOPhone" to good.copy(self = true))
        refusals.forEach { (name, changed) ->
            assertTrue(PreviewAppRules.canAdd("calculator", store.starter(), good)) // list-time facts
            assertFalse(name, store.add("calculator", listOf("camera")) {
                PreviewAppRules.canAdd("calculator", store.starter(), changed)
            })
            assertEquals(setOf("camera"), store.starter()); assertEquals(listOf("camera"), store.order())
        }
        assertThrows(IllegalStateException::class.java) { store.add("calculator", listOf("camera")) { error("unreadable source") } }
        assertEquals(setOf("camera"), store.starter()); assertEquals(listOf("camera"), store.order())
    }
    @Test fun browserClassifierRejectsProbeCatalogAndStoreEvenOnUnknownBuild() {
        val signer = setOf(CertDigest("a".repeat(64)))
        val classifier = BrowserClassifier(PlainphoneIdentity(PackageName("org.plainphone"), signer))
        val catalog = BrowserCatalog.parse("version=1\ncom.example.catalog\tBROWSER\tEMULATOR\tfixture\tfixture\ncom.example.store\tAPP_STORE\tEMULATOR\tfixture\tfixture")
        val packages = listOf(PackageFacts(PackageName("com.example.probe"), signer, false, true, true),
            PackageFacts(PackageName("com.example.catalog"), signer, true, false, false),
            PackageFacts(PackageName("com.example.store"), signer, false, false, false))
        classifier.classify(packages, catalog, NeverHideList(emptySet()), false).results.forEach { result ->
            val allowed = result.verdict in setOf(Verdict.ALLOWED, Verdict.FLAG_REVIEW)
            assertFalse(PreviewAppRules.canAdd(result.packageName.value, emptySet(), good.copy(browserAllowed = allowed)))
        }
    }
}
