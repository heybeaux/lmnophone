// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.boot

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.PackageName
import org.plainphone.device.ApplyResult
import org.plainphone.device.boot.BootOutcome
import org.plainphone.policy.ApplyStage

class BootPassCoverageTest {
    private val good = BootOutcome(true, false,
        ApplyResult(ApplyStage.entries.toList(), null, emptySet(), emptySet()), emptySet())

    private inner class Fixture {
        val coverage = BootPassCoverage()
        var applies = 0
        var publications = 0
        var pending = false
        var publishedReady = true
        val skips = mutableListOf<Long>()
        fun run(reason: String, locked: Boolean = false, outcome: BootOutcome? = good) =
            coverage.run(reason, locked, { pending }, apply = { applies++; outcome },
                afterPass = { publications++; publishedReady }, skipped = { skips += it })
    }

    @Test fun captureRunsInNextTaskAfterReadyPublication() {
        val coverage = BootPassCoverage()
        val tasks = mutableListOf<() -> Unit>()
        val events = mutableListOf<String>()
        coverage.run("user-unlocked", false, { false }, apply = { events += "apply"; good },
            afterPass = { events += "home-published"; true }, skipped = {},
            afterPublished = { coverage.captureAfterPublished({ tasks += it }) { events += "capture" } })
        assertEquals(listOf("apply", "home-published"), events)
        tasks.single()()
        assertEquals(listOf("apply", "home-published", "capture"), events)
    }

    @Test fun interveningPassOrInvalidationDiscardsPendingCapture() {
        for (intervening in listOf("invalidate", "alarm", "boot", "failed")) {
            val f = Fixture(); val tasks = mutableListOf<() -> Unit>()
            f.run("user-unlocked")
            f.coverage.captureAfterPublished({ tasks += it }) { fail(intervening) }
            when (intervening) {
                "invalidate" -> f.coverage.invalidate()
                "failed" -> f.run("alarm", outcome = good.copy(ready = false))
                else -> f.run(intervening)
            }
            tasks.single()()
        }
    }

    @Test fun unavailableCoverageCannotScheduleCapture() {
        for (mode in listOf("locked", "failed", "attention", "retry", "publication")) {
            val f = Fixture()
            f.pending = mode == "retry"; f.publishedReady = mode != "publication"
            f.coverage.run("start", mode == "locked", { f.pending }, apply = {
                when (mode) {
                    "failed" -> good.copy(ready = false)
                    "attention" -> good.copy(flipRecordUnknown = true)
                    else -> good
                }
            }, afterPass = { f.publishedReady }, skipped = {}, afterPublished = { fail(mode) })
            f.coverage.captureAfterPublished({ fail(mode) }) { fail(mode) }
        }
    }

    @Test fun unchangedPackageSkipsWithoutHomeRefresh() {
        val f = Fixture(); f.run("start")
        f.coverage.run("package:PACKAGE_CHANGED:example", false, { false },
            packageUnchanged = { true }, apply = { fail("full pass"); good },
            afterPass = { fail("refresh"); true }, skipped = { f.skips += it },
            afterPackageSkip = { true })
        assertEquals(listOf(1L), f.skips)
    }

    @Test fun proofCannotOverrideOtherTriggersOrInvalidCoverage() {
        for (reason in listOf("package:PACKAGE_ADDED:example", "package:PACKAGE_REMOVED:example",
            "package:PACKAGE_REPLACED:example", "updated", "user-unlocked", "start", "alarm",
            "trusted-person", "pin-ended", "package-burst")) {
            val f = Fixture(); f.run("start")
            f.coverage.run(reason, false, { false }, packageUnchanged = { true },
                apply = { f.applies++; good }, afterPass = { true }, skipped = { fail(reason) })
            assertEquals(reason, 2, f.applies)
        }
        for (mode in listOf("absent", "locked", "retry", "failed", "attention", "unverified")) {
            val f = Fixture()
            if (mode != "absent") f.run("start", outcome = when (mode) {
                "failed" -> good.copy(ready = false)
                "attention" -> good.copy(flipRecordUnknown = true)
                "unverified" -> good.copy(apply = null)
                else -> good
            })
            f.coverage.run("package:PACKAGE_CHANGED:example", mode == "locked", { mode == "retry" },
                packageUnchanged = { fail(mode); true }, apply = { good },
                afterPass = { true }, skipped = { fail(mode) })
        }
    }

