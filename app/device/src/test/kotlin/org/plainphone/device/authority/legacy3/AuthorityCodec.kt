// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority.legacy3

// Test-only: production AuthorityCodec.kt at 4501f7f (schema 3), package relocated. Real old writer for migration tests (PLA-135 bundle).

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.CertDigest
import org.plainphone.core.Mode
import org.plainphone.core.PackageName
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.core.WindowKind
import org.plainphone.policy.AppApprovalOperation
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.AppApprovalReceipt
import org.plainphone.policy.AppApprovalReceiptKind
import org.plainphone.policy.AppApprovalRecord
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.CredentialChannel
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationCountdown
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationGrant
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationPersonBinding
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.EnrollmentConfirmations
import org.plainphone.policy.EnrollmentWitness
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.FrictionSettingsPatch
import org.plainphone.policy.FrictionSettingsReceipt
import org.plainphone.policy.IncomingPersonEnrollment
import org.plainphone.policy.LockoutNotice
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RecoveredState
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.RecoveryReceipt
import org.plainphone.policy.RuntimePermission
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.SessionPhase
import org.plainphone.policy.StagedUpdateBinding
import org.plainphone.policy.TrustedPersonPatch
import org.plainphone.policy.WaitingChangeKind
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
 * Encoders return null for data this adapter cannot yet read back: mode-switch patches. People
 * patches store the original immutable enrollment and decode through the policy's restore checks.
 * Live countdowns, grants and session journals decode through the policy's restore functions, which
 * restore data only. The controllers still revoke on startup; nothing here restores authority.
 * Sets and maps whose order carries no meaning are written sorted, so equal states encode equally.
 */
internal object AuthorityCodec {
    private const val MAX_ITEMS = 4096

