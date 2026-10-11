// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.SweepMode

/**
 * device-lockdown, "Keyguard first" (PLA-264 finding 1). The lock-task features are written and read
 * back with KEYGUARD before any lock-task membership is granted, and Home may start lock task only
 * while they read back that way. Entering lock task without KEYGUARD dismisses even a secure keyguard.
 */
class KeyguardFirstTest {
    private val pp = PackageName("org.plainphone")
    private val dialer = PackageName("com.android.dialer")
    private val camera = PackageName("com.android.camera2")
    private val all = LockTaskFeature.entries.toSet()

    private fun plan(stages: List<ApplyStage> = ApplyStage.entries) = PolicyPlan(
        hidden = emptySet(), unhide = setOf(pp, dialer, camera), neverHide = setOf(pp, dialer), emergencyUi = setOf(dialer),
        lockTaskPackages = setOf(pp, dialer, camera), features = all, restrictions = Restriction.entries.toSet(),
        steps = stages.map { ApplyStep(it) },
        expected = ExpectedPolicyState(emptySet(), setOf(pp, dialer, camera), setOf(pp, dialer), setOf(pp, dialer, camera), all, Restriction.entries.toSet(), pp),
        sweepMode = SweepMode.FULL, refusedDestruction = emptyList(), allowedDestruction = emptyList(),
        classifierConflicts = emptySet(), changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false,
    )
    private val locked = ReadbackPolicyApplier.EMERGENCY_STAGES

    /** Records whether any lock-task list write granted a package while KEYGUARD was not set. */
    private class Watch(val fake: FakeDevicePolicyPort) : DevicePolicyPort by fake {
        val grantsWithoutKeyguard = mutableListOf<Set<PackageName>>()
        override fun setLockTaskPackages(packages: Set<PackageName>) {
            val granted = packages - fake.lockTask
            if (granted.isNotEmpty() && LockTaskFeature.KEYGUARD !in fake.features) grantsWithoutKeyguard += granted
            fake.setLockTaskPackages(packages)
        }
    }

    private fun fake(lockTask: Set<PackageName>, features: Set<LockTaskFeature>) =
        FakeDevicePolicyPort(listOf(pp, dialer, camera).associate { it.value to visible() }).apply {
            this.lockTask = lockTask
            this.features = features
        }

    @Test fun retainedMembershipLostFeaturesRestoresFeaturesAndNeverGrantsWithoutKeyguard() {
        for (stages in listOf(locked, ApplyStage.entries)) {
            val port = Watch(fake(setOf(pp, dialer, camera), setOf(LockTaskFeature.GLOBAL_ACTIONS)))
            assertTrue(ReadbackPolicyApplier(port).apply(plan(stages)).complete)
            assertEquals(all, port.fake.features)
            assertTrue(port.grantsWithoutKeyguard.isEmpty())
            assertTrue(KeyguardFirst.mayEnterLockTask(port, pp))
        }
    }

    @Test fun lostMembershipLostFeaturesWritesFeaturesBeforeMembership() {
        for (stages in listOf(locked, ApplyStage.entries)) {
            val port = Watch(fake(emptySet(), emptySet()))
            assertTrue(ReadbackPolicyApplier(port).apply(plan(stages)).complete)
            assertTrue(port.grantsWithoutKeyguard.isEmpty())
            val ops = port.fake.ops
            assertTrue(ops.indexOfFirst { it.startsWith("features") } < ops.indexOfFirst { it.startsWith("lockTask") })
            assertTrue(pp in port.fake.lockTask)
        }
    }

    @Test fun aFeatureWriteExceptionGrantsNoMembership() {
        for (stages in listOf(locked, ApplyStage.entries)) {
            val fake = fake(setOf(dialer), emptySet()).apply { throwOnWrite = "features" }
            val result = ReadbackPolicyApplier(fake).apply(plan(stages))
            assertEquals(ApplyStage.EMERGENCY_FLOOR, result.failedStage)
            assertTrue("no lock-task write: ${fake.ops}", fake.ops.none { it.startsWith("lockTask") })
            assertFalse(KeyguardFirst.mayEnterLockTask(fake, pp))
        }
    }

    @Test fun aFeatureReadbackMismatchGrantsNoMembershipInTheFloorOrTheRepair() {
        val fake = fake(setOf(dialer), emptySet()).apply { featuresStick = false }
        val applier = ReadbackPolicyApplier(fake)
        assertEquals(ApplyStage.EMERGENCY_FLOOR, applier.apply(plan()).failedStage)
        val repair = applier.repairEmergency(plan())
        assertFalse(ApplyStage.EMERGENCY_FLOOR in repair.applied)
        assertTrue(ApplyStage.RESTRICTIONS in repair.applied && ApplyStage.NEVER_HIDE_GUARD in repair.applied)
        assertTrue("no lock-task write: ${fake.ops}", fake.ops.none { it.startsWith("lockTask") })
        assertEquals(setOf(dialer), fake.lockTask)
    }

    @Test fun theLockTaskStageGrantsNothingNewWhenItsFeatureWriteDoesNotStick() {
        val fake = fake(setOf(pp, dialer), all)
        val port = object : DevicePolicyPort by fake {
            override fun setLockTaskFeatures(features: Set<LockTaskFeature>) {
                fake.setLockTaskFeatures(features)
                fake.features = setOf(LockTaskFeature.HOME) // lost between the floor and LOCK_TASK
            }
        }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertEquals(ApplyStage.LOCK_TASK, result.failedStage)
        assertFalse("camera is not newly granted", camera in fake.lockTask)
    }

    @Test fun homeMayEnterOnlyWhileKeyguardReadsBackAndHomeIsOnTheList() {
        val fake = fake(setOf(pp), all)
        assertTrue(KeyguardFirst.mayEnterLockTask(fake, pp))
        fake.features = all - LockTaskFeature.KEYGUARD
        assertFalse(KeyguardFirst.mayEnterLockTask(fake, pp))
        fake.features = all
        fake.lockTask = setOf(dialer)
        assertFalse(KeyguardFirst.mayEnterLockTask(fake, pp))
        fake.lockTask = setOf(pp)
        fake.forbidden += "features"
        assertFalse("an unreadable feature set is no", KeyguardFirst.mayEnterLockTask(fake, pp))
    }
}
