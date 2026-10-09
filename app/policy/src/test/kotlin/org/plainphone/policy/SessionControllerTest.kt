// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class SessionControllerTest {
    private val play = PackageName("com.android.vending")
    private val settings = PackageName("test.settings")
    private val sibling = PackageName("test.settings.sibling")
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(emptySet()), emptySet(), setOf(settings, sibling))
    private var time = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    private val calls = mutableListOf<String>()
    private var crashAt: Int? = null
    private var boundary = 0
    private var failWrite = false
    private var checkWorks = true
    private var readbackWorks = true
    private var authorized = true
    private var relaxed = false
    private var journal: SessionJournal = SessionJournal.Empty
    private val clock = RunningClock { time }
    private fun hit(name: String) { calls += name; if (++boundary == crashAt) throw SimulatedCrash() }
    private val store = object : JournalStore {
        override fun read(): SessionJournal = journal
        override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean {
            hit("before-write")
            if (failWrite || journal != expected) return false
            journal = replacement; hit("after-write"); return true
        }
    }
    private val effects = object : SessionEffects {
        override fun revokeAllRelaxations(): Boolean { hit("before-revoke"); relaxed = false; hit("after-revoke"); return true }
        override fun open(access: SessionAccess): Boolean { hit("before-open"); relaxed = true; hit("after-open"); return true }
        override fun protectedStateCheck(): Boolean { hit("check"); return checkWorks }
        override fun readBackPolicy(): Boolean { hit("readback"); return readbackWorks }
    }
    private fun controller() = SessionController(store, effects, clock, inventory, AuthorityConsumer { _, _ -> authorized })
    private fun ready(): SessionController = controller().also { assertTrue(it.onStart()); calls.clear(); boundary = 0 }
    private fun start(c: SessionController, id: String = "session", kind: WindowKind = WindowKind.PLAY, battery: BatteryState = BatteryState(100, false)) =
        c.start(id, kind, battery, FreshAuthority())

    @Test fun `journal is durable before package access opens`() {
        val c = ready()
        assertEquals(SessionOutcome.OPENED, start(c))
        assertTrue(calls.indexOf("after-write") < calls.indexOf("before-open"))
        assertEquals(setOf(play), c.access()!!.packages)
        assertEquals(600_000L, c.access()!!.deadline.elapsedMillis)
    }

    @Test fun `settings includes only inventoried same UID siblings with five minute deadline`() {
        val c = ready()
        assertEquals(SessionOutcome.OPENED, start(c, kind = WindowKind.SETTINGS))
        assertEquals(setOf(settings, sibling), c.access()!!.packages)
        assertEquals(300_000L, c.access()!!.deadline.elapsedMillis)
    }

    @Test fun `no start before startup repair or without a supported session kind`() {
        assertEquals(SessionOutcome.NOT_READY, start(controller()))
        val c = ready()
        for (kind in listOf(WindowKind.APPROVE, WindowKind.UPDATE, WindowKind.WEB, WindowKind.WIFI_SIGNIN)) {
            assertEquals(SessionOutcome.REFUSED, start(c, kind = kind))
        }
        assertFalse(relaxed)
    }

    @Test fun `missing fresh authority never writes a relaxation`() {
        val c = ready(); authorized = false
        assertEquals(SessionOutcome.REFUSED, start(c))
        assertTrue(calls.isEmpty())
        assertFalse(relaxed)
    }

    @Test fun `failed journal write never opens packages`() {
        val c = ready(); failWrite = true
        assertEquals(SessionOutcome.NOT_READY, start(c))
        assertFalse(relaxed)
        assertFalse("before-open" in calls)
    }

    @Test fun `low battery refuses discretionary access but never startup revocation`() {
        val c = ready()
        assertEquals(SessionOutcome.LOW_BATTERY, start(c, battery = BatteryState(4, false)))
        assertEquals(SessionOutcome.OPENED, start(c, battery = BatteryState(4, true)))
        calls.clear()
        assertTrue(controller().onStart())
        assertEquals("before-revoke", calls.first())
        assertFalse(relaxed)
    }

    @Test fun `all session end triggers revoke and read back before clearing journal`() {
        for (trigger in SessionEnd.entries) {
            journal = SessionJournal.Empty; val c = ready(); start(c); calls.clear()
            assertEquals(SessionOutcome.CLOSED, c.end("session", trigger))
            assertEquals("before-write", calls.first())
            assertTrue(calls.indexOf("after-revoke") < calls.indexOf("check"))
            assertTrue(calls.indexOf("readback") < calls.lastIndexOf("before-write"))
            assertEquals(SessionJournal.Empty, journal)
            assertFalse(relaxed)
        }
    }

    @Test fun `deadline is exclusive and reboot invalidates package access`() {
        val c = ready(); start(c)
        time = time.copy(boot = BootTime("boot", 599_999)); assertNotNull(c.access())
        time = time.copy(boot = BootTime("boot", 600_000)); assertNull(c.access())
        assertEquals(SessionOutcome.CLOSED, c.tick())
        start(c, "second")
        time = time.copy(boot = BootTime("other", 0)); assertNull(c.access())
        assertEquals(SessionOutcome.CLOSED, c.tick())
    }

    @Test fun `stale event and second start cannot change a live session`() {
        val c = ready(); start(c)
        assertEquals(SessionOutcome.BUSY, start(c, "second"))
        assertEquals(SessionOutcome.IGNORED, c.end("old-session", SessionEnd.DONE))
        assertTrue(relaxed)
        assertEquals(SessionOutcome.CLOSED, c.end("session", SessionEnd.DONE))
        assertEquals(SessionOutcome.IGNORED, c.end("session", SessionEnd.DONE))
    }

    @Test fun `failed protected check or readback retains journal and forbids handback`() {
        for (failCheck in listOf(true, false)) {
            journal = SessionJournal.Empty; checkWorks = true; readbackWorks = true
            val c = ready(); start(c)
            checkWorks = !failCheck; readbackWorks = failCheck
            assertEquals(SessionOutcome.NOT_READY, c.end("session", SessionEnd.DONE))
            assertIs<SessionJournal.Active>(journal)
            assertFalse(relaxed)
            assertFalse(c.handbackReady)
            checkWorks = true; readbackWorks = true
            assertTrue(controller().onStart())
            assertEquals(SessionJournal.Empty, journal)
        }
    }

    @Test fun `damaged journal revokes all possible relaxations first`() {
        journal = SessionJournal.Damaged; relaxed = true
        assertTrue(controller().onStart())
        assertEquals("before-revoke", calls.first())
        assertFalse(relaxed)
        assertEquals(SessionJournal.Empty, journal)
    }

    @Test fun `crash at every start and close boundary always revokes first on next start`() {
        // Capture the complete path then kill before/after each storage and side-effect boundary.
        var c = ready(); start(c); c.end("session", SessionEnd.DONE)
        val boundaries = boundary
        for (crash in 1..boundaries) {
            journal = SessionJournal.Empty; relaxed = false; crashAt = null
            c = ready(); crashAt = crash
            assertFailsWith<SimulatedCrash>("boundary $crash") { start(c); c.end("session", SessionEnd.DONE) }
            crashAt = null; calls.clear()
            val restarted = controller()
            assertTrue(restarted.onStart(), "boundary $crash")
            assertEquals("before-revoke", calls.first(), "boundary $crash")
            assertFalse(relaxed)
            assertEquals(SessionJournal.Empty, journal)
        }
    }

    @Test fun `journal damage or loss during access revokes on the next tick`() {
        for (broken in listOf(SessionJournal.Damaged, SessionJournal.Empty)) {
            journal = SessionJournal.Empty
            val c = ready(); start(c); journal = broken; calls.clear()
            assertEquals(SessionOutcome.CLOSED, c.tick())
            assertEquals("before-revoke", calls.first())
            assertFalse(relaxed)
        }
    }

    @Test fun `crash at every startup boundary retries revocation before other effects`() {
        journal = SessionJournal.Damaged
        controller().onStart()
        val boundaries = boundary
        for (crash in 1..boundaries) {
            journal = SessionJournal.Damaged; relaxed = true; boundary = 0; crashAt = crash
            assertFailsWith<SimulatedCrash> { controller().onStart() }
            crashAt = null; calls.clear()
            assertTrue(controller().onStart())
            assertEquals("before-revoke", calls.first())
            assertFalse(relaxed)
        }
    }

    private class SimulatedCrash : RuntimeException()
}
