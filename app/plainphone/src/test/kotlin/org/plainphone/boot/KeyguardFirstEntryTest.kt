// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.ui.home.HomeNotice
import org.w3c.dom.Element

/**
 * device-lockdown, "Keyguard first" (PLA-264 finding 1). Lock-task entry is explicit: no activity may
 * enter lock task from the manifest, because Android would start it before the re-apply restored KEYGUARD.
 * When the re-apply cannot finish (for example, features that do not read back), the screen Home shows
 * after unlock says LMNOPhone needs attention.
 */
class KeyguardFirstEntryTest {
    @Test fun noActivityEntersLockTaskFromTheManifest() {
        val manifest = File("src/main/AndroidManifest.xml")
        assertTrue("run from the module directory: ${manifest.absolutePath}", manifest.exists())
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val activities = factory.newDocumentBuilder().parse(manifest).getElementsByTagName("activity")
        assertTrue(activities.length > 0)
        for (i in 0 until activities.length) {
            val activity = activities.item(i) as Element
            val mode = activity.getAttributeNS(ANDROID, "lockTaskMode")
            assertEquals("${activity.getAttributeNS(ANDROID, "name")} lockTaskMode", "normal", mode.ifEmpty { "normal" })
        }
    }

    @Test fun theCheckingScreenSaysNeedsAttentionOnlyWhenTheReapplyIsNotReady() {
        assertTrue(checkingShowsAttention(HomeGate.NOT_READY, HomeNotice.NeedsAttention))
        assertFalse(checkingShowsAttention(HomeGate.CHECKING, HomeNotice.NeedsAttention))
        assertFalse(checkingShowsAttention(HomeGate.NOT_READY, null))
        assertFalse("READY draws Home, whose own notice shows", checkingShowsAttention(HomeGate.READY, HomeNotice.NeedsAttention))
    }

    private companion object {
        const val ANDROID = "http://schemas.android.com/apk/res/android"
    }
}
