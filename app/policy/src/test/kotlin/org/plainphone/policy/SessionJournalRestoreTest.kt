// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class SessionJournalRestoreTest {
    private val play = PackageName("com.android.vending")
    private val settings = PackageName("test.settings")
    private val sibling = PackageName("test.settings.sibling")
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(emptySet()), emptySet(), setOf(settings, sibling))
    private val deadline = BootTime("boot", 600_000)

    @Test fun `public restoration rejects unsupported kinds and wrong package sets`() {
        fun restore(kind: WindowKind, packages: Set<PackageName>, matching: BuildInventory? = inventory) =
            SessionJournal.restore("session", kind, packages, deadline, SessionPhase.OPEN, matching)
        assertIs<SessionJournal.Active>(restore(WindowKind.PLAY, setOf(play), null))
        assertIs<SessionJournal.Active>(restore(WindowKind.SETTINGS, setOf(settings, sibling)))
        for (kind in WindowKind.entries.filter { it != WindowKind.PLAY && it != WindowKind.SETTINGS }) {
            assertEquals(SessionJournal.Damaged, restore(kind, setOf(play)))
        }
        assertEquals(SessionJournal.Damaged, restore(WindowKind.PLAY, setOf(play, settings)))
        assertEquals(SessionJournal.Damaged, restore(WindowKind.PLAY, emptySet()))
        assertEquals(SessionJournal.Damaged, restore(WindowKind.SETTINGS, setOf(settings)))
        assertEquals(SessionJournal.Damaged, restore(WindowKind.SETTINGS, setOf(settings, sibling), null))
        assertEquals(SessionJournal.Damaged, SessionJournal.restore("", WindowKind.PLAY, setOf(play), deadline, SessionPhase.OPEN, inventory))
    }

    @Test fun `plain field journal round trip supports independent reads and CAS`() {
        val store = PlainFieldStore(inventory)
        val clock = RunningClock { ClockReading(RunningTime("epoch", 0), BootTime("boot", 0)) }
        val effects = object : SessionEffects {
            override fun revokeAllRelaxations() = true
            override fun open(access: SessionAccess) = true
            override fun protectedStateCheck() = true
            override fun readBackPolicy() = true
        }
        for (kind in listOf(WindowKind.PLAY, WindowKind.SETTINGS)) {
            val controller = SessionController(store, effects, clock, inventory, AuthorityConsumer { _, _ -> true })
            assertTrue(controller.onStart())
            assertEquals(SessionOutcome.OPENED, controller.start("session-${kind.name}", kind, BatteryState(100, false), FreshAuthority()))
            val firstRead = assertIs<SessionJournal.Active>(store.read())
            val nextRead = assertIs<SessionJournal.Active>(store.read())
            assertNotSame(firstRead.access, nextRead.access)
            assertEquals(firstRead, nextRead)
            assertEquals(firstRead.hashCode(), nextRead.hashCode())
            assertNotNull(controller.access())
            assertEquals(SessionOutcome.CLOSED, controller.end(firstRead.access.id, SessionEnd.DONE))
            assertEquals(SessionJournal.Empty, store.read())
        }
    }

    @Test fun `restored package collections are immutable snapshots`() {
        val packages = mutableSetOf(play)
        val active = assertIs<SessionJournal.Active>(SessionJournal.restore("session", WindowKind.PLAY, packages, deadline, SessionPhase.OPEN, null))
        packages.clear()
        assertEquals(setOf(play), active.access.packages)
        assertFailsWith<UnsupportedOperationException> { (active.access.packages as MutableSet).clear() }
    }

    /** Stores only scalar fields, never a live SessionAccess object. Mirrors an external adapter. */
    private class PlainFieldStore(private val matchingInventory: BuildInventory?) : JournalStore {
        private data class Fields(val id: String, val kind: String, val packages: Set<String>, val boot: String, val elapsed: Long, val phase: String)
        private var fields: Fields? = null
        override fun read(): SessionJournal {
            val saved = fields ?: return SessionJournal.Empty
            return SessionJournal.restore(saved.id, WindowKind.valueOf(saved.kind), saved.packages.map(::PackageName).toSet(),
                BootTime(saved.boot, saved.elapsed), SessionPhase.valueOf(saved.phase), matchingInventory)
        }
        override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean {
            if (read() != expected) return false
            fields = when (replacement) {
                SessionJournal.Empty -> null
                SessionJournal.Damaged -> error("not a writer state")
                is SessionJournal.Active -> replacement.access.let {
                    Fields(it.id, it.kind.name, it.packages.map { p -> p.value }.toSet(), it.deadline.bootId, it.deadline.elapsedMillis, replacement.phase.name)
                }
            }
            return true
        }
    }
}
