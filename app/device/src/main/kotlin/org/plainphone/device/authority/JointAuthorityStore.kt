// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import org.plainphone.core.BuildInventory
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CancelIntentStore
import org.plainphone.policy.CredentialState
import org.plainphone.policy.CredentialStore
import org.plainphone.policy.ElevationApprovalStore
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationStore
import org.plainphone.policy.FrictionSettingsReceipt
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.FrictionWaitingStore
import org.plainphone.policy.PeopleChangeCommitter
import org.plainphone.policy.PersonContact
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingHandOverCommitter
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingSkipCommitter
import org.plainphone.policy.WaitingText
import org.plainphone.policy.WaitingChangeStore
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState

internal enum class Section { CREDENTIALS, WAITING, ELEVATION, RECEIPTS, APPROVALS }
internal enum class WritePoint { BEFORE_COPY_A, AFTER_COPY_A, AFTER_COPY_B, AFTER_FENCE }
/** Test seam for crash and readback faults at fixed points of a transaction. */
internal fun interface StorageFaults { fun at(point: WritePoint, dir: File) }

/**
 * One device-protected record joining credentials, the waiting operation and its texts, the
 * elevation record, the friction receipt outbox and the app approval state with its receipt outbox. Every transaction is a single replacement of
 * the whole record, so a crash exposes either the old or the new record and never a mix.
 *
 * The record is kept as two copies (`authority-a`, `authority-b`) plus a sequence fence written
 * after both. A read returns the highest-sequence valid copy whose sequence is not below the
 * fence. A copy older than an acknowledged write is never returned, even with a valid checksum;
 * if no current copy survives, every view reads null so the policy's repair paths take over.
 *
 * All views share one non-reentrant coordinator per directory. A call made from inside a running
 * transaction is refused rather than allowed to interleave. Each section is preserved byte for
 * byte by writers that do not replace it.
 *
 * An active session journal reads back only against [matchingInventory], the inventory of this
 * exact build. Without it, a record holding an active session reads as null (damaged).
 */
