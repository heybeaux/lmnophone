// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.boot

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.PackageName
import org.plainphone.core.WindowKind
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.hiddenApp
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.device.visible
import org.plainphone.policy.ApplyStage
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorResult
import org.plainphone.policy.JournalStore
import org.plainphone.policy.FloorRole
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.LockTaskFeature
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.PolicyHealth
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.Restriction
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.SessionPhase

/**
 * Boot paths (device-lockdown 4.11 and re-apply 4.3, PLA-190). At locked boot the journal is revoked
 * first, then only restrictions, the emergency floor and the never-hide guard run. On an unlocked start
 * the full plan runs; [BootOutcome.ready] (the only thing that lets Home show) needs every step read back.
 */
class BootReapplyTest {
    @get:Rule val tmp = TemporaryFolder()

    private val dialer = "com.google.android.dialer"
    private val phone = "com.android.phone"
    private val messages = "com.google.android.apps.messaging"
    private val plainphone = "org.plainphone"
    private val play = "com.android.vending"
    private val notes = "com.example.notes"

    private val port = FakeDevicePolicyPort(
        mapOf(dialer to visible(), phone to visible(), messages to visible(), plainphone to visible(),
            play to visible(), notes to visible()),
    )
    private lateinit var dir: File
    private val journal by lazy { FileJournalStore(dir, null, "model", "build-1") }
    private var decideFails = false
    private val decided = mutableListOf<Boolean>()

    private fun input(locked: Boolean) = PolicyInput(
        model = "model", fingerprint = "build-1",
        plainphone = PackageName(plainphone), messages = PackageName(messages),
        installed = listOf(
            InstalledPackage(PackageName(dialer), true, 1), InstalledPackage(PackageName(phone), true, 1),
            InstalledPackage(PackageName(messages), true, 1), InstalledPackage(PackageName(plainphone), false, 1),
            InstalledPackage(PackageName(play), true, 1), InstalledPackage(PackageName(notes), false, 1),
        ),
        identities = listOf(
            FloorIdentity(PackageName(dialer), FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source),
            FloorIdentity(PackageName(phone), FloorRole.EMERGENCY_DIALER, FloorRole.EMERGENCY_DIALER.source),
            FloorIdentity(PackageName(messages), FloorRole.SMS, FloorRole.SMS.source),
        ),
        lockedBoot = locked,
    )

    private fun reapply(filter: RestrictionFilter = RestrictionFilter.TEST_BUILD) = BootReapply(
        journal, port, setOf(PackageName(play)),
        { locked -> decided += locked; check(!decideFails) { "no facts" }; PolicyEngine.decide(input(locked)) },
        filter,
    )

    private fun names(vararg n: String) = n.map(::PackageName).toSet()

    private fun damageJournal() {
        val record = File(dir, "journal.rec")
        val bytes = record.readBytes()
        bytes[bytes.size / 2] = (bytes[bytes.size / 2].toInt() xor 0x01).toByte()
        record.writeBytes(bytes)
    }

    private fun seedEmptyJournal() {
        dir = tmp.newFolder("de")
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
    }

    @Test fun lockedBootWithADamagedJournalRevokesEverySessionPackageFirst() {
        seedEmptyJournal()
        damageJournal()
        assertEquals(SessionJournal.Damaged, journal.read())
        // A Play window was open when the power went: Play is visible and in lock task.
        port.lockTask = names(plainphone, dialer, play)

        val outcome = reapply().run(locked = true)

        assertTrue(outcome.ready)
        assertFalse(PackageName(play) in port.lockTask)
        assertTrue(port.hidden(play))
        assertEquals(SessionJournal.Empty, journal.read())
        assertFalse(journal.revocationPending())
        // The revoke ran before any policy step.
        val firstPolicyWrite = port.ops.indexOfFirst { it.startsWith("restrict") }
        val revoke = port.ops.indexOf("hide $play")
        assertTrue(revoke in 0 until firstPolicyWrite)
        assertEquals(listOf(true), decided)
    }

    @Test fun lockedBootRunsOnlyRestrictionsFloorAndGuard() {
        seedEmptyJournal()
        port.lockTask = names(plainphone)
        port.features = LockTaskFeature.entries.toSet()
        port.forbidden += setOf("home")

        val outcome = reapply().run(locked = true)

        assertTrue(outcome.ready)
        assertEquals(listOf(ApplyStage.RESTRICTIONS, ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD), outcome.apply!!.applied)
        // The floor adds the emergency UI to lock task and keeps what was there.
        assertEquals(names(plainphone, dialer, phone), port.lockTask)
        // No sweep at locked boot: the unapproved user app keeps its state.
        assertFalse(port.hidden(notes))
    }

