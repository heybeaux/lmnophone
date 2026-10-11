// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.ApplyStep
import org.plainphone.policy.DestructiveKind
import org.plainphone.policy.DestructiveRequest
import org.plainphone.policy.ExpectedPolicyState
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PolicyPlan
import org.plainphone.policy.Restriction
import org.plainphone.policy.SweepMode

/** device-lockdown, Order of application steps 2–7, and the applier half of "Restriction lost in a power cut". */
class ReadbackPolicyApplierTest {
    private val pp = "org.plainphone"
    private val dialer = "com.android.dialer"
    private val messages = "com.google.android.apps.messaging"
    private val gms = "com.google.android.gms"
    private val camera = "com.android.camera2"
    private val chrome = "com.android.chrome"
    private val maps = "com.google.android.apps.maps"

    private fun names(vararg n: String) = n.map(::PackageName).toSet()

    /** A normal unlocked plan: approved camera, chrome and maps hidden, every restriction and feature. */
    private fun plan(
        hidden: Set<PackageName> = names(chrome, maps),
        unhide: Set<PackageName> = names(pp, dialer, messages, gms, camera),
        neverHide: Set<PackageName> = names(pp, dialer, messages, gms),
        emergencyUi: Set<PackageName> = names(dialer),
        lockTask: Set<PackageName> = names(pp, dialer, messages, camera),
        stages: List<ApplyStage> = ApplyStage.entries,
        expected: ExpectedPolicyState = ExpectedPolicyState(
            hidden, unhide, neverHide, lockTask, LockTaskFeature.entries.toSet(), Restriction.entries.toSet(), PackageName(pp),
        ),
        allowedDestruction: List<DestructiveRequest> = emptyList(),
        readbackRequired: Boolean = true,
    ) = PolicyPlan(
        hidden = hidden, unhide = unhide, neverHide = neverHide, emergencyUi = emergencyUi,
        lockTaskPackages = lockTask, features = LockTaskFeature.entries.toSet(), restrictions = Restriction.entries.toSet(),
        steps = stages.map { ApplyStep(it, readbackRequired) }, expected = expected, sweepMode = SweepMode.FULL,
        refusedDestruction = emptyList(), allowedDestruction = allowedDestruction, classifierConflicts = emptySet(),
        changedPackages = emptySet(), needsAttention = false, notifyBuildReview = false,
    )

    /** A re-apply: the floor's lock-task membership is already in place, so only LOCK_TASK writes the list. */
    private fun port(vararg hiddenNow: String) = FakeDevicePolicyPort(
        listOf(pp, dialer, messages, gms, camera, chrome, maps).associateWith { if (it in hiddenNow) hiddenApp() else visible() },
    ).apply { lockTask = names(pp, dialer) }

    private fun firstIndex(ops: List<String>, prefix: String) = ops.indexOfFirst { it.startsWith(prefix) }

    @Test fun timingSeparatesWritesReadbacksAndFinalVerificationWithoutChangingResults() {
        var now = 0L
        val timing = ReapplyTimings { now++ }
        val measuredPort = port(dialer, camera)
        val baselinePort = port(dialer, camera)
        val measured = ReadbackPolicyApplier(measuredPort, timings = timing)
        assertEquals(ReadbackPolicyApplier(baselinePort).apply(plan()), measured.apply(plan()))
        assertEquals(baselinePort.ops, measuredPort.ops)
        assertTrue(measured.readBack(plan()))
        val expected = ApplyStage.entries.drop(1).flatMap {
            listOf("write.$it=1", "read.$it=1")
        } + ApplyStage.entries.map { "verify.$it=1" }
        assertEquals(expected.joinToString(","), timing.summary())
    }

    @Test fun appliesStepsTwoToSevenInPlanOrderAndReadsEachBack() {
        val port = port(dialer, camera)
        val result = ReadbackPolicyApplier(port).apply(plan())

        assertTrue(result.complete)
        assertNull(result.failedStage)
        assertEquals(ApplyStage.entries.drop(1), result.applied)
        // The floor restores the fixed features first (PLA-232 finding 9); LOCK_TASK sets them again.
        val order = listOf("restrict", "unhide $dialer", "features", "hide $chrome", "unhide $camera", "lockTask", "home")
        val at = order.map { firstIndex(port.ops, it) }
        assertTrue("every stage wrote: ${port.ops}", at.all { it >= 0 })
        assertEquals(at.sorted(), at)
        assertEquals(Restriction.entries.toSet(), port.restrictionSet)
        assertTrue(port.inputMethodsSystemOnly && port.accessibilitySystemOnly)
        assertTrue(port.hidden(chrome) && port.hidden(maps) && !port.hidden(camera))
        assertEquals(names(pp, dialer, messages, camera), port.lockTask)
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
        assertEquals(PackageName(pp), port.homePackage)
    }

