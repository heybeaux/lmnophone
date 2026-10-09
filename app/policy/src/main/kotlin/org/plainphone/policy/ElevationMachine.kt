// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

private const val ELEVATION_DAY = 86_400_000L
private const val ELEVATION_MIN_COUNTDOWN = 600_000L
private const val ELEVATION_START_WINDOW = 300_000L

data class ElevationLimits(val countdownMillis: Long = ELEVATION_MIN_COUNTDOWN, val dailyLimit: Int = 2) {
    init { require(countdownMillis in ELEVATION_MIN_COUNTDOWN..ELEVATION_DAY && dailyLimit in 0..3) }
}
/** Digest of verified staged bytes, not a URL, requested version or certificate digest. */
data class StagedUpdateBinding(val sha256: String, val versionCode: Long) {
    init { require(sha256.matches(Regex("[0-9a-f]{64}")) && versionCode >= 0) }
}
sealed interface ElevationTarget {
    data object Play : ElevationTarget
    data object Settings : ElevationTarget
    data class Approve(val identity: ApprovalBinding) : ElevationTarget
    data class Update(val staged: StagedUpdateBinding) : ElevationTarget
}
sealed interface ElevationAction {
    /** For Play, caller derives both fields from a candidate observed in this grant's session. */
    data class Approve(val identity: ApprovalBinding, val playGrantId: String? = null) : ElevationAction
    data class Update(val staged: StagedUpdateBinding) : ElevationAction
}
data class ElevationDebit(val id: String, val at: RunningTime) { init { require(id.isNotBlank()) } }
data class ElevationAvailability(val turnsLeft: Int, val nextTurnInMillis: Long?)

/** Validated ledger data. Invalid decoding must propose repair, never construct an empty ledger. */
class ElevationLedger(debits: List<ElevationDebit> = emptyList()) {
    val debits: List<ElevationDebit> = immutableList(debits)
    init { require(this.debits.size <= 3 && this.debits.map { it.id }.toSet().size == this.debits.size) }
    fun availability(limit: Int, now: RunningTime): ElevationAvailability {
        require(limit in 0..3)
        val remaining = debits.map {
            if (it.at.epoch == now.epoch && it.at.totalMillis > now.totalMillis) {
                val ahead = it.at.totalMillis - now.totalMillis
                if (ahead > Long.MAX_VALUE - ELEVATION_DAY) Long.MAX_VALUE else ahead + ELEVATION_DAY
            } else (ELEVATION_DAY - now.since(it.at)).coerceAtLeast(0)
        }.filter { it > 0 }.sorted()
        val left = (limit - remaining.size).coerceAtLeast(0)
        val next = if (limit == 0 || left > 0) null else remaining[remaining.size - limit]
        return ElevationAvailability(left, next)
    }
    internal fun adding(id: String, now: RunningTime) = ElevationLedger(
        debits.filter { now.since(it.at) < ELEVATION_DAY } + ElevationDebit(id, now))
    companion object {
        /** Persist this replacement before any start; failed repair persistence leaves starts blocked. */
        fun repair(now: RunningTime) = ElevationLedger((1..3).map { ElevationDebit("repair-$it", now) })
    }
}

