// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.time.LocalDate
import org.plainphone.core.*
import kotlin.test.*

class MonthlyCheckTest {
    private val due = LocalDate.of(2026, 1, 31)
    private val person = TrustedPersonId("one")
    private val app = PackageName("test.app")
    private val now = BootTime("boot", 100)
    private val access = SessionAccess("check-play", WindowKind.PLAY, setOf(PackageName("com.android.vending")), BootTime("boot", 600_100))
    private fun initial() = MonthlyCheckState("cycle", due)
    private fun accept() = AuthorityConsumer { _, _ -> true }

    @Test fun `due date then seven daily retries with no duplicate attempt per day`() {
        var state = initial()
        assertNull(MonthlyCheck.reminder(state, due.minusDays(1), person))
        for (day in 0L..7L) {
            val request = MonthlyCheck.reminder(state, due.plusDays(day), person)!!
            state = request.state
            assertEquals(request.key, MonthlyCheck.reminder(state, due.plusDays(day), person)!!.key)
            state = MonthlyCheck.reminderResult(state, request, false)
            assertNull(MonthlyCheck.reminder(state, due.plusDays(day), person))
            assertNull(MonthlyCheck.reminder(state, due.minusDays(1), person))
        }
        assertNull(MonthlyCheck.reminder(state, due.plusDays(8), person))
    }

    @Test fun `restart retains outbox identity and delivered recipient stops independently`() {
        val pending = MonthlyCheck.reminder(initial(), due, person)!!
        val restored = MonthlyCheckState(pending.state.cycleId, pending.state.dueDate,
            pending.state.lastCompleted, pending.state.reminders, pending.state.unresolvedFailures)
        val retry = MonthlyCheck.reminder(restored, due.plusDays(3), person)!!
        assertEquals(pending.key, retry.key)
        assertEquals(pending.attemptDate, retry.attemptDate)
        val delivered = MonthlyCheck.reminderResult(restored, retry, true)
        assertNull(MonthlyCheck.reminder(delivered, due.plusDays(1), person))
        assertNotNull(MonthlyCheck.reminder(delivered, due.plusDays(1), TrustedPersonId("two")))
        assertSame(delivered, MonthlyCheck.reminderResult(delivered, pending, false))
    }

    @Test fun `overdue is strictly after seven days regardless of text delivery and tap explains only`() {
        assertFalse(MonthlyCheck.notice(initial(), due.plusDays(7), Mode.TRUSTED).visible)
        for (delivered in listOf(false, true)) {
            val request = MonthlyCheck.reminder(initial(), due, person)!!
            val state = MonthlyCheck.reminderResult(request.state, request, delivered)
            val notice = MonthlyCheck.notice(state, due.plusDays(8), Mode.TRUSTED)
            assertTrue(notice.visible)
            assertEquals(MonthlyNoticeAction.EXPLAIN, notice.tapAction)
            assertEquals("Ask [trusted person] to do the monthly phone check", notice.text)
            assertEquals("Time for your monthly phone check", MonthlyCheck.notice(state, due.plusDays(8), Mode.SELF_MANAGED).text)
        }
    }

    @Test fun `completion needs linked live Play session and fresh authority`() {
        val state = initial()
        assertNull(MonthlyCheck.openedFromCheck(state, null, now))
        val review = MonthlyCheck.openedFromCheck(state, access, now)!!
        assertNull(MonthlyCheck.complete(state, review, access, now, due, "next", emptySet(), emptySet(), FreshAuthority(), AuthorityConsumer { _, _ -> false }))
        for (current in listOf(null, SessionAccess("other", WindowKind.PLAY, access.packages, access.deadline))) {
            assertNull(MonthlyCheck.complete(state, review, current, now, due, "next", emptySet(), emptySet(), FreshAuthority(), accept()))
        }
        for (reading in listOf(access.deadline, BootTime("reboot", 0), BootTime("boot", 99))) {
            assertNull(MonthlyCheck.complete(state, review, access, reading, due, "next", emptySet(), emptySet(), FreshAuthority(), accept()))
        }
        var action: PrivilegedAction? = null
        val completed = MonthlyCheck.complete(state, review, access, now, due, "next", setOf(app), emptySet(), FreshAuthority(), AuthorityConsumer { _, a -> action = a; true })!!
        assertEquals(MonthlyCheck.completionAction(review), action)
        assertEquals(due, completed.lastCompleted)
        assertEquals(LocalDate.of(2026, 2, 28), completed.dueDate)
        assertFalse(MonthlyCheck.notice(completed, due, Mode.TRUSTED).visible)
        assertEquals(setOf(app), completed.unresolvedFailures)
        assertNull(MonthlyCheck.complete(completed, review, access, now, due, "third", emptySet(), emptySet(), FreshAuthority(), accept()))
    }

    @Test fun `unresolved failures persist until explicitly resolved`() {
        val state = MonthlyCheckState("cycle", due, unresolvedFailures = setOf(app))
        val review = MonthlyCheck.openedFromCheck(state, access, now)!!
        val retained = MonthlyCheck.complete(state, review, access, now, due, "next", emptySet(), emptySet(), FreshAuthority(), accept())!!
        assertEquals(setOf(app), retained.unresolvedFailures)
        val resolved = MonthlyCheck.complete(state, review, access, now, due, "next", emptySet(), setOf(app), FreshAuthority(), accept())!!
        assertTrue(resolved.unresolvedFailures.isEmpty())
    }

    @Test fun `old callback cannot acknowledge a new cycle`() {
        val request = MonthlyCheck.reminder(initial(), due, person)!!
        val next = MonthlyCheckState("next", due.plusMonths(1))
        assertSame(next, MonthlyCheck.reminderResult(next, request, true))
    }

    @Test fun `stored collections snapshot their inputs`() {
        val failures = mutableSetOf(app)
        val state = MonthlyCheckState("cycle", due, unresolvedFailures = failures)
        failures.clear()
        assertEquals(setOf(app), state.unresolvedFailures)
        assertFailsWith<UnsupportedOperationException> { (state.unresolvedFailures as MutableSet).clear() }
    }
}
