// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

/** Generic rolling proven-time quota. Each start/extension unit buys windowMillis of boot time. */
data class TimeWindowConfig(val capacity: Int, val periodMillis: Long, val windowMillis: Long, val extendable: Boolean) {
    init { require(capacity > 0 && periodMillis > 0 && windowMillis > 0) }
}

/** Storage generation is durably unique, including after recovery; revision never wraps. */
data class TimeWindowVersion(val generation: String, val revision: Long) {
    init { require(generation.isNotBlank() && revision >= 0) }
}
data class TimeWindowDebit(val at: RunningTime, val units: Int) {
    init { require(units > 0) }
}

/** Journal data only, not SessionAccess, PIN authority, or a capability accepted by PolicyEngine. */
data class TimeWindow(val id: TimeWindowVersion, val startedAt: BootTime, val deadline: BootTime) {
    init {
        require(id.revision > 0 && startedAt.bootId == deadline.bootId && deadline.elapsedMillis > startedAt.elapsedMillis)
    }
    internal fun live(now: BootTime) = now.bootId == startedAt.bootId &&
        now.elapsedMillis >= startedAt.elapsedMillis && now.elapsedMillis < deadline.elapsedMillis
}
data class TimeWindowAvailability(val unitsLeft: Int, val nextUnitInMillis: Long?)
enum class TimeWindowOutcome { STARTED, EXTENDED, CLOSED, RESTARTED, EXPIRED, IDLE, ACTIVE,
    STALE, BUSY, LIMIT_REACHED, REFUSED, NOT_READY, DISABLED }

/** A proposal, never an acknowledgement of storage, physical access, or revocation. */
data class TimeWindowTransition(val next: TimeWindowQuota, val outcome: TimeWindowOutcome)

/**
 * Immutable proposals using the existing RunningClock contract. No effects, clock sampling, storage,
 * or process-owned access capability. A future adapter must serialize, CAS and read back the complete
 * record before effects, and revoke/read back on startup before publishing authority. See README.
 * Expected versions fence commands against the supplied snapshot, NOT against another process/store.
 */
