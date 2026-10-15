// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.session

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*
import org.plainphone.device.*
import org.plainphone.device.boot.RestrictionFilter
import org.plainphone.policy.*

class ReadbackPlayEffectsTest {
    private val home = PackageName("org.plainphone")
    private val play = PackageName("com.android.vending")
    private val dialer = PackageName("test.dialer")
    private val sms = PackageName("test.sms")
    private val app = PackageName("test.newapp")
    private val inventory = BuildInventory("test", "build", 1, NeverHideList(setOf(sms)), setOf(sms), emptySet())
    private val access = (SessionJournal.restore("play", WindowKind.PLAY, setOf(play),
        BootTime("boot", 600_000), SessionPhase.OPENING, null) as SessionJournal.Active).access
    private inner class Rig {
        val port = FakeDevicePolicyPort(listOf(home, play, dialer, sms, app).associate { it.value to visible() })
        var input = PolicyInput("test", "build", home, sms,
            listOf(home, play, dialer, sms, app).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), inventory,
            currentBoot = BootTime("boot", 0))
        var factsFail = false
        var broaden = false
        fun plan(access: SessionAccess?): PolicyPlan {
            check(!factsFail)
            val plan = RestrictionFilter.TEST_BUILD.apply(PolicyEngine.decide(input.copy(sessionAccess = access)))
            return if (broaden && access != null) plan.copy(lockTaskPackages = plan.lockTaskPackages + app,
                expected = plan.expected.copy(lockTaskPackages = plan.lockTaskPackages + app)) else plan
        }
        val effects = ReadbackPlayEffects(port, ::plan)
        init { assertTrue(ReadbackPolicyApplier(port).apply(plan(null)).complete); port.ops.clear() }
    }

    @Test fun opensOnlyPlayFlushesEachUnhideAndReadsThePlanBack() {
        val r = Rig(); val baseline = r.port.lockTask
        assertTrue(r.effects.open(access))
        assertEquals(baseline + play, r.port.lockTask)
        assertFalse(r.port.hidden(play.value)); assertTrue(r.port.hidden(app.value))
        assertEquals(1, r.port.flips.size)
        assertTrue(r.effects.readBackPolicy())
        assertTrue(r.effects.revokeAllRelaxations())
        assertTrue(r.effects.protectedStateCheck()); assertTrue(r.effects.readBackPolicy())
        assertEquals(baseline, r.port.lockTask)
        assertTrue(r.port.hidden(play.value)); assertFalse(r.port.hidden(dialer.value))
    }

    @Test fun aPlanAddingAnyOtherPackageIsRefusedBeforeAnEffect() {
        val r = Rig(); r.broaden = true
        assertFalse(r.effects.open(access)); assertTrue(r.port.ops.isEmpty())
        assertTrue(r.port.hidden(play.value))
    }

    @Test fun failedOpenReadbackIsReportedAndCanBeRevoked() {
        val r = Rig(); r.port.lockTaskSticks = false
        assertFalse(r.effects.open(access))
        assertTrue(r.port.ops.any { it.startsWith("lockTask ") })
        r.port.lockTaskSticks = true
        assertTrue(r.effects.revokeAllRelaxations())
        assertTrue(r.effects.protectedStateCheck()); assertTrue(r.effects.readBackPolicy())
        assertTrue(r.port.hidden(play.value)); assertFalse(play in r.port.lockTask)
    }

    @Test fun failedRevokeNeverReportsReadbackAndEmergencyRepairStillRuns() {
        val r = Rig(); assertTrue(r.effects.open(access))
        r.port.hidesStick = false
        r.port.state[dialer] = hiddenApp()
        assertFalse(r.effects.revokeAllRelaxations())
        // A failed revoke cannot gate the independent emergency repair.
        assertFalse(r.port.hidden(dialer.value))
        assertFalse(r.effects.readBackPolicy())
        assertFalse(play in r.port.lockTask)
    }

    @Test fun unreadablePlanStillRemovesPlayAndDoesNotGuessFloorMembership() {
        val r = Rig(); assertTrue(r.effects.open(access)); r.factsFail = true
        assertFalse(r.effects.revokeAllRelaxations())
        assertFalse(play in r.port.lockTask)
        assertFalse(r.port.hidden(dialer.value))
        assertFalse(r.effects.readBackPolicy())
    }

    @Test fun suspendedOrDisabledPlayIsNotSilentlyChangedWithoutARestoreRecord() {
        for (state in listOf(PackageState(true, true, true), PackageState(true, false, false))) {
            val r = Rig(); r.port.state[play] = state
            assertFalse(r.effects.open(access)); assertTrue(r.port.ops.isEmpty())
            assertEquals(state, r.port.state[play])
        }
    }
}
