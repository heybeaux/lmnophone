// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreviewAppListStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    @Test fun orderAndStarterSurviveNewStoreAndNewAppsAppend() {
        val dir = tmp.newFolder()
        val store = PreviewAppListStore(dir)
        store.saveStarter(listOf("camera", "calculator", "phone"))
        store.saveOrder(listOf("camera", "calculator"))
        val reopened = PreviewAppListStore(dir)
        assertEquals(listOf("camera", "calculator"), reopened.order())
        assertEquals(setOf("camera", "calculator", "phone"), reopened.starter())
        reopened.saveStarter(listOf("camera", "calculator", "phone", "alarm"))
        assertEquals(listOf("camera", "calculator"), store.order())
    }
    @Test fun failedAtomicReplaceLeavesOldFileUntouched() {
        val dir = tmp.newFolder()
        val store = PreviewAppListStore(dir)
        store.saveStarter(listOf("calculator", "phone"))
        val failing = PreviewAppListStore(dir) { _, _ -> throw java.io.IOException("injected") }
        assertThrows(java.io.IOException::class.java) { failing.saveStarter(listOf("phone")) }
        assertEquals(setOf("calculator", "phone"), store.starter())
        store.saveOrder(listOf("camera", "calculator"))
        assertThrows(java.io.IOException::class.java) { failing.saveOrder(listOf("calculator", "camera")) }
        assertEquals(listOf("camera", "calculator"), store.order())
    }
}