    @Test fun stagesRunInTheOrderThePlanGives() {
        val port = port()
        val stages = listOf(ApplyStage.HOME, ApplyStage.RESTRICTIONS)
        val result = ReadbackPolicyApplier(port).apply(plan(stages = stages))
        assertEquals(stages, result.applied)
        assertTrue(firstIndex(port.ops, "home") < firstIndex(port.ops, "restrict"))
    }

    @Test fun failedRestrictionReadbackStopsBeforeAnyLaterStep() {
        val port = port(camera).apply { restrictionsStick = false }
        val result = ReadbackPolicyApplier(port).apply(plan())

        assertFalse(result.complete)
        assertTrue(result.needsAttention)
        assertEquals(ApplyStage.RESTRICTIONS, result.failedStage)
        assertTrue(result.applied.isEmpty())
        assertTrue(port.ops.all { it.startsWith("restrict") || it.startsWith("inputMethods") || it.startsWith("accessibility") })
        assertTrue("nothing hidden or unhidden after the failure", port.hidden(camera) && !port.hidden(chrome))
        assertNull(port.homePackage)
    }

    @Test fun failedHideReadbackNeverClaimsLaterSteps() {
        val port = port().apply { hidesStick = false }
        val result = ReadbackPolicyApplier(port).apply(plan())

        assertEquals(ApplyStage.HIDES, result.failedStage)
        assertEquals(listOf(ApplyStage.RESTRICTIONS, ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD), result.applied)
        assertEquals(-1, firstIndex(port.ops, "lockTask"))
        assertNull(port.homePackage)
    }

    @Test fun aThrowingWriteFailsItsStep() {
        val port = port().apply { throwOnWrite = "lockTask" }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertEquals(ApplyStage.LOCK_TASK, result.failedStage)
        assertFalse(ApplyStage.LOCK_TASK in result.applied)
        assertNull(port.homePackage)
    }

    @Test fun failedLockTaskReadbackStopsBeforeHome() {
        val port = port().apply { lockTaskSticks = false }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertEquals(ApplyStage.LOCK_TASK, result.failedStage)
        assertNull(port.homePackage)
    }

    @Test fun nullExpectedFieldsAreNeitherWrittenNorRead() {
        // The shape PolicyEngine uses while a turn-off commits: only the floor, everything else outside the plan.
        val floor = names(dialer, gms)
        val turnOff = plan(
            hidden = emptySet(), unhide = floor, neverHide = floor, lockTask = emptySet(),
            stages = ApplyStage.entries, // every stage present, to prove null alone keeps it out
            expected = ExpectedPolicyState(
                hidden = null, unhiddenAndEnabled = floor, unsuspended = floor, lockTaskPackages = null,
                features = null, restrictions = null, home = null,
                systemInputMethodsOnly = null, systemAccessibilityServicesOnly = null,
            ),
        )
        val port = port(dialer, chrome).apply {
            forbidden += setOf("restrictions", "inputMethods", "accessibility", "lockTask", "features", "home")
        }
        val result = ReadbackPolicyApplier(port).apply(turnOff)

        assertTrue("${result.failedStage} ${port.ops}", result.complete)
        assertFalse(port.hidden(dialer))
        assertTrue("hidden state outside the plan is left alone", port.hidden(chrome))
        assertEquals(-1, firstIndex(port.ops, "hide "))
    }

    @Test fun revokingTheJournalBelongsToTheSessionController() {
        val port = FakeDevicePolicyPort().apply {
            forbidden += setOf("packages", "restrictions", "inputMethods", "accessibility", "lockTask", "features", "home", "flush")
        }
        val result = ReadbackPolicyApplier(port).apply(plan(stages = listOf(ApplyStage.REVOKE_JOURNAL)))
        assertTrue(result.complete)
        assertTrue(result.applied.isEmpty())
        assertTrue(port.ops.isEmpty())
    }

    @Test fun neverHidePackageInTheHiddenSetFailsTheGuardBeforeAnyHide() {
        val bad = names(chrome, gms)
        val port = port()
        val result = ReadbackPolicyApplier(port).apply(plan(hidden = bad))

        assertEquals(ApplyStage.NEVER_HIDE_GUARD, result.failedStage)
        assertEquals(-1, firstIndex(port.ops, "hide "))
        assertFalse(port.hidden(gms))
        assertFalse(port.hidden(chrome))
    }

