// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

/** Requests use their own explicit recipient, never a name match or a guessed Family contact. */
data class RequestRecipient(val name: String, val number: String) {
    init { require((name.isEmpty() || FamilyRules.validName(name)) && FamilyRules.validNumber(number)) }
    override fun toString() = "RequestRecipient(redacted)"
}
enum class RequestPart { Accepted, NoSignal, NoPermission, Failed, Unconfirmed }
enum class RequestStatus { Sending, Sent, NoSignal, NoPermission, Failed, NotConfirmed, NotSetUp, Invalid, DailyLimit, StorageError }
data class RequestEntry(val id: String, val text: String, val day: Long, val started: Long, val process: String,
    val parts: List<RequestPart?>) {
    override fun toString() = "RequestEntry(redacted)"
}
data class RequestBook(val recipient: RequestRecipient? = null, val requests: List<RequestEntry> = emptyList())
interface RequestStore { fun read(): RequestBook; fun write(value: RequestBook) }
interface RequestSmsPort {
    fun problem(): RequestStatus?
    fun parts(text: String): List<String>
    fun send(number: String, id: String, parts: List<String>)
}
data class RequestDisplay(val id: String, val text: String, val day: Long, val status: RequestStatus)
data class RequestView(val isSetUp: Boolean, val recipientName: String, val requests: List<RequestDisplay>, val available: Boolean = true)
data class RequestSubmission(val status: RequestStatus, val id: String? = null)

/** Serial durable outbox. Every attempt consumes quota; callbacks only update their own stored part.
 * Interrupted/partial sends are uncertain and never automatically retried. No delivery claim is made.
 */
class AppRequests(private val store: RequestStore, private val sms: RequestSmsPort, private val process: String,
    private val clock: () -> Long, private val day: () -> Long) {
    @Synchronized fun recipient(): RequestRecipient? = store.read().recipient
    @Synchronized fun setRecipient(recipient: RequestRecipient?) { store.write(store.read().copy(recipient = recipient)) }
    @Synchronized fun view(): RequestView = try {
        val book = store.read()
        RequestView(book.recipient != null, book.recipient?.name.orEmpty(), book.requests.map {
            RequestDisplay(it.id, it.text, it.day, status(it))
        })
    } catch (_: Exception) { RequestView(false, "", emptyList(), available = false) }

    @Synchronized fun submit(id: String, text: String): RequestSubmission {
        if (!id.matches(Regex("[A-Za-z0-9-]{1,64}")) || !validText(text)) return RequestSubmission(RequestStatus.Invalid)
        val book = try { store.read() } catch (_: Exception) { return RequestSubmission(RequestStatus.StorageError) }
        book.requests.firstOrNull { it.id == id }?.let { return RequestSubmission(status(it), id) }
        val recipient = book.recipient ?: return RequestSubmission(RequestStatus.NotSetUp)
        if (book.requests.count { it.day == day() } >= DAILY_LIMIT) return RequestSubmission(RequestStatus.DailyLimit)
        val problem = try { sms.problem() } catch (_: Exception) { RequestStatus.Failed }
        val parts = if (problem != null) listOf(text) else try { sms.parts(text) } catch (_: Exception) { return RequestSubmission(RequestStatus.StorageError) }
        if (parts.isEmpty() || parts.size > 16) return RequestSubmission(RequestStatus.Invalid)
        val rejected = when (problem) {
            RequestStatus.NoSignal -> RequestPart.NoSignal
            RequestStatus.NoPermission -> RequestPart.NoPermission
            null -> null
            else -> RequestPart.Failed
        }
        val entry = RequestEntry(id, text, day(), clock(), process, List(parts.size) { rejected })
        try { store.write(book.copy(requests = listOf(entry) + book.requests)) }
        catch (_: Exception) { return RequestSubmission(RequestStatus.StorageError) }
        if (problem == null) try { sms.send(recipient.number, id, parts) }
        catch (_: SecurityException) { reject(id, RequestPart.NoPermission) }
        // A call may have reached the modem before throwing. Preserve Sending => Not confirmed.
        catch (_: Exception) { reject(id, RequestPart.Unconfirmed); return RequestSubmission(RequestStatus.NotConfirmed, id) }
        return RequestSubmission(view().requests.firstOrNull { it.id == id }?.status ?: RequestStatus.NotConfirmed, id)
    }
    private fun reject(id: String, part: RequestPart) {
        val count = try { store.read().requests.first { it.id == id }.parts.size } catch (_: Exception) { return }
        repeat(count) { result(id, it, part) }
    }
    @Synchronized fun result(id: String, index: Int, part: RequestPart): Boolean { return try {
        val book = store.read()
        val entry = book.requests.firstOrNull { it.id == id } ?: return false
        if (index !in entry.parts.indices || entry.parts[index] != null && entry.parts[index] != RequestPart.Unconfirmed) return false
        val updated = entry.copy(parts = entry.parts.mapIndexed { i, old -> if (i == index) part else old })
        store.write(book.copy(requests = book.requests.map { if (it.id == id) updated else it })); true
    } catch (_: Exception) { false } }
    private fun status(entry: RequestEntry): RequestStatus {
        val parts = entry.parts
        if (parts.any { it == RequestPart.Unconfirmed }) return RequestStatus.NotConfirmed
        if (parts.all { it == RequestPart.Accepted }) return RequestStatus.Sent
        val failures = parts.filterNotNull().filter { it != RequestPart.Accepted }
        if (failures.isNotEmpty()) {
            if (parts.any { it == null || it == RequestPart.Accepted }) return RequestStatus.NotConfirmed
            return when {
                failures.all { it == RequestPart.NoSignal } -> RequestStatus.NoSignal
                failures.all { it == RequestPart.NoPermission } -> RequestStatus.NoPermission
                else -> RequestStatus.Failed
            }
        }
        if (entry.process != process || clock() - entry.started > UNANSWERED_MS || clock() < entry.started)
            return RequestStatus.NotConfirmed
        return RequestStatus.Sending
    }
    companion object {
        const val DAILY_LIMIT = 5
        const val UNANSWERED_MS = 60_000L
        fun validText(text: String) = text.length <= 280 && text.any(Char::isLetter)
    }
}
