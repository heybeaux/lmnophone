// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.device.*
import org.plainphone.policy.*

class ReadbackGiveBackPortTest {
    private val port = FakeDevicePolicyPort(mapOf("test.app" to PackageState(true, false, true)))
    private val platform = object : GiveBackPort {
        var permissionWrites = 0
        override fun isDeviceOwner() = true
        override fun inventory() = emptyList<GiveBackAction>()
        override fun restore(action: GiveBackAction) { permissionWrites++ }
        override fun readBack(action: GiveBackAction) = true
        override fun anotherHomeResolves() = true
        override fun systemDialerResolves() = true
        override fun removeDeviceOwner() = Unit
    }
    private val effects = ReadbackGiveBackPort(port, platform)
    private val visible = GiveBackAction(GiveBackKind.VISIBLE, "test.app")
    @Test fun unhideHasSynchronousFlipAndReadbackBeforePermissionReset() {
        val permission = GiveBackAction(GiveBackKind.PERMISSION, "test.app", "test.permission")
        try { effects.restore(permission); fail("hidden grant must refuse") } catch (_: IllegalStateException) { }
        assertEquals(0, platform.permissionWrites)
        effects.restore(visible)
        assertEquals(listOf("unhide test.app", "flush true", "flush false"), port.ops)
        assertTrue(effects.readBack(visible))
        effects.restore(permission)
        assertEquals(1, platform.permissionWrites)
    }
    @Test fun disabledAppStaysAnExplicitFailedReadback() {
        port.state[org.plainphone.core.PackageName("test.app")] = PackageState(true, false, false)
        effects.restore(visible)
        assertFalse(effects.readBack(visible))
        assertTrue(port.ops.any { it == "enable test.app" })
    }
    @Test fun failedFlipIsRetriedAndNeverHiddenAgain() {
        port.flipsIgnored = 2
        try { effects.restore(visible); fail("flip failure must report") } catch (_: IllegalStateException) { }
        assertEquals(2, port.flips.size)
        assertFalse(port.state.values.single().hidden)
        assertFalse(port.ops.any { it.startsWith("hide ") })
    }
}