class TimeWindowQuota private constructor(
    val config: TimeWindowConfig,
    val version: TimeWindowVersion,
    debits: List<TimeWindowDebit>,
    val window: TimeWindow?,
) {
    val debits: List<TimeWindowDebit> = immutableList(debits)

    init {
        require(this.debits.sumOf { it.units.toLong() } <= config.capacity)
        require(window == null || (window.id.generation == version.generation &&
            window.id.revision <= version.revision && this.debits.isNotEmpty()))
    }

    /** Display/accounting only. Callers must supply a freshly persisted proven-clock reading. */
    fun availability(now: RunningTime): TimeWindowAvailability {
        val live = debits.map { it to remaining(it.at, now) }.filter { it.second > 0 }
        val left = config.capacity - live.sumOf { it.first.units }
        return TimeWindowAvailability(left, if (left > 0) null else live.minOf { it.second })
    }

    fun start(expected: TimeWindowVersion, now: ClockReading?): TimeWindowTransition {
        if (expected != version) return result(TimeWindowOutcome.STALE)
        if (now == null || version.revision == Long.MAX_VALUE) return result(TimeWindowOutcome.NOT_READY)
        // Even an expired recorded window requires a separate cleanup/revocation before a new start.
        if (window != null) return result(TimeWindowOutcome.BUSY)
        if (availability(now.proven).unitsLeft == 0) return result(TimeWindowOutcome.LIMIT_REACHED)
        val end = add(now.boot.elapsedMillis, config.windowMillis) ?: return result(TimeWindowOutcome.REFUSED)
        val nextVersion = nextVersion()
        return TimeWindowTransition(TimeWindowQuota(config, nextVersion, charge(1, now.proven),
            TimeWindow(nextVersion, now.boot, BootTime(now.boot.bootId, end))), TimeWindowOutcome.STARTED)
    }

    /** Each unit is charged at this checkpoint. Extension adds to the fixed old deadline, never now. */
    fun extend(expected: TimeWindowVersion, id: TimeWindowVersion, units: Int, now: ClockReading?): TimeWindowTransition {
        if (expected != version || window?.id != id) return result(TimeWindowOutcome.STALE)
        if (now == null || version.revision == Long.MAX_VALUE) return result(TimeWindowOutcome.NOT_READY)
        val current = window ?: return result(TimeWindowOutcome.REFUSED)
        if (!current.live(now.boot)) return cleared(TimeWindowOutcome.EXPIRED)
        if (!config.extendable || units <= 0) return result(TimeWindowOutcome.REFUSED)
        if (units > availability(now.proven).unitsLeft) return result(TimeWindowOutcome.LIMIT_REACHED)
        val extra = try { Math.multiplyExact(config.windowMillis, units.toLong()) }
            catch (_: ArithmeticException) { return result(TimeWindowOutcome.REFUSED) }
        val end = add(current.deadline.elapsedMillis, extra) ?: return result(TimeWindowOutcome.REFUSED)
        return TimeWindowTransition(TimeWindowQuota(config, nextVersion(), charge(units, now.proven),
            current.copy(deadline = BootTime(current.deadline.bootId, end))), TimeWindowOutcome.EXTENDED)
    }

    fun tick(expected: TimeWindowVersion, now: ClockReading?): TimeWindowTransition {
        if (expected != version) return result(TimeWindowOutcome.STALE)
        if (now == null) return result(TimeWindowOutcome.NOT_READY)
        val current = window ?: return result(TimeWindowOutcome.IDLE)
        return if (current.live(now.boot)) result(TimeWindowOutcome.ACTIVE) else cleared(TimeWindowOutcome.EXPIRED)
    }

    /** Also used after an effect failure. Never removes or changes debits. */
    fun close(expected: TimeWindowVersion, id: TimeWindowVersion): TimeWindowTransition =
        if (expected != version || window?.id != id) result(TimeWindowOutcome.STALE) else cleared(TimeWindowOutcome.CLOSED)

    /** Cleanup proposal only. Actual startup must revoke/read back access before acknowledging cleanup. */
    fun restart(expected: TimeWindowVersion): TimeWindowTransition =
        if (expected != version) result(TimeWindowOutcome.STALE) else cleared(TimeWindowOutcome.RESTARTED)

    private fun cleared(outcome: TimeWindowOutcome): TimeWindowTransition {
        if (version.revision == Long.MAX_VALUE) return result(TimeWindowOutcome.NOT_READY)
        // Advance even if empty: startup invalidates outstanding commands from the previous process.
        return TimeWindowTransition(TimeWindowQuota(config, nextVersion(), debits, null), outcome)
    }
    private fun nextVersion() = version.copy(revision = version.revision + 1)
    private fun result(outcome: TimeWindowOutcome) = TimeWindowTransition(this, outcome)
    private fun charge(units: Int, now: RunningTime) =
        debits.filter { remaining(it.at, now) > 0 } + TimeWindowDebit(now, units)

    private fun remaining(at: RunningTime, now: RunningTime): Long {
        if (at.epoch == now.epoch && at.totalMillis > now.totalMillis) {
            return add(at.totalMillis - now.totalMillis, config.periodMillis) ?: Long.MAX_VALUE
        }
        return (config.periodMillis - now.since(at)).coerceAtLeast(0)
    }
    private fun add(a: Long, b: Long): Long? = try { Math.addExact(a, b) } catch (_: ArithmeticException) { null }

    companion object {
        /** Setup only, after proving no previous history exists. Never call on missing/corrupt storage. */
        fun provision(config: TimeWindowConfig, newGeneration: String) =
            TimeWindowQuota(config, TimeWindowVersion(newGeneration, 0), emptyList(), null)

        /** Missing/corrupt history: propose a full debit from NOW under a never-reused storage generation. */
        fun recover(config: TimeWindowConfig, newGeneration: String, now: ClockReading?): TimeWindowQuota? =
            now?.let { TimeWindowQuota(config, TimeWindowVersion(newGeneration, 0),
                listOf(TimeWindowDebit(it.proven, config.capacity)), null) }

        /** Validated data decoder only. Storage checksums/schema/rollback fences are adapter obligations. */
        fun restore(config: TimeWindowConfig, version: TimeWindowVersion, debits: List<TimeWindowDebit>, window: TimeWindow?) =
            TimeWindowQuota(config, version, debits, window)
    }
}

/** Unapproved product consumers: no enable switch, session authority, effects or policy integration. */
object DisabledTimeWindows {
    const val webEnabled = false
    const val wifiSignInEnabled = false
    // Disabled illustrative configuration. Rolling period/extension accounting are not product approval.
    val webConfiguration = TimeWindowConfig(5, 86_400_000, 300_000, extendable = true)
    const val wifiSignInLimit = 3
    const val wifiSignInPeriodMillis = 86_400_000L
    // Deliberately no Wi-Fi window configuration: its duration has not been approved or specified.
    fun request(kind: WindowKind, state: TimeWindowQuota): TimeWindowTransition {
        require(kind == WindowKind.WEB || kind == WindowKind.WIFI_SIGNIN)
        return TimeWindowTransition(state, TimeWindowOutcome.DISABLED)
    }
}