    @Test fun theTestBuildFilterWithholdsDebuggingAndFactoryReset() {
        seedEmptyJournal()
        val outcome = reapply().run(locked = true)
        assertTrue(outcome.ready)
        assertEquals(setOf(Restriction.DISALLOW_DEBUGGING_FEATURES, Restriction.DISALLOW_FACTORY_RESET), outcome.withheld)
        assertFalse(Restriction.DISALLOW_DEBUGGING_FEATURES in port.restrictionSet)
        assertFalse(Restriction.DISALLOW_FACTORY_RESET in port.restrictionSet)
        assertTrue(Restriction.DISALLOW_SAFE_BOOT in port.restrictionSet)
        assertEquals(Restriction.entries.size - 2, port.restrictionSet.size)
    }

    @Test fun theReleaseFilterWithholdsNothing() {
        seedEmptyJournal()
        val outcome = reapply(RestrictionFilter.NONE).run(locked = false)
        assertTrue(outcome.ready)
        assertTrue(outcome.withheld.isEmpty())
        assertEquals(Restriction.entries.toSet() - outcome.withheld, port.restrictionSet)
    }

    @Test fun unlockedStartRestoresALostRestrictionAndLockTaskBeforeReady() {
        seedEmptyJournal()
        assertTrue(reapply().run(locked = false).ready)
        // Power cut: one restriction and the lock-task list are gone.
        port.restrictionSet -= Restriction.DISALLOW_SAFE_BOOT
        port.lockTask = emptySet()

        val outcome = reapply().run(locked = false)

        assertTrue(outcome.ready)
        assertTrue(Restriction.DISALLOW_SAFE_BOOT in port.restrictionSet)
        assertEquals(names(plainphone, dialer, phone), port.lockTask)
        assertEquals(PackageName(plainphone), port.homePackage)
        assertTrue(port.hidden(notes))
        assertTrue(outcome.apply!!.complete)
    }

    @Test fun aFailedReadbackKeepsHomeHidden() {
        seedEmptyJournal()
        port.lockTaskSticks = false
        val outcome = reapply().run(locked = false)
        assertFalse(outcome.ready)
        assertEquals(ApplyStage.EMERGENCY_FLOOR, outcome.apply!!.failedStage)
        // Restrictive intent stays latched for the next try.
        assertTrue(journal.revocationPending())
    }

    @Test fun noPlanStillRevokesSessionLockTaskButIsNotReady() {
        seedEmptyJournal()
        port.lockTask = names(plainphone, play)
        decideFails = true
        val outcome = reapply().run(locked = true)
        assertFalse(outcome.ready)
        // The plan-independent repair still ran (PLA-241 finding 2); nothing was hidden by it.
        assertEquals(listOf(ApplyStage.RESTRICTIONS, ApplyStage.EMERGENCY_FLOOR, ApplyStage.NEVER_HIDE_GUARD), outcome.apply!!.applied)
        assertTrue(outcome.needsAttention)
        assertFalse(PackageName(play) in port.lockTask)
        assertFalse(port.hidden(notes))
    }

    private val osFloor = FloorResult(names(dialer, phone, messages), names(dialer, phone), true)

    private fun fallback(committing: () -> Boolean, floor: () -> FloorResult? = { osFloor }) = BootReapply(
        journal, port, setOf(PackageName(play)), { check(false) { "no facts" }; PolicyEngine.decide(input(it)) },
        RestrictionFilter.TEST_BUILD, floor = floor, home = PackageName(plainphone), turnOffCommitting = committing,
    )

