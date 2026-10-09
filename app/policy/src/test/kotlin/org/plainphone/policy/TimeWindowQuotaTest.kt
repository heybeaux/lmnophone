// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class TimeWindowQuotaTest {
    private val config = TimeWindowConfig(capacity = 3, periodMillis = 1_000, windowMillis = 100, extendable = true)
    private fun fresh(c: TimeWindowConfig = config) = TimeWindowQuota.provision(c, "generation")
    private fun at(p: Long = 0, elapsed: Long = p, epoch: String = "epoch", boot: String = "boot") =
        ClockReading(RunningTime(epoch, p), BootTime(boot, elapsed))
    private fun start(q: TimeWindowQuota = fresh(), now: ClockReading? = at()) = q.start(q.version, now)

    @Test fun `start proposes debit and fixed deadline together without mutating source`() {
        val before = fresh()
        val result = start(before, at(50))
        assertEquals(TimeWindowOutcome.STARTED, result.outcome)
        assertNull(before.window)
        assertTrue(before.debits.isEmpty())
        assertEquals(listOf(TimeWindowDebit(RunningTime("epoch", 50), 1)), result.next.debits)
        assertEquals(BootTime("boot", 150), result.next.window!!.deadline)
        assertEquals(1, result.next.version.revision)
        assertEquals(2, result.next.availability(at(50).proven).unitsLeft)
    }

    @Test fun `double tap stale version and occupied slot cannot start again`() {
        val before = fresh()
        val active = start(before).next
        assertEquals(TimeWindowOutcome.STALE, active.start(before.version, at()).outcome)
        assertEquals(TimeWindowOutcome.BUSY, start(active).outcome)
        // Expiry must be closed and physically revoked before another start can be committed.
        assertEquals(TimeWindowOutcome.BUSY, start(active, at(100)).outcome)
        assertEquals(1, active.debits.sumOf { it.units })
    }

    @Test fun `expiry exact boundary and ticks never move deadline`() {
        val active = start().next
        val token = active.window!!.id
        assertSame(active, active.tick(active.version, at(99)).next)
        val expired = active.tick(active.version, at(100))
        assertEquals(TimeWindowOutcome.EXPIRED, expired.outcome)
        assertNull(expired.next.window)
        assertEquals(active.debits, expired.next.debits)
        val newer = start(expired.next, at(101)).next
        assertEquals(TimeWindowOutcome.STALE, newer.close(newer.version, token).outcome)
    }

    @Test fun `early close failure cleanup and restart never refund charges`() {
        val active = start().next
        val closed = active.close(active.version, active.window!!.id)
        assertEquals(TimeWindowOutcome.CLOSED, closed.outcome)
        assertNull(closed.next.window)
        assertEquals(active.debits, closed.next.debits)
        val restarted = active.restart(active.version)
        assertEquals(TimeWindowOutcome.RESTARTED, restarted.outcome)
        assertNull(restarted.next.window)
        assertEquals(active.debits, restarted.next.debits)
        assertEquals(TimeWindowOutcome.STALE, restarted.next.start(active.version, at()).outcome)
    }

    @Test fun `extension charges requested units now and adds to old deadline`() {
        val active = start().next
        val result = active.extend(active.version, active.window!!.id, 2, at(90))
        assertEquals(TimeWindowOutcome.EXTENDED, result.outcome)
        assertEquals(BootTime("boot", 300), result.next.window!!.deadline)
        assertEquals(active.window!!.id, result.next.window!!.id)
        assertEquals(listOf(TimeWindowDebit(at().proven, 1), TimeWindowDebit(at(90).proven, 2)), result.next.debits)
        assertEquals(0, result.next.availability(at(90).proven).unitsLeft)
        assertEquals(TimeWindowOutcome.STALE, result.next.extend(active.version, active.window!!.id, 1, at(90)).outcome)
        assertEquals(TimeWindowOutcome.LIMIT_REACHED, result.next.extend(result.next.version, active.window!!.id, 1, at(90)).outcome)
    }

    @Test fun `extensions fail atomically beyond allowance or when disabled invalid stale or expired`() {
        val active = start().next
        for (units in listOf(0, -1, 3, Int.MAX_VALUE)) {
            val refused = active.extend(active.version, active.window!!.id, units, at(10))
            assertSame(active, refused.next)
            assertNotEquals(TimeWindowOutcome.EXTENDED, refused.outcome)
        }
        assertSame(active, active.extend(active.version, TimeWindowVersion("other", 1), 1, at(10)).next)
        assertEquals(TimeWindowOutcome.EXPIRED, active.extend(active.version, active.window!!.id, 1, at(100)).outcome)
        val fixed = start(fresh(config.copy(extendable = false))).next
        assertEquals(TimeWindowOutcome.REFUSED, fixed.extend(fixed.version, fixed.window!!.id, 1, at(10)).outcome)
    }

    @Test fun `rolling quota ages each charge independently at exact proven boundary`() {
        val active = start().next
        val full = active.extend(active.version, active.window!!.id, 2, at(90)).next
        val closed = full.close(full.version, full.window!!.id).next
        assertEquals(TimeWindowAvailability(0, 1), closed.availability(at(999).proven))
        assertEquals(TimeWindowAvailability(1, null), closed.availability(at(1_000).proven))
        val used = start(closed, at(1_000)).next
        assertEquals(TimeWindowAvailability(0, 90), used.availability(at(1_000).proven))
        assertEquals(TimeWindowAvailability(2, null), used.availability(at(1_090).proven))
        assertEquals(3, closed.availability(at(1_090).proven).unitsLeft)
    }

    @Test fun `exhaustion survives close restart and arbitrarily advanced boot without proven time`() {
        var q = fresh()
        repeat(3) {
            q = start(q).next
            q = q.close(q.version, q.window!!.id).next
        }
        assertEquals(TimeWindowOutcome.LIMIT_REACHED, start(q, at(0, 100_000)).outcome)
        assertEquals(0, q.restart(q.version).next.availability(at().proven).unitsLeft)
    }

    @Test fun `old epoch charges restart ageing at epoch zero without changing active deadline`() {
        val active = start().next
        val full = active.extend(active.version, active.window!!.id, 2, at(90)).next
        val repaired = at(0, 95, "repaired")
        assertEquals(0, full.availability(repaired.proven).unitsLeft)
        assertSame(full, full.tick(full.version, repaired).next)
        assertEquals(BootTime("boot", 300), full.window!!.deadline)
        assertEquals(TimeWindowAvailability(0, 1), full.availability(at(999, epoch = "repaired").proven))
        assertEquals(3, full.availability(at(1_000, epoch = "repaired").proven).unitsLeft)
    }

    @Test fun `future same epoch debit is conservative and remaining duration saturates`() {
        val q = TimeWindowQuota.restore(config, TimeWindowVersion("generation", 4),
            listOf(TimeWindowDebit(RunningTime("epoch", Long.MAX_VALUE), 3)), null)
        assertEquals(TimeWindowAvailability(0, Long.MAX_VALUE), q.availability(at().proven))
        assertEquals(TimeWindowOutcome.LIMIT_REACHED, start(q).outcome)
    }

    @Test fun `reboot and backwards elapsed close window with no refund`() {
        val active = start(now = at(50)).next
        for (now in listOf(at(60, 10, boot = "next"), at(50, 49))) {
            val result = active.tick(active.version, now)
            assertEquals(TimeWindowOutcome.EXPIRED, result.outcome)
            assertNull(result.next.window)
            assertEquals(active.debits, result.next.debits)
        }
    }

    @Test fun `failed checkpoint never starts extends or ages quota`() {
        val q = fresh()
        assertEquals(TimeWindowOutcome.NOT_READY, start(q, null).outcome)
        val active = start(q).next
        assertEquals(TimeWindowOutcome.NOT_READY, active.extend(active.version, active.window!!.id, 1, null).outcome)
        assertEquals(TimeWindowOutcome.NOT_READY, active.tick(active.version, null).outcome)
        assertEquals(active.debits, active.restart(active.version).next.debits)
    }

    @Test fun `missing and corrupt history need explicit full recovery not fresh provisioning`() {
        assertNull(TimeWindowQuota.recover(config, "recovery", null))
        val repaired = TimeWindowQuota.recover(config, "recovery", at(500))!!
        assertNull(repaired.window)
        assertEquals(TimeWindowAvailability(0, 1_000), repaired.availability(at(500).proven))
        assertEquals(TimeWindowOutcome.LIMIT_REACHED, start(repaired, at(1_499)).outcome)
        assertEquals(TimeWindowOutcome.STARTED, start(repaired, at(1_500)).outcome)
        assertEquals(TimeWindowOutcome.STALE, repaired.start(fresh().version, at(1_500)).outcome)
        assertEquals(3, fresh().availability(at().proven).unitsLeft)
    }

    @Test fun `restore validates records and defensively copies history`() {
        val debits = mutableListOf(TimeWindowDebit(at().proven, 1))
        val q = TimeWindowQuota.restore(config, TimeWindowVersion("generation", 1), debits, null)
        debits.clear()
        assertEquals(1, q.debits.size)
        assertFailsWith<UnsupportedOperationException> { (q.debits as MutableList).clear() }
        assertFailsWith<IllegalArgumentException> { TimeWindowQuota.restore(config, q.version,
            listOf(TimeWindowDebit(at().proven, 4)), null) }
        assertFailsWith<IllegalArgumentException> { TimeWindowDebit(at().proven, 0) }
        assertFailsWith<IllegalArgumentException> { TimeWindowVersion("", 0) }
        assertFailsWith<IllegalArgumentException> { TimeWindowVersion("g", -1) }
        val active = start().next
        val restored = TimeWindowQuota.restore(config, active.version, active.debits, active.window)
        assertEquals(active.window, restored.window) // cleanup data, never authority
        assertNull(restored.restart(restored.version).next.window)
        assertFailsWith<IllegalArgumentException> { TimeWindowQuota.restore(config,
            TimeWindowVersion("other", 1), active.debits, active.window) }
    }

    @Test fun `configuration and arithmetic cannot wrap quota deadlines or revisions`() {
        for (c in listOf({ config.copy(capacity = 0) }, { config.copy(periodMillis = 0) },
            { config.copy(windowMillis = 0) })) assertFailsWith<IllegalArgumentException> { c() }
        val q = fresh()
        assertEquals(TimeWindowOutcome.REFUSED, start(q, at(0, Long.MAX_VALUE - 50)).outcome)
        val active = start(q, at(0, Long.MAX_VALUE - 150)).next
        assertEquals(TimeWindowOutcome.REFUSED,
            active.extend(active.version, active.window!!.id, 1, at(1, Long.MAX_VALUE - 149)).outcome)
        val exhaustedRevision = TimeWindowQuota.restore(config, TimeWindowVersion("g", Long.MAX_VALUE), emptyList(), null)
        assertEquals(TimeWindowOutcome.NOT_READY, start(exhaustedRevision).outcome)
    }

    @Test fun `old start remains stale even after its debit ages out`() {
        val q = fresh()
        val active = start(q).next
        val closed = active.close(active.version, active.window!!.id).next
        val next = start(closed, at(2_000)).next
        assertEquals(1, next.debits.size)
        assertEquals(TimeWindowOutcome.STALE, next.start(q.version, at(3_000)).outcome)
    }

    @Test fun `two concurrent proposals require exactly one durable compare and set winner`() {
        val before = fresh()
        val first = start(before)
        val second = start(before)
        var durable = before
        fun commit(proposal: TimeWindowTransition): Boolean {
            if (durable.version != before.version) return false
            durable = proposal.next
            return true
        }
        assertTrue(commit(first))
        assertFalse(commit(second))
        assertEquals(1, durable.debits.sumOf { it.units })
        // Losing/discarded proposals have no effects; only adapter commit/readback can authorize one.
        assertNull(before.window)
    }

    @Test fun `real proven clock excludes uncheckpointed reboot gap and failed writes`() {
        var boot = BootTime("boot", 0)
        var record: ClockRecord? = null
        var fail = false
        val clock = ProvenRunningClock(object : ClockStateStore {
            override fun read() = record
            override fun compareAndSet(expected: ClockRecord?, replacement: ClockRecord): Boolean {
                if (fail || record != expected) return false
                record = replacement
                return true
            }
        }, { boot }, { "epoch" })
        val q = TimeWindowQuota.recover(config, "recovery", clock.checkpoint())!!
        boot = BootTime("boot", 400)
        assertEquals(0, q.availability(clock.checkpoint()!!.proven).unitsLeft)
        boot = BootTime("boot", 900) // uncheckpointed time is lost on reboot
        boot = BootTime("next", 599)
        assertEquals(TimeWindowAvailability(0, 1), q.availability(clock.checkpoint()!!.proven))
        boot = BootTime("next", 600)
        fail = true
        assertEquals(TimeWindowOutcome.NOT_READY, start(q, clock.checkpoint()).outcome)
        fail = false
        assertEquals(TimeWindowOutcome.STARTED, start(q, clock.checkpoint()).outcome)
    }

    @Test fun `web and wifi requests remain disabled with no debit or window proposal`() {
        assertFalse(DisabledTimeWindows.webEnabled)
        assertFalse(DisabledTimeWindows.wifiSignInEnabled)
        assertEquals(5, DisabledTimeWindows.webConfiguration.capacity)
        assertEquals(300_000, DisabledTimeWindows.webConfiguration.windowMillis)
        assertTrue(DisabledTimeWindows.webConfiguration.extendable)
        assertEquals(3, DisabledTimeWindows.wifiSignInLimit)
        for (kind in listOf(WindowKind.WEB, WindowKind.WIFI_SIGNIN)) {
            val q = fresh()
            val result = DisabledTimeWindows.request(kind, q)
            assertEquals(TimeWindowOutcome.DISABLED, result.outcome)
            assertSame(q, result.next)
            assertTrue(result.next.debits.isEmpty())
            assertNull(result.next.window)
        }
    }

    @Test fun `disabled requests never change policy browser and portal containment`() {
        val home = PackageName("test.home")
        val sms = PackageName("test.sms")
        val browser = PackageName("test.browser")
        val portal = PackageName("test.portal")
        val input = PolicyInput(model = "model", fingerprint = "build", plainphone = home, messages = sms,
            inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(home, sms)), setOf(sms), emptySet()),
            installed = listOf(home, sms, browser, portal).map { InstalledPackage(it, true, 1) },
            identities = emptyList(), classifiedBrowsers = setOf(browser), previousHidden = setOf(browser, portal))
        for (kind in listOf(WindowKind.WEB, WindowKind.WIFI_SIGNIN)) {
            DisabledTimeWindows.request(kind, fresh())
            for (health in PolicyHealth.entries) for (locked in listOf(false, true)) {
                val plan = PolicyEngine.decide(input.copy(health = health, lockedBoot = locked))
                assertTrue(setOf(browser, portal).all { it in plan.hidden })
                assertTrue(setOf(browser, portal).none { it in plan.unhide || it in plan.lockTaskPackages })
            }
        }
    }
}
