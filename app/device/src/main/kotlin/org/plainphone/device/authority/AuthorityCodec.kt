// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.CredentialChannel
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.FrictionSettingsPatch
import org.plainphone.policy.FrictionSettingsReceipt
import org.plainphone.policy.LockoutNotice
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RecoveredState
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.RecoveryReceipt
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingIdentity
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState
import org.plainphone.policy.WaitingText

/** A routine friction receipt as stored in the outbox. Recipients and addresses are fixed at commit. */
class StoredFrictionReceipt(
    val key: String,
    val actor: TrustedPersonId,
    val source: FrictionSettings,
    val target: FrictionSettings,
    val recipientGenerations: Map<TrustedPersonId, Long>,
    /** Null means no available address; never look one up later. */
    val recipientContacts: Map<TrustedPersonId, PersonContact?>,
) {
    override fun toString() = "StoredFrictionReceipt(redacted)"

    internal companion object {
        fun of(r: FrictionSettingsReceipt) =
            StoredFrictionReceipt(r.key, r.actor, r.source, r.target, r.recipientGenerations, r.recipientContacts)
    }
}

/**
 * Strict binary encoding for each section of the joint authority record. Every decoder rejects
 * truncated, padded or invalid data by returning null; it never builds an empty or default state.
 *
 * Encoders return null for data this adapter cannot yet read back: live elevation countdowns and
 * grants, active session journals, and people or mode-switch patches. Their public decoders are
 * pending in policy (PLA-114), so storing them would leave an undecodable record.
 */
internal object AuthorityCodec {
    private const val MAX_ITEMS = 4096

    fun encodeCredentials(s: CredentialState): ByteArray = write {
        writeUTF(s.mode.name); writeLong(s.revision); writeLong(s.structureGeneration)
        list(s.people.entries) { (id, p) ->
            writeUTF(id.value); verifier(p.pin); verifier(p.recovery); counter(p.pinCounter); counter(p.recoveryCounter)
            writeLong(p.generation); optional(p.contact) { contact(it) }
        }
        list(s.pendingNotices) { writeUTF(it.person.value); writeUTF(it.channel.name); writeLong(it.revision) }
        optional(s.recoveryProfile) { profile ->
            list(profile.holderUserIds.entries) { (id, user) -> writeUTF(id.value); writeUTF(user) }
            optional(profile.backupUserId) { writeUTF(it) }
            friction(profile.friction)
        }
        optional(s.appliedPeopleChange) { writeUTF(it.requestId); writeUTF(it.waitingGeneration); writeLong(it.structureGeneration) }
    }!!

    fun decodeCredentials(bytes: ByteArray): CredentialState? = read(bytes) {
        val mode = Mode.valueOf(readUTF()); val revision = readLong(); val structure = readLong()
        val people = map { TrustedPersonId(readUTF()) to PersonCredential(verifier(), verifier(), counter(), counter(), readLong(), optional { contact() }) }
        val noticeList = list { LockoutNotice(TrustedPersonId(readUTF()), CredentialChannel.valueOf(readUTF()), readLong()) }
        val notices = noticeList.toSet().also { require(it.size == noticeList.size) }
        val profile = optional {
            RecoveryProfile(map { TrustedPersonId(readUTF()) to readUTF() }, optional { readUTF() }, friction())
        }
        val applied = optional { AppliedPeopleChange(readUTF(), readUTF(), readLong()) }
        CredentialState(mode, people, revision, structure, notices, profile, applied)
    }

    fun encodeWaiting(s: WaitingState): ByteArray? = write {
        writeLong(s.revision); writeUTF(s.storageGeneration)
        optional(s.operation) { operation(it) }
        list(s.texts) { t ->
            writeUTF(t.key); writeUTF(t.event.name); operation(t.operation); recipients(t.recipientGenerations, t.recipientContacts)
        }
        list(s.recoveryReceipts) { r ->
            writeUTF(r.key); writeUTF(r.mode.name)
            list(r.recipientGenerations.entries) { (id, g) -> writeUTF(id.value); writeLong(g) }
            friction(r.friction); writeUTF(r.state.name)
        }
    }

