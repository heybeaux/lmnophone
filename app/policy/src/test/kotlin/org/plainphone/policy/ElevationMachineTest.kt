// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class ElevationMachineTest {
    private val minute = 60_000L
    private val day = 86_400_000L
    private val limits = ElevationLimits()
    private val a = ApprovalBinding(PackageName("test.a"), setOf(CertDigest("ab".repeat(32))))
    private val b = ApprovalBinding(PackageName("test.b"), setOf(CertDigest("cd".repeat(32))))
    private val update = StagedUpdateBinding("ef".repeat(32), 42)
    private fun now(t: Long, p: Long = t, epoch: String = "epoch", boot: String = "boot") =
        ClockReading(RunningTime(epoch, p), BootTime(boot, t))
    private fun request(target: ElevationTarget = ElevationTarget.Play, state: ElevationState = ElevationState(), id: String = "grant", t: Long = 0) =
        ElevationMachine.request(state, id, target, limits, now(t)).also { assertEquals(ElevationOutcome.COUNTING, it.outcome) }.state
    private fun start(target: ElevationTarget = ElevationTarget.Play) =
        ElevationMachine.start(request(target), "grant", target, limits, now(10 * minute)).also {
            assertEquals(ElevationOutcome.STARTED, it.outcome)
        }.state

    @Test fun `countdown needs explicit Start and exact kind within five minutes`() {
        val s = request()
        assertEquals(ElevationOutcome.COUNTING, ElevationMachine.tick(s, limits, now(10 * minute - 1)).outcome)
        val ready = ElevationMachine.tick(s, limits, now(10 * minute))
        assertEquals(ElevationOutcome.READY, ready.outcome); assertNull(ready.state.grant)
        assertTrue(ready.state.ledger.debits.isEmpty())
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.start(s, "other", ElevationTarget.Play, limits, now(10 * minute)).outcome)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.start(s, "grant", ElevationTarget.Settings, limits, now(10 * minute)).outcome)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.start(s, "grant", ElevationTarget.Play, limits, now(10 * minute - 1)).outcome)
        assertEquals(ElevationOutcome.STARTED, ElevationMachine.start(s, "grant", ElevationTarget.Play, limits, now(15 * minute - 1)).outcome)
        val late = ElevationMachine.start(s, "grant", ElevationTarget.Play, limits, now(15 * minute))
        assertEquals(ElevationOutcome.LAPSED, late.outcome); assertNull(late.state.countdown); assertTrue(late.state.ledger.debits.isEmpty())
    }

    @Test fun `Start proposes debit and bound grant together and second start cannot debit again`() {
        val s = start(ElevationTarget.Approve(a))
        assertNull(s.countdown); assertEquals("grant", s.grant!!.id)
        assertEquals(ElevationTarget.Approve(a), s.grant!!.target)
        assertEquals(BootTime("boot", 15 * minute), s.grant!!.deadline)
        assertEquals(listOf(ElevationDebit("grant", RunningTime("epoch", 10 * minute))), s.ledger.debits)
        val double = ElevationMachine.start(s, "grant", ElevationTarget.Approve(a), limits, now(10 * minute))
        assertEquals(ElevationOutcome.REFUSED, double.outcome); assertSame(s, double.state)
    }

    @Test fun `only one countdown or grant exists and cancellation never debits`() {
        val s = request()
        assertEquals(ElevationOutcome.BUSY, ElevationMachine.request(s, "second", ElevationTarget.Settings, limits, now(1)).outcome)
        assertSame(s, ElevationMachine.cancel(s, "wrong").state)
        val cancelled = ElevationMachine.cancel(s, "grant")
        assertNull(cancelled.state.countdown); assertTrue(cancelled.state.ledger.debits.isEmpty())
        val active = start()
        assertEquals(ElevationOutcome.BUSY, ElevationMachine.request(active, "second", ElevationTarget.Settings, limits, now(10 * minute)).outcome)
        assertFailsWith<IllegalArgumentException> { ElevationState(active.ledger, s.countdown, active.grant) }
    }

    @Test fun `longer countdown applies while shortening never accelerates an existing one`() {
        val longer = ElevationLimits(20 * minute, 2)
        val extended = ElevationMachine.tick(request(), longer, now(9 * minute)).state
        assertEquals(20 * minute, extended.countdown!!.durationMillis)
        assertEquals(ElevationOutcome.COUNTING, ElevationMachine.tick(extended, limits, now(10 * minute)).outcome)
        assertEquals(ElevationOutcome.READY, ElevationMachine.tick(extended, limits, now(20 * minute)).outcome)
        assertEquals(ElevationOutcome.LAPSED, ElevationMachine.tick(request(), longer, now(15 * minute)).outcome)
        val grant = start()
        assertEquals(grant.grant!!.deadline, ElevationMachine.tick(grant, ElevationLimits(day, 3), now(11 * minute)).state.grant!!.deadline)
    }

    @Test fun `restart and reboot end countdowns and grants but never refund debit`() {
        for (s in listOf(request(), start())) {
            val restarted = ElevationMachine.onRestart(s)
            assertNull(restarted.countdown); assertNull(restarted.grant); assertSame(s.ledger, restarted.ledger)
            val rebooted = ElevationMachine.tick(s, limits, now(0, 10 * minute, boot = "next"))
            assertEquals(ElevationOutcome.LAPSED, rebooted.outcome)
            assertEquals(s.ledger.debits, rebooted.state.ledger.debits)
        }
        val started = start()
        val restarted = ElevationMachine.onRestart(started)
        repeat(10) { assertEquals(1, ElevationMachine.onRestart(restarted).ledger.debits.size) }
    }

    @Test fun `clock repair changes debit age but not boot countdown Start window or grant deadline`() {
        val s = request()
        assertEquals(ElevationOutcome.READY, ElevationMachine.tick(s, limits, now(10 * minute, 0, "repair")).outcome)
        val started = ElevationMachine.start(s, "grant", ElevationTarget.Play, limits, now(10 * minute, 0, "repair")).state
        assertEquals(BootTime("boot", 20 * minute), started.grant!!.deadline)
        assertEquals(ElevationOutcome.ACTIVE, ElevationMachine.tick(started, limits, now(11 * minute, 0, "repair-again")).outcome)
        assertEquals(ElevationOutcome.LAPSED, ElevationMachine.tick(started, limits, now(20 * minute, 0, "repair-again")).outcome)
    }

    @Test fun `two rolling debits block third across reboot and expire at exactly proven 24 hours`() {
        val ledger = ElevationLedger(listOf(ElevationDebit("a", RunningTime("epoch", 0)), ElevationDebit("b", RunningTime("epoch", minute))))
        val state = ElevationState(ledger)
        val availability = ledger.availability(2, RunningTime("epoch", day - 1))
        assertEquals(0, availability.turnsLeft); assertEquals(1L, availability.nextTurnInMillis)
        assertEquals(ElevationOutcome.LIMIT_REACHED, ElevationMachine.request(state, "third", ElevationTarget.Play, limits, now(0, day - 1, boot = "next")).outcome)
        assertEquals(1, ledger.availability(2, RunningTime("epoch", day)).turnsLeft)
        assertEquals(ElevationOutcome.COUNTING, ElevationMachine.request(state, "third", ElevationTarget.Play, limits, now(0, day, boot = "next")).outcome)
        assertEquals(2, ledger.availability(2, RunningTime("epoch", day + minute)).turnsLeft)
    }

    @Test fun `damaged ledger repair fills maximum limit and old epochs need full new day`() {
        val repaired = ElevationLedger.repair(RunningTime("old", 30 * 60 * minute))
        assertEquals(3, repaired.debits.size)
        for (limit in 0..3) assertEquals(0, repaired.availability(limit, RunningTime("old", 30 * 60 * minute)).turnsLeft)
        assertEquals(0, repaired.availability(3, RunningTime("new", day - 1)).turnsLeft)
        assertEquals(3, repaired.availability(3, RunningTime("new", day)).turnsLeft)
        assertNull(repaired.availability(0, RunningTime("new", day)).nextTurnInMillis)
    }

    @Test fun `lower daily limit is rechecked on Start and next available time handles excess debits`() {
        val ledger = ElevationLedger(listOf(ElevationDebit("a", RunningTime("epoch", 0)), ElevationDebit("b", RunningTime("epoch", minute)), ElevationDebit("c", RunningTime("epoch", 2 * minute))))
        assertEquals(day + minute, ledger.availability(1, RunningTime("epoch", minute)).nextTurnInMillis)
        val s = request(state = ElevationState(ElevationLedger(listOf(ElevationDebit("old", RunningTime("epoch", 0))))))
        val denied = ElevationMachine.start(s, "grant", ElevationTarget.Play, ElevationLimits(10 * minute, 1), now(10 * minute))
        assertEquals(ElevationOutcome.LIMIT_REACHED, denied.outcome); assertEquals(1, denied.state.ledger.debits.size); assertNull(denied.state.grant)
    }

    @Test fun `Play closes package access but approves two distinct session candidates until deadline`() {
        val s = start()
        val closed = ElevationMachine.closePackageAccess(s, "grant").state
        assertFalse(closed.grant!!.packageAccessOpen); assertEquals(s.grant!!.deadline, closed.grant!!.deadline)
        val first = ElevationMachine.consume(closed, "grant", ElevationAction.Approve(a, "grant"), now(11 * minute))
        assertEquals(ElevationOutcome.CONSUMED, first.outcome); assertNotNull(first.state.grant)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.consume(first.state, "grant", ElevationAction.Approve(a, "grant"), now(12 * minute)).outcome)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.consume(first.state, "grant", ElevationAction.Approve(b, "other-session"), now(12 * minute)).outcome)
        val second = ElevationMachine.consume(first.state, "grant", ElevationAction.Approve(b, "grant"), now(12 * minute))
        assertEquals(ElevationOutcome.CONSUMED, second.outcome); assertEquals(setOf(a, b), second.state.grant!!.approved)
        assertEquals(ElevationOutcome.LAPSED, ElevationMachine.consume(closed, "grant", ElevationAction.Approve(a, "grant"), now(20 * minute)).outcome)
    }

    @Test fun `Approve binding and Update bytes version are exact and consumed once`() {
        val s = start(ElevationTarget.Approve(a))
        val replacement = ApprovalBinding(a.packageName, b.signingCerts)
        for (wrong in listOf(b, replacement)) assertEquals(ElevationOutcome.REFUSED,
            ElevationMachine.consume(s, "grant", ElevationAction.Approve(wrong), now(11 * minute)).outcome)
        val used = ElevationMachine.consume(s, "grant", ElevationAction.Approve(a), now(11 * minute))
        assertEquals(ElevationOutcome.CONSUMED, used.outcome); assertNull(used.state.grant)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.consume(used.state, "grant", ElevationAction.Approve(b), now(11 * minute)).outcome)
        val u = ElevationMachine.closePackageAccess(start(ElevationTarget.Update(update)), "grant").state
        for (wrong in listOf(update.copy(versionCode = 43), update.copy(sha256 = "aa".repeat(32)))) assertEquals(ElevationOutcome.REFUSED,
            ElevationMachine.consume(u, "grant", ElevationAction.Update(wrong), now(11 * minute)).outcome)
        assertEquals(ElevationOutcome.CONSUMED, ElevationMachine.consume(u, "grant", ElevationAction.Update(update), now(20 * minute - 1)).outcome)
        assertEquals(ElevationOutcome.LAPSED, ElevationMachine.consume(u, "grant", ElevationAction.Update(update), now(20 * minute)).outcome)
    }

    @Test fun `Settings never grants app approval and Done cannot refund debit`() {
        val s = start(ElevationTarget.Settings)
        assertEquals(BootTime("boot", 15 * minute), s.grant!!.deadline)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.consume(s, "grant", ElevationAction.Approve(a, "grant"), now(11 * minute)).outcome)
        val ended = ElevationMachine.done(s, "grant").state
        assertNull(ended.grant); assertEquals(1, ended.ledger.debits.size)
    }

    @Test fun `invalid ranges duplicates overflow and unavailable clocks never mint grant`() {
        for (duration in listOf(0L, 10 * minute - 1, day + 1)) assertFailsWith<IllegalArgumentException> { ElevationLimits(duration, 2) }
        for (limit in listOf(-1, 4)) assertFailsWith<IllegalArgumentException> { ElevationLimits(10 * minute, limit) }
        assertFailsWith<IllegalArgumentException> { StagedUpdateBinding("not-a-digest", 1) }
        assertFailsWith<IllegalArgumentException> { ElevationLedger(listOf(ElevationDebit("x", RunningTime("e", 0)), ElevationDebit("x", RunningTime("e", 1)))) }
        assertEquals(ElevationOutcome.NOT_READY, ElevationMachine.request(ElevationState(), "grant", ElevationTarget.Play, limits, null).outcome)
        assertEquals(ElevationOutcome.REFUSED, ElevationMachine.request(ElevationState(), "grant", ElevationTarget.Play, limits, now(Long.MAX_VALUE)).outcome)
        val s = request()
        assertEquals(ElevationOutcome.NOT_READY, ElevationMachine.start(s, "grant", ElevationTarget.Play, limits, null).outcome)
        assertEquals(ElevationOutcome.LAPSED, ElevationMachine.tick(request(t = 100), limits, now(99)).outcome)
    }

    @Test fun `proposals are immutable and failed persistence grants no new durable debit or action`() {
        val items = mutableListOf(ElevationDebit("old", RunningTime("e", 0)))
        val ledger = ElevationLedger(items); items.clear(); assertEquals(1, ledger.debits.size)
        assertFailsWith<UnsupportedOperationException> { (ledger.debits as MutableList).clear() }
        val durable = request()
        val proposal = ElevationMachine.start(durable, "grant", ElevationTarget.Play, limits, now(10 * minute))
        assertEquals(1, proposal.state.ledger.debits.size); assertTrue(durable.ledger.debits.isEmpty()); assertNull(durable.grant)
        // Failed storage keeps the original state; restart discards its countdown. Neither proposal is a capability.
        assertNull(ElevationMachine.onRestart(durable).grant)
    }
}
