// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.Mode
import org.plainphone.core.PackageName
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.device.authority.ModeSwitchRig.Companion.SAM
import org.plainphone.device.boot.BootReapply
import org.plainphone.device.boot.RestrictionFilter
import org.plainphone.device.storage.FileJournalStore
import org.plainphone.device.visible
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PolicyEngine
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.WaitingPhase

/**
 * PLA-439 R1: a locked-boot pass applies only part of the protected policy, so it never authorizes
 * a mode switch step, forward or rollback. The step waits for a ready unlocked pass, which has
 * applied and read back the full plan. JVM tests over the real BootReapply with a fake Android port.
 */
class ModeSwitchLockedBootTest {
    @get:Rule val tmp = TemporaryFolder()
    private val notes = PackageName("test.notes")

    private inner class Runtime(val rig: ModeSwitchRig) {
        private val input = ModeSwitchRig.POLICY.copy(
            installed = ModeSwitchRig.POLICY.installed + InstalledPackage(notes, false, 1), inventory = null)
        val port = FakeDevicePolicyPort(input.installed.associate { it.name.value to visible() })
        private val journal = FileJournalStore(tmp.newFolder(), null, "model", "build").also {
            assertTrue(it.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty))
        }
        val boot = BootReapply(journal, port, setOf(PackageName("com.android.vending")),
            { locked -> PolicyEngine.decide(input.copy(lockedBoot = locked)) }, RestrictionFilter.TEST_BUILD)
        val driver = PeopleCommitDriver(rig.store,
            DeviceModeSwitchEffects({ boot.sessions.onStart() }, { boot.readBackProtected() }, rig.effects, rig.store),
            rig.clock, rig.vault)
        fun pass(locked: Boolean) = boot.run(locked).also { assertTrue("ready", it.ready) }
    }

    private fun committing(): ModeSwitchRig {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        rig.mature(rig.toSupervised())
        rig.wire()
        return rig
    }

    @Test fun aLockedPassNeverStepsTheSwapAndTheNextUnlockedPassDoes() {
        val rig = committing()
        val runtime = Runtime(rig)
        val locked = runtime.pass(true)
        assertNull(runtime.driver.driveAfter(locked))
        assertFalse(runtime.boot.readBackProtected()) // a locked plan is never the complete protected state
        assertTrue(rig.effects.calls.isEmpty())
        assertFalse(runtime.port.hidden(notes.value))
        assertEquals(Mode.SELF_MANAGED, rig.credentials().mode)
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation?.phase)
        val unlocked = runtime.pass(false)
        assertTrue(runtime.port.hidden(notes.value))
        assertEquals(PeopleChangeOutcome.APPLIED, runtime.driver.driveAfter(unlocked)?.outcome)
        assertEquals(Mode.TRUSTED, rig.credentials().mode)
        assertEquals(PeopleChangeOutcome.COMPLETED, runtime.driver.driveAfter(runtime.pass(false))?.outcome)
    }

    @Test fun aLockedPassNeverStepsARollbackOrACompletionEither() {
        val rig = committing()
        val id = checkNotNull(rig.journal().operation).identity.requestId
        val stop = checkNotNull(rig.people.prepareStop(id, SAM))
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(SAM, stop.action)).outcome)
        val runtime = Runtime(rig)
        assertNull(runtime.driver.driveAfter(runtime.pass(true)))
        assertTrue(rig.effects.calls.isEmpty())
        assertEquals(WaitingPhase.ROLLING_BACK, rig.journal().operation?.phase)
        assertEquals(PeopleChangeOutcome.CANCELLED, runtime.driver.driveAfter(runtime.pass(false))?.outcome)

        // Completion after a landed swap waits for an unlocked pass too.
        val forward = committing()
        val second = Runtime(forward)
        assertEquals(PeopleChangeOutcome.APPLIED, second.driver.driveAfter(second.pass(false))?.outcome)
        val calls = forward.effects.calls.size
        assertNull(second.driver.driveAfter(second.pass(true)))
        assertEquals(calls, forward.effects.calls.size)
        assertEquals(WaitingPhase.COMMITTING, forward.journal().operation?.phase)
        assertEquals(PeopleChangeOutcome.COMPLETED, second.driver.driveAfter(second.pass(false))?.outcome)
    }
}