    @Test fun aFailedPlanRestoresCompiledRestrictionsFeaturesAndTheOsFloor() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp()
        port.state[PackageName(notes)] = hiddenApp()
        val outcome = fallback({ false }).run(locked = true)
        assertFalse(outcome.ready)
        assertEquals(Restriction.entries.toSet() - outcome.withheld, port.restrictionSet)
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
        assertFalse(port.hidden(dialer))
        assertTrue(port.lockTask.containsAll(names(dialer, phone, plainphone)))
        // Uncertain packages are left as they are: never unhidden by the fallback, never hidden.
        assertTrue(port.hidden(notes))
        assertFalse(port.hidden(play))
        assertTrue(journal.revocationPending())
    }

    @Test fun aCommittingTurnOffGetsOnlyTheFloorFromTheFallback() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp()
        val outcome = fallback({ true }).run(locked = false)
        assertFalse(outcome.ready)
        assertTrue(port.restrictionSet.isEmpty())
        assertTrue(port.features.isEmpty())
        assertFalse(port.hidden(dialer))
    }

    @Test fun anUnreadableTurnOffStateCountsAsCommitting() {
        seedEmptyJournal()
        fallback({ error("waiting store unreadable") }).run(locked = true)
        assertTrue(port.restrictionSet.isEmpty())
        assertTrue(port.features.isEmpty())
    }

    @Test fun aThrowingFloorReaderStillRestoresRestrictions() {
        seedEmptyJournal()
        val outcome = fallback({ false }, { error("roles unreadable") }).run(locked = true)
        assertFalse(outcome.ready)
        assertEquals(Restriction.entries.toSet() - outcome.withheld, port.restrictionSet)
    }

    private fun withStore(store: FlipFailureStore) = BootReapply(
        journal, port, setOf(PackageName(play)), { PolicyEngine.decide(input(it)) }, RestrictionFilter.TEST_BUILD,
        flipFailures = store,
    )

    @Test fun aFlipFailureSurvivesAProcessRestartUntilALaterFlipReadsBack() {
        seedEmptyJournal()
        val store = org.plainphone.device.storage.FileFlipFailureStore(tmp.newFolder("flips"))
        port.state[PackageName(dialer)] = hiddenApp()
        port.flushComponent = null
        assertTrue(PackageName(dialer) in withStore(store).run(locked = true).apply!!.flipFailures)

        // A new process with nothing to unhide still shows the failure.
        val afterRestart = withStore(store).run(locked = false)
        assertTrue(PackageName(dialer) in afterRestart.apply!!.flipFailures)
        assertTrue(afterRestart.needsAttention)

        // A later flip for that package that reads back is the defined repair.
        port.state[PackageName(dialer)] = hiddenApp()
        port.flushComponent = false
        val repaired = withStore(store).run(locked = false)
        assertFalse(PackageName(dialer) in repaired.apply!!.flipFailures)
        assertEquals(FlipFailures.NONE, store.read())
    }

    @Test fun acknowledgingClearsTheFlipFailure() {
        seedEmptyJournal()
        val store = InMemoryFlipFailureStore()
        port.state[PackageName(dialer)] = hiddenApp()
        port.flushComponent = null
        val boot = withStore(store)
        boot.run(locked = true)
        boot.acknowledgeFlipFailures()
        assertEquals(FlipFailures.NONE, store.read())
        assertTrue(boot.run(locked = false).apply!!.flipFailures.isEmpty())
    }

    @Test fun aThrowingOnStartStillRepairsTheFloorAndIsNotReady() {
        dir = tmp.newFolder("de")
        val throwing = object : JournalStore by journal { override fun read(): SessionJournal = throw java.io.IOException("unreadable") }
        port.state[PackageName(dialer)] = hiddenApp()
        val outcome = BootReapply(throwing, port, setOf(PackageName(play)), { PolicyEngine.decide(input(it)) }, RestrictionFilter.TEST_BUILD)
            .run(locked = true)
        assertFalse(outcome.ready)
        assertFalse(port.hidden(dialer))
        assertTrue(Restriction.DISALLOW_SAFE_BOOT in port.restrictionSet)
    }

    @Test fun anOpenSessionIsClosedOnEveryStart() {
        dir = tmp.newFolder("de")
        val access = SessionJournal.restore("s1", WindowKind.PLAY, names(play), BootTime("boot-1", 600_000), SessionPhase.OPEN, null)
        assertTrue(journal.compareAndSet(SessionJournal.Damaged, access))
        port.lockTask = names(plainphone, play)

        val outcome = reapply().run(locked = false)

        assertTrue(outcome.ready)
        assertFalse(PackageName(play) in port.lockTask)
        assertTrue(port.hidden(play))
        assertEquals(SessionJournal.Empty, journal.read())
    }

    @Test fun aNeverHideSessionPackageLeavesLockTaskButIsNotHidden() {
        seedEmptyJournal()
        port.state[PackageName(messages)] = hiddenApp()
        port.lockTask = names(plainphone, messages)
        val outcome = BootReapply(journal, port, names(messages), { PolicyEngine.decide(input(it)) }, RestrictionFilter.TEST_BUILD)
            .run(locked = true)
        assertTrue(outcome.ready)
        assertFalse(port.hidden(messages))
        assertFalse(port.ops.contains("hide $messages"))
    }

    // --- Locked boot after the list or the features were lost (PLA-232 findings 4 and 9, spec 722610b) ---

    @Test fun lockedBootRestoresPlainphoneAndTheEmergencyUiAfterTheListWasLost() {
        seedEmptyJournal()
        port.lockTask = emptySet()
        port.features = LockTaskFeature.entries.toSet()

        val outcome = reapply().run(locked = true)

        assertTrue(outcome.ready)
        assertEquals(names(plainphone, dialer, phone), port.lockTask)
    }

    @Test fun lockedBootRestoresTheFixedFeaturesAfterTheyWereLost() {
        seedEmptyJournal()
        port.lockTask = names(plainphone, dialer, phone)
        port.features = setOf(LockTaskFeature.GLOBAL_ACTIONS) // mFlags=16, as in the PLA-232 evidence

        val outcome = reapply().run(locked = true)

        assertTrue(outcome.ready)
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
        assertTrue(LockTaskFeature.BLOCK_ACTIVITY_START_IN_TASK in port.features)
        assertEquals(names(plainphone, dialer, phone), port.lockTask)
    }

    @Test fun lockedBootRestoresTheListAndTheFeaturesLostTogether() {
        seedEmptyJournal()
        port.lockTask = emptySet()
        port.features = setOf(LockTaskFeature.GLOBAL_ACTIONS)

        val outcome = reapply().run(locked = true)

        assertTrue(outcome.ready)
        assertEquals(names(plainphone, dialer, phone), port.lockTask)
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
    }

    @Test fun lockedBootIsNotReadyWhenTheListDoesNotReadBack() {
        seedEmptyJournal()
        port.lockTask = emptySet()
        port.lockTaskSticks = false

        assertFalse(reapply().run(locked = true).ready)
    }

    // --- Emergency repair does not depend on the revoke (PLA-232 finding 5, spec 722610b) ---

    @Test fun aFailedRevokeStillRepairsTheFloorFeaturesAndGuardButIsNotReady() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp()
        port.lockTask = names(plainphone, play)
        port.hidesStick = false

        val outcome = reapply().run(locked = false)

        assertFalse(outcome.ready)
        assertTrue(journal.revocationPending())
        assertFalse(port.hidden(dialer))
        assertTrue(PackageName(dialer) in port.lockTask)
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
        // Nothing past the guard: no Home preference while the revoke is pending.
        assertNull(port.homePackage)
    }

    @Test fun aFailedRestrictionReadbackStillRepairsTheFloor() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp()
        port.restrictionsStick = false

        val outcome = reapply().run(locked = true)

        assertFalse(outcome.ready)
        assertFalse(port.hidden(dialer))
        assertEquals(LockTaskFeature.entries.toSet(), port.features)
    }

    @Test fun aFailedJournalWriteStillRepairsTheFloor() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp()
        val failing = FileJournalStore(dir, null, "model", "build-1") { throw java.io.IOException("disk full") }
        val boot = BootReapply(failing, port, setOf(PackageName(play)), { PolicyEngine.decide(input(it)) }, RestrictionFilter.TEST_BUILD)

        val outcome = boot.run(locked = true)

        assertFalse(outcome.ready)
        assertFalse(port.hidden(dialer))
    }

    // --- A disabled floor package (PLA-232 finding 3) ---

    @Test fun aDisabledFloorPackageKeepsCallsReachableButBlocksSetup() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp().copy(enabled = false)

        val outcome = reapply().run(locked = false)

        assertTrue(outcome.ready)
        assertFalse(port.hidden(dialer))
        assertTrue(PackageName(dialer) in port.lockTask)
        assertEquals(names(dialer), outcome.disabledFloor)
        assertTrue(outcome.needsAttention)
        assertFalse(outcome.setupMayFinish)
    }

    // --- The attention signal reaches the outcome (PLA-232 finding 8) ---

    @Test fun corruptPolicyIsReadyButNeedsAttention() {
        seedEmptyJournal()
        val boot = BootReapply(journal, port, setOf(PackageName(play)),
            { PolicyEngine.decide(input(it).copy(health = PolicyHealth.BOTH_INVALID)) }, RestrictionFilter.TEST_BUILD)

        val outcome = boot.run(locked = false)

        assertTrue(outcome.ready)
        assertTrue(outcome.needsAttention)
    }

    @Test fun aFailedFlipIsReadyButNeedsAttention() {
        seedEmptyJournal()
        port.state[PackageName(dialer)] = hiddenApp()
        port.flushComponent = null

        val outcome = reapply().run(locked = false)

        assertTrue(outcome.ready)
        assertEquals(names(dialer), outcome.apply!!.flipFailures)
        assertTrue(outcome.needsAttention)
    }

    @Test fun aCleanRunNeedsNoAttention() {
        seedEmptyJournal()
        val outcome = reapply().run(locked = false)
        assertTrue(outcome.ready)
        assertFalse(outcome.needsAttention)
    }
}
