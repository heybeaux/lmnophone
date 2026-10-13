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
