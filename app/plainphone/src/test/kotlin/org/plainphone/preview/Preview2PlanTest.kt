// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.LockTaskFeature

class Preview2PlanTest {
    private fun p(name: String) = PackageName(name)
    private fun sys(name: String) = InstalledPackage(p(name), isSystem = true, versionCode = 1)
    private fun user(name: String) = InstalledPackage(p(name), isSystem = false, versionCode = 1)

    private val plainphone = p("org.plainphone")
    private val dialer = "com.dialer"
    private val sms = "com.sms"
    private val installed = listOf(
        user("org.plainphone"), sys(dialer), sys(sms), sys("com.camera"), sys("com.maps"),
        sys("com.chrome"), user("com.game"), sys("com.settings"), sys("com.launcher"), sys("com.safety"),
    )
    private val identities = listOf(
        FloorIdentity(p(dialer), FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source),
        FloorIdentity(p(sms), FloorRole.SMS, FloorRole.SMS.source),
    )
    private val launchable = setOf(dialer, sms, "com.camera", "com.maps", "com.chrome", "com.game",
        "com.settings", "com.launcher", "org.plainphone").map(::p).toSet()
    private val starter = setOf(dialer, sms, "com.camera", "com.maps", "com.not.installed").map(::p).toSet()
    private val keepVisible = setOf(p("com.settings"), p("com.launcher"))

    private fun plan(
        launchable: Set<PackageName> = this.launchable,
        identities: List<FloorIdentity> = this.identities,
    ) = Preview2Plan.decide(plainphone, installed, identities, launchable, starter, keepVisible, setOf(p("com.safety")))

    @Test fun `hides only launcher apps outside the starter list, the floor and the kept system screens`() {
        assertEquals(setOf(p("com.chrome"), p("com.game")), plan().hidden)
        assertEquals(plan().hidden, plan().expected.hidden)
    }

    @Test fun `never hides the floor, the starter list, Plainphone or the kept screens even if launchable`() {
        val never = plan().neverHide
        listOf(dialer, sms, "com.camera", "com.maps", "org.plainphone", "com.settings", "com.launcher")
            .forEach { assertTrue(it, p(it) in never) }
        assertTrue(plan().hidden.none { it in never })
    }

    @Test fun `a floor package stays shown even when the starter list leaves it out`() {
        val floorOnly = Preview2Plan.decide(plainphone, installed, identities, launchable,
            setOf(p("com.camera")), keepVisible, emptySet())
        assertTrue(p(dialer) in floorOnly.expected.unhiddenAndEnabled)
        assertFalse(p(dialer) in floorOnly.hidden)
    }

    @Test fun `starter apps that are installed get unhidden and enabled, kept screens are left as they are`() {
        val shown = plan().expected.unhiddenAndEnabled
        assertTrue(shown.containsAll(listOf(dialer, sms, "com.camera", "com.maps", "org.plainphone").map(::p)))
        assertFalse(p("com.settings") in shown)
        assertFalse(p("com.launcher") in shown)
        assertFalse(p("com.not.installed") in shown)
    }

    @Test fun `lock task allows Plainphone, the starter apps, the emergency UI and the extras that are installed`() {
        val expected = setOf("org.plainphone", dialer, sms, "com.camera", "com.maps", "com.safety").map(::p).toSet()
        assertEquals(expected, plan().lockTaskPackages)
        assertEquals(expected, plan().expected.lockTaskPackages)
    }

    @Test fun `features keep Home, the lock screen, the power menu and the status bar, no notification shade`() {
        assertEquals(setOf(LockTaskFeature.HOME, LockTaskFeature.KEYGUARD, LockTaskFeature.GLOBAL_ACTIONS,
            LockTaskFeature.SYSTEM_INFO), plan().expected.features)
    }

    @Test fun `no user restrictions in preview-2, so debugging and factory reset are never both set`() {
        assertTrue(plan().restrictions.isEmpty())
        assertNull(plan().expected.restrictions)
        assertNull(plan().expected.systemInputMethodsOnly)
        assertNull(plan().expected.systemAccessibilityServicesOnly)
        assertFalse(plan().steps.any { it.stage == ApplyStage.RESTRICTIONS })
    }

    @Test fun `Plainphone is Home and every step reads back`() {
        assertEquals(plainphone, plan().expected.home)
        assertEquals(listOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD, ApplyStage.HIDES,
            ApplyStage.LOCK_TASK, ApplyStage.HOME), plan().steps.map { it.stage })
        assertTrue(plan().steps.all { it.readbackRequired })
    }

    @Test fun `without a system dialer the plan asks for attention`() {
        assertTrue(plan(identities = emptyList()).needsAttention)
        assertFalse(plan().needsAttention)
    }

    private fun locked() = Preview2Plan.decideLocked(plainphone, installed, identities)

    @Test fun `before the first unlock the plan only restores the floor, Plainphone's lock task entry and the features`() {
        assertEquals(listOf(ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD), locked().steps.map { it.stage })
        assertTrue(locked().steps.all { it.readbackRequired })
        assertTrue(locked().hidden.isEmpty())
        assertEquals(emptySet<PackageName>(), locked().expected.hidden)
        assertEquals(setOf(plainphone, p(dialer)), locked().expected.lockTaskPackages)
        assertEquals(plainphone, locked().expected.home)
        assertEquals(plan().expected.features, locked().expected.features)
    }

    @Test fun `the locked plan never hides, never sets restrictions and keeps the floor shown`() {
        assertTrue(locked().restrictions.isEmpty())
        assertNull(locked().expected.restrictions)
        assertTrue(locked().neverHide.containsAll(listOf(plainphone, p(dialer), p(sms))))
        assertEquals(locked().neverHide, locked().unhide)
    }
}
