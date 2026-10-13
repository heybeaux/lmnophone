// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*

/** PLA-494: Change PIN on the preview controls. Synthetic PINs only. */
class PreviewChangePinTest {
    @get:Rule val tmp = TemporaryFolder()
    private var sequence = 0
    private var now = 0L
    private val clock = RunningClock { ClockReading(RunningTime("epoch", now), BootTime("boot", now)) }
    private fun old() = CharArray(6) { '1' }
    private fun new() = CharArray(8) { '2' }
    private fun wrong() = CharArray(6) { '9' }
    private inner class Rig {
        val store = JointAuthorityStore(tmp.newFolder())
        val vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        val menu = PreviewPinAccess(store, vault)
        val change = PreviewChangePin(store, vault)
        val turnOff = PreviewTurnOff(store, vault, clock)
        val checkpoints = WaitingCheckpoints(store, clock, { BatteryState(80, true) },
            PersonAuthorityConsumer { _, _, _ -> false }, testBuild = true)
        init { assertTrue(menu.enroll(old()) is PinEnrollmentResult.Created) }
        fun self() = store.credentials.read()!!.people.values.single()
    }

    @Test fun rightOldPinChangesItTheNewOneOpensAndTheOldOneIsRefused() {
        val r = Rig()
        val before = r.self()
        assertEquals(PinAccessResult.Open, r.change.authenticate("flow", old()))
        assertEquals(PinChangeOutcome.CHANGED, r.change.change("flow", new()))
        assertEquals(before.generation + 1, r.self().generation)
        assertTrue(r.menu.authenticate(old()) is PinAccessResult.Wrong)
        assertEquals(PinAccessResult.Open, r.menu.authenticate(new()))
        // And back again, as on the spare.
        assertEquals(PinAccessResult.Open, r.change.authenticate("back", new()))
        assertEquals(PinChangeOutcome.CHANGED, r.change.change("back", old()))
        assertEquals(PinAccessResult.Open, r.menu.authenticate(old()))
    }

    @Test fun wrongOldPinCountsTowardLockoutAndALockedOutPersonCannotChange() {
        val r = Rig()
        assertTrue(r.change.authenticate("flow", wrong()) is PinAccessResult.Wrong)
        assertEquals(PinChangeOutcome.FAILED, r.change.change("flow", new()))
        assertEquals(1, r.self().pinCounter.failures)
        repeat(4) { r.change.authenticate("flow", wrong()) }
        assertEquals(5, r.self().pinCounter.failures)
        assertTrue(r.change.authenticate("flow", old()) is PinAccessResult.Delayed)
        assertEquals(PinChangeOutcome.FAILED, r.change.change("flow", new()))
        assertTrue(r.menu.authenticate(old()) is PinAccessResult.Delayed)
        now += 30_000
        assertEquals(PinAccessResult.Open, r.menu.authenticate(old()))
    }

    @Test fun noChangeWithoutAFreshGrantForThisFlow() {
        val r = Rig()
        val generation = r.self().generation
        assertEquals(PinChangeOutcome.FAILED, r.change.change("none", new()))
        // The menu PIN is consumed at once and cannot change the PIN.
        assertEquals(PinAccessResult.Open, r.menu.authenticate(old()))
        assertEquals(PinChangeOutcome.FAILED, r.change.change("menu", new()))
        r.change.authenticate("closed", old()); r.change.close("closed")
        assertEquals(PinChangeOutcome.FAILED, r.change.change("closed", new()))
        r.change.authenticate("one", old()); r.change.authenticate("two", old())
        assertEquals(PinChangeOutcome.FAILED, r.change.change("one", new()))
        assertEquals(PinChangeOutcome.FAILED, PreviewChangePin(r.store, r.vault).change("two", new()))
        r.change.authenticate("late", old()); now += 120_000
        assertEquals(PinChangeOutcome.FAILED, r.change.change("late", new()))
        r.change.authenticate("spent", old())
        assertEquals(PinChangeOutcome.FAILED, r.change.change("spent", CharArray(5) { '3' }))
        assertEquals(PinChangeOutcome.FAILED, r.change.change("spent", new()))
        assertEquals(generation, r.self().generation)
        assertEquals(PinAccessResult.Open, r.menu.authenticate(old()))
    }

    @Test fun aWaitingChangeStartedWithTheOldPinIsCancelledByTheNextCheckpoint() {
        val r = Rig()
        assertEquals(PinAccessResult.Open, r.turnOff.authenticate("off", old()))
        assertEquals(WaitingOutcome.PENDING, r.turnOff.start("off", true, true))
        assertTrue(r.change.waitingChange())
        assertEquals(WaitingOutcome.PENDING, r.checkpoints.checkpoint())
        assertNotNull(r.checkpoints.notice())
        assertEquals(PinAccessResult.Open, r.change.authenticate("flow", old()))
        assertEquals(PinChangeOutcome.CHANGED, r.change.change("flow", new()))
        assertEquals(WaitingOutcome.CANCELLED, r.checkpoints.checkpoint())
        val op = r.store.waitingStore.read()!!.operation!!
        assertEquals(WaitingPhase.CANCELLED, op.phase)
        assertTrue(r.store.waitingStore.read()!!.texts.any { it.event == WaitingEvent.DEPENDENCY_CHANGED })
        assertNull(r.checkpoints.notice())
        assertNull(r.checkpoints.pendingRequest())
        assertFalse(r.change.waitingChange())
    }

    @Test fun noWaitingChangeByDefault() { assertFalse(Rig().change.waitingChange()) }

    @Test fun pinArraysAreZeroed() {
        val r = Rig()
        val a = old(); val b = new()
        r.change.authenticate("flow", a); r.change.change("flow", b)
        assertTrue(a.all { it == '\u0000' } && b.all { it == '\u0000' })
    }
}
