// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*

class PreviewPlayPlanTest {
    @Test fun openingChangesOnlyPlayAndKeepsFloorAndRestrictions() {
        val home = PackageName("org.plainphone"); val dialer = PackageName("test.dialer"); val play = PackageName("com.android.vending")
        val base = PolicyEngine.decide(PolicyInput("model", "build", home, PackageName("test.sms"),
            listOf(home, dialer, play).map { InstalledPackage(it, it != home, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED))))
        val access = (SessionJournal.restore("play", WindowKind.PLAY, setOf(play), BootTime("boot", 600000),
            SessionPhase.OPEN, null) as SessionJournal.Active).access
        val opened = PreviewPlayPlan.open(base, access)
        assertEquals(base.lockTaskPackages + play, opened.lockTaskPackages)
        assertEquals(base.hidden - play, opened.hidden)
        assertEquals(base.unhide + play, opened.unhide)
        assertEquals(base.neverHide, opened.neverHide)
        assertEquals(base.restrictions, opened.restrictions)
        assertEquals(base.features, opened.features)
        assertEquals(base.expected.unhiddenAndEnabled + play, opened.expected.unhiddenAndEnabled)
    }
}
