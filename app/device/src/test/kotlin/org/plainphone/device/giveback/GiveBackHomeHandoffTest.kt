// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.giveback

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*
import org.plainphone.device.FakeDevicePolicyPort
import org.plainphone.policy.*

/** Host model of the Home alias and journal; does not claim Android resolver coverage. */
class GiveBackHomeHandoffTest {
    private val home = GiveBackAction(GiveBackKind.PREFERRED_HOME, "org.plainphone")
    private val lockTask = GiveBackAction(GiveBackKind.LOCK_TASK)
    private val person = TrustedPersonId("holder")
    private var store = Store(WaitingState(1, WaitingOperation(
        WaitingIdentity("off", "code"), person, 0, 0, Mode.TRUSTED,
        RunningTime("epoch", 0), WaitingPhase.COMMITTING, mapOf(person to 0L),
    )))
    private val platform = HomePlatform()

    private fun run(retry: Boolean = false): GiveBackResult = GiveBackController(
        store, ReadbackGiveBackPort(FakeDevicePolicyPort(emptyMap()), platform),
    ).run("off", BatteryState(80, true), retry)

    @Test fun noSuccessorKeepsHomeAndOwnerUntilExplicitRetryCanHandOff() {
        assertHomeRetained(run())
        assertFalse(platform.locked)
        assertHomeRetained(run(retry = true))

        platform.successor = true
        assertEquals(GiveBackOutcome.OFF, run(retry = true).outcome)
        assertEquals(1, platform.homeWrites)
        assertFalse(platform.homeEntryEnabled)
        assertFalse(platform.owner)
    }

    @Test fun restartRetainsFailedHomeAndRetryRouteWithoutReplayingTheWrite() {
        assertEquals(GiveBackOutcome.FAILED, run().outcome)
        // Recreate the store wrapper, controller and readback port from saved state.
        store = Store(store.state)
        assertHomeRetained(run())
        assertFalse(platform.locked)

        // A newly available successor alone must not resume a FAILED item.
        platform.successor = true
        assertHomeRetained(run())
        assertEquals(GiveBackOutcome.OFF, run(retry = true).outcome)
        assertEquals(1, platform.homeWrites)
        assertFalse(platform.owner)
    }

    private fun assertHomeRetained(result: GiveBackResult) {
        // FAILED is the runtime's Try again outcome; exercise that retry above.
        assertEquals(GiveBackOutcome.FAILED, result.outcome)
        assertEquals(1, result.remaining)
        assertEquals(GiveBackStatus.FAILED, store.state.giveBack!!.entries.single { it.action == home }.status)
        assertEquals(WaitingPhase.COMMITTING, store.state.operation!!.phase)
        assertEquals(0, platform.homeWrites)
        assertTrue(platform.homeEntryEnabled)
        assertTrue(platform.owner)
    }

    private class Store(var state: WaitingState) : WaitingChangeStore {
        override fun read() = state
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
            if (state !== expected) return false
            state = replacement
            return true
        }
    }

    private inner class HomePlatform : GiveBackPort {
        var owner = true
        var locked = true
        var homeEntryEnabled = true
        var successor = false
        var homeWrites = 0
        override fun isDeviceOwner() = owner
        override fun inventory() = listOf(lockTask, home)
        override fun restore(action: GiveBackAction) {
            when (action.kind) {
                GiveBackKind.LOCK_TASK -> locked = false
                GiveBackKind.PREFERRED_HOME -> { homeWrites++; homeEntryEnabled = false }
                else -> error("unexpected action")
            }
        }
        override fun readBack(action: GiveBackAction) = when (action.kind) {
            GiveBackKind.LOCK_TASK -> !locked
            GiveBackKind.PREFERRED_HOME -> !homeEntryEnabled && successor
            else -> error("unexpected action")
        }
        override fun anotherHomeResolves() = successor
        override fun systemDialerResolves() = true
        override fun removeDeviceOwner() { owner = false }
    }
}