    fun decodeWaiting(bytes: ByteArray): WaitingState? = read(bytes) {
        val revision = readLong(); val generation = readUTF()
        val operation = optional { operation() }
        val texts = list {
            val key = readUTF(); val event = WaitingEvent.valueOf(readUTF()); val op = operation()
            val (generations, contacts) = recipients()
            WaitingText(key, event, op, generations, contacts)
        }
        val receipts = list {
            RecoveryReceipt(readUTF(), Mode.valueOf(readUTF()), map { TrustedPersonId(readUTF()) to readLong() },
                friction(), RecoveredState.valueOf(readUTF()))
        }
        WaitingState(revision, operation, texts, generation, receipts)
    }

    fun encodeElevation(r: ElevationRecord): ByteArray? = write {
        if (r.state.countdown != null || r.state.grant != null || r.person != null || r.journal != SessionJournal.Empty) unsupported()
        writeLong(r.revision)
        list(r.state.ledger.debits) { writeUTF(it.id); time(it.at) }
    }

    fun decodeElevation(bytes: ByteArray): ElevationRecord? = read(bytes) {
        val revision = readLong()
        ElevationRecord(revision, ElevationState(ElevationLedger(list { ElevationDebit(readUTF(), time()) })))
    }

    fun encodeReceipts(receipts: List<StoredFrictionReceipt>): ByteArray = write {
        list(receipts) { r ->
            writeUTF(r.key); writeUTF(r.actor.value); friction(r.source); friction(r.target)
            recipients(r.recipientGenerations, r.recipientContacts)
        }
    }!!

    fun decodeReceipts(bytes: ByteArray): List<StoredFrictionReceipt>? = read(bytes) {
        list {
            val key = readUTF(); val actor = TrustedPersonId(readUTF()); val source = friction(); val target = friction()
            val (generations, contacts) = recipients()
            require(key.isNotBlank())
            StoredFrictionReceipt(key, actor, source, target, generations, contacts)
        }
    }

    private class Unsupported : RuntimeException()
    private fun unsupported(): Nothing = throw Unsupported()

    private fun write(block: DataOutputStream.() -> Unit): ByteArray? = try {
        val bytes = ByteArrayOutputStream()
        DataOutputStream(bytes).use { it.block() }
        bytes.toByteArray()
    } catch (_: Unsupported) {
        null
    }

    private fun <T> read(bytes: ByteArray, block: DataInputStream.() -> T): T? = try {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        val value = input.block()
        if (input.available() != 0) null else value
    } catch (_: IOException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } catch (_: IllegalStateException) {
        null
    } catch (_: ArithmeticException) {
        null
    }

    private fun DataOutputStream.operation(op: WaitingOperation) {
        if (op.peoplePatch != null || op.modePatch != null) unsupported()
        writeUTF(op.identity.requestId); writeUTF(op.identity.cancelCode); writeUTF(op.starter.value)
        writeLong(op.generation); writeLong(op.structureGeneration); writeUTF(op.mode.name); time(op.startedAt)
        writeUTF(op.phase.name); recipients(op.recipientGenerations, op.recipientContacts); writeInt(op.restoredSteps)
        optional(op.skipper) { writeUTF(it.value) }; optional(op.skipperGeneration) { writeLong(it) }
        optional(op.stopper) { writeUTF(it.value) }; optional(op.stopperGeneration) { writeLong(it) }
        // The original immutable patch fields, never re-derived from current credentials.
        optional(op.frictionPatch) { p ->
            writeUTF(p.starter.value); writeLong(p.generation); writeLong(p.structureGeneration); friction(p.source); friction(p.target)
        }
    }

