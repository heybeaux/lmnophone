// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.*
import org.junit.Test
import org.w3c.dom.Element

class GiveBackHomeManifestTest {
    @Test fun homeCanBeDisabledWithoutRemovingTheLauncherAndRetryActivity() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = doc.getElementsByTagName("activity")
        val main = (0 until activities.length).map { activities.item(it) as Element }
            .single { it.getAttribute("android:name") == ".MainActivity" }
        fun categories(e: Element): List<String> = e.getElementsByTagName("category").let { nodes ->
            (0 until nodes.length).map { (nodes.item(it) as Element).getAttribute("android:name") }
        }
        assertTrue("android.intent.category.LAUNCHER" in categories(main))
        assertFalse("android.intent.category.HOME" in categories(main))
        val aliases = doc.getElementsByTagName("activity-alias")
        val home = (0 until aliases.length).map { aliases.item(it) as Element }
            .single { it.getAttribute("android:name") == ".HomeEntry" }
        assertEquals(".MainActivity", home.getAttribute("android:targetActivity"))
        assertTrue("android.intent.category.HOME" in categories(home))
    }
}
