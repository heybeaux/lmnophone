// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.ReapplyTimings
import org.plainphone.device.facts.*

class PreviewPackageInputTest {
    private class Port : PackageSystemPort {
        var reads = 0
        override fun packages(): List<RawPackage> {
            val first = reads++ == 0
            return listOf(
                RawPackage("org.plainphone", false, 9, null),
                RawPackage("com.example.dialer", true, 1, null, false),
                RawPackage("com.example.disabled", false, 1, null, false),
                RawPackage("com.example.changing", first, if (first) 1 else 2, null, !first),
            )
        }
        override fun model() = "test"
        override fun fingerprint() = "test"
        override fun browsableHandlers(url: String) = emptySet<String>()
        override fun systemDialer() = "com.example.dialer"
        override fun defaultDialer() = systemDialer()
        override fun defaultSms(): String? = null
        override fun webViewProvider(): String? = null
        override fun enabledInputMethods() = emptySet<String>()
        override fun systemInCallUiPackages() = setOf("com.example.dialer")
        override fun systemOnlyActivities(action: String, data: String?) = emptySet<String>()
        override fun systemOnlyReceivers(action: String) = emptySet<String>()
    }

    @Test fun previewReceivesTheSameFilteredInputAndPlansLockedAndUnlocked() {
        val fullPort = Port()
        lateinit var floor: FloorRead
        val full = LivePackageReader(fullPort).read { floor = FloorIdentityReader(fullPort).read() }
        val floorNames = floor.identities.map { it.name }.toSet()
        val expected = full.installed.filter { it.name !in full.disabled || it.name in floorNames }
        val light = PreviewPackageInput.read(Port(), ReapplyTimings())
        assertEquals(expected, light.installed)
        assertEquals(floor, light.floor)
        assertTrue(light.installed.any { it.name.value == "com.example.dialer" })
        assertFalse(light.installed.any { it.name.value == "com.example.disabled" })
        val self = PackageName("org.plainphone")
        val launchable = full.installed.map { it.name }.toSet()
        val starter = setOf(PackageName("com.example.changing"))
        assertEquals(Preview2Plan.decideLocked(self, expected, floor.identities),
            Preview2Plan.decideLocked(self, light.installed, light.floor.identities))
        assertEquals(Preview2Plan.decide(self, expected, floor.identities, launchable, starter, emptySet(), emptySet()),
            Preview2Plan.decide(self, light.installed, light.floor.identities, launchable, starter, emptySet(), emptySet()))
    }
}
