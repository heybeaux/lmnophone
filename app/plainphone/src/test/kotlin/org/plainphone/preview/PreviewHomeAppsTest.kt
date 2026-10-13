// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.device.PreviewAppListStore

class PreviewHomeAppsTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun addingBackAppAppendsAfterCurrentHomeOrderEvenWithStaleOrderEntry() {
        val store = PreviewAppListStore(tmp.newFolder())
        store.saveStarter(listOf("zebra", "camera", "phone"))
        store.saveOrder(listOf("calculator", "zebra"))
        fun home() = PreviewHomeApps.read(store, setOf("phone")) { it }.map { it.first }
        assertTrue(store.add("calculator", home()) { true })
        assertEquals(listOf("zebra", "camera", "calculator"), home())
        assertEquals(home(), store.order())
    }
    @Test fun saveRechecksEligibilityAndFailedSavePreservesBothFiles() {
        val dir = tmp.newFolder(); val store = PreviewAppListStore(dir)
        store.saveStarter(listOf("camera")); store.saveOrder(listOf("camera"))
        assertFalse(store.add("calculator", listOf("camera")) { false })
        val failing = PreviewAppListStore(dir) { _, target ->
            if (target.name == "starter-list.txt") throw java.io.IOException("injected")
            java.nio.file.Files.move(java.io.File(dir, target.name + ".new").toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        }
        assertThrows(java.io.IOException::class.java) { failing.add("calculator", listOf("camera")) { true } }
        assertEquals(setOf("camera"), store.starter()); assertEquals(listOf("camera"), store.order())
    }
    @Test fun productionMapperReadsPersistedOrderAndAppendsNewApps() {
        val dir = tmp.newFolder()
        val store = PreviewAppListStore(dir)
        store.saveStarter(listOf("camera", "calculator", "phone", "sms", "home"))
        store.saveOrder(listOf("camera", "calculator"))
        val labels = mapOf("camera" to "Camera", "calculator" to "Calculator", "alarm" to "Alarm")
        fun home() = PreviewHomeApps.read(PreviewAppListStore(dir), setOf("phone", "sms", "home")) { labels[it] }
        assertEquals(listOf("camera", "calculator"), home().map { it.first })
        store.saveStarter(store.starter().toList() + "alarm")
        assertEquals(listOf("camera", "calculator", "alarm"), home().map { it.first })
        store.saveOrder(listOf("calculator", "camera", "alarm"))
        assertEquals(listOf("calculator", "camera", "alarm"), home().map { it.first })
    }
}
