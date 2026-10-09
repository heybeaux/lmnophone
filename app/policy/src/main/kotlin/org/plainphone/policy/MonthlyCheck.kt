// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.Collections
import org.plainphone.core.*

/** Persist this snapshot before dispatching any reminder; cycle IDs must never be reused. */
class MonthlyCheckState(
    val cycleId: String,
    val dueDate: LocalDate,
    val lastCompleted: LocalDate? = null,
    reminders: Map<TrustedPersonId, MonthlyReminderStatus> = emptyMap(),
    unresolvedFailures: Set<PackageName> = emptySet(),
) {
    init { require(cycleId.isNotBlank()) }
    val reminders: Map<TrustedPersonId, MonthlyReminderStatus> = Collections.unmodifiableMap(LinkedHashMap(reminders))
    val unresolvedFailures: Set<PackageName> = immutableSet(unresolvedFailures)
    internal fun withReminder(person: TrustedPersonId, status: MonthlyReminderStatus) =
        MonthlyCheckState(cycleId, dueDate, lastCompleted, reminders + (person to status), unresolvedFailures)
}

data class MonthlyReminderStatus(val attemptedOn: LocalDate, val pending: Boolean, val delivered: Boolean) {
    init { require(!(pending && delivered)) }
}
/** Stable for one attempt across replays/reboots; each new daily retry has a distinct key. */
data class MonthlyReminderKey(val cycleId: String, val recipient: TrustedPersonId, val attemptDate: LocalDate)
data class MonthlyReminderRequest(val state: MonthlyCheckState, val key: MonthlyReminderKey, val attemptDate: LocalDate)
enum class MonthlyNoticeAction { EXPLAIN }
data class MonthlyNotice(val visible: Boolean, val text: String, val tapAction: MonthlyNoticeAction = MonthlyNoticeAction.EXPLAIN)

/** Process-local purpose binding, created only by the monthly-check flow after its Play session opens. */
class MonthlyReview internal constructor(internal val cycleId: String, internal val access: SessionAccess, internal val startedAt: BootTime)

/** Explicit immutable transitions. Lane D atomically persists replacements on the shared serialized dispatcher. */
object MonthlyCheck {
    /** Call for every current trusted person (including the actor). Never send before persisting request.state. */
    fun reminder(state: MonthlyCheckState, today: LocalDate, person: TrustedPersonId): MonthlyReminderRequest? {
        val days = ChronoUnit.DAYS.between(state.dueDate, today)
        if (days !in 0L..7L) return null // Due date plus at most seven daily retries; no catch-up burst.
        val before = state.reminders[person]
        if (before?.delivered == true) return null
        val key = MonthlyReminderKey(state.cycleId, person, if (before?.pending == true) before.attemptedOn else today)
        if (before?.pending == true) return MonthlyReminderRequest(state, key, before.attemptedOn)
        if (before != null && !today.isAfter(before.attemptedOn)) return null
        return MonthlyReminderRequest(state.withReminder(person, MonthlyReminderStatus(today, true, false)), key, today)
    }

    /** false means definitively not delivered; an uncertain outcome stays pending for outbox reconciliation. */
    fun reminderResult(state: MonthlyCheckState, request: MonthlyReminderRequest, delivered: Boolean): MonthlyCheckState {
        if (request.key.cycleId != state.cycleId) return state
        val current = state.reminders[request.key.recipient] ?: return state
        if (!current.pending || current.attemptedOn != request.attemptDate || request.key.attemptDate != request.attemptDate) return state
        return state.withReminder(request.key.recipient, current.copy(pending = false, delivered = delivered))
    }

    fun notice(state: MonthlyCheckState, today: LocalDate, mode: Mode): MonthlyNotice = MonthlyNotice(
        ChronoUnit.DAYS.between(state.dueDate, today) > 7,
        if (mode == Mode.SELF_MANAGED) "Time for your monthly phone check"
        else "Ask [trusted person] to do the monthly phone check",
    )

    /** Adapter calls only for the session opened by the check, never an arbitrary existing Play session. */
    fun openedFromCheck(state: MonthlyCheckState, access: SessionAccess?, now: BootTime): MonthlyReview? {
        if (PlaySessionMachine.begin(access, now) == null) return null
        return MonthlyReview(state.cycleId, access!!, now)
    }

    fun completionAction(review: MonthlyReview): PrivilegedAction = PrivilegedAction("monthly-check:complete",
        "${review.cycleId.length}:${review.cycleId}${review.access.id.length}:${review.access.id}")

    /**
     * A confirmation that the update list was reviewed, not evidence updates exist or succeeded.
     * Persist the returned replacement with compare-and-set against state before removing the notice.
     * A failed persistence attempt requires a new PIN entry; the consumed authority is never restored.
     */
    fun complete(
        state: MonthlyCheckState, review: MonthlyReview, currentAccess: SessionAccess?, now: BootTime,
        today: LocalDate, nextCycleId: String, failedUpdates: Set<PackageName>, resolvedFailures: Set<PackageName>,
        freshAuthority: FreshAuthority, authority: AuthorityConsumer,
    ): MonthlyCheckState? {
        if (state.cycleId != review.cycleId || nextCycleId.isBlank() || nextCycleId == state.cycleId ||
            currentAccess != review.access || now.bootId != review.startedAt.bootId ||
            now.elapsedMillis < review.startedAt.elapsedMillis || !review.access.isLive(now)) return null
        if (!state.unresolvedFailures.containsAll(resolvedFailures) || failedUpdates.any { it in resolvedFailures }) return null
        val nextDue = try { today.plusMonths(1) } catch (_: java.time.DateTimeException) { return null }
        if (!authority.consume(freshAuthority, completionAction(review))) return null
        return MonthlyCheckState(nextCycleId, nextDue, today, unresolvedFailures =
            (state.unresolvedFailures - resolvedFailures) + failedUpdates)
    }
}
