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
        for (intervening in listOf("invalidate", "alarm", "locked-boot-full", "failed", "attention")) {
            val f = Fixture(); val tasks = mutableListOf<() -> Unit>()
            f.run("user-unlocked")
            f.coverage.captureAfterPublished({ tasks += it }) { fail(intervening) }
            when (intervening) {
                "invalidate" -> f.coverage.invalidate()
                "failed" -> f.run("alarm", outcome = good.copy(ready = false))
                "attention" -> f.run("alarm", outcome = good.copy(planNeedsAttention = true))
                "locked-boot-full" -> f.run("boot", locked = true, outcome = good.copy(locked = true))
                else -> f.run(intervening)
            }
            tasks.single()()
        }
    }

    @Test fun coveredBootSkipPreservesPublishedPassCapture() {
        for (reason in listOf("boot", "locked-boot")) {
            val f = Fixture(); val tasks = mutableListOf<() -> Unit>()
            var captures = 0
            f.coverage.run("user-unlocked", false, { false },
                apply = { f.applies++; good }, afterPass = { f.publications++; true }, skipped = {},
                afterPublished = { f.coverage.captureAfterPublished({ tasks += it }) { captures++ } })
            f.run(reason)
            assertEquals(listOf(1L), f.skips)
            assertEquals(1, f.applies)
            assertEquals(2, f.publications)
            assertEquals(0, captures)
            tasks.single()()
            assertEquals(reason, 1, captures)
        }
    }

    @Test fun coveredSkipWithFailedRecheckDiscardsPendingCapture() {
        for (reason in listOf("boot", "locked-boot")) {
            for (failure in listOf("retry", "publication")) {
                val f = Fixture(); val tasks = mutableListOf<() -> Unit>()
                f.run("user-unlocked")
                f.coverage.captureAfterPublished({ tasks += it }) { fail("$reason $failure") }
                f.coverage.run(reason, false, { f.pending },
                    apply = { fail("must take covered skip"); good },
                    afterPass = { f.pending = failure == "retry"; failure != "publication" },
                    skipped = { f.skips += it })
                assertEquals(listOf(1L), f.skips)
                tasks.single()()
                f.pending = false
                f.run("boot")
                assertEquals(2, f.applies)
            }
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

    @Test fun unchangedGroupSkipsWithoutHomeRefreshAndKeepsPassAndCapture() {
        val f = Fixture(); f.run("user-unlocked")
        val tasks = mutableListOf<() -> Unit>()
        var captures = 0
        f.coverage.captureAfterPublished({ tasks += it }) { captures++ }
        val reads = mutableListOf<String>()
        repeat(2) {
            f.coverage.run("package-burst", false, { false },
                packageReasons = listOf("package:PACKAGE_CHANGED:one", "package:PACKAGE_CHANGED:two"),
                packageUnchanged = { name -> reads += name; true },
                apply = { fail("full pass"); good }, afterPass = { fail("Home refresh"); true },
                skipped = { f.skips += it }, afterPackageSkip = { true },
                afterPublished = { fail("skip must not create a new capture") })
        }
        assertEquals(listOf("one", "two", "one", "two"), reads)
        assertEquals(listOf(1L, 1L), f.skips)
        assertEquals(1, f.applies)
        assertEquals(1, f.publications)
        tasks.single()()
        assertEquals(1, captures)
    }

    @Test fun everyGroupFailureFallsBackToFullPass() {
        for (mode in listOf("changed", "added", "removed", "replaced", "before-snapshot",
            "locked", "retry", "absent", "throws", "empty")) {
            val f = Fixture()
            if (mode != "absent") f.run("user-unlocked")
            val before = f.applies
            val reasons = when (mode) {
                "added" -> listOf("package:PACKAGE_CHANGED:one", "package:PACKAGE_ADDED:two")
                "removed" -> listOf("package:PACKAGE_CHANGED:one", "package:PACKAGE_REMOVED:two")
                "replaced" -> listOf("package:PACKAGE_CHANGED:one", "package:PACKAGE_REPLACED:two")
                "empty" -> emptyList()
                else -> listOf("package:PACKAGE_CHANGED:one", "package:PACKAGE_CHANGED:two")
            }
            val reads = mutableListOf<String>()
            var refreshed = false
            f.coverage.run("package-burst", mode == "locked", { mode == "retry" },
                packageReasons = reasons, packageMaySkip = mode != "before-snapshot",
                packageUnchanged = { name ->
                    reads += name
                    if (mode == "throws" && name == "two") error("unreadable")
                    !(mode == "changed" && name == "two")
                },
                apply = { f.applies++; good }, afterPass = { refreshed = true; true },
                skipped = { fail(mode) }, afterPackageSkip = { fail(mode); true })
            assertEquals(mode, before + 1, f.applies)
            assertTrue(mode, refreshed)
            if (mode in listOf("changed", "throws")) assertEquals(mode, listOf("one", "two"), reads)
            else assertTrue(mode, reads.isEmpty())
        }
    }

    @Test fun onePreSnapshotEventMakesWholeDispatchedGroupIneligible() {
        val f = Fixture(); f.run("user-unlocked")
        val tasks = mutableListOf<() -> Unit>()
        var captured = false
        val burst = PackageEventBurst({ tasks += it }, { reasons, maySkip ->
            f.coverage.run("package-burst", false, { false },
                packageReasons = reasons, packageMaySkip = maySkip,
                packageUnchanged = { fail("receipt-time eligibility lost"); true },
                apply = { f.applies++; good }, afterPass = { f.publications++; true },
                skipped = { fail("must apply") })
        }, { captured })
        burst.add("package:PACKAGE_CHANGED:one")
        captured = true
        burst.add("package:PACKAGE_CHANGED:two")
        tasks.single()()
        assertEquals(2, f.applies)
        assertEquals(2, f.publications)
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
