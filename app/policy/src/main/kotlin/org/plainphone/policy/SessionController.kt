// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

data class BatteryState(val percent: Int, val charging: Boolean) {
    init { require(percent in 0..100) }
    val allowsDiscretionaryUnhide: Boolean get() = charging || percent >= 5
}

class SessionAccess internal constructor(val id: String, val kind: WindowKind, packages: Set<PackageName>, val deadline: BootTime) {
    val packages: Set<PackageName> = immutableSet(packages)
    fun isLive(now: BootTime): Boolean = now.bootId == deadline.bootId && now.elapsedMillis < deadline.elapsedMillis
}

enum class SessionPhase { OPENING, OPEN, CLOSING }
sealed interface SessionJournal {
    data object Empty : SessionJournal
    data object Damaged : SessionJournal
    data class Active(val access: SessionAccess, val phase: SessionPhase) : SessionJournal
}
interface JournalStore {
    /** Device-protected only. Invalid/missing checksum on an entry returns Damaged. */
    fun read(): SessionJournal
    /** Durable compare-and-set; never acknowledge before the checksum-protected write completes. */
    fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean
}

/** Lane D implements the effects and their readbacks. Calls/emergency must never depend on these returning true. */
interface SessionEffects {
    /** Remove every possible session relaxation, including for missing/damaged journals. Do not hide emergency dependencies. */
    fun revokeAllRelaxations(): Boolean
    /** Only these packages may open. Every unhide is followed by the synchronous flush and readback. No restriction is lifted. */
    fun open(access: SessionAccess): Boolean
    /** Repair only the spec's protected invariants; preserve ordinary settings. False prevents handback. */
    fun protectedStateCheck(): Boolean
    fun readBackPolicy(): Boolean
}
fun interface AuthorityConsumer { fun consume(authority: FreshAuthority, action: PrivilegedAction): Boolean }
enum class SessionOutcome { OPENED, CLOSED, BUSY, REFUSED, LOW_BATTERY, NOT_READY, IGNORED }
enum class SessionEnd { DONE, SCREEN_OFF, INCOMING_CALL, DEADLINE, LEFT_PACKAGES }

/** One serialized dispatcher owns this controller, the vault, the clock and all journal/policy writes. */
class SessionController(
    private val store: JournalStore,
    private val effects: SessionEffects,
    private val clock: RunningClock,
    private val inventory: BuildInventory?,
    private val authority: AuthorityConsumer,
) {
    var handbackReady: Boolean = false
        private set
    private var initialized = false

    /** Invoke before any other Plainphone work, including Direct Boot. No credential storage is needed. */
    @Synchronized
    fun onStart(): Boolean {
        initialized = false; handbackReady = false
        val before = store.read()
        if (!effects.revokeAllRelaxations()) return false
        if (!effects.protectedStateCheck() || !effects.readBackPolicy()) return false
        if (!store.compareAndSet(before, SessionJournal.Empty)) return false
        initialized = true; handbackReady = true
        return true
    }

    @Synchronized
    fun start(id: String, kind: WindowKind, battery: BatteryState, freshAuthority: FreshAuthority): SessionOutcome {
        if (!initialized) return SessionOutcome.NOT_READY
        val before = store.read()
        if (before != SessionJournal.Empty) return SessionOutcome.BUSY
        if (!handbackReady) return SessionOutcome.NOT_READY
        if (id.isBlank()) return SessionOutcome.REFUSED
        val packages = when (kind) {
            WindowKind.PLAY -> setOf(PackageName("com.android.vending"))
            WindowKind.SETTINGS -> inventory?.settingsSessionPackages?.takeIf { it.isNotEmpty() } ?: return SessionOutcome.REFUSED
            else -> return SessionOutcome.REFUSED
        }
        if (!battery.allowsDiscretionaryUnhide) return SessionOutcome.LOW_BATTERY
        val now = clock.checkpoint() ?: return SessionOutcome.NOT_READY
        if (!authority.consume(freshAuthority, PrivilegedAction("session:${kind.name}", id))) return SessionOutcome.REFUSED
        val duration = if (kind == WindowKind.PLAY) 600_000L else 300_000L
        val end = try { Math.addExact(now.boot.elapsedMillis, duration) } catch (_: ArithmeticException) { return SessionOutcome.REFUSED }
        val access = SessionAccess(id, kind, packages, BootTime(now.boot.bootId, end))
        val opening = SessionJournal.Active(access, SessionPhase.OPENING)
        handbackReady = false
        if (!store.compareAndSet(before, opening)) return SessionOutcome.NOT_READY
        val beforeOpen = clock.checkpoint()
        if (beforeOpen == null || !access.isLive(beforeOpen.boot) || !effects.open(access) || !effects.readBackPolicy()) {
            close(opening)
            return SessionOutcome.NOT_READY
        }
        if (!store.compareAndSet(opening, opening.copy(phase = SessionPhase.OPEN))) {
            onStart()
            return SessionOutcome.NOT_READY
        }
        return SessionOutcome.OPENED
    }

    @Synchronized
    fun access(): SessionAccess? {
        if (!initialized) return null
        val active = store.read() as? SessionJournal.Active ?: return null
        if (active.phase != SessionPhase.OPEN) return null
        val now = clock.checkpoint() ?: return null
        return active.access.takeIf { it.isLive(now.boot) }
    }

    @Synchronized
    fun tick(): SessionOutcome {
        val record = store.read()
        if (record == SessionJournal.Damaged || (record == SessionJournal.Empty && !handbackReady)) {
            return if (onStart()) SessionOutcome.CLOSED else SessionOutcome.NOT_READY
        }
        val active = record as? SessionJournal.Active ?: return SessionOutcome.IGNORED
        if (access() != null) return SessionOutcome.IGNORED
        return close(active)
    }

    @Synchronized
    fun end(id: String, trigger: SessionEnd): SessionOutcome {
        val active = store.read() as? SessionJournal.Active ?: return SessionOutcome.IGNORED
        if (active.access.id != id) return SessionOutcome.IGNORED
        // All explicit end events are restrictive; none needs fresh authority.
        return close(active)
    }

    private fun close(before: SessionJournal.Active): SessionOutcome {
        handbackReady = false
        val closing = before.copy(phase = SessionPhase.CLOSING)
        if (!store.compareAndSet(before, closing)) {
            effects.revokeAllRelaxations()
            return SessionOutcome.NOT_READY
        }
        if (!effects.revokeAllRelaxations() || !effects.protectedStateCheck() || !effects.readBackPolicy()) return SessionOutcome.NOT_READY
        if (!store.compareAndSet(closing, SessionJournal.Empty)) return SessionOutcome.NOT_READY
        handbackReady = true
        return SessionOutcome.CLOSED
    }
}