    @Test fun withoutAGuardStepHidesStillNeverTouchTheNeverHideList() {
        val port = port()
        val stages = ApplyStage.entries - ApplyStage.NEVER_HIDE_GUARD
        val result = ReadbackPolicyApplier(port).apply(plan(hidden = names(chrome, gms), stages = stages))

        assertFalse(port.hidden(gms))
        assertTrue(port.hidden(chrome))
        assertEquals("expected hidden gms cannot read back", ApplyStage.HIDES, result.failedStage)
        assertFalse("hide $gms" in port.ops)
    }

    @Test fun allowedDestructionIsNeverExecuted() {
        val destruction = listOf(
            DestructiveRequest(PackageName(camera), DestructiveKind.HIDE),
            DestructiveRequest(PackageName(messages), DestructiveKind.SUSPEND),
            DestructiveRequest(PackageName(maps), DestructiveKind.UNINSTALL),
        )
        val port = port()
        val result = ReadbackPolicyApplier(port).apply(plan(allowedDestruction = destruction))

        assertTrue(result.complete)
        assertFalse(port.hidden(camera))
        assertFalse(port.state.getValue(PackageName(messages)).suspended)
        val hides = port.ops.filter { it.startsWith("hide ") }.map { it.removePrefix("hide ") }.toSet()
        assertEquals(setOf(chrome, maps), hides)
    }

    @Test fun everyUnhideIsFollowedByItsOwnAlternatingFlip() {
        for (start in listOf(false, true)) {
            val port = port(dialer, gms, camera).apply { flushComponent = start }
            val result = ReadbackPolicyApplier(port).apply(plan())

            assertTrue(result.complete)
            val unhides = port.ops.withIndex().filter { it.value.startsWith("unhide ") }
            assertEquals(3, unhides.size)
            for ((i, _) in unhides) assertTrue("flip right after ${port.ops[i]}: ${port.ops}", port.ops[i + 1].startsWith("flip "))
            assertEquals(3, port.flips.size)
            assertEquals(listOf(!start, start, !start), port.flips.map { it.second })
            assertTrue("never a same-state flip", port.flips.all { (before, target) -> before != target })
            assertTrue(result.flipFailures.isEmpty())
        }
    }

    @Test fun visiblePackagesNeedNoUnhideAndNoFlip() {
        val port = port()
        ReadbackPolicyApplier(port).apply(plan())
        assertEquals(-1, firstIndex(port.ops, "unhide "))
        assertTrue(port.flips.isEmpty())
    }

    @Test fun flipThatDoesNotReadBackIsRetriedOnceThenNeedsAttentionAndThePackageStaysUnhidden() {
        val port = port(camera).apply { flipsIgnored = 2 }
        val result = ReadbackPolicyApplier(port).apply(plan())

        assertEquals(2, port.flips.size)
        assertTrue(port.flips.all { (before, target) -> before != target })
        assertEquals(names(camera), result.flipFailures)
        assertFalse(port.hidden(camera))
        assertTrue("an emergency repair must not be undone or stall the apply", result.complete)
        assertTrue(result.needsAttention)
    }

    @Test fun flipRetrySucceeds() {
        val port = port(camera).apply { flipsIgnored = 1 }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertEquals(2, port.flips.size)
        assertTrue(result.flipFailures.isEmpty())
        assertFalse(result.needsAttention)
    }

    @Test fun unreadableFlushComponentIsNeverFlippedBlind() {
        val port = port(camera).apply { flushComponent = null }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertTrue(port.flips.isEmpty())
        assertEquals(names(camera), result.flipFailures)
        assertFalse(port.hidden(camera))
    }

    @Test fun floorUnhidesFirstAndRepairsSuspendedFloorPackages() {
        val port = port(dialer).apply { state[PackageName(messages)] = visible().copy(suspended = true) }
        val result = ReadbackPolicyApplier(port).apply(plan())

        assertTrue(result.complete)
        assertTrue(firstIndex(port.ops, "unhide $dialer") < firstIndex(port.ops, "hide "))
        assertFalse(port.state.getValue(PackageName(messages)).suspended)
        assertTrue(result.unrepairable.isEmpty())
    }

    // --- A user-disabled package cannot be re-enabled by a Device Owner (PLA-232 finding 3) ---