    @Test fun falseOrThrowingProofRunsFullPass() {
        for (throws in listOf(false, true)) {
            val f = Fixture(); f.run("start")
            f.coverage.run("package:PACKAGE_CHANGED:example", false, { false },
                packageUnchanged = { if (throws) error("read") else false },
                apply = { f.applies++; good }, afterPass = { true }, skipped = { fail() })
            assertEquals(2, f.applies)
        }
    }

    @Test fun fullPassCoversBothLateBootTriggersButKeepsSideEffects() {
        val f = Fixture()
        f.run("user-unlocked")
        f.run("locked-boot")
        f.run("boot")
        assertEquals(1, f.applies)
        assertEquals(3, f.publications)
        assertEquals(listOf(1L, 1L), f.skips)
    }

    @Test fun noPriorPassAndNewProcessMustApply() {
        for (reason in listOf("boot", "locked-boot")) {
            val f = Fixture()
            f.run(reason)
            assertEquals(1, f.applies)
            assertTrue(f.skips.isEmpty())
        }
    }

    @Test fun onlyBootTriggersCanReuseCoverage() {
        for (reason in listOf("package:PACKAGE_CHANGED:example", "updated", "user-unlocked", "start", "alarm", "trusted-person", "pin-ended")) {
            val f = Fixture()
            f.run("start")
            f.run(reason)
            assertEquals(reason, 2, f.applies)
            assertTrue(f.skips.isEmpty())
        }
    }

    @Test fun lockedPassNeverSkipsOrCoversTheFirstUnlockedBoot() {
        val f = Fixture()
        f.run("start")
        f.run("locked-boot", locked = true, outcome = good.copy(locked = true))
        f.run("boot")
        assertEquals(3, f.applies)
        assertTrue(f.skips.isEmpty())
    }

    @Test fun failedUnverifiedMissingOrAttentionOutcomeInvalidatesEarlierSuccess() {
        val bad = listOf(null, good.copy(ready = false), good.copy(apply = null),
            good.copy(apply = good.apply!!.copy(failedStage = ApplyStage.HOME)),
            good.copy(apply = good.apply!!.copy(applied = listOf(ApplyStage.RESTRICTIONS))),
            good.copy(planNeedsAttention = true), good.copy(flipRecordUnknown = true),
            good.copy(disabledFloor = setOf(PackageName("com.example.dialer"))),
            good.copy(apply = good.apply!!.copy(flipFailures = setOf(PackageName("com.example.dialer")))))
        for (outcome in bad) {
            val f = Fixture()
            f.run("start")
            f.run("alarm", outcome = outcome)
            f.run("boot")
            assertEquals(outcome.toString(), 3, f.applies)
            assertTrue(f.skips.isEmpty())
        }
    }

    @Test fun pendingDurabilityRetryPreventsSkippingEvenAfterPriorSuccess() {
        val f = Fixture()
        f.run("start")
        f.pending = true
        f.run("boot")
        f.pending = false
        f.run("locked-boot")
        assertEquals(3, f.applies)
    }

    @Test fun failedPublicationInvalidatesCoverage() {
        val f = Fixture()
        f.run("start")
        f.publishedReady = false
        f.run("boot")
        f.publishedReady = true
        f.run("locked-boot")
        assertEquals(2, f.applies)
    }

    @Test fun throwInPassOrSideEffectsInvalidatesCoverage() {
        for (throwInApply in listOf(true, false)) {
            val f = Fixture()
            f.run("start")
            try {
                f.coverage.run("alarm", false, { false },
                    apply = { if (throwInApply) error("apply") else good },
                    afterPass = { error("publish") }, skipped = {})
                fail("must throw")
            } catch (_: IllegalStateException) { }
            f.run("boot")
            assertEquals(2, f.applies)
        }
    }

    @Test fun explicitInvalidationForLostOwnershipRequiresANewPass() {
        val f = Fixture()
        f.run("start")
        f.coverage.invalidate()
        f.run("boot")
        assertEquals(2, f.applies)
    }
}
