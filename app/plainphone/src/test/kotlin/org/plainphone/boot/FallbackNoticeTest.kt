// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.boot.Containment

class FallbackNoticeTest {
    private val settings = PackageName("com.android.settings")
    private val keychain = PackageName("com.android.keychain")
    private val installer = PackageName("com.google.android.packageinstaller")

    @Test fun namesEveryRefusedPackageTheDisclosureDoesNotCover() {
        val notice = fallbackNotice(Containment(emptySet(), setOf(settings, keychain, installer), recorded = true),
            setOf(settings) + PERMISSION_CONTROLLERS) { it.value.substringAfterLast('.') }
        assertEquals(listOf("keychain", "packageinstaller"), notice.others)
        assertFalse(notice.incomplete)
    }

    @Test fun anUnrecordedContainmentIsIncomplete() {
        assertTrue(fallbackNotice(Containment(emptySet(), emptySet(), recorded = false), emptySet()) { it.value }.incomplete)
    }

    /** AVD pla242-a35 (PLA-344): the first pass suspended these; alerts, SMS storage and call audio need them. */
    @Test fun keepsTheAlertAndCallingServicesUnsuspended() {
        listOf(
            "com.google.android.cellbroadcastservice", "com.android.cellbroadcastservice",
            "com.google.android.cellbroadcastreceiver", "com.android.cellbroadcastreceiver",
            "com.android.providers.telephony", "com.android.bluetooth", "com.google.android.bluetooth",
            "com.google.android.telephony.satellite",
        ).forEach { assertTrue(it, PackageName(it) in PLATFORM_KEEP) }
    }
}

class FallbackStepTest {
    @Test fun containsOnlyAfterUnlockWhenLockTaskCannotStartWithKeyguard() {
        assertEquals(FallbackStep.CONTAIN, fallbackStep(userLocked = false, LockTaskState.NONE, mayEnterLockTask = false))
        assertEquals(FallbackStep.CONTAIN, fallbackStep(userLocked = false, LockTaskState.PINNED, mayEnterLockTask = false))
        assertEquals(FallbackStep.CONTAIN, fallbackStep(userLocked = false, LockTaskState.UNREADABLE, mayEnterLockTask = false))
        // Before the PIN the locked-boot plan's keep set is smaller; the keyguard is up anyway.
        assertEquals(FallbackStep.NONE, fallbackStep(userLocked = true, LockTaskState.NONE, mayEnterLockTask = false))
    }

    @Test fun liftsOnlyWhileLockedWithKeyguard() {
        assertEquals(FallbackStep.LIFT, fallbackStep(userLocked = false, LockTaskState.LOCKED, mayEnterLockTask = true))
        assertEquals(FallbackStep.NONE, fallbackStep(userLocked = false, LockTaskState.LOCKED, mayEnterLockTask = false))
        // Home has not started lock task yet: nothing to contain, nothing to lift.
        assertEquals(FallbackStep.NONE, fallbackStep(userLocked = false, LockTaskState.NONE, mayEnterLockTask = true))
    }
}

/** PLA-399 finding 1: no suspension from an unknown keep set. */
class ContainmentKeepTest {
    private val home = PackageName("org.plainphone")
    private val dialer = PackageName("com.android.dialer")
    private fun outcome(keep: Set<PackageName>, complete: Boolean) =
        org.plainphone.device.boot.BootOutcome(false, false, null, emptySet(), keep = keep, keepComplete = complete)

    @Test fun aCompleteKeepSetWithHomeAddsThePlatformKeep() {
        assertEquals(setOf(home, dialer) + PLATFORM_KEEP, containmentKeep(outcome(setOf(home, dialer), true), home))
    }

    @Test fun anUnknownFloorGivesNoKeepSet() {
        assertNull(containmentKeep(outcome(setOf(home, dialer), false), home))
    }

    @Test fun noOutcomeOrNoHomeGivesNoKeepSet() {
        assertNull(containmentKeep(null, home))
        assertNull(containmentKeep(outcome(setOf(dialer), true), home))
    }
}
