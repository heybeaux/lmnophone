// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*

data class BatteryState(val percent: Int, val charging: Boolean) {
    init { require(percent in 0..100) }
    val allowsDiscretionaryUnhide: Boolean get() = charging || percent >= 5
}

internal val PLAY_PACKAGE = PackageName("com.android.vending")
internal fun sessionPackages(kind: WindowKind, matchingInventory: BuildInventory?): Set<PackageName>? = when (kind) {
    WindowKind.PLAY -> setOf(PLAY_PACKAGE)
    WindowKind.SETTINGS -> matchingInventory?.settingsSessionPackages?.takeIf { it.isNotEmpty() }
    else -> null
}

class SessionAccess internal constructor(val id: String, val kind: WindowKind, packages: Set<PackageName>, val deadline: BootTime) {
    val packages: Set<PackageName> = immutableSet(packages)
    fun isLive(now: BootTime): Boolean = now.bootId == deadline.bootId && now.elapsedMillis < deadline.elapsedMillis
    override fun equals(other: Any?): Boolean = other is SessionAccess &&
        id == other.id && kind == other.kind && packages == other.packages && deadline == other.deadline
    override fun hashCode(): Int = listOf(id, kind, packages, deadline).hashCode()
}

enum class SessionPhase { OPENING, OPEN, CLOSING }
sealed interface SessionJournal {
    data object Empty : SessionJournal
    data object Damaged : SessionJournal
    data class Active(val access: SessionAccess, val phase: SessionPhase) : SessionJournal

    companion object {
        /**
         * Public adapter boundary after schema/checksum and scalar decoding. Pass only an inventory
         * matching the live model/fingerprint. Invalid scope becomes Damaged for full revocation.
         * Restoring data does not restore authority: the controller must run onStart before access.
         */
        fun restore(
            id: String, kind: WindowKind, packages: Set<PackageName>, deadline: BootTime,
            phase: SessionPhase, matchingInventory: BuildInventory?,
        ): SessionJournal {
            val expectedPackages = sessionPackages(kind, matchingInventory)
            if (id.isBlank() || expectedPackages == null || packages != expectedPackages) return Damaged
            return Active(SessionAccess(id, kind, packages, deadline), phase)
        }
    }
}
interface JournalStore {
    /** Shared stores latch restrictive intent before effects or a possibly failing journal write. */
    fun beginRevocation() {}
    fun revocationPending(): Boolean = false
    /** Ownership data only; never grants access. Combined stores override on the same coordinator. */
    fun ownedByElevation(): Boolean = false
    /** Device-protected only. Invalid/missing checksum on an entry returns Damaged. */
    fun read(): SessionJournal
    /** Durable compare-and-set; never acknowledge before the checksum-protected write completes. */
    fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean
}

/** Lane D implements the effects and their readbacks. Calls/emergency must never depend on these returning true. */
interface SessionEffects {
    /**
     * Invalidate every session relaxation, including missing/damaged journals. During a COMMITTING
     * turn-off, invalidate temporary authority without reinstating restored lockdown settings;
     * physical policy changes must follow PolicyEngine's fresh operation-aware plan.
     */
    fun revokeAllRelaxations(): Boolean
    /** Only these packages may open. Every unhide is followed by the synchronous flush and readback. No restriction is lifted. */
    fun open(access: SessionAccess): Boolean
    /**
     * Repair only invariants emitted by PolicyEngine.decide with a fresh waiting-operation read.
     * A COMMITTING turn-off permits emergency-floor repair only. Never re-assert full lockdown
     * independently here, including during onStart. Preserve ordinary settings; false prevents handback.
     */
    fun protectedStateCheck(): Boolean
    /** Check only non-null expectations of that same operation-aware plan. */
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
    private val supervisedMode: () -> Boolean = { true },
    private val selfManagedMode: () -> Boolean = { false },
) {
    /** Combined-store entry: policy owns the journal projection and checks current supervised mode. */
    constructor(store: ElevationStore, effects: SessionEffects, clock: RunningClock,
        inventory: BuildInventory?, authority: AuthorityConsumer, mode: () -> Mode?) :
        this(ElevationSessionJournal(store), effects, clock, inventory, authority,
            { mode() == Mode.TRUSTED }, { mode() == Mode.SELF_MANAGED })
    var handbackReady: Boolean = false
        private set
    private var initialized = false
    // An end event remains restrictive even when its durable closing write fails.
    private var closingRequested = false

    /** Invoke before any other Plainphone work, including Direct Boot. No credential storage is needed. */
    @Synchronized
    fun onStart(): Boolean {
        initialized = false; handbackReady = false; closingRequested = true
        store.beginRevocation()
        val before = store.read()
        if (!effects.revokeAllRelaxations()) return false
        if (!effects.protectedStateCheck() || !effects.readBackPolicy()) return false
        if (!store.compareAndSet(before, SessionJournal.Empty)) return false
        initialized = true; handbackReady = true; closingRequested = false
        return true
    }

    @Synchronized
    fun start(id: String, kind: WindowKind, battery: BatteryState, freshAuthority: FreshAuthority): SessionOutcome {
        if (!initialized) return SessionOutcome.NOT_READY
        if (!supervisedMode()) return SessionOutcome.REFUSED
        if (store.revocationPending()) return SessionOutcome.NOT_READY
        val before = store.read()
        if (before != SessionJournal.Empty) return SessionOutcome.BUSY
        if (!handbackReady) return SessionOutcome.NOT_READY
        if (id.isBlank()) return SessionOutcome.REFUSED
        val packages = sessionPackages(kind, inventory) ?: return SessionOutcome.REFUSED
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
        if (!initialized || closingRequested || store.revocationPending() || !supervisedMode()) return null
        val active = store.read() as? SessionJournal.Active ?: return null
        if (active.phase != SessionPhase.OPEN) return null
        val now = clock.checkpoint() ?: return null
        return active.access.takeIf { it.isLive(now.boot) }
    }

    @Synchronized
    fun tick(): SessionOutcome {
        if (closingRequested) return if (onStart()) SessionOutcome.CLOSED else SessionOutcome.NOT_READY
        val record = store.read()
        if (record == SessionJournal.Damaged || (record == SessionJournal.Empty && !handbackReady)) {
            return if (onStart()) SessionOutcome.CLOSED else SessionOutcome.NOT_READY
        }
        val active = record as? SessionJournal.Active ?: return SessionOutcome.IGNORED
        // The elevation controller owns its periodic liveness/deadline checks. Do not mistake the
        // non-granting foreign projection for a request to close a healthy self-managed session.
        // Existing local/shared revocation and unknown modes still take the restrictive path.
        if (initialized && !store.revocationPending() && selfManagedMode() && store.ownedByElevation())
            return SessionOutcome.IGNORED
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
        closingRequested = true
        store.beginRevocation()
        handbackReady = false
        val closing = before.copy(phase = SessionPhase.CLOSING)
        if (!store.compareAndSet(before, closing)) {
            effects.revokeAllRelaxations()
            return SessionOutcome.NOT_READY
        }
        if (!effects.revokeAllRelaxations() || !effects.protectedStateCheck() || !effects.readBackPolicy()) return SessionOutcome.NOT_READY
        if (!store.compareAndSet(closing, SessionJournal.Empty)) return SessionOutcome.NOT_READY
        handbackReady = true; closingRequested = false
        return SessionOutcome.CLOSED
    }
}
