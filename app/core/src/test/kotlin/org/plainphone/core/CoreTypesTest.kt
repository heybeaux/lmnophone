// SPDX-License-Identifier: Apache-2.0
package org.plainphone.core

import kotlin.test.*

class CoreTypesTest {
    @Test fun `package identity is exact and rejects malformed names`() {
        assertEquals(PackageName("android"), PackageName("android"))
        assertNotEquals(PackageName("test.Dialer"), PackageName("test.dialer"))
        listOf("", " test.dialer", "test.dialer ", "test/dialer", "test..dialer").forEach {
            assertFailsWith<IllegalArgumentException> { PackageName(it) }
        }
    }

    @Test fun `never hide takes an immutable snapshot`() {
        val dialer = PackageName("test.dialer")
        val source = mutableSetOf(dialer)
        val protected = NeverHideList(source)
        source.clear()
        assertTrue(dialer in protected)
        assertFailsWith<UnsupportedOperationException> { (protected.packages as MutableSet).clear() }
    }

    @Test fun `inventory minimum UI cannot be swept`() {
        assertFailsWith<IllegalArgumentException> {
            BuildInventory("model", "fingerprint", 1, NeverHideList(emptySet()), setOf(PackageName("test.sms")), emptySet())
        }
    }

    @Test fun `inventory snapshots its minimum UI and session packages`() {
        val sms = PackageName("test.sms")
        val ui = mutableSetOf(sms)
        val settings = mutableSetOf(PackageName("test.settings"))
        val inventory = BuildInventory("model", "fingerprint", 1, NeverHideList(ui), ui, settings)
        ui.clear(); settings.clear()
        assertEquals(setOf(sms), inventory.minimumUi)
        assertEquals(setOf(PackageName("test.settings")), inventory.settingsSessionPackages)
    }

    @Test fun `running time requires epoch and nonnegative elapsed readings`() {
        assertFailsWith<IllegalArgumentException> { RunningTime("", 0) }
        assertFailsWith<IllegalArgumentException> { RunningTime("epoch", -1) }
        assertFailsWith<IllegalArgumentException> { BootTime("", 0) }
        assertFailsWith<IllegalArgumentException> { BootTime("boot", -1) }
    }

    @Test fun `unknown epoch readings start at current epoch zero`() {
        assertEquals(12L, RunningTime("new", 12).since(RunningTime("old", 999)))
        assertEquals(5L, RunningTime("new", 12).since(RunningTime("new", 7)))
        assertEquals(0L, RunningTime("new", 12).since(RunningTime("new", 13)))
    }

    @Test fun `shared mode and window identities include gated windows`() {
        assertEquals(setOf("TRUSTED", "SELF_MANAGED"), Mode.entries.map { it.name }.toSet())
        assertEquals(setOf("PLAY", "SETTINGS", "APPROVE", "UPDATE", "WEB", "WIFI_SIGNIN"), WindowKind.entries.map { it.name }.toSet())
        assertFailsWith<IllegalArgumentException> { TrustedPersonId("") }
    }
}
