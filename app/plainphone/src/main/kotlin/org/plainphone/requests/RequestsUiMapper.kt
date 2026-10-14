// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.requests

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.plainphone.policy.*
import org.plainphone.ui.requests.*

object RequestsUiMapper {
    fun map(view: RequestView, draft: String, result: RequestStatus?, busy: Boolean, historyOnly: Boolean = false): RequestsUiState =
        RequestsUiState(view.isSetUp, view.recipientName, draft, view.available && view.isSetUp && AppRequests.validText(draft) && !busy,
            busy, (if (!view.available) RequestStatus.StorageError else result)?.let(::status),
            view.requests.map { RequestUiRow(it.text, LocalDate.ofEpochDay(it.day).format(DateTimeFormatter.ofPattern("d MMM uuuu")), status(it.status), it.id) }, historyOnly)
    private fun status(value: RequestStatus) = RequestUiStatus.valueOf(value.name)
}

/** Run on the policy dispatcher. A new PIN check is consumed immediately for each mutation. */
class RecipientChange(private val authenticate: (CharArray) -> Boolean, private val save: (RequestRecipient?) -> Unit) {
    fun apply(pin: CharArray, recipient: RequestRecipient?, active: () -> Boolean): Boolean = try {
        if (!active() || !authenticate(pin) || !active()) false else { save(recipient); true }
    } catch (_: Exception) { false } finally { pin.fill('\u0000') }
}
