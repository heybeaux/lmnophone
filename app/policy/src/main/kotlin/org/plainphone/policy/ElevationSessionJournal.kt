// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/**
 * Policy-owned journal projection. SessionController never writes a journal-only replacement into a
 * combined elevation record: restrictive writes close its access flag in the very same durable CAS.
 * One shared ElevationStore instance also fences failed revocation across both live controllers.
 */
internal class ElevationSessionJournal(private val store: ElevationStore) : JournalStore {
    override fun read(): SessionJournal = store.read()?.journal ?: SessionJournal.Damaged
    override fun beginRevocation() { store.revocationPending = true }
    override fun revocationPending(): Boolean = store.revocationPending

    override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal): Boolean {
        val before = store.read() ?: return false // Only elevation startup can repair a damaged debit ledger.
        if (before.journal != expected || before.revision == Long.MAX_VALUE || replacement == SessionJournal.Damaged) return false
        val active = replacement as? SessionJournal.Active
        val grant = before.state.grant
        val nextState = if (grant != null) {
            // The supervised controller may restrict an elevation, never open/reopen/replace its session.
            if (active != null && (active.phase != SessionPhase.CLOSING ||
                    active.access != (before.journal as? SessionJournal.Active)?.access)) return false
            ElevationMachine.closePackageAccess(before.state, grant.id).state
        } else {
            if (before.state.countdown != null && active != null) return false
            before.state
        }
        val next = before.replacing(nextState, replacement)
        if (!store.compareAndSet(before, next, null)) return false
        if (replacement == SessionJournal.Empty) store.revocationPending = false
        return true
    }
}
