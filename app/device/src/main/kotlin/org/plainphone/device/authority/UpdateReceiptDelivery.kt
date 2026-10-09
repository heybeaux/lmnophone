// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.PersonContact
import org.plainphone.policy.UpdateReceipt

/**
 * Hands one Update submission notice to the delivery channel. [dedupId] is stable for this receipt
 * and recipient across restarts, so a receiver can drop a repeat. True means handed over durably.
 */
fun interface UpdateNoticeSender {
    fun send(dedupId: String, recipient: TrustedPersonId, contact: PersonContact, receipt: UpdateReceipt): Boolean
}

/**
 * Startup delivery of the Update receipt outbox. Each recipient gets the address frozen with the
 * submission, never a later holder's; a recipient frozen without an address is acknowledged without
 * sending. An acknowledgement is written only after the sender accepts, so a crash in between sends
 * the same dedup ID again. Delivery never authorizes or retries an installation.
 */
class UpdateReceiptDelivery(private val store: JointAuthorityStore, private val sender: UpdateNoticeSender) {
    /** True when nothing is left pending; false when the outbox is unreadable or any notice is still owed. */
    fun deliverPending(): Boolean {
        val outbox = store.updateReceipts() ?: return false
        var done = true
        for (entry in outbox) {
            for (person in entry.receipt.recipientGenerations.keys.sortedBy { it.value }) {
                if (person in entry.acknowledged) continue
                val contact = entry.recipientContacts[person]
                val handed = contact == null || sender.send("${entry.receipt.id}:${person.value}", person, contact, entry.receipt)
                done = handed && store.acknowledgeUpdateReceipt(entry.receipt.id, person) && done
            }
        }
        return done
    }
}