    fun encodeCredentials(s: CredentialState): ByteArray = write {
        writeUTF(s.mode.name); writeLong(s.revision); writeLong(s.structureGeneration)
        list(s.people.entries) { (id, p) ->
            writeUTF(id.value); credential(p)
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
        val people = map { TrustedPersonId(readUTF()) to credential() }
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

    /** For exact comparisons of one operation; null when it holds an unsupported patch. */
    fun encodeOperation(op: WaitingOperation): ByteArray? = write { operation(op) }

    fun encodeElevation(r: ElevationRecord): ByteArray? = write {
        writeLong(r.revision)
        list(r.state.ledger.debits) { writeUTF(it.id); time(it.at) }
        optional(r.state.countdown) { c -> writeUTF(c.id); target(c.target); boot(c.startedAt); writeLong(c.durationMillis) }
        optional(r.state.grant) { g ->
            writeUTF(g.id); target(g.target); boot(g.startedAt); boot(g.deadline); writeBoolean(g.packageAccessOpen)
            list(g.approved.sortedBy { it.packageName.value }) { binding(it) }
        }
        when (val journal = r.journal) {
            SessionJournal.Empty -> writeByte(0)
            is SessionJournal.Active -> {
                writeByte(1); writeUTF(journal.access.id); writeUTF(journal.access.kind.name)
                list(journal.access.packages.map { it.value }.sorted()) { writeUTF(it) }
                boot(journal.access.deadline); writeUTF(journal.phase.name)
            }
            SessionJournal.Damaged -> unsupported()
        }
        optional(r.person) { writeUTF(it.person.value); writeLong(it.generation); writeLong(it.structureGeneration) }
    }

    /** An active journal decodes only against the inventory of this exact build; otherwise null. */
    fun decodeElevation(bytes: ByteArray, matchingInventory: BuildInventory? = null): ElevationRecord? = read(bytes) {
        val revision = readLong()
        val ledger = ElevationLedger(list { ElevationDebit(readUTF(), time()) })
        val countdown = optional { requireNotNull(ElevationCountdown.restore(readUTF(), target(), boot(), readLong())) }
        val grant = optional {
            requireNotNull(ElevationGrant.restore(readUTF(), target(), boot(), boot(), flag(), set { binding() }))
        }
        val journal = when (readByte().toInt()) {
            0 -> SessionJournal.Empty
            1 -> SessionJournal.restore(readUTF(), WindowKind.valueOf(readUTF()), set { PackageName(readUTF()) }, boot(),
                SessionPhase.valueOf(readUTF()), matchingInventory)
            else -> throw IllegalArgumentException()
        }
        val person = optional { ElevationPersonBinding(TrustedPersonId(readUTF()), readLong(), readLong()) }
        // A Damaged journal fails the record's own validation, so the whole record reads as null.
        ElevationRecord(revision, ElevationState(ledger, countdown, grant), journal, person)
    }

    fun encodeApprovals(s: AppApprovalState): ByteArray? = write {
        writeLong(s.revision)
        list(s.apps.values.sortedBy { it.identity.packageName.value }) { approvalRecord(it) }
        optional(s.pending) { op ->
            approvalRecord(op.record); optional(op.receipt) { approvalReceipt(it) }; writeUTF(op.phase.name)
            optional(op.withdrawalReceipt) { approvalReceipt(it) }
        }
        list(s.receipts) { approvalReceipt(it) }
        list(s.removedApps.values.sortedBy { it.packageName.value }) { binding(it) }
    }

    fun decodeApprovals(bytes: ByteArray): AppApprovalState? = read(bytes) {
        val revision = readLong()
        val apps = map { approvalRecord().let { it.identity.packageName to it } }
        val pending = optional {
            AppApprovalOperation(approvalRecord(), optional { approvalReceipt() }, AppApprovalPhase.valueOf(readUTF()),
                optional { approvalReceipt() })
        }
        val receipts = list { approvalReceipt() }
        val removed = map { binding().let { it.packageName to it } }
        AppApprovalState(revision, apps, pending, receipts, removed)
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
        if (op.modePatch != null) unsupported()
        writeUTF(op.identity.requestId); writeUTF(op.identity.cancelCode); writeUTF(op.starter.value)
        writeLong(op.generation); writeLong(op.structureGeneration); writeUTF(op.mode.name); time(op.startedAt)
        writeUTF(op.phase.name); recipients(op.recipientGenerations, op.recipientContacts); writeInt(op.restoredSteps)
        optional(op.skipper) { writeUTF(it.value) }; optional(op.skipperGeneration) { writeLong(it) }
        optional(op.stopper) { writeUTF(it.value) }; optional(op.stopperGeneration) { writeLong(it) }
        // The original immutable patch fields, never re-derived from current credentials.
        optional(op.frictionPatch) { p ->
            writeUTF(p.starter.value); writeLong(p.generation); writeLong(p.structureGeneration); friction(p.source); friction(p.target)
        }
        optional(op.peoplePatch) { peoplePatch(it) }
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
        val people = optional { peoplePatch() }
        return WaitingOperation(identity, starter, generation, structure, mode, startedAt, phase, generations, restored,
            skipper, skipperGeneration, people, contacts, stopper, stopperGeneration, null, patch)
    }

    /** The original patch and pending enrollment; only verifiers are stored, never a recovery code. */
    private fun DataOutputStream.peoplePatch(p: TrustedPersonPatch) {
        writeUTF(p.kind.name); writeUTF(p.starter.value); writeLong(p.structureGeneration)
        list(p.dependencyGenerations.entries) { (id, g) -> writeUTF(id.value); writeLong(g) }
        list(p.removedSlots.map { it.value }.sorted()) { writeUTF(it) }
        optional(p.incoming) { e ->
            writeUTF(e.person.value); credential(e.credential); writeUTF(e.resetProtectionUserId)
            with(e.confirmations) {
                listOf(inPerson, nameAndNumberEntered, ownPinSet, recoveryCodeReceived, resetProtectionIdFromOwnDevice,
                    testTextReceived, offDeviceSignInChecked, independentSecondFactorChecked, accountAtLeast72HoursOld)
            }.forEach { writeBoolean(it) }
            when (val w = e.witness) {
                EnrollmentWitness.Helper -> writeByte(0)
                is EnrollmentWitness.CurrentPerson -> { writeByte(1); writeUTF(w.person.value) }
            }
        }
    }

    private fun DataInputStream.peoplePatch(): TrustedPersonPatch {
        val kind = WaitingChangeKind.valueOf(readUTF()); val starter = TrustedPersonId(readUTF()); val structure = readLong()
        val dependencies = map { TrustedPersonId(readUTF()) to readLong() }
        val removed = set { TrustedPersonId(readUTF()) }
        val incoming = optional {
            val person = TrustedPersonId(readUTF()); val credential = credential(); val account = readUTF()
            val c = EnrollmentConfirmations(flag(), flag(), flag(), flag(), flag(), flag(), flag(), flag(), flag())
            val witness = when (readByte().toInt()) {
                0 -> EnrollmentWitness.Helper
                1 -> EnrollmentWitness.CurrentPerson(TrustedPersonId(readUTF()))
                else -> throw IllegalArgumentException()
            }
            requireNotNull(IncomingPersonEnrollment.restore(person, credential, account, c, witness))
        }
        return requireNotNull(TrustedPersonPatch.restore(kind, starter, structure, dependencies, removed, incoming))
    }

    private fun DataOutputStream.credential(p: PersonCredential) {
        verifier(p.pin); verifier(p.recovery); counter(p.pinCounter); counter(p.recoveryCounter); writeLong(p.generation)
        optional(p.contact) { contact(it) }
    }
    private fun DataInputStream.credential() = PersonCredential(verifier(), verifier(), counter(), counter(), readLong(), optional { contact() })

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

    private fun DataOutputStream.target(t: ElevationTarget) = when (t) {
        ElevationTarget.Play -> writeByte(0)
        ElevationTarget.Settings -> writeByte(1)
        is ElevationTarget.Approve -> { writeByte(2); binding(t.identity) }
        is ElevationTarget.Update -> { writeByte(3); writeUTF(t.staged.sha256); writeLong(t.staged.versionCode) }
    }
    private fun DataInputStream.target(): ElevationTarget = when (readByte().toInt()) {
        0 -> ElevationTarget.Play
        1 -> ElevationTarget.Settings
        2 -> ElevationTarget.Approve(binding())
        3 -> ElevationTarget.Update(StagedUpdateBinding(readUTF(), readLong()))
        else -> throw IllegalArgumentException()
    }
    private fun DataOutputStream.binding(b: ApprovalBinding) {
        writeUTF(b.packageName.value); list(b.signingCerts.map { it.value }.sorted()) { writeUTF(it) }
    }
    private fun DataInputStream.binding() = ApprovalBinding(PackageName(readUTF()), set { CertDigest(readUTF()) })
    private fun DataOutputStream.approvalRecord(r: AppApprovalRecord) {
        val manifest = r.permissions.manifest
        writeUTF(r.id); binding(manifest.identity); writeLong(manifest.versionCode); writeUTF(manifest.buildFingerprint)
        list(manifest.permissions) { p ->
            writeUTF(p.name.value); writeUTF(p.label); list(p.features) { writeUTF(it) }; writeUTF(p.grantCapability.name)
        }
        list(r.permissions.choices.entries.sortedBy { it.key.value }) { (name, choice) -> writeUTF(name.value); writeUTF(choice.name) }
    }
    private fun DataInputStream.approvalRecord(): AppApprovalRecord {
        val id = readUTF()
        val manifest = PermissionManifest(binding(), readLong(), readUTF(), list {
            RuntimePermission(PermissionName(readUTF()), readUTF(), list { readUTF() }, PermissionGrantCapability.valueOf(readUTF()))
        })
        return AppApprovalRecord(id, PermissionSelection(manifest, map { PermissionName(readUTF()) to PermissionChoice.valueOf(readUTF()) }))
    }
    private fun DataOutputStream.approvalReceipt(r: AppApprovalReceipt) {
        writeUTF(r.id); writeUTF(r.actor.value)
        list(r.recipientGenerations.entries.sortedBy { it.key.value }) { (id, g) -> writeUTF(id.value); writeLong(g) }
        writeUTF(r.kind.name); writeUTF(r.approvalId)
    }
    private fun DataInputStream.approvalReceipt() = AppApprovalReceipt(readUTF(), TrustedPersonId(readUTF()),
        map { TrustedPersonId(readUTF()) to readLong() }, AppApprovalReceiptKind.valueOf(readUTF()), readUTF())
    private fun DataOutputStream.boot(t: BootTime) { writeUTF(t.bootId); writeLong(t.elapsedMillis) }
    private fun DataInputStream.boot() = BootTime(readUTF(), readLong())
    private fun DataInputStream.flag(): Boolean = when (readByte().toInt()) { 0 -> false; 1 -> true; else -> throw IllegalArgumentException() }
    private fun <T> DataInputStream.set(block: DataInputStream.() -> T): Set<T> =
        list(block).let { items -> items.toSet().also { require(it.size == items.size) } }

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
