// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.session

import org.plainphone.policy.JournalStore
import org.plainphone.policy.SessionJournal

/** Before PIN enrollment there can be no elevation. Once any authority record exists, damage
 * must remain damage in that combined record; never fall back to an empty legacy journal.
 * Both stores are device protected, and the caller serializes enrollment and all session work.
 */
class PreviewSessionJournal(private val legacy: JournalStore, private val combined: JournalStore,
    private val enrolled: () -> Boolean) : JournalStore {
    private fun current() = if (enrolled()) combined else legacy
    override fun beginRevocation() = current().beginRevocation()
    override fun revocationPending() = current().revocationPending()
    override fun ownedByElevation() = current().ownedByElevation()
    override fun read() = current().read()
    override fun compareAndSet(expected: SessionJournal, replacement: SessionJournal) =
        current().compareAndSet(expected, replacement)
}