    @Test fun aDisabledFloorPackageIsReportedUnrepairableAndTheApplyContinues() {
        val port = port(dialer).apply { state[PackageName(dialer)] = hiddenApp().copy(enabled = false) }
        val result = ReadbackPolicyApplier(port).apply(plan())

        // One best-effort enable; it does not stick, so the package is reported, not retried forever.
        assertTrue("enable $dialer" in port.ops)
        assertEquals(names(dialer), result.unrepairable)
        assertTrue(result.needsAttention)
        // The rest still runs: the package is unhidden, and lock task and Home are applied.
        assertTrue(result.complete)
        assertFalse(port.hidden(dialer))
        assertEquals(PackageName(pp), port.homePackage)
        assertTrue(ReadbackPolicyApplier(port).readBack(plan()))
    }

    @Test fun aDisabledPackageThatStaysHiddenStillFailsTheReadback() {
        val port = port(dialer).apply { state[PackageName(dialer)] = hiddenApp().copy(enabled = false) }
        // An unhide that does not stick: hidden is still read back even though enabled is not.
        val result = ReadbackPolicyApplier(object : DevicePolicyPort by port {
            override fun setHidden(name: PackageName, hidden: Boolean) { port.ops += "ignored $hidden ${name.value}" }
        }).apply(plan())
        assertEquals(ApplyStage.EMERGENCY_FLOOR, result.failedStage)
    }

    @Test fun aPackageTheBuildCanReEnableIsNotReported() {
        val port = port().apply {
            state[PackageName(gms)] = visible().copy(enabled = false)
            enableRepairs = true
        }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertTrue(result.complete)
        assertTrue(result.unrepairable.isEmpty())
        assertTrue(port.state.getValue(PackageName(gms)).enabled)
    }

    @Test fun absentPackagesAreReportedNotFailed() {
        val port = port().apply { state.remove(PackageName(gms)) }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertTrue(result.complete)
        assertEquals(names(gms), result.absent)
    }

    @Test fun lockedBootRestoresEmergencyUiLockTaskMembershipAndStopsAtTheGuard() {
        val port = port(dialer).apply { lockTask = names(pp) }
        val result = ReadbackPolicyApplier(port).apply(plan(stages = ApplyStage.entries.take(4)))

        assertTrue(result.complete)
        assertEquals(names(pp, dialer), port.lockTask)
        assertFalse(port.hidden(dialer))
        assertEquals(-1, firstIndex(port.ops, "hide "))
        // The locked-boot path restores the fixed features too (spec 722610b, PLA-232 finding 9).
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
        assertNull(port.homePackage)
    }

    @Test fun restrictionLostInAPowerCutIsReappliedAndReadBackBeforeHome() {
        val port = port().apply { restrictionSet += Restriction.entries - Restriction.DISALLOW_FACTORY_RESET }
        val result = ReadbackPolicyApplier(port).apply(plan())

        assertTrue(result.complete)
        assertTrue(Restriction.DISALLOW_FACTORY_RESET in port.restrictionSet)
        assertTrue(firstIndex(port.ops, "restrict DISALLOW_FACTORY_RESET") < firstIndex(port.ops, "home"))
    }

    @Test fun restrictionThatStillWillNotStickAfterBootLeavesHomeUnset() {
        val port = port().apply { restrictionsStick = false }
        val result = ReadbackPolicyApplier(port).apply(plan())
        assertEquals(ApplyStage.RESTRICTIONS, result.failedStage)
        assertNull(port.homePackage)
    }

    @Test fun stepWithoutRequiredReadbackIsWrittenButNotRead() {
        val port = port().apply { restrictionsStick = false }
        val result = ReadbackPolicyApplier(port).apply(plan(stages = listOf(ApplyStage.RESTRICTIONS), readbackRequired = false))
        assertTrue(result.complete)
        assertTrue(firstIndex(port.ops, "restrict") >= 0)
    }

    /** PLA-242 spike: a Home entering lock task between the two writes must already have KEYGUARD. */
    @Test fun lostFeaturesAreRestoredBeforeLockTaskMembership() {
        for (repair in listOf(false, true)) {
            val port = port().apply { lockTask = emptySet(); features = setOf(LockTaskFeature.GLOBAL_ACTIONS) }
            val applier = ReadbackPolicyApplier(port)
            if (repair) applier.repairEmergency(plan()) else applier.apply(plan())
            val featureWrites = port.ops.withIndex().filter { it.value.startsWith("features ") }.map { it.index }
            val listWrites = port.ops.withIndex().filter { it.value.startsWith("lockTask ") }.map { it.index }
            assertTrue("repair=$repair ${port.ops}", featureWrites.isNotEmpty() && listWrites.isNotEmpty())
            assertTrue("repair=$repair features first: ${port.ops}", featureWrites.first() < listWrites.first())
        }
    }
}