/** Process-lifetime timing data only: the controller must separately bind the initiating PIN/person. */
class ElevationCountdown internal constructor(val id: String, val target: ElevationTarget,
    val startedAt: BootTime, val durationMillis: Long) {
    internal val readyAt = Math.addExact(startedAt.elapsedMillis, durationMillis)
    internal val lapsesAt = Math.addExact(readyAt, ELEVATION_START_WINDOW)
    init { require(id.isNotBlank() && durationMillis in ELEVATION_MIN_COUNTDOWN..ELEVATION_DAY) }

    companion object {
        /**
         * Decode timing data only; never restores elevation authority or process-local ownership.
         * The controller must still revoke on startup and bind a freshly authenticated request.
         * Invalid fields, including ready/lapse arithmetic overflow, return null.
         */
        fun restore(id: String, target: ElevationTarget, startedAt: BootTime, durationMillis: Long): ElevationCountdown? =
            try { ElevationCountdown(id, target, startedAt, durationMillis) }
            catch (_: IllegalArgumentException) { null }
            catch (_: ArithmeticException) { null }
    }
}
/** Proposed grant data, NOT an authority capability. Only a controller's durable atomic step may use it. */
class ElevationGrant internal constructor(val id: String, val target: ElevationTarget, val startedAt: BootTime,
    val deadline: BootTime, val packageAccessOpen: Boolean, approved: Set<ApprovalBinding> = emptySet()) {
    val approved: Set<ApprovalBinding> = immutableSet(approved)
    init {
        require(id.isNotBlank() && deadline.bootId == startedAt.bootId && deadline.elapsedMillis > startedAt.elapsedMillis)
        require(target == ElevationTarget.Play || this.approved.isEmpty())
        require(this.approved.map { it.packageName }.toSet().size == this.approved.size)
        require(!packageAccessOpen || target == ElevationTarget.Play || target == ElevationTarget.Settings)
    }
    companion object {
        /**
         * Decode immutable grant data, never elevation authority. No owner or live intake is restored;
         * controller startup revocation and existing binding checks remain mandatory.
         * Deadlines must retain the target's fixed duration. Invalid combinations return null.
         */
        fun restore(id: String, target: ElevationTarget, startedAt: BootTime, deadline: BootTime,
            packageAccessOpen: Boolean, approved: Set<ApprovalBinding> = emptySet()): ElevationGrant? {
            val duration = when (target) {
                ElevationTarget.Play, is ElevationTarget.Update -> 600_000L
                ElevationTarget.Settings, is ElevationTarget.Approve -> 300_000L
            }
            if (deadline.elapsedMillis - startedAt.elapsedMillis != duration) return null
            return try { ElevationGrant(id, target, startedAt, deadline, packageAccessOpen, approved) }
                catch (_: IllegalArgumentException) { null }
        }
    }
    internal fun live(now: BootTime) = now.bootId == startedAt.bootId &&
        now.elapsedMillis >= startedAt.elapsedMillis && now.elapsedMillis < deadline.elapsedMillis
    internal fun closingAccess() = ElevationGrant(id, target, startedAt, deadline, false, approved)
    internal fun approving(identity: ApprovalBinding) = ElevationGrant(id, target, startedAt, deadline, packageAccessOpen, approved + identity)
}
class ElevationState(val ledger: ElevationLedger = ElevationLedger(), val countdown: ElevationCountdown? = null,
    val grant: ElevationGrant? = null) {
    init {
        require(countdown == null || grant == null)
        require(grant == null || ledger.debits.any { it.id == grant.id })
    }
}
enum class ElevationOutcome { IDLE, COUNTING, READY, STARTED, ACTIVE, BUSY, REFUSED, LAPSED, LIMIT_REACHED,
    CONSUMED, ACCESS_CLOSED, ENDED, NOT_READY }
data class ElevationTransition(val state: ElevationState, val outcome: ElevationOutcome)

/**
 * Pure proposals only. No PIN check, storage, session effect, approval write or updater commit occurs here.
 * The controller must checkpoint, validate live authority/facts and atomically persist a proposal with its
 * effect before reporting success. Never pass these grant records directly to policy as authority.
 */
object ElevationMachine {
    fun request(state: ElevationState, id: String, target: ElevationTarget, limits: ElevationLimits, now: ClockReading?): ElevationTransition {
        if (now == null) return ElevationTransition(state, ElevationOutcome.NOT_READY)
        val checked = tick(state, limits, now).state
        if (checked.countdown != null || checked.grant != null) return ElevationTransition(checked, ElevationOutcome.BUSY)
        if (id.isBlank() || checked.ledger.debits.any { it.id == id }) return ElevationTransition(checked, ElevationOutcome.REFUSED)
        if (checked.ledger.availability(limits.dailyLimit, now.proven).turnsLeft == 0) return ElevationTransition(checked, ElevationOutcome.LIMIT_REACHED)
        val countdown = try { ElevationCountdown(id, target, now.boot, limits.countdownMillis) }
            catch (_: ArithmeticException) { return ElevationTransition(checked, ElevationOutcome.REFUSED) }
        return ElevationTransition(ElevationState(checked.ledger, countdown), ElevationOutcome.COUNTING)
    }

    fun tick(state: ElevationState, limits: ElevationLimits, now: ClockReading?): ElevationTransition {
        if (now == null) return ElevationTransition(state, ElevationOutcome.NOT_READY)
        val countdown = state.countdown
        if (countdown != null) {
            if (now.boot.bootId != countdown.startedAt.bootId || now.boot.elapsedMillis < countdown.startedAt.elapsedMillis ||
                now.boot.elapsedMillis >= countdown.lapsesAt) return ElevationTransition(onRestart(state), ElevationOutcome.LAPSED)
            val extended = if (limits.countdownMillis > countdown.durationMillis) try {
                ElevationCountdown(countdown.id, countdown.target, countdown.startedAt, limits.countdownMillis)
            } catch (_: ArithmeticException) { return ElevationTransition(onRestart(state), ElevationOutcome.LAPSED) } else countdown
            return ElevationTransition(if (extended === countdown) state else ElevationState(state.ledger, extended),
                if (now.boot.elapsedMillis < extended.readyAt) ElevationOutcome.COUNTING else ElevationOutcome.READY)
        }
        val grant = state.grant ?: return ElevationTransition(state, ElevationOutcome.IDLE)
        return if (grant.live(now.boot)) ElevationTransition(state, ElevationOutcome.ACTIVE)
            else ElevationTransition(onRestart(state), ElevationOutcome.LAPSED)
    }