    private fun DataInputStream.operation(): WaitingOperation {
        val identity = WaitingIdentity(readUTF(), readUTF()); val starter = TrustedPersonId(readUTF())
        val generation = readLong(); val structure = readLong(); val mode = Mode.valueOf(readUTF()); val startedAt = time()
        val phase = WaitingPhase.valueOf(readUTF()); val (generations, contacts) = recipients(); val restored = readInt()
        val skipper = optional { TrustedPersonId(readUTF()) }; val skipperGeneration = optional { readLong() }
        val stopper = optional { TrustedPersonId(readUTF()) }; val stopperGeneration = optional { readLong() }
        val patch = optional {
            requireNotNull(FrictionSettingsPatch.restore(TrustedPersonId(readUTF()), readLong(), readLong(), friction(), friction()))
        }
        return WaitingOperation(identity, starter, generation, structure, mode, startedAt, phase, generations, restored,
            skipper, skipperGeneration, null, contacts, stopper, stopperGeneration, null, patch)
    }

    private fun DataOutputStream.recipients(generations: Map<TrustedPersonId, Long>, contacts: Map<TrustedPersonId, PersonContact?>) {
        list(generations.entries) { (id, g) -> writeUTF(id.value); writeLong(g); optional(contacts[id]) { contact(it) } }
    }

    private fun DataInputStream.recipients(): Pair<Map<TrustedPersonId, Long>, Map<TrustedPersonId, PersonContact?>> {
        val generations = LinkedHashMap<TrustedPersonId, Long>(); val contacts = LinkedHashMap<TrustedPersonId, PersonContact?>()
        repeat(count()) {
            val id = TrustedPersonId(readUTF())
            require(id !in generations)
            generations[id] = readLong(); contacts[id] = optional { contact() }
        }
        return generations to contacts
    }

    private fun DataOutputStream.verifier(v: SecretVerifier) { bytes(v.salt); bytes(v.hash); writeInt(v.iterations) }
    private fun DataInputStream.verifier() = SecretVerifier(bytes(), bytes(), readInt())
    private fun DataOutputStream.counter(c: FailureCounter) {
        writeInt(c.failures); writeLong(c.remainingMillis); optional(c.checkpoint) { time(it) }
    }
    private fun DataInputStream.counter() = FailureCounter(readInt(), readLong(), optional { time() })
    private fun DataOutputStream.contact(c: PersonContact) { writeUTF(c.name); writeUTF(c.number) }
    private fun DataInputStream.contact() = PersonContact(readUTF(), readUTF())
    private fun DataOutputStream.friction(f: FrictionSettings) { writeLong(f.countdownMillis); writeInt(f.dailyLimit) }
    private fun DataInputStream.friction() = FrictionSettings(readLong(), readInt())
    private fun DataOutputStream.time(t: RunningTime) { writeUTF(t.epoch); writeLong(t.totalMillis) }
    private fun DataInputStream.time() = RunningTime(readUTF(), readLong())

    private fun DataOutputStream.bytes(b: ByteArray) { writeInt(b.size); write(b) }
    private fun DataInputStream.bytes(): ByteArray {
        val size = readInt()
        require(size in 0..4096)
        return ByteArray(size).also { readFully(it) }
    }
    private fun <T> DataOutputStream.optional(value: T?, block: DataOutputStream.(T) -> Unit) {
        writeBoolean(value != null); if (value != null) block(value)
    }
    private fun <T> DataInputStream.optional(block: DataInputStream.() -> T): T? {
        val flag = readByte().toInt()
        require(flag == 0 || flag == 1)
        return if (flag == 1) block() else null
    }
    private fun <T> DataOutputStream.list(items: Collection<T>, block: DataOutputStream.(T) -> Unit) {
        writeInt(items.size); items.forEach { block(it) }
    }
    private fun DataInputStream.count(): Int = readInt().also { require(it in 0..MAX_ITEMS) }
    private fun <T> DataInputStream.list(block: DataInputStream.() -> T): List<T> = List(count()) { block() }
    private fun <K, V> DataInputStream.map(block: DataInputStream.() -> Pair<K, V>): Map<K, V> {
        val result = LinkedHashMap<K, V>()
        repeat(count()) { val (k, v) = block(); require(k !in result); result[k] = v }
        return result
    }
}
