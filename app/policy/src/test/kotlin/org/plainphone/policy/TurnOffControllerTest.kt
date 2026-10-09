// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class TurnOffControllerTest {
    private val person = TrustedPersonId("holder")
    private val operation = WaitingOperation(WaitingIdentity("off-request", "synthetic-code"), person, 0, 0,
        Mode.TRUSTED, RunningTime("epoch", 0), WaitingPhase.COMMITTING, mapOf(person to 0L))
    private val store = Store(WaitingState(1, operation))
    private val effects = Effects()
    private fun controller() = TurnOffController(store, effects) { null }
    private val controller = controller()
    private fun tick() = controller.tick("off-request")
    private fun restoreAll() { repeat(TurnOffStep.entries.size) { assertEquals(TurnOffOutcome.PROGRESS, tick().outcome) } }

    @Test fun `all recorded changes restore in order and off requires observed owner absence`() {
        restoreAll()
        assertEquals(TurnOffStep.entries.toList(), effects.applied)
        assertEquals(TurnOffStep.entries.size, store.state!!.operation!!.restoredSteps)
        assertTrue(effects.owner == true)
        val result = tick()
        assertEquals(TurnOffOutcome.OFF, result.outcome)
        assertFalse(result.journalPending)
        assertEquals(listOf("revoke", "owner"), effects.events.take(2))
        assertTrue(effects.events.indexOf("home") < effects.events.indexOf("remove"))
        assertTrue(effects.events.indexOf("dialer") < effects.events.indexOf("remove"))
        assertEquals(WaitingPhase.COMPLETED, store.state!!.operation!!.phase)
        assertEquals(WaitingEvent.COMPLETED, store.state!!.texts.single().event)
        assertEquals(mapOf(person to 0L), store.state!!.texts.single().recipientGenerations)
        assertEquals(TurnOffOutcome.OFF, tick().outcome)
        assertEquals(1, store.state!!.texts.size)
    }

    @Test fun `pending cancelled rollback and stale requests never restore anything`() {
        for (phase in listOf(WaitingPhase.PENDING, WaitingPhase.CANCELLED, WaitingPhase.ROLLING_BACK)) {
            store.state = WaitingState(2, operation.withPhase(phase))
            assertEquals(TurnOffOutcome.IGNORED, controller().tick("off-request").outcome)
        }
        store.state = WaitingState(3, operation)
        assertEquals(TurnOffOutcome.IGNORED, controller().tick("old-request").outcome)
        assertTrue(effects.applied.isEmpty())
        assertFalse("remove" in effects.events)
    }

    @Test fun `failed effects readbacks and checkpoint writes never skip a step`() {
        effects.restoreWorks = false
        assertEquals(TurnOffStep.USER_RESTRICTIONS, tick().failedStep)
        assertEquals(0, store.state!!.operation!!.restoredSteps)
        effects.restoreWorks = true
        effects.readWorks = false
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        assertEquals(0, store.state!!.operation!!.restoredSteps)
        effects.readWorks = true
        store.fail = true
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        assertEquals(0, store.state!!.operation!!.restoredSteps)
        store.fail = false
        assertEquals(TurnOffOutcome.PROGRESS, controller().tick("off-request").outcome)
        assertEquals(1, store.state!!.operation!!.restoredSteps)
        assertTrue(effects.applied.all { it == TurnOffStep.USER_RESTRICTIONS })
    }

    @Test fun `restart after every step revokes first and resumes next unread step`() {
        for (step in TurnOffStep.entries) {
            effects.events.clear()
            assertEquals(TurnOffOutcome.PROGRESS, controller().tick("off-request").outcome)
            assertEquals("revoke", effects.events.first())
            assertEquals(step, effects.applied.last())
        }
        assertEquals(TurnOffOutcome.OFF, controller().tick("off-request").outcome)
    }

    @Test fun `interruption before and after each checkpoint repeats only uncommitted progress`() {
        for (step in TurnOffStep.entries) for (afterWrite in listOf(false, true)) {
            store.state = WaitingState(1, operation.withRestoredSteps(step.ordinal))
            effects.owner = true
            effects.applied.clear()
            store.crash = true; store.crashAfterWrite = afterWrite
            assertFailsWith<IllegalStateException> { controller().tick("off-request") }
            store.crash = false
            assertEquals(step.ordinal + if (afterWrite) 1 else 0, store.state!!.operation!!.restoredSteps)
            controller().tick("off-request")
            if (!afterWrite) assertEquals(listOf(step, step), effects.applied)
        }
    }

    @Test fun `interruption after effect or readback but before checkpoint repeats the same step`() {
        for (stage in listOf("restore", "read")) {
            store.state = WaitingState(1, operation)
            effects.crashAt = stage
            assertFailsWith<IllegalStateException> { controller().tick("off-request") }
            assertEquals(0, store.state!!.operation!!.restoredSteps)
            effects.crashAt = null
            assertEquals(TurnOffOutcome.PROGRESS, controller().tick("off-request").outcome)
            assertEquals(1, store.state!!.operation!!.restoredSteps)
        }
    }

    @Test fun `successor failure and owner removal failure never claim off`() {
        restoreAll()
        effects.home = false
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        assertFalse("remove" in effects.events)
        effects.home = true; effects.dialer = false
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        assertFalse("remove" in effects.events)
        effects.dialer = true; effects.removes = false
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        assertEquals(WaitingPhase.COMMITTING, store.state!!.operation!!.phase)
        effects.removes = true
        assertEquals(TurnOffOutcome.OFF, tick().outcome)
    }

    @Test fun `completed checkpoints are revalidated before owner removal`() {
        restoreAll()
        effects.mismatch = TurnOffStep.HIDDEN_APPS
        val result = controller().tick("off-request")
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, result.outcome)
        assertEquals(TurnOffStep.HIDDEN_APPS, result.failedStep)
        assertEquals(TurnOffStep.HIDDEN_APPS.ordinal, store.state!!.operation!!.restoredSteps)
        assertFalse("remove" in effects.events)
        effects.mismatch = null
        assertEquals(TurnOffOutcome.PROGRESS, controller().tick("off-request").outcome)
        assertEquals(TurnOffStep.HIDDEN_APPS, effects.applied.last())
    }

    @Test fun `device owner absence completes despite failed completion write then queues receipt on retry`() {
        effects.owner = false
        store.fail = true
        assertTrue(tick().journalPending)
        assertEquals(TurnOffOutcome.OFF, tick().outcome)
        store.fail = false
        assertFalse(controller().tick("off-request").journalPending)
        assertEquals(WaitingPhase.COMPLETED, store.state!!.operation!!.phase)
        assertEquals(1, store.state!!.texts.size)
        assertTrue(effects.applied.isEmpty())
    }

    @Test fun `owner removal crash is completed on restart without more privileged effects`() {
        restoreAll()
        effects.crashAt = "remove"
        assertFailsWith<IllegalStateException> { tick() }
        assertFalse(effects.owner!!)
        effects.crashAt = null
        val count = effects.applied.size
        assertEquals(TurnOffOutcome.OFF, controller().tick("off-request").outcome)
        assertEquals(count, effects.applied.size)
    }

    @Test fun `damaged journal or unknown owner never guesses forward progress`() {
        store.state = null
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        effects.owner = null
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        effects.owner = false
        assertEquals(TurnOffOutcome.OFF, tick().outcome)
        assertTrue(effects.applied.isEmpty())
    }

    @Test fun `failed startup revocation prevents restore and is retried`() {
        effects.revokeWorks = false
        assertEquals(TurnOffOutcome.NEEDS_ATTENTION, tick().outcome)
        assertEquals(listOf("revoke", "owner"), effects.events)
        effects.revokeWorks = true
        assertEquals(TurnOffOutcome.PROGRESS, tick().outcome)
    }

    @Test fun `observed owner absence reports off even when revocation cannot run`() {
        effects.revokeWorks = false
        effects.owner = false
        assertEquals(TurnOffOutcome.OFF, tick().outcome)
        assertTrue(effects.applied.isEmpty())
    }

    @Test fun `boot and package reapply between restore steps cannot relock restored categories`() {
        val dialer = PackageName("test.systemdialer")
        val facts = PolicyInput("model", "build", PackageName("test.home"), PackageName("test.sms"),
            listOf(InstalledPackage(dialer, true, 1)),
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)))
        for (step in TurnOffStep.entries) {
            assertEquals(TurnOffOutcome.PROGRESS, tick().outcome)
            val plan = PolicyEngine.decide(facts.copy(waitingOperation = store.state!!.operation,
                lockedBoot = step.ordinal % 2 == 0))
            // A normal applier would put restrictions back and cause the sequencer's final check to rewind.
            if (plan.steps.any { it.stage == ApplyStage.RESTRICTIONS }) effects.mismatch = TurnOffStep.USER_RESTRICTIONS
            assertTrue(plan.steps.none { it.stage in setOf(ApplyStage.HIDES, ApplyStage.LOCK_TASK, ApplyStage.HOME) })
        }
        assertEquals(TurnOffOutcome.OFF, tick().outcome)
    }

    private class Store(var state: WaitingState?) : WaitingChangeStore {
        var fail = false
        var crash = false
        var crashAfterWrite = false
        override fun read() = state
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean {
            if (crash && !crashAfterWrite) error("crash before checkpoint")
            if (fail || state?.revision != expected.revision) return false
            state = replacement
            if (crash) error("crash after checkpoint")
            return true
        }
    }
    private class Effects : TurnOffEffects {
        val events = mutableListOf<String>()
        val applied = mutableListOf<TurnOffStep>()
        var owner: Boolean? = true
        var revokeWorks = true
        var restoreWorks = true
        var readWorks = true
        var home = true
        var dialer = true
        var removes = true
        var mismatch: TurnOffStep? = null
        var crashAt: String? = null
        override fun revokeTemporaryAccess(): Boolean { events += "revoke"; return revokeWorks }
        override fun isDeviceOwner(): Boolean? { events += "owner"; return owner }
        override fun restoreRecorded(step: TurnOffStep): Boolean {
            events += "restore:$step"; applied += step
            if (crashAt == "restore") error("crash after effect")
            return restoreWorks
        }
        override fun readBackRecorded(step: TurnOffStep): Boolean {
            events += "read:$step"
            if (crashAt == "read") error("crash after readback")
            return readWorks && mismatch != step
        }
        override fun anotherHomeResolves(): Boolean { events += "home"; return home }
        override fun systemDialerResolves(): Boolean { events += "dialer"; return dialer }
        override fun removeDeviceOwner(): Boolean {
            events += "remove"
            if (removes) owner = false
            if (crashAt == "remove") error("crash after removal")
            return removes
        }
    }
}