    fun start(state: ElevationState, id: String, confirmedTarget: ElevationTarget, limits: ElevationLimits, now: ClockReading?): ElevationTransition {
        if (now == null) return ElevationTransition(state, ElevationOutcome.NOT_READY)
        val checked = tick(state, limits, now)
        if (checked.outcome == ElevationOutcome.LAPSED) return checked
        val countdown = checked.state.countdown
        if (checked.outcome != ElevationOutcome.READY || countdown == null || countdown.id != id || countdown.target != confirmedTarget)
            return ElevationTransition(checked.state, ElevationOutcome.REFUSED)
        if (checked.state.ledger.availability(limits.dailyLimit, now.proven).turnsLeft == 0)
            return ElevationTransition(checked.state, ElevationOutcome.LIMIT_REACHED)
        val duration = when (confirmedTarget) {
            ElevationTarget.Play, is ElevationTarget.Update -> 600_000L
            ElevationTarget.Settings, is ElevationTarget.Approve -> 300_000L
        }
        val deadline = try { BootTime(now.boot.bootId, Math.addExact(now.boot.elapsedMillis, duration)) }
            catch (_: ArithmeticException) { return ElevationTransition(checked.state, ElevationOutcome.REFUSED) }
        val grant = ElevationGrant(id, confirmedTarget, now.boot, deadline,
            confirmedTarget == ElevationTarget.Play || confirmedTarget == ElevationTarget.Settings)
        return ElevationTransition(ElevationState(checked.state.ledger.adding(id, now.proven), grant = grant), ElevationOutcome.STARTED)
    }

    fun consume(state: ElevationState, id: String, action: ElevationAction, now: ClockReading?): ElevationTransition {
        if (now == null) return ElevationTransition(state, ElevationOutcome.NOT_READY)
        val grant = state.grant ?: return ElevationTransition(state, ElevationOutcome.REFUSED)
        if (!grant.live(now.boot)) return ElevationTransition(onRestart(state), ElevationOutcome.LAPSED)
        if (id != grant.id) return ElevationTransition(state, ElevationOutcome.REFUSED)
        val matches = when (val target = grant.target) {
            ElevationTarget.Play -> action is ElevationAction.Approve && action.playGrantId == grant.id &&
                grant.approved.none { it.packageName == action.identity.packageName }
            is ElevationTarget.Approve -> action is ElevationAction.Approve && action.identity == target.identity
            is ElevationTarget.Update -> action is ElevationAction.Update && action.staged == target.staged
            ElevationTarget.Settings -> false
        }
        if (!matches) return ElevationTransition(state, ElevationOutcome.REFUSED)
        val next = if (grant.target == ElevationTarget.Play) grant.approving((action as ElevationAction.Approve).identity) else null
        return ElevationTransition(ElevationState(state.ledger, grant = next), ElevationOutcome.CONSUMED)
    }

    fun closePackageAccess(state: ElevationState, id: String): ElevationTransition {
        val grant = state.grant?.takeIf { it.id == id } ?: return ElevationTransition(state, ElevationOutcome.REFUSED)
        return ElevationTransition(ElevationState(state.ledger, grant = grant.closingAccess()), ElevationOutcome.ACCESS_CLOSED)
    }
    fun cancel(state: ElevationState, id: String): ElevationTransition =
        if (state.countdown?.id == id) ElevationTransition(onRestart(state), ElevationOutcome.ENDED)
        else ElevationTransition(state, ElevationOutcome.REFUSED)
    fun done(state: ElevationState, id: String): ElevationTransition =
        if (state.grant?.id == id) ElevationTransition(onRestart(state), ElevationOutcome.ENDED)
        else ElevationTransition(state, ElevationOutcome.REFUSED)

    /** Actual startup must revoke package access first; only debit data survives, never grant authority. */
    fun onRestart(state: ElevationState) = ElevationState(state.ledger)
}
