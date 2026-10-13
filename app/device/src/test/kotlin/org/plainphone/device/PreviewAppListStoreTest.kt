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
    @Test fun removalWritesOnlyTheSelectedAppAndPreservesTheFloor() {
        val store = PreviewAppListStore(tmp.newFolder())
        val floor = setOf("phone", "sms", "home", "keyboard", "settings", "sos")
        store.saveStarter((floor + "calculator").toList())
        floor.forEach { assertFalse(store.remove(it, floor)) }
        assertTrue(store.remove("calculator", floor))
        assertEquals(floor, store.starter())
    }
    @Test fun removingLastOptionalAppRetainsAConfiguredEmptyList() {
        val store = PreviewAppListStore(tmp.newFolder())
        assertFalse(store.configured())
        store.saveStarter(listOf("calculator"))
        assertTrue(store.remove("calculator", setOf("phone", "sms")))
        assertTrue(store.configured())
        assertTrue(store.starter().isEmpty())
    }
    @Test fun failedAtomicReplaceLeavesOldFileUntouched() {
        val dir = tmp.newFolder()
        val store = PreviewAppListStore(dir)
        store.saveStarter(listOf("calculator", "phone"))
        val failing = PreviewAppListStore(dir) { _, _ -> throw java.io.IOException("injected") }
        assertThrows(java.io.IOException::class.java) { failing.remove("calculator", setOf("phone")) }
        assertEquals(setOf("calculator", "phone"), store.starter())
        store.saveOrder(listOf("camera", "calculator"))
        assertThrows(java.io.IOException::class.java) { failing.saveOrder(listOf("calculator", "camera")) }
        assertEquals(listOf("camera", "calculator"), store.order())
    }
}