class JointAuthorityStore internal constructor(
    private val dir: File,
    private val faults: StorageFaults,
    private val matchingInventory: BuildInventory?,
) : FrictionWaitingStore {
    constructor(dir: File) : this(dir, StorageFaults { _, _ -> }, null)
    constructor(dir: File, matchingInventory: BuildInventory?) : this(dir, StorageFaults { _, _ -> }, matchingInventory)
    internal constructor(dir: File, faults: StorageFaults) : this(dir, faults, null)

    private val copyA = AtomicRecord(dir, "authority-a", SCHEMA)
    private val copyB = AtomicRecord(dir, "authority-b", SCHEMA)
    private val fenceA = AtomicRecord(dir, "fence-a", SCHEMA)
    private val fenceB = AtomicRecord(dir, "fence-b", SCHEMA)
    private val coordinator = coordinators.computeIfAbsent(dir.absoluteFile.normalize().path) { Coordinator() }

    val credentials: CredentialStore = object : CredentialStore {
        override fun read(): CredentialState? = section(Section.CREDENTIALS)?.let(AuthorityCodec::decodeCredentials)
        override fun compareAndSet(expected: CredentialState, replacement: CredentialState): Boolean = commit { s ->
            if (!s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expected))) null
            else s.with(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(replacement))
        }
    }

    override val waitingStore: WaitingChangeStore = object : WaitingChangeStore {
        override fun read(): WaitingState? = section(Section.WAITING)?.let(AuthorityCodec::decodeWaiting)
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean = commit { s ->
            if (!s.matches(Section.WAITING, AuthorityCodec.encodeWaiting(expected)) ||
                replacement.storageGeneration != expected.storageGeneration || replacement.revision <= expected.revision) null
            else s.with(Section.WAITING, AuthorityCodec.encodeWaiting(replacement) ?: return@commit null)
        }
    }

    override val elevationStore: ElevationStore = object : ElevationStore(waitingStore) {
        override fun read(): ElevationRecord? = section(Section.ELEVATION)?.let { AuthorityCodec.decodeElevation(it, matchingInventory) }
        override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean = commit { s ->
            val current = s.sections[Section.ELEVATION.ordinal]
            // A null expectation repairs only the exact damaged observation, never a readable record.
            val matches = if (expected == null) AuthorityCodec.decodeElevation(current, matchingInventory) == null
                else s.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expected))
            if (!matches || (expectedCredentials != null && !s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)))) null
            else s.with(Section.ELEVATION, AuthorityCodec.encodeElevation(replacement) ?: return@commit null)
        }
    }

    /**
     * App approvals on the same coordinator. [ElevationApprovalStore.elevationStore] is this store's
     * own [elevationStore] instance, so both controllers fence on one record.
     */
    val approvals: ElevationApprovalStore = object : ElevationApprovalStore {
        override val elevationStore: ElevationStore get() = this@JointAuthorityStore.elevationStore

        override fun read(): AppApprovalState? = section(Section.APPROVALS)?.let(AuthorityCodec::decodeApprovals)

        override fun compareAndSet(expected: AppApprovalState, replacement: AppApprovalState, expectedCredentials: CredentialState?): Boolean = commit { s ->
            if (replacement.revision != expected.revision + 1 || !s.matches(Section.APPROVALS, AuthorityCodec.encodeApprovals(expected)) ||
                (expectedCredentials != null && !s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)))) null
            else s.with(Section.APPROVALS, AuthorityCodec.encodeApprovals(replacement) ?: return@commit null)
        }

        override fun commitElevationApproval(expected: AppApprovalState, replacement: AppApprovalState,
            expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
            expectedCredentials: CredentialState, finalCheck: () -> Boolean): Boolean = commit { s ->
            if (!joinable(expected, replacement, expectedElevation, replacementElevation)) return@commit null
            val approvalBytes = AuthorityCodec.encodeApprovals(replacement) ?: return@commit null
            val elevationBytes = AuthorityCodec.encodeElevation(replacementElevation) ?: return@commit null
            fun current(of: Snapshot) = of.matches(Section.APPROVALS, AuthorityCodec.encodeApprovals(expected)) &&
                of.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expectedElevation)) &&
                of.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials))
            if (!current(s)) return@commit null
            // Once, after all blocking work. The check may read this record; any write it tries is refused.
            if (!coordinator.viewing(s, finalCheck)) return@commit null
            // Compare again against storage before the single write of both sections.
            val again = load() ?: return@commit null
            if (again.sequence != s.sequence || !current(again)) return@commit null
            s.with(Section.APPROVALS, approvalBytes).with(Section.ELEVATION, elevationBytes)
        }
    }

    /**
     * The only shape the joint write accepts: PREPARING becomes COMMITTED with its receipt, and the
     * grant is used up in place. Revisions advance by one; debits, grant start, deadline, target and
     * person never change; no session is open before or after.
     */
    private fun joinable(expected: AppApprovalState, replacement: AppApprovalState,
        expectedElevation: ElevationRecord, replacementElevation: ElevationRecord): Boolean {
        val before = expected.pending ?: return false
        val after = replacement.pending ?: return false
        if (before.phase != AppApprovalPhase.PREPARING || after.phase != AppApprovalPhase.COMMITTED ||
            after.record.id != before.record.id || replacement.receipts.none { it.id == before.record.id } ||
            replacement.revision != expected.revision + 1) return false
        val grant = expectedElevation.state.grant ?: return false
        val used = replacementElevation.state.grant
        return replacementElevation.revision == expectedElevation.revision + 1 &&
            replacementElevation.state.ledger.debits == expectedElevation.state.ledger.debits &&
            expectedElevation.journal == SessionJournal.Empty && replacementElevation.journal == SessionJournal.Empty &&
            !grant.packageAccessOpen && replacementElevation.state.countdown == null &&
            (used == null || (used.id == grant.id && used.target == grant.target && used.startedAt == grant.startedAt &&
                used.deadline == grant.deadline && !used.packageAccessOpen && used.approved.containsAll(grant.approved) &&
                replacementElevation.person == expectedElevation.person))
    }

    /**
     * Cancel intents, kept apart from the joint record so a request survives even when that record is
     * unreadable. Each request has a CLEAR entry and, once asked, a separate REQUESTED marker. The marker
     * is never removed, and its presence alone (even damaged) reads as REQUESTED, so it is monotonic.
     * A missing or damaged CLEAR entry with no marker reads as UNKNOWN. Same coordinator as every write.
     */
    val cancelIntents: CancelIntentStore = object : CancelIntentStore {
        override fun initialize(requestId: String): Boolean = coordinator.run(false) {
            val (clear, requested) = intentRecords(requestId)
            if (requested.exists()) false
            else clear.read()?.contentEquals(requestId.toByteArray()) == true || clear.write(requestId.toByteArray()) {}
        }
        override fun request(requestId: String): Boolean = coordinator.run(false) {
            val requested = intentRecords(requestId).second
            requested.read()?.contentEquals(requestId.toByteArray()) == true || requested.write(requestId.toByteArray()) {}
        }
        override fun read(requestId: String): CancelIntent = coordinator.run(CancelIntent.UNKNOWN) { intent(requestId) }
    }

    /**
     * Turn-off and people skips: one write rotates only the skipper's PIN and code, moves the exact
     * PENDING request to COMMITTING with that skipper, and appends one SKIPPED receipt addressed from the
     * rotated record. Refused unless the request's cancel intent reads CLEAR inside this transaction.
     */
    val skipCommitter: WaitingSkipCommitter = WaitingSkipCommitter { expectedCredentials, replacementCredentials, expectedWaiting, replacementWaiting ->
        commit { s ->
            val before = expectedWaiting.operation ?: return@commit null
            val after = replacementWaiting.operation ?: return@commit null
            val skipper = after.skipper ?: return@commit null
            if (before.phase != WaitingPhase.PENDING || before.skipper != null ||
                !sameOperation(after, before.copied(WaitingPhase.COMMITTING, skipper = skipper, skipperGeneration = after.skipperGeneration)) ||
                !rotatesOnly(expectedCredentials, replacementCredentials, skipper) ||
                after.skipperGeneration != replacementCredentials.people.getValue(skipper).generation) return@commit null
            joint(s, expectedCredentials, replacementCredentials, expectedWaiting, replacementWaiting,
                listOf(WaitingEvent.SKIPPED to replacementCredentials))
        }
    }

    /**
     * The outgoing holder's code starts and skips a handover in one write: their PIN and code rotate,
     * STARTED (addressed from the old record) and SKIPPED (from the rotated one) are queued, and the slot
     * moves from vacant or terminal straight to COMMITTING. Incoming credentials stay in the patch only.
     */
    val handOverCommitter: WaitingHandOverCommitter = WaitingHandOverCommitter { expectedCredentials, replacementCredentials, expectedWaiting, replacementWaiting ->
        commit { s ->
            val after = replacementWaiting.operation ?: return@commit null
            val patch = after.peoplePatch ?: return@commit null
            if (expectedWaiting.operation?.phase !in setOf(null, WaitingPhase.COMPLETED, WaitingPhase.CANCELLED) ||
                patch.kind != WaitingChangeKind.HAND_OVER || after.phase != WaitingPhase.COMMITTING ||
                after.skipper != after.starter || after.stopper != null || !patch.dependenciesMatch(expectedCredentials) ||
                !snapshotOf(after.recipientGenerations, after.recipientContacts, expectedCredentials) ||
                !rotatesOnly(expectedCredentials, replacementCredentials, after.starter) ||
                after.skipperGeneration != replacementCredentials.people.getValue(after.starter).generation) return@commit null
            val started = after.copied(WaitingPhase.PENDING, skipper = null, skipperGeneration = null)
            val texts = replacementWaiting.texts.takeLast(2)
            if (texts.size != 2 || !sameOperation(texts[0].operation, started)) return@commit null
            joint(s, expectedCredentials, replacementCredentials, expectedWaiting, replacementWaiting,
                listOf(WaitingEvent.STARTED to expectedCredentials, WaitingEvent.SKIPPED to replacementCredentials))
        }
    }

    /**
     * People patch and direction moves. [applyPatch] writes the whole patched credential record with
     * its applied marker while the journal stays COMMITTING; [move] writes only the journal and its one
     * receipt. Both compare the credential and journal records inside the same transaction.
     */
    val peopleCommitter: PeopleChangeCommitter = object : PeopleChangeCommitter {
        override fun applyPatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
            expectedWaiting: WaitingState): Boolean = commit { s ->
            val op = expectedWaiting.operation ?: return@commit null
            val patch = op.peoplePatch ?: return@commit null
            if (op.phase != WaitingPhase.COMMITTING || applied(expectedWaiting, expectedCredentials) ||
                expectedCredentials.mode != Mode.TRUSTED || expectedCredentials.structureGeneration != op.structureGeneration ||
                patch.incoming?.person in expectedCredentials.people ||
                !expectedCredentials.people.keys.containsAll(patch.dependencyGenerations.keys)) return@commit null
            val profile = expectedCredentials.recoveryProfile ?: return@commit null
            // The one acceptable result, built the same way as the policy's candidate, then compared byte for byte.
            val people = expectedCredentials.people.toMutableMap()
            val accounts = profile.holderUserIds.toMutableMap()
            patch.removedSlots.forEach { people.remove(it); accounts.remove(it) }
            patch.incoming?.let { people[it.person] = it.credential; accounts[it.person] = it.resetProtectionUserId }
            if (people.size !in 1..2 || expectedCredentials.revision == Long.MAX_VALUE ||
                expectedCredentials.structureGeneration == Long.MAX_VALUE) return@commit null
            val structure = expectedCredentials.structureGeneration + 1
            val wanted = CredentialState(expectedCredentials.mode, people, expectedCredentials.revision + 1, structure,
                expectedCredentials.pendingNotices, RecoveryProfile(accounts, profile.backupUserId, profile.friction),
                AppliedPeopleChange(op.identity.requestId, expectedWaiting.storageGeneration, structure))
            val bytes = AuthorityCodec.encodeCredentials(replacementCredentials)
            if (!bytes.contentEquals(AuthorityCodec.encodeCredentials(wanted)) ||
                !s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)) ||
                !s.matches(Section.WAITING, AuthorityCodec.encodeWaiting(expectedWaiting))) return@commit null
            s.with(Section.CREDENTIALS, bytes)
        }

        override fun move(expectedCredentials: CredentialState, expectedWaiting: WaitingState, replacementWaiting: WaitingState): Boolean = commit { s ->
            val before = expectedWaiting.operation ?: return@commit null
            val after = replacementWaiting.operation ?: return@commit null
            if (before.peoplePatch == null) return@commit null
            val done = applied(expectedWaiting, expectedCredentials)
            val (shape, event) = when {
                before.phase == WaitingPhase.COMMITTING && after.phase == WaitingPhase.ROLLING_BACK && !done -> {
                    val stopper = after.stopper ?: return@commit null
                    if (expectedCredentials.people[stopper]?.generation != after.stopperGeneration) return@commit null
                    before.copied(WaitingPhase.ROLLING_BACK, stopper = stopper, stopperGeneration = after.stopperGeneration) to WaitingEvent.ROLLING_BACK
                }
                before.phase == WaitingPhase.COMMITTING && after.phase == WaitingPhase.COMPLETED && done ->
                    before.copied(WaitingPhase.COMPLETED) to WaitingEvent.COMPLETED
                before.phase == WaitingPhase.ROLLING_BACK && after.phase == WaitingPhase.CANCELLED && !done ->
                    before.copied(WaitingPhase.CANCELLED) to WaitingEvent.CANCELLED_AFTER_ROLLBACK
                else -> return@commit null
            }
            if (!sameOperation(after, shape)) return@commit null
            joint(s, expectedCredentials, null, expectedWaiting, replacementWaiting, listOf(event to expectedCredentials))
        }
    }

    /**
     * The shared tail of every joint people write: both stored sections still match, the journal
     * advances by one revision in the same storage generation, earlier texts and recovery receipts are
     * kept exactly, and each new receipt is addressed from one whole credential snapshot.
     */
    private fun joint(s: Snapshot, expectedCredentials: CredentialState, replacementCredentials: CredentialState?,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState, added: List<Pair<WaitingEvent, CredentialState>>): Snapshot? {
        val after = replacementWaiting.operation ?: return null
        val before = expectedWaiting.operation
        // A live request keeps its ID; a new one never reuses the ID of the terminal request it replaces.
        val sameRequest = before?.identity?.requestId == after.identity.requestId
        if (!advances(expectedWaiting, replacementWaiting) || (before != null && (before.phase in TERMINAL) == sameRequest)) return null
        if (replacementWaiting.texts.size != expectedWaiting.texts.size + added.size ||
            !sameTexts(replacementWaiting.texts.take(expectedWaiting.texts.size), expectedWaiting) ||
            !sameRecoveryReceipts(expectedWaiting, replacementWaiting)) return null
        val keys = expectedWaiting.texts.map { it.key }.toMutableSet()
        replacementWaiting.texts.drop(expectedWaiting.texts.size).zip(added).forEachIndexed { i, (text, expected) ->
            val (event, credentials) = expected
            if (text.event != event || !keys.add(text.key) || text.key.isBlank() ||
                !snapshotOf(text.recipientGenerations, text.recipientContacts, credentials) ||
                (i == added.lastIndex && !sameOperation(text.operation, after))) return null
        }
        // Entering COMMITTING (skip or handover) needs a CLEAR intent read inside this transaction.
        if (after.phase == WaitingPhase.COMMITTING && before?.phase != WaitingPhase.COMMITTING &&
            intent(after.identity.requestId) != CancelIntent.CLEAR) return null
        if (!s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)) ||
            !s.matches(Section.WAITING, AuthorityCodec.encodeWaiting(expectedWaiting))) return null
        val waiting = AuthorityCodec.encodeWaiting(replacementWaiting) ?: return null
        var next = s.with(Section.WAITING, waiting)
        if (replacementCredentials != null) next = next.with(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(replacementCredentials))
        return next
    }

    /** True only when [replacement] differs from [expected] by one revision and [person]'s new PIN and code. */
    private fun rotatesOnly(expected: CredentialState, replacement: CredentialState, person: TrustedPersonId): Boolean {
        val old = expected.people[person] ?: return false
        val new = replacement.people[person] ?: return false
        if (expected.revision == Long.MAX_VALUE || replacement.revision != expected.revision + 1 || new.generation != old.generation + 1 ||
            new.contact?.name != old.contact?.name || new.contact?.number != old.contact?.number ||
            AuthorityCodec.encodeCredentials(CredentialState(replacement.mode, mapOf(person to new))).contentEquals(
                AuthorityCodec.encodeCredentials(CredentialState(expected.mode, mapOf(person to old))))) return false
        val restored = CredentialState(replacement.mode, replacement.people + (person to old), expected.revision,
            replacement.structureGeneration, replacement.pendingNotices, replacement.recoveryProfile, replacement.appliedPeopleChange)
        return replacement.people.keys == expected.people.keys &&
            AuthorityCodec.encodeCredentials(restored).contentEquals(AuthorityCodec.encodeCredentials(expected))
    }

    /** A receipt's frozen recipients are exactly one record's holders, generations and contacts (null stays null). */
    private fun snapshotOf(generations: Map<TrustedPersonId, Long>,
        contacts: Map<TrustedPersonId, PersonContact?>, credentials: CredentialState): Boolean =
        generations == credentials.people.mapValues { it.value.generation } && contacts.keys == credentials.people.keys &&
            credentials.people.all { (id, p) -> contacts[id]?.name == p.contact?.name && contacts[id]?.number == p.contact?.number }

    private fun applied(waiting: WaitingState, credentials: CredentialState): Boolean {
        val op = waiting.operation ?: return false
        val marker = credentials.appliedPeopleChange ?: return false
        if (op.structureGeneration == Long.MAX_VALUE) return false
        val target = op.structureGeneration + 1
        return marker.requestId == op.identity.requestId && marker.waitingGeneration == waiting.storageGeneration &&
            marker.structureGeneration == target && credentials.structureGeneration == target
    }

    private fun sameOperation(a: WaitingOperation, b: WaitingOperation): Boolean {
        val left = AuthorityCodec.encodeOperation(a) ?: return false
        return left.contentEquals(AuthorityCodec.encodeOperation(b))
    }

    private fun sameTexts(texts: List<WaitingText>, expected: WaitingState): Boolean {
        val probe = AuthorityCodec.encodeWaiting(WaitingState(0, null, texts, "probe")) ?: return false
        return probe.contentEquals(AuthorityCodec.encodeWaiting(WaitingState(0, null, expected.texts, "probe")))
    }

    private fun sameRecoveryReceipts(expected: WaitingState, replacement: WaitingState): Boolean {
        val probe = AuthorityCodec.encodeWaiting(WaitingState(0, null, emptyList(), "probe", replacement.recoveryReceipts)) ?: return false
        return probe.contentEquals(AuthorityCodec.encodeWaiting(WaitingState(0, null, emptyList(), "probe", expected.recoveryReceipts)))
    }

    private fun WaitingOperation.copied(phase: WaitingPhase, skipper: TrustedPersonId? = this.skipper,
        skipperGeneration: Long? = this.skipperGeneration, stopper: TrustedPersonId? = this.stopper,
        stopperGeneration: Long? = this.stopperGeneration) = WaitingOperation(identity, starter, generation, structureGeneration, mode,
        startedAt, phase, recipientGenerations, restoredSteps, skipper, skipperGeneration, peoplePatch, recipientContacts,
        stopper, stopperGeneration, modePatch, frictionPatch)

    private fun intentRecords(requestId: String): Pair<AtomicRecord, AtomicRecord> {
        val hash = MessageDigest.getInstance("SHA-256").digest(requestId.toByteArray()).joinToString("") { "%02x".format(it) }
        return AtomicRecord(dir, "intent-clear-$hash", SCHEMA) to AtomicRecord(dir, "intent-requested-$hash", SCHEMA)
    }

    /** Callers hold the coordinator. */
    private fun intent(requestId: String): CancelIntent {
        val (clear, requested) = intentRecords(requestId)
        return when {
            requested.exists() -> CancelIntent.REQUESTED
            clear.read()?.contentEquals(requestId.toByteArray()) == true -> CancelIntent.CLEAR
            else -> CancelIntent.UNKNOWN
        }
    }

    /** Enrollment-time creation of the first record. Refuses if any copy or fence already exists. */
    fun initialize(credentials: CredentialState, waiting: WaitingState, elevation: ElevationRecord,
        approvals: AppApprovalState = AppApprovalState()): Boolean = coordinator.run(false) {
        if (listOf(copyA, copyB, fenceA, fenceB).any { it.exists() }) return@run false
        val sections = listOf(AuthorityCodec.encodeCredentials(credentials), AuthorityCodec.encodeWaiting(waiting) ?: return@run false,
            AuthorityCodec.encodeElevation(elevation) ?: return@run false, AuthorityCodec.encodeReceipts(emptyList()),
            AuthorityCodec.encodeApprovals(approvals) ?: return@run false)
        save(null, Snapshot(0, sections))
    }

    /** Routine friction receipts awaiting delivery, in commit order. Null when the record is unreadable. */
    fun frictionReceipts(): List<StoredFrictionReceipt>? = section(Section.RECEIPTS)?.let(AuthorityCodec::decodeReceipts)

    internal fun sectionBytesForTest(section: Section): ByteArray? = section(section)

    override fun applyStricter(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
        receipt: FrictionSettingsReceipt): Boolean = commit { s ->
        val op = expectedWaiting.operation
        // A pending looser target must be cancelled with tightenAndCancel, never left pending.
        if (op != null && (op.phase in BUSY || (op.phase == WaitingPhase.PENDING && op.frictionPatch != null))) return@commit null
        stricter(s, expectedCredentials, replacementCredentials, expectedWaiting, null, expectedElevation, replacementElevation, receipt)
    }

    override fun tightenAndCancel(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState, expectedElevation: ElevationRecord,
        replacementElevation: ElevationRecord, receipt: FrictionSettingsReceipt): Boolean = commit { s ->
        val before = expectedWaiting.operation ?: return@commit null
        val after = replacementWaiting.operation ?: return@commit null
        if (before.frictionPatch == null || before.phase != WaitingPhase.PENDING || after.phase != WaitingPhase.CANCELLED ||
            after.identity.requestId != before.identity.requestId || after.frictionPatch == null ||
            !advances(expectedWaiting, replacementWaiting) ||
            replacementWaiting.texts.lastOrNull()?.event != WaitingEvent.DEPENDENCY_CHANGED) return@commit null
        stricter(s, expectedCredentials, replacementCredentials, expectedWaiting, replacementWaiting, expectedElevation, replacementElevation, receipt)
    }

    override fun completeFriction(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean = commit { s ->
        val before = expectedWaiting.operation ?: return@commit null
        val after = replacementWaiting.operation ?: return@commit null
        val patch = before.frictionPatch ?: return@commit null
        if (before.phase != WaitingPhase.COMMITTING || after.phase != WaitingPhase.COMPLETED ||
            after.identity.requestId != before.identity.requestId || !advances(expectedWaiting, replacementWaiting) ||
            replacementWaiting.texts.lastOrNull()?.event != WaitingEvent.COMPLETED ||
            replacementCredentials.recoveryProfile?.friction != patch.target ||
            replacementCredentials.mode != expectedCredentials.mode ||
            replacementCredentials.structureGeneration != expectedCredentials.structureGeneration ||
            replacementCredentials.revision != expectedCredentials.revision + 1) return@commit null
        val waiting = AuthorityCodec.encodeWaiting(replacementWaiting) ?: return@commit null
        // The ENTIRE elevation record, including its revision, is compared and kept unchanged.
        if (!s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)) ||
            !s.matches(Section.WAITING, AuthorityCodec.encodeWaiting(expectedWaiting)) ||
            !s.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expectedElevation))) return@commit null
        s.with(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(replacementCredentials)).with(Section.WAITING, waiting)
    }

    private fun stricter(s: Snapshot, expectedCredentials: CredentialState, replacementCredentials: CredentialState,
        expectedWaiting: WaitingState, replacementWaiting: WaitingState?, expectedElevation: ElevationRecord,
        replacementElevation: ElevationRecord, receipt: FrictionSettingsReceipt): Snapshot? {
        if (replacementCredentials.revision != expectedCredentials.revision + 1 ||
            replacementElevation.revision != expectedElevation.revision + 1 ||
            replacementCredentials.recoveryProfile?.friction != receipt.target) return null
        if (!s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)) ||
            !s.matches(Section.WAITING, AuthorityCodec.encodeWaiting(expectedWaiting)) ||
            !s.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expectedElevation))) return null
        val receipts = AuthorityCodec.decodeReceipts(s.sections[Section.RECEIPTS.ordinal]) ?: return null
        if (receipts.any { it.key == receipt.key }) return null
        var next = s.with(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(replacementCredentials))
            .with(Section.ELEVATION, AuthorityCodec.encodeElevation(replacementElevation) ?: return null)
            .with(Section.RECEIPTS, AuthorityCodec.encodeReceipts(receipts + StoredFrictionReceipt.of(receipt)))
        if (replacementWaiting != null) next = next.with(Section.WAITING, AuthorityCodec.encodeWaiting(replacementWaiting) ?: return null)
        return next
    }

    /** Same storage generation, exactly one revision higher. */
    private fun advances(expected: WaitingState, replacement: WaitingState) =
        replacement.storageGeneration == expected.storageGeneration && replacement.revision == expected.revision + 1

    private class Snapshot(val sequence: Long, val sections: List<ByteArray>) {
        fun matches(section: Section, bytes: ByteArray?) = bytes != null && bytes.contentEquals(sections[section.ordinal])
        fun with(section: Section, bytes: ByteArray) = Snapshot(sequence, sections.toMutableList().also { it[section.ordinal] = bytes })
        fun encode(): ByteArray {
            val out = ByteArrayOutputStream()
            DataOutputStream(out).use { data ->
                data.writeLong(sequence)
                sections.forEach { data.writeInt(it.size); data.write(it) }
            }
            return out.toByteArray()
        }
        companion object {
            fun decode(bytes: ByteArray): Snapshot? = try {
                val buf = ByteBuffer.wrap(bytes)
                val sequence = buf.long
                val sections = Section.entries.map {
                    val size = buf.int
                    require(size in 0..buf.remaining())
                    ByteArray(size).also { b -> buf.get(b) }
                }
                if (buf.hasRemaining() || sequence < 0) null else Snapshot(sequence, sections)
            } catch (_: RuntimeException) {
                null
            }
        }
    }

    private fun section(section: Section): ByteArray? =
        coordinator.view()?.sections?.get(section.ordinal) ?: coordinator.run(null) { load()?.sections?.get(section.ordinal) }

    /** Highest valid copy at or above the fence; null if the fence or every current copy is gone. */
    private fun load(): Snapshot? {
        val fence = listOfNotNull(fenceA.read(), fenceB.read())
            .mapNotNull { b -> if (b.size == 8) ByteBuffer.wrap(b).long else null }.maxOrNull() ?: return null
        val copies = listOfNotNull(copyA.read(), copyB.read()).mapNotNull { b -> Snapshot.decode(b)?.let { it to b } }
            .filter { it.first.sequence >= fence }
        val newest = copies.maxByOrNull { it.first.sequence } ?: return null
        // Two copies claiming the same sequence must agree exactly.
        if (copies.any { it.first.sequence == newest.first.sequence && !it.second.contentEquals(newest.second) }) return null
        return newest.first
    }

    private fun commit(change: (Snapshot) -> Snapshot?): Boolean = coordinator.run(false) {
        val current = load() ?: return@run false
        if (current.sequence == Long.MAX_VALUE) return@run false
        val next = change(current) ?: return@run false
        save(current, Snapshot(current.sequence + 1, next.sections))
    }

    /**
     * Copy A, then copy B, then both fences, each read back. A failure before the fence is
     * written restores both copies to [previous]; the caller then sees false and the old record.
     */
    private fun save(previous: Snapshot?, next: Snapshot): Boolean {
        val bytes = next.encode()
        val fence = ByteBuffer.allocate(8).putLong(next.sequence).array()
        faults.at(WritePoint.BEFORE_COPY_A, dir)
        val copied = copyA.write(bytes) { faults.at(WritePoint.AFTER_COPY_A, dir) } &&
            copyB.write(bytes) { faults.at(WritePoint.AFTER_COPY_B, dir) }
        if (!copied) {
            previous?.encode()?.let { old -> copyA.write(old) {}; copyB.write(old) {} }
            return false
        }
        val fenced = fenceA.write(fence) {} && fenceB.write(fence) { faults.at(WritePoint.AFTER_FENCE, dir) }
        return fenced && load()?.encode()?.contentEquals(bytes) == true
    }

    private class Coordinator {
        private val lock = ReentrantLock()
        private var viewed: Snapshot? = null
        /** Non-reentrant: a nested call from the thread already inside a transaction is refused. */
        fun <T> run(refused: T, block: () -> T): T {
            if (lock.isHeldByCurrentThread) return refused
            lock.lock()
            try { return block() } finally { lock.unlock() }
        }
        /** Inside a transaction only: lets [block] read [snapshot]. Writes from it are still refused. */
        fun viewing(snapshot: Snapshot, block: () -> Boolean): Boolean {
            check(lock.isHeldByCurrentThread)
            viewed = snapshot
            try { return block() } finally { viewed = null }
        }
        fun view(): Snapshot? = if (lock.isHeldByCurrentThread) viewed else null
    }

    private companion object {
        /** 2 adds the app approval section; 3 stores people patches in waiting operations. */
        const val SCHEMA = 3
        val BUSY = setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)
        val TERMINAL = setOf(WaitingPhase.COMPLETED, WaitingPhase.CANCELLED)
        val coordinators = ConcurrentHashMap<String, Coordinator>()
    }
}

/** A single checksummed file replaced by synced temp-file rename, then read back. */
internal class AtomicRecord(private val dir: File, name: String, private val schema: Int) {
    private val file = File(dir, "$name.rec")

    fun exists() = file.exists()

    fun read(): ByteArray? = try {
        if (file.isFile) org.plainphone.device.storage.RecordCodec.decode(file.readBytes(), schema) else null
    } catch (_: IOException) {
        null
    }

    /** [afterRename] runs between the rename and the readback. */
    fun write(payload: ByteArray, afterRename: () -> Unit): Boolean = try {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no directory")
        val temp = File(dir, "${file.name}.tmp")
        java.io.FileOutputStream(temp).use { out ->
            out.write(org.plainphone.device.storage.RecordCodec.encode(schema, payload))
            out.fd.sync()
        }
        java.nio.file.Files.move(temp.toPath(), file.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        try {
            java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
        } catch (_: IOException) {
            // Directory sync is not supported everywhere; the file contents are already synced.
        }
        afterRename()
        read()?.contentEquals(payload) == true
    } catch (_: IOException) {
        false
    }
}
