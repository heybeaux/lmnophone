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
import org.plainphone.device.authority.ModeSwitchRig.Companion.ALEX
import org.plainphone.device.authority.ModeSwitchRig.Companion.SAM
import org.plainphone.device.boot.BootOutcome
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PeopleResetProtectionEffects
import org.plainphone.policy.WaitingPhase

/**
 * PLA-97 slice 6: the runtime's directed callback drives a committing or rolling-back mode switch
 * only when it is given [DeviceModeSwitchEffects]; the people-only driver leaves mode switches
 * alone. The Android effects revoke first, then read back the protected state, then run the
 * reset-protection steps, and any throw is a failed step. JVM tests with synthetic Android steps.
 */
class ModeSwitchDriverTest {
    @get:Rule val tmp = TemporaryFolder()
    private val ready = BootOutcome(true, false, null, emptySet())
    private val notReady = BootOutcome(false, false, null, emptySet())

    /** Records the Android steps in order; [throwAt] makes that step throw. */
    private class Android(val reset: ModeSwitchFakeEffects) {
        val calls = mutableListOf<String>()
        var throwAt: String? = null
        fun step(name: String): Boolean { calls += name; if (throwAt == name) throw IllegalStateException(name); return true }
        fun effects(store: JointAuthorityStore) = DeviceModeSwitchEffects({ step("revoke") }, { step("readback") }, object : PeopleResetProtectionEffects {
            override fun setResetProtection(userIds: Set<String>) = step("set") && reset.setResetProtection(userIds)
            override fun broadcastResetProtectionChanged() = step("broadcast") && reset.broadcastResetProtectionChanged()
            override fun readBackResetProtection(userIds: Set<String>) = step("reset-readback") && reset.readBackResetProtection(userIds)
        }, store)
    }

    private fun committing(): Pair<ModeSwitchRig, String> {
        val rig = ModeSwitchRig(Mode.SELF_MANAGED, tmp.newFolder())
        val id = rig.toSupervised(); rig.mature(id)
        rig.wire()
        return rig to id
    }

    @Test fun theModeDriverAppliesAndCompletesAfterAReadyStartupInTheRequiredOrder() {
        val (rig, _) = committing()
        val android = Android(rig.effects)
        val driver = PeopleCommitDriver(rig.store, android.effects(rig.store), rig.clock, rig.vault)
        assertNull(driver.driveAfter(notReady))
        assertTrue(android.calls.isEmpty())
        assertEquals(PeopleChangeOutcome.APPLIED, driver.driveAfter(ready)?.outcome)
        assertEquals(listOf("revoke", "readback", "set", "broadcast", "reset-readback"), android.calls)
        assertEquals(Mode.TRUSTED, rig.credentials().mode); assertEquals(setOf(ALEX), rig.credentials().people.keys)
        assertEquals(PeopleChangeOutcome.COMPLETED, driver.driveAfter(ready)?.outcome)
        assertEquals(WaitingPhase.COMPLETED, rig.journal().operation?.phase)
        // Nothing left to drive.
        assertNull(driver.driveAfter(ready))
    }

    @Test fun theModeDriverFinishesARollbackInItsDirection() {
        val (rig, id) = committing()
        val stop = checkNotNull(rig.people.prepareStop(id, SAM))
        assertEquals(PeopleChangeOutcome.ROLLING_BACK, rig.people.stop(stop, rig.auth(SAM, stop.action)).outcome)
        val android = Android(rig.effects)
        val driver = PeopleCommitDriver(rig.store, android.effects(rig.store), rig.clock, rig.vault)
        assertEquals(PeopleChangeOutcome.CANCELLED, driver.driveAfter(ready)?.outcome)
        assertEquals(Mode.SELF_MANAGED, rig.credentials().mode)
        assertEquals(setOf("account-sam", "account-backup"), rig.effects.applied)
    }

    @Test fun aThrowingAndroidStepIsAFailedStepAndChangesNoCredentials() {
        for (step in listOf("revoke", "readback")) {
            val (rig, _) = committing()
            val android = Android(rig.effects).also { it.throwAt = step }
            val driver = PeopleCommitDriver(rig.store, android.effects(rig.store), rig.clock, rig.vault)
            val result = driver.driveAfter(ready)
            assertEquals(step, PeopleChangeOutcome.NEEDS_ATTENTION, result?.outcome)
            assertFalse(step, "set" in android.calls)
            assertEquals(step, Mode.SELF_MANAGED, rig.credentials().mode)
            android.throwAt = null
            assertEquals(step, PeopleChangeOutcome.APPLIED, driver.driveAfter(ready)?.outcome)
        }
    }

    @Test fun thePeopleOnlyDriverLeavesModeSwitchesAlone() {
        val (rig, _) = committing()
        val peopleOnly = object : PeopleResetProtectionEffects {
            override fun setResetProtection(userIds: Set<String>) = throw AssertionError("set")
            override fun broadcastResetProtectionChanged() = throw AssertionError("broadcast")
            override fun readBackResetProtection(userIds: Set<String>) = throw AssertionError("readback")
        }
        assertNull(PeopleCommitDriver(rig.store, peopleOnly, rig.clock, rig.vault).driveAfter(ready))
        assertEquals(WaitingPhase.COMMITTING, rig.journal().operation?.phase)
        assertEquals(Mode.SELF_MANAGED, rig.credentials().mode)
    }
}
