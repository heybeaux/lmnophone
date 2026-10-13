// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import java.io.File
import java.io.IOException
import java.security.MessageDigest
import org.plainphone.device.voice.VoiceDraftStore
import org.plainphone.device.voice.VoicePerson
import org.plainphone.device.voice.VoiceSendPort

/** The Android side of a send. [AndroidMmsPort] is the real one. */
interface MmsPort {
    /** The carrier's MMS settings, or null when they can't be read. */
    fun carrier(): CarrierMms?
    /** False only when the phone knows it has no service; null when it can't tell. */
    fun hasSignal(): Boolean?
    /** True when this app holds SEND_SMS (granting it first if the phone allows). */
    fun permitted(): Boolean
    /**
     * Hands [pdu] to the phone's MMS service. The answer comes later through [VoiceSender.onResult] with the
     * same [personId] and [attemptId]. May throw (SecurityException without SEND_SMS).
     */
    fun send(personId: String, attemptId: String, pdu: ByteArray)
    /** Deletes the copy of the message kept for [attemptId]. Never throws. */
    fun release(attemptId: String)
}

data class SendView(val status: SendStatus, val seen: Boolean, val failures: Int)

/** One person's last send, as stored. Never holds the phone number or the audio. */
data class SendRecord(val attemptId: String, val status: SendStatus, val processId: String, val startedAtMs: Long,
    val draftDigest: String, val failures: Int, val seen: Boolean)

/**
 * One file per person, `<personId>.send`, written to a temporary file and renamed into place so a process death
 * leaves either the old or the new record. A damaged record reads as no record, never as Sent.
 */
class VoiceSendStore(val dir: File) {
    fun read(personId: String): SendRecord? {
        if (!ID.matches(personId)) return null
        return try {
            val f = File(dir, "$personId.send")
            if (!f.isFile || f.length() > 4_096) return null
            parse(f.readText(Charsets.UTF_8))
        } catch (_: IOException) { null }
    }

    /** @throws IOException when the record can't be saved. */
    fun write(personId: String, record: SendRecord) {
        require(ID.matches(personId)) { "not a person id" }
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("send folder unavailable")
        val tmp = File(dir, "$personId.send.tmp")
        tmp.writeText(format(record), Charsets.UTF_8)
        if (!tmp.renameTo(File(dir, "$personId.send"))) { tmp.delete(); throw IOException("could not save send") }
    }

    fun delete(personId: String) { if (ID.matches(personId)) File(dir, "$personId.send").delete() }

    private fun format(r: SendRecord) = buildString {
        append("attempt=").append(r.attemptId).append('\n')
        append("status=").append(statusText(r.status)).append('\n')
        append("process=").append(r.processId).append('\n')
        append("started=").append(r.startedAtMs).append('\n')
        append("draft=").append(r.draftDigest).append('\n')
        append("failures=").append(r.failures).append('\n')
        append("seen=").append(r.seen).append('\n')
    }

    private fun parse(text: String): SendRecord? {
        val map = HashMap<String, String>()
        for (line in text.split('\n')) {
            if (line.isEmpty()) continue
            val eq = line.indexOf('=')
            if (eq <= 0) return null
            map[line.substring(0, eq)] = line.substring(eq + 1)
        }
        val attempt = map["attempt"]?.takeIf { TOKEN.matches(it) } ?: return null
        val status = map["status"]?.let(::statusOf) ?: return null
        val process = map["process"]?.takeIf { TOKEN.matches(it) } ?: return null
        val started = map["started"]?.toLongOrNull() ?: return null
        val draft = map["draft"]?.takeIf { DIGEST.matches(it) } ?: return null
        val failures = map["failures"]?.toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val seen = map["seen"]?.toBooleanStrictOrNull() ?: return null
        return SendRecord(attempt, status, process, started, draft, failures, seen)
    }

    private fun statusText(s: SendStatus) = when (s) {
        SendStatus.Sending -> "sending"
        SendStatus.Sent -> "sent"
        is SendStatus.NotSent -> "notsent:${s.problem.name}"
    }

    private fun statusOf(t: String): SendStatus? = when {
        t == "sending" -> SendStatus.Sending
        t == "sent" -> SendStatus.Sent
        t.startsWith("notsent:") -> SendProblem.entries.firstOrNull { it.name == t.removePrefix("notsent:") }
            ?.let { SendStatus.NotSent(it) }
        else -> null
    }

    companion object {
        val ID = Regex("[a-z0-9]{1,32}")
        val TOKEN = Regex("[A-Za-z0-9._:-]{1,64}")
        val DIGEST = Regex("[0-9a-f]{64}|none")
    }
}

/**
 * Sends one person's voice draft as MMS, only when asked (the Review screen's Send or Try again), and says
 * truthfully what happened (PLA-499). No timer, no retry and no resend: every send is one explicit call.
 *
 * - The record says Sending before the message is handed over, so a process death mid-send is never lost.
 * - Sent only on RESULT_OK with an m-send-conf whose status is Ok ([MmsSendResult]).
 * - A Sending record from an earlier process, or one with no answer for [UNANSWERED_MS], shows as
 *   Not confirmed. That only changes what is shown; it never resends.
 * - After a confirmed Sent, our draft is deleted, but only if it is still the recording that was sent.
 */
class VoiceSender(
    private val store: VoiceSendStore,
    private val drafts: VoiceDraftStore,
    private val port: MmsPort,
    private val processId: String,
    private val clock: () -> Long,
    private val newAttemptId: () -> String,
) : VoiceSendPort {
    /** Called with the person id after a result is recorded. */
    var onUpdate: (String) -> Unit = {}

    override fun send(person: VoicePerson, number: String): SendView {
        if (!VoiceSendStore.ID.matches(person.id)) return SendView(SendStatus.NotSent(SendProblem.OtherError), false, 0)
        val previous = store.read(person.id)
        val failures = previous?.failures ?: 0
        val current = previous?.let {
            if (it.status == SendStatus.Sent) deleteDraftIfSent(person.id, it.draftDigest)
            shown(it)
        }
        if (current?.status == SendStatus.Sending) return current
        if (current?.status == SendStatus.Sent && drafts.draft(person.id) == null) return current
        val normalized = VoiceNumber.normalize(number)
        val audio = if (drafts.draft(person.id) != null) readOrNull(drafts.file(person.id)) else null
        if (normalized == null || audio == null || audio.isEmpty()) {
            return SendView(SendStatus.NotSent(SendProblem.OtherError), false, failures)
        }
        val attempt = newAttemptId()
        val digest = digest(audio)
        fun refuse(problem: SendProblem): SendView {
            val record = SendRecord(attempt, SendStatus.NotSent(problem), processId, clock(), digest, failures + 1, false)
            try { store.write(person.id, record) } catch (_: IOException) { }
            return SendView(record.status, false, record.failures)
        }
        val permitted = try { port.permitted() } catch (_: Exception) { false }
        if (!permitted) return refuse(SendProblem.NoPermission)
        val carrier = port.carrier()
        if (carrier?.enabled == false) return refuse(SendProblem.MmsOff)
        if (port.hasSignal() == false) return refuse(SendProblem.NoSignal)
        val pdu = try { VoiceMmsPdu.sendRequest(normalized, audio, clock() / 1000) } catch (_: RuntimeException) {
            return refuse(SendProblem.OtherError)
        }
        if (pdu.size > VoiceSendLimits.maxMessageBytes(carrier)) return refuse(SendProblem.TooBig)
        val sending = SendRecord(attempt, SendStatus.Sending, processId, clock(), digest, failures, false)
        try { store.write(person.id, sending) } catch (_: IOException) {
            return SendView(SendStatus.NotSent(SendProblem.OtherError), false, failures)
        }
        return try {
            port.send(person.id, attempt, pdu)
            view(person.id) ?: SendView(SendStatus.Sending, false, failures)
        } catch (_: SecurityException) {
            port.release(attempt)
            refuse(SendProblem.NoPermission)
        } catch (_: Exception) {
            port.release(attempt)
            refuse(SendProblem.OtherError)
        }
    }

    /**
     * The MMS service's answer for [attemptId]. Ignored (false) unless it is the person's latest send and that
     * send is still waiting or not confirmed. The attempt's copy of the message is always released.
     */
    fun onResult(personId: String, attemptId: String, code: Int, data: ByteArray?): Boolean {
        port.release(attemptId)
        val record = store.read(personId) ?: return false
        if (record.attemptId != attemptId || record.status != SendStatus.Sending) return false
        val status = MmsSendResult.map(code, data)
        val failures = if (status is SendStatus.NotSent) record.failures + 1 else record.failures
        try {
            store.write(personId, record.copy(status = status, failures = failures, seen = false))
        } catch (_: IOException) { return false }
        if (status == SendStatus.Sent) deleteDraftIfSent(personId, record.draftDigest)
        onUpdate(personId)
        return true
    }

    /** What to show for [personId], or null when nothing was sent. */
    override fun view(personId: String): SendView? = store.read(personId)?.let {
        if (it.status == SendStatus.Sent) deleteDraftIfSent(personId, it.draftDigest)
        shown(it)
    }

    /** The person has seen the outcome. */
    override fun seen(personId: String) {
        val record = store.read(personId) ?: return
        if (!record.seen) try { store.write(personId, record.copy(seen = true)) } catch (_: IOException) { }
    }

    /** Forgets the outcome; the draft is not touched. */
    fun forget(personId: String) = store.delete(personId)

    private fun shown(r: SendRecord): SendView {
        val unanswered = r.status == SendStatus.Sending &&
            (r.processId != processId || clock() - r.startedAtMs > UNANSWERED_MS)
        return SendView(if (unanswered) SendStatus.NotSent(SendProblem.NotConfirmed) else r.status, r.seen, r.failures)
    }

    private fun deleteDraftIfSent(personId: String, sentDigest: String) {
        if (drafts.draft(personId) == null) return
        val current = readOrNull(drafts.file(personId)) ?: return
        if (digest(current) == sentDigest) drafts.file(personId).delete()
    }

    private fun readOrNull(f: File): ByteArray? = try { f.readBytes() } catch (_: IOException) { null }

    private fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    companion object {
        /** A send with no answer for longer than this shows as Not confirmed. An unreachable MMSC took ~3 min. */
        const val UNANSWERED_MS = 10L * 60 * 1000
    }
}
