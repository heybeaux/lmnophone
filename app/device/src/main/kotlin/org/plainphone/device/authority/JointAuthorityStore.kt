// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import org.plainphone.core.BuildInventory
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppApprovalOperation
import org.plainphone.policy.AppApprovalPhase
import org.plainphone.policy.AppApprovalState
import org.plainphone.policy.AppliedPeopleChange
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CancelIntentStore
import org.plainphone.policy.CredentialState
import org.plainphone.policy.CredentialStore
import org.plainphone.policy.ElevationApprovalStore
import org.plainphone.policy.ElevationGrant
import org.plainphone.policy.ElevationLimits
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.ElevationStore
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.FrictionSettingsReceipt
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.FrictionWaitingStore
import org.plainphone.policy.ModeSwitchCommitter
import org.plainphone.policy.PeopleChangeCommitter
import org.plainphone.policy.PersonContact
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.WaitingChangeKind
import org.plainphone.policy.WaitingHandOverCommitter
import org.plainphone.policy.WaitingIdentity
import org.plainphone.policy.WaitingIdentitySource
import org.plainphone.policy.WaitingOperation
import org.plainphone.policy.WaitingSkipCommitter
import org.plainphone.policy.WaitingText
import org.plainphone.policy.WaitingChangeStore
import org.plainphone.policy.WaitingEvent
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingState
import org.plainphone.policy.WaitingReconciliationStore
import org.plainphone.policy.RecoveredState
import org.plainphone.policy.LostWaitingRecord
import org.plainphone.policy.UpdatePhase
import org.plainphone.policy.UpdateRecord
import org.plainphone.policy.UpdateSelfCheck
import org.plainphone.policy.UpdateSubmission
import org.plainphone.policy.UpdateUseStore
import org.plainphone.policy.StagedUpdateBinding

internal enum class Section { CREDENTIALS, WAITING, ELEVATION, RECEIPTS, APPROVALS, UPDATE, RECOVERY }
/** In write order: copy A, copy B (both agreeing is the commit point), fence A, fence B. */
internal enum class WritePoint { BEFORE_COPY_A, AFTER_COPY_A, AFTER_COPY_B, AFTER_FENCE_A, AFTER_FENCE }
/** Storage could not durably reserve a new identity; the caller shows "not ready" and retries later. */
class IdentityUnavailable : IllegalStateException("identity reservation unavailable")

/** Makes a directory's entries durable, or throws. Identity reservations refuse when this fails. */
internal fun interface DirectorySync { fun sync(dir: File) }

/** fsync of the directory itself through a read-only channel, which Linux and Android's libcore support. */
internal object PlatformDirectorySync : DirectorySync {
    override fun sync(dir: File) {
        java.nio.channels.FileChannel.open(dir.toPath(), java.nio.file.StandardOpenOption.READ).use { it.force(true) }
    }
}

/** Test seam for crash and readback faults at fixed points of a transaction. */
internal fun interface StorageFaults { fun at(point: WritePoint, dir: File) }

/**
 * One device-protected record joining credentials, the waiting operation and its texts, the
 * elevation record, the friction receipt outbox, the app approval state with its receipt outbox, and
 * the Update record with its receipt outbox. Every transaction is a single replacement of
 * the whole record, so a crash exposes either the old or the new record and never a mix.
 *
 * The record is kept as two copies (`authority-a`, `authority-b`) and two fences holding the
 * last committed sequence. A write first repairs every file to the current record, then writes
 * copy A, then copy B, then both fences. Both copies agreeing is the commit point. A read returns
 * the two copies when they agree at or above the fence, else the single copy at exactly the
 * fence, else null. So an interrupted write reads old until copy B lands and new after; a lone
 * copy above the fence (a refused or unfinished write) never surfaces, even after the other copy
 * is lost; and a copy below the fence (a stale or replayed replica) is never served.
 *
 * Results (PLA-160): true means acknowledged, so the new record reads back. After true, no older
 * record remains, so any later loss reads that record or nothing. False means not acknowledged:
 * the store may hold the previous record or the new one. A writer that cannot read its own
 * directory (for example after losing search permission once copy B has landed) cannot tell
 * which, so callers read again before acting and never treat false as "unchanged" or as a
 * definite refusal. This holds for every compare-and-set, commit, move, friction transaction,
 * [initialize] and [migrate]. A section that would not decode is never written.
 *
 * All views share one coordinator per directory, keyed by its canonical path so symlinked aliases
 * share it too. A write made from inside a running transaction is refused rather than allowed to
 * interleave; reads from a transaction's final check see the record under comparison. A thread
 * waits at most [coordinatorWaitMillis] for the coordinator and is then refused, so a lock taken
 * outside the store in the other order (such as PinVault's) can delay a call but never deadlock it.
 * A [serialized] operation, such as lost-record reconciliation, holds the coordinator across all of
 * its calls and effects: other threads' calls wait for it, and calls from inside its Android-only
 * [androidEffect]s are refused.
 * Each section is preserved byte for byte by writers that do not replace it.
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

    private val copyA = AtomicRecord(dir, "authority-a", SCHEMA) { directorySync }
    private val copyB = AtomicRecord(dir, "authority-b", SCHEMA) { directorySync }
    private val fenceA = AtomicRecord(dir, "fence-a", SCHEMA) { directorySync }
    private val fenceB = AtomicRecord(dir, "fence-b", SCHEMA) { directorySync }
    private val coordinator = coordinators.computeIfAbsent(canonical(dir)) { Coordinator() }

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
     * The Update record on the same coordinator (see [UpdateUseStore]). [commitUpdateUse] writes the
     * used-up elevation, SUBMITTED and its receipt with frozen recipient addresses in one record
     * replacement; the receipt joins the outbox, so an undelivered earlier receipt survives a later
     * submission. Only the exact replacement elevation the controller builds is accepted.
     */
    val update: UpdateUseStore = object : UpdateUseStore {
        override val elevationStore: ElevationStore get() = this@JointAuthorityStore.elevationStore

        override fun readUpdate(): UpdateRecord? = section(Section.UPDATE)?.let(AuthorityCodec::decodeUpdate)?.record

        override fun compareAndSetUpdate(expected: UpdateRecord, replacement: UpdateRecord): Boolean = commit { s ->
            val stored = AuthorityCodec.decodeUpdate(s.sections[Section.UPDATE.ordinal]) ?: return@commit null
            if (!resultOnly(expected, replacement) || !sameRecord(stored.record, expected)) return@commit null
            s.with(Section.UPDATE, AuthorityCodec.encodeUpdate(UpdateSection(replacement, stored.outbox)))
        }

        override fun commitUpdateUse(expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
            expectedCredentials: CredentialState, expectedUpdate: UpdateRecord, submission: UpdateSubmission,
            finalCheck: () -> Boolean): Boolean = commitChecked(finalCheck) { s ->
            if (!usable(expectedElevation, replacementElevation, expectedCredentials, expectedUpdate, submission)) return@commitChecked null
            val elevationBytes = AuthorityCodec.encodeElevation(replacementElevation) ?: return@commitChecked null
            fun current(of: Snapshot): UpdateSection? {
                val stored = AuthorityCodec.decodeUpdate(of.sections[Section.UPDATE.ordinal]) ?: return null
                return stored.takeIf {
                    sameRecord(it.record, expectedUpdate) &&
                        of.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expectedElevation)) &&
                        of.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials))
                }
            }
            val stored = current(s) ?: return@commitChecked null
            if (stored.outbox.any { it.receipt.id == submission.receipt.id }) return@commitChecked null
            val contacts = submission.receipt.recipientGenerations.keys.associateWith { expectedCredentials.people.getValue(it).contact }
            val next = try {
                UpdateSection(UpdateRecord(expectedUpdate.revision + 1, submission), stored.outbox + StoredUpdateReceipt(submission.receipt, contacts))
            } catch (_: IllegalArgumentException) {
                return@commitChecked null
            }
            val updateBytes = AuthorityCodec.encodeUpdate(next) ?: return@commitChecked null
            s.with(Section.ELEVATION, elevationBytes).with(Section.UPDATE, updateBytes)
        }
    }

    /** Update receipts awaiting delivery, oldest first. Null when the record is unreadable. */
    fun updateReceipts(): List<StoredUpdateReceipt>? = section(Section.UPDATE)?.let(AuthorityCodec::decodeUpdate)?.outbox

    /**
     * Durably records that [person]'s notice for receipt [id] was handed over. A receipt leaves the
     * outbox once every recipient is acknowledged. True when, afterwards, nothing for [person] under
     * [id] is pending (including when it already was not). False is not acknowledged: the next
     * delivery pass reads the outbox again and may resend under the same dedup ID.
     */
    fun acknowledgeUpdateReceipt(id: String, person: TrustedPersonId): Boolean {
        var settled = false
        val written = commit { s ->
            val stored = AuthorityCodec.decodeUpdate(s.sections[Section.UPDATE.ordinal]) ?: return@commit null
            val entry = stored.outbox.firstOrNull { it.receipt.id == id }
            if (entry == null || person !in entry.receipt.recipientGenerations || person in entry.acknowledged) {
                settled = true
                return@commit null
            }
            val outbox = stored.outbox.mapNotNull { r ->
                if (r !== entry) r else (r.acknowledged + person).let { acked ->
                    if (acked == r.receipt.recipientGenerations.keys) null else StoredUpdateReceipt(r.receipt, r.recipientContacts, acked)
                }
            }
            s.with(Section.UPDATE, AuthorityCodec.encodeUpdate(UpdateSection(stored.record, outbox)))
        }
        return written || settled
    }

    /** The stored record equals [expected] in every field, compared through the canonical encoding. */
    private fun sameRecord(stored: UpdateRecord, expected: UpdateRecord): Boolean {
        val a = AuthorityCodec.encodeUpdate(UpdateSection(stored, emptyList())) ?: return false
        return a.contentEquals(AuthorityCodec.encodeUpdate(UpdateSection(expected, emptyList())))
    }

    /**
     * The result transitions the protocol writes, one revision at a time, with every other field
     * (session, hash, version, receipt, deadline) kept: SUBMITTED -> INSTALLED or FAILED, FAILED ->
     * INSTALLED (a late install), and INSTALLED's pending self-check -> PASSED or FAILED.
     */
    private fun resultOnly(expected: UpdateRecord, replacement: UpdateRecord): Boolean {
        val before = expected.submission ?: return false
        val after = replacement.submission ?: return false
        if (expected.revision == Long.MAX_VALUE || replacement.revision != expected.revision + 1) return false
        val kept = after.copy(phase = before.phase, selfCheck = before.selfCheck)
        if (!sameRecord(UpdateRecord(0, kept), UpdateRecord(0, before))) return false
        val pending = UpdateSelfCheck.PENDING
        return when (before.phase) {
            UpdatePhase.SUBMITTED -> after.phase != UpdatePhase.SUBMITTED && after.selfCheck == pending
            UpdatePhase.FAILED -> after.phase == UpdatePhase.INSTALLED && after.selfCheck == pending
            UpdatePhase.INSTALLED -> before.selfCheck == pending && after.phase == UpdatePhase.INSTALLED && after.selfCheck != pending
        }
    }

    /**
     * The use shapes the controller builds. No outstanding SUBMITTED or pending self-check; a new
     * SUBMITTED whose receipt names exactly the current holders and generations. Self-managed: the
     * live Update grant for these exact bytes, held by the receipt's actor, is used up to the same
     * ledger with no grant or holder, one revision higher, keeping its deadline in the submission.
     * Supervised: no countdown or grant, the elevation written back unchanged and no deadline.
     */
    private fun usable(expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
        credentials: CredentialState, expectedUpdate: UpdateRecord, submission: UpdateSubmission): Boolean {
        val previous = expectedUpdate.submission
        if (expectedUpdate.revision == Long.MAX_VALUE || previous?.phase == UpdatePhase.SUBMITTED ||
            (previous?.phase == UpdatePhase.INSTALLED && previous.selfCheck == UpdateSelfCheck.PENDING) ||
            submission.phase != UpdatePhase.SUBMITTED || submission.selfCheck != UpdateSelfCheck.PENDING) return false
        val receipt = submission.receipt
        if (receipt.recipientGenerations != credentials.people.mapValues { it.value.generation } ||
            expectedElevation.journal != SessionJournal.Empty) return false
        val grant = expectedElevation.state.grant
        if (grant == null) {
            if (expectedElevation.state.countdown != null || submission.elevationDeadline != null) return false
            val bytes = AuthorityCodec.encodeElevation(expectedElevation) ?: return false
            return bytes.contentEquals(AuthorityCodec.encodeElevation(replacementElevation))
        }
        val holder = expectedElevation.person ?: return false
        if (grant.target != ElevationTarget.Update(StagedUpdateBinding(submission.sha256, submission.versionCode)) ||
            grant.packageAccessOpen || grant.id != receipt.id || holder.person != receipt.actor ||
            holder.generation != receipt.actorGeneration || submission.elevationDeadline != grant.deadline ||
            expectedElevation.revision == Long.MAX_VALUE) return false
        val used = AuthorityCodec.encodeElevation(ElevationRecord(expectedElevation.revision + 1, ElevationState(expectedElevation.state.ledger)))
            ?: return false
        return used.contentEquals(AuthorityCodec.encodeElevation(replacementElevation))
    }

    /**
     * The only replacement pair the joint write accepts, rebuilt here from the expected pair and
     * compared byte for byte. The approval moves PREPARING -> COMMITTED exactly as the controller
     * builds it, with every unrelated field kept. The grant is used up as the policy defines it:
     * a Play grant gains exactly this package (once), a matching Approve grant is removed. Debits,
     * grant start, deadline, target and person never change; no session is open before or after.
     */
    private fun joinable(expected: AppApprovalState, replacement: AppApprovalState,
        expectedElevation: ElevationRecord, replacementElevation: ElevationRecord): Boolean {
        val op = expected.pending?.takeIf { it.phase == AppApprovalPhase.PREPARING } ?: return false
        val receipt = op.receipt ?: return false
        val identity = op.record.identity
        if (expected.revision == Long.MAX_VALUE || expectedElevation.revision == Long.MAX_VALUE) return false
        val approval = try {
            AppApprovalState(expected.revision + 1, expected.apps + (identity.packageName to op.record),
                AppApprovalOperation(op.record, receipt, AppApprovalPhase.COMMITTED), expected.receipts + receipt,
                expected.removedApps - identity.packageName)
        } catch (_: IllegalArgumentException) {
            return false
        }
        val grant = expectedElevation.state.grant ?: return false
        if (grant.packageAccessOpen || expectedElevation.journal != SessionJournal.Empty) return false
        val used = when (val target = grant.target) {
            ElevationTarget.Play -> {
                if (grant.approved.any { it.packageName == identity.packageName }) return false
                ElevationRecord(expectedElevation.revision + 1, ElevationState(expectedElevation.state.ledger,
                    grant = ElevationGrant.restore(grant.id, grant.target, grant.startedAt, grant.deadline, false,
                        grant.approved + identity) ?: return false), SessionJournal.Empty, expectedElevation.person)
            }
            is ElevationTarget.Approve -> {
                if (target.identity != identity) return false
                ElevationRecord(expectedElevation.revision + 1, ElevationState(expectedElevation.state.ledger))
            }
            else -> return false
        }
        val wantedApproval = AuthorityCodec.encodeApprovals(approval) ?: return false
        val wantedElevation = AuthorityCodec.encodeElevation(used) ?: return false
        return wantedApproval.contentEquals(AuthorityCodec.encodeApprovals(replacement)) &&
            wantedElevation.contentEquals(AuthorityCodec.encodeElevation(replacementElevation))
    }

    /**
     * Cancel intents, kept apart from the joint record so a request survives even when that record is
     * unreadable. Each request has an entry and, once asked, a separate REQUESTED marker. A request
     * writes the marker and then overwrites the entry with REQUESTED, so losing either file still reads
     * REQUESTED (PLA-128 F1). The marker's presence alone (even damaged) reads REQUESTED. CLEAR needs an
     * intact CLEAR entry and no marker; anything else is UNKNOWN. Initialize never overwrites an existing
     * file, so a damaged entry is never guessed back to CLEAR. Same coordinator as every write.
     */
    val cancelIntents: CancelIntentStore = object : CancelIntentStore {
        override fun initialize(requestId: String): Boolean = coordinator.run(false) {
            val (entry, marker) = intentRecords(requestId)
            when {
                // An existing CLEAR entry is acknowledged only once its directory entry is synced.
                marker.exists() || entry.exists() -> intent(requestId) == CancelIntent.CLEAR && entry.ensure(intentPayload(CLEAR_TAG, requestId))
                else -> entry.write(intentPayload(CLEAR_TAG, requestId)) {}
            }
        }
        override fun request(requestId: String): Boolean = coordinator.run(false) {
            val (entry, marker) = intentRecords(requestId)
            val requested = intentPayload(REQUESTED_TAG, requestId)
            // Both writes are attempted even if the first fails; true only when both are durable.
            val markerSaved = marker.ensure(requested)
            val entrySaved = entry.ensure(requested)
            markerSaved && entrySaved
        }
        override fun read(requestId: String): CancelIntent = coordinator.run(CancelIntent.UNKNOWN) { intent(requestId) }
    }

    private var entropy: (Int) -> ByteArray = { n -> ByteArray(n).also { SecureRandom().nextBytes(it) } }

    private var directorySync: DirectorySync = PlatformDirectorySync

    /** Test seam: the random source for identities. */
    internal fun withEntropy(source: (Int) -> ByteArray) = apply { entropy = source }

    /** Test seam: how directory entries are made durable. */
    internal fun withDirectorySync(sync: DirectorySync) = apply { directorySync = sync }

    /**
     * Request IDs and cancel codes, each reserved before it is returned and never reused, even when
     * the preparation is abandoned. A reservation is a file in `reserved/` created exclusively and
     * never deleted; its existence alone (even empty or damaged) marks the value as taken. Cancel codes
     * are 8 digits so they can be typed in a text reply; a taken code is drawn again. Throws
     * [IdentityUnavailable] when storage cannot record a reservation: nothing unreserved is handed out.
     */
    val identities: WaitingIdentitySource = WaitingIdentitySource {
        coordinator.run(null) {
            val request = reserve("request") { "req-" + hex(entropy(16)) } ?: return@run null
            val code = reserve("cancel") { digits(entropy(8)) } ?: return@run null
            WaitingIdentity(request, code)
        } ?: throw IdentityUnavailable()
    }

    /** A new, never-reused slot ID for an incoming trusted person, or null if none can be reserved. */
    fun reservePerson(): TrustedPersonId? = coordinator.run(null) {
        reserve("person") { "person-" + hex(entropy(16)) }?.let(::TrustedPersonId)
    }

    /**
     * A new, never-reused epoch ID for the proven running clock, or "" if none can be reserved durably;
     * the clock treats a blank epoch as no reading.
     */
    fun reserveEpoch(): String = coordinator.run(null) { reserve("epoch") { "epoch-" + hex(entropy(16)) } } ?: ""

    /**
     * Callers hold the coordinator. Up to [ATTEMPTS] draws; null if none could be reserved durably.
     * A value is handed out only after its file contents, the `reserved/` folder entry and the folder's
     * own entry in [dir] are all synced (PLA-143 R1). Any failure refuses; the created file still marks
     * the value as taken, so a refused value is never handed out later either.
     */
    private fun reserve(kind: String, draw: () -> String): String? {
        val folder = File(dir, "reserved")
        if (!folder.isDirectory && !folder.mkdirs()) return null
        repeat(ATTEMPTS) {
            val value = draw()
            val file = File(folder, "$kind-" + hex(MessageDigest.getInstance("SHA-256").digest(value.toByteArray())))
            try {
                Files.createFile(file.toPath())
            } catch (_: FileAlreadyExistsException) {
                return@repeat
            } catch (_: IOException) {
                return null
            }
            return try {
                java.io.FileOutputStream(file).use { it.write(value.toByteArray()); it.fd.sync() }
                directorySync.sync(folder)
                directorySync.sync(dir)
                value
            } catch (_: IOException) {
                null
            }
        }
        return null
    }

    private fun hex(bytes: ByteArray) = bytes.joinToString("") { "%02x".format(it) }
    private fun digits(bytes: ByteArray) = "%08d".format(Math.floorMod(ByteBuffer.wrap(bytes).long, 100_000_000L))

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
     * People and mode-switch commits on this store's coordinator (PLA-97). People operations go to
     * [peopleCommitter] unchanged. A mode switch's [elevationStore] is this store's own, built over
     * this store's [waitingStore], so the controller's exact-store binding check holds only for a
     * controller wired to that same [waitingStore] instance.
     *
     * [applyModePatch] compares the credential, waiting and elevation sections in one transaction,
     * requires the elevation record to hold no temporary authority, and writes only the credential
     * section: holders, mode, recovery profile, raised structure generation and applied marker
     * together. The elevation record and its whole ledger are kept byte for byte. [move] for a mode
     * switch only persists the rollback direction; the terminal write is [finishMode], which also
     * compares the elevation record. Nothing here runs an effect.
     */
    val modeCommitter: ModeSwitchCommitter = object : ModeSwitchCommitter {
        override val elevationStore: ElevationStore get() = this@JointAuthorityStore.elevationStore

        override fun applyPatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
            expectedWaiting: WaitingState): Boolean =
            expectedWaiting.operation?.modePatch == null && peopleCommitter.applyPatch(expectedCredentials, replacementCredentials, expectedWaiting)

        override fun move(expectedCredentials: CredentialState, expectedWaiting: WaitingState, replacementWaiting: WaitingState): Boolean {
            val before = expectedWaiting.operation ?: return false
            if (before.modePatch == null) return peopleCommitter.move(expectedCredentials, expectedWaiting, replacementWaiting)
            return commit { s ->
                val after = replacementWaiting.operation ?: return@commit null
                // Only the rollback direction; a terminal mode write must also compare the elevation record.
                if (before.phase != WaitingPhase.COMMITTING || after.phase != WaitingPhase.ROLLING_BACK ||
                    applied(expectedWaiting, expectedCredentials)) return@commit null
                val stopper = after.stopper ?: return@commit null
                if (expectedCredentials.people[stopper]?.generation != after.stopperGeneration ||
                    !sameOperation(after, before.copied(WaitingPhase.ROLLING_BACK, stopper = stopper, stopperGeneration = after.stopperGeneration))) return@commit null
                joint(s, expectedCredentials, null, expectedWaiting, replacementWaiting, listOf(WaitingEvent.ROLLING_BACK to expectedCredentials))
            }
        }

        override fun applyModePatch(expectedCredentials: CredentialState, replacementCredentials: CredentialState,
            expectedWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean = commit { s ->
            val op = expectedWaiting.operation ?: return@commit null
            val patch = op.modePatch ?: return@commit null
            if (op.phase != WaitingPhase.COMMITTING || applied(expectedWaiting, expectedCredentials) ||
                !patch.dependenciesMatch(expectedCredentials) || !clearOfAuthority(expectedElevation)) return@commit null
            val profile = expectedCredentials.recoveryProfile ?: return@commit null
            if (expectedCredentials.revision == Long.MAX_VALUE || expectedCredentials.structureGeneration == Long.MAX_VALUE) return@commit null
            // The one acceptable result, built from the stored patch the same way as the policy's candidate.
            val person = patch.incomingPerson
            val credential = patch.incomingSelfManaged?.credential ?: patch.incomingTrustedPerson?.credential ?: return@commit null
            val account = patch.incomingSelfManaged?.resetProtectionUserId ?: patch.incomingTrustedPerson?.resetProtectionUserId ?: return@commit null
            val structure = expectedCredentials.structureGeneration + 1
            val wanted = CredentialState(patch.targetMode, mapOf(person to credential), expectedCredentials.revision + 1, structure,
                expectedCredentials.pendingNotices, RecoveryProfile(mapOf(person to account), profile.backupUserId, profile.friction),
                AppliedPeopleChange(op.identity.requestId, expectedWaiting.storageGeneration, structure))
            val bytes = AuthorityCodec.encodeCredentials(replacementCredentials)
            if (!bytes.contentEquals(AuthorityCodec.encodeCredentials(wanted)) ||
                !s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials)) ||
                !s.matches(Section.WAITING, AuthorityCodec.encodeWaiting(expectedWaiting)) ||
                !s.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expectedElevation))) return@commit null
            s.with(Section.CREDENTIALS, bytes)
        }

        override fun finishMode(expectedCredentials: CredentialState, expectedWaiting: WaitingState,
            replacementWaiting: WaitingState, expectedElevation: ElevationRecord): Boolean = commit { s ->
            val before = expectedWaiting.operation ?: return@commit null
            val after = replacementWaiting.operation ?: return@commit null
            if (before.modePatch == null || !clearOfAuthority(expectedElevation)) return@commit null
            val done = applied(expectedWaiting, expectedCredentials)
            val event = when {
                before.phase == WaitingPhase.COMMITTING && after.phase == WaitingPhase.COMPLETED && done -> WaitingEvent.COMPLETED
                before.phase == WaitingPhase.ROLLING_BACK && after.phase == WaitingPhase.CANCELLED && !done -> WaitingEvent.CANCELLED_AFTER_ROLLBACK
                else -> return@commit null
            }
            if (!sameOperation(after, before.copied(after.phase)) ||
                !s.matches(Section.ELEVATION, AuthorityCodec.encodeElevation(expectedElevation))) return@commit null
            joint(s, expectedCredentials, null, expectedWaiting, replacementWaiting, listOf(event to expectedCredentials))
        }
    }

    /** No shared session journal, countdown, grant or person binding: the ledger alone. */
    private fun clearOfAuthority(r: ElevationRecord) = r.journal == SessionJournal.Empty &&
        r.state.countdown == null && r.state.grant == null && r.person == null

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

    /** The entry keeps its original `intent-clear-` file name although it can also hold REQUESTED. */
    private fun intentRecords(requestId: String): Pair<AtomicRecord, AtomicRecord> {
        val hash = intentHash(requestId)
        return AtomicRecord(dir, "$INTENT_ENTRY$hash", INTENT_SCHEMA) { directorySync } to
            AtomicRecord(dir, "$INTENT_MARKER$hash", INTENT_SCHEMA) { directorySync }
    }

    private fun intentPayload(tag: Byte, requestId: String) = byteArrayOf(tag) + requestId.toByteArray()

    /** Callers hold the coordinator. */
    private fun intent(requestId: String): CancelIntent {
        val (entry, marker) = intentRecords(requestId)
        val stored = entry.read()
        return when {
            marker.exists() || stored?.contentEquals(intentPayload(REQUESTED_TAG, requestId)) == true -> CancelIntent.REQUESTED
            stored?.contentEquals(intentPayload(CLEAR_TAG, requestId)) == true -> CancelIntent.CLEAR
            else -> CancelIntent.UNKNOWN
        }
    }

    /**
     * Enrollment-time creation of the first record. Refuses if a fence exists or a record reads.
     * True means genesis reads back. False is not acknowledged, as for every write. The first-record
     * cases are explicit (PLA-159): a blocked second copy returns false with no readable genesis, so
     * initialize can run again; a blocked fence returns true with a readable genesis that [migrate]
     * completes later; search denial after the second copy returns false yet genesis reads once
     * access returns, so initialize then refuses and [migrate] completes it. Callers read after false.
     */
    fun initialize(credentials: CredentialState, waiting: WaitingState, elevation: ElevationRecord,
        approvals: AppApprovalState = AppApprovalState()): Boolean = coordinator.run(false) {
        if (!firstSetupVacant()) return@run false
        // Enrollment is the only place an empty Update record is created.
        val sections = listOf(AuthorityCodec.encodeCredentials(credentials) ?: return@run false,
            AuthorityCodec.encodeWaiting(waiting) ?: return@run false, AuthorityCodec.encodeElevation(elevation) ?: return@run false,
            AuthorityCodec.encodeReceipts(emptyList()) ?: return@run false, AuthorityCodec.encodeApprovals(approvals) ?: return@run false,
            AuthorityCodec.encodeUpdate(UpdateSection(UpdateRecord(), emptyList())) ?: return@run false,
            AuthorityCodec.encodeRecovery(RecoverySection(null, emptyList())) ?: return@run false)
        val genesis = Snapshot(0, sections)
        if (!readable(genesis)) return@run false
        save(null, genesis)
    }

    /** The only state [initialize] writes over ([InitialProvisioning], PLA-97); see [firstSetupVacant]. */
    internal fun vacant(): Boolean = coordinator.run(false) { firstSetupVacant() }

    /**
     * Callers hold the coordinator. No fence, no readable record, and each copy file is absent or an
     * interrupted genesis (sequence 0) that never committed, so replacing it loses nothing. A copy of
     * a used record, a damaged copy or another schema's copy is never vacant, even with both fences
     * gone (PLA-409 R1): missing is not vacant.
     */
    private fun firstSetupVacant(): Boolean = !fenceA.exists() && !fenceB.exists() && load() == null &&
        listOf(copyA, copyB).all { !it.exists() || it.read()?.let(Snapshot::decode)?.sequence == 0L }

    /**
     * A directory sync with nothing written: makes every visible entry durable, including renames
     * whose own sync failed. True only when it succeeded (PLA-409 R2).
     */
    internal fun durabilityBarrier(): Boolean = coordinator.run(false) { syncDirectory() }

    /**
     * Explicit startup step, run before any controller reads this store. A record written by a
     * schema 1-3 store reads as missing until it is migrated. Migration reads it with the old
     * rules, converts every section to its canonical current encoding, checks the result decodes,
     * and commits it with the normal protocol. Values are kept; bytes are kept only where the old
     * bytes were already canonical (unordered maps and sets, such as several people, come out
     * sorted, which keeps the byte compare-and-set checks exact). The first new copy goes over a
     * file that does not hold the old record, so the old record stays readable to a retry until
     * both new copies agree; after that the old files are never read again. Schema 3 cancel
     * intents are rewritten in the current format too (see [migrateIntents]).
     *
     * This is the startup readiness gate: no controller and no permissive authority action runs
     * until it returns true. True only when the migration is complete: both copies and both fences
     * hold one readable current record and no rewritable old intent is left. The one exception:
     * damaged or misnamed old intent files, which name no request, stay in place and read UNKNOWN
     * (their markers still read REQUESTED). False means unavailable, so run again later. It does not
     * mean unchanged or empty: the record may already be current. Restrictions stay in place and
     * nothing initializes over it. An old record that does not decode is left untouched and refused.
     */
    fun migrate(): Boolean = coordinator.run(false) {
        val intents = migrateIntents()
        val record = migrateRecord()
        // Readiness also covers renames an earlier failed attempt left unsynced, which a retry may no
        // longer find to rewrite (PLA-220 R1): one successful directory sync after all of them.
        record && intents && syncDirectory()
    }

    /** Callers hold the coordinator. Every record, intent and reservation folder entry lives in [dir]. */
    private fun syncDirectory(): Boolean = try { directorySync.sync(dir); true } catch (_: IOException) { false }

    /** Callers hold the coordinator. */
    private fun migrateRecord(): Boolean {
        // Both new copies may already agree before any new fence is written; that record is current.
        val loaded = load()
        if (loaded != null || fenceA.read() != null || fenceB.read() != null) {
            val current = loaded ?: return false
            if (!readable(current)) return false
            if (complete(current)) return true
            // Finish an interrupted migration, so no old fence or copy is left beside the current record.
            if (current.sequence == Long.MAX_VALUE) return false
            val next = Snapshot(current.sequence + 1, current.sections)
            return save(current, next) && complete(next)
        }
        val legacy = LegacyAuthority.load(dir) ?: return false
        if (legacy.sequence == Long.MAX_VALUE) return false
        val next = Snapshot(legacy.sequence + 1, LegacyAuthority.convert(legacy, matchingInventory) ?: return false)
        if (!readable(next)) return false
        val keepB = "authority-b" in legacy.holders
        return save(null, next, spare = if (keepB) copyA else copyB) && complete(next)
    }

    /** Both copies and both fences hold exactly [s]. */
    private fun complete(s: Snapshot): Boolean {
        val bytes = s.encode(); val fence = fenceBytes(s.sequence)
        return copyA.read()?.contentEquals(bytes) == true && copyB.read()?.contentEquals(bytes) == true &&
            fenceA.read()?.contentEquals(fence) == true && fenceB.read()?.contentEquals(fence) == true
    }

    /**
     * Rewrites every cancel intent the schema 3 store left (see [LegacyAuthority.intentRequests])
     * as a current entry: REQUESTED while any marker exists for the request, else CLEAR, then a
     * current marker if one existed. A crash between the two writes still reads REQUESTED, from
     * the old marker. Old files that name no request of their own are left alone and read
     * UNKNOWN, so nothing is guessed CLEAR. True when no rewritable old intent is left. Callers
     * hold the coordinator.
     */
    private fun migrateIntents(): Boolean {
        val requests = LegacyAuthority.intentRequests(dir) ?: return false
        var done = true
        for (requestId in requests) {
            val (entry, marker) = intentRecords(requestId)
            val requested = marker.exists()
            val tag = if (requested) REQUESTED_TAG else CLEAR_TAG
            fun rewrite(record: AtomicRecord, payload: ByteArray) = record.ensure(payload)
            val entrySaved = rewrite(entry, intentPayload(tag, requestId))
            val markerSaved = !requested || (entrySaved && rewrite(marker, intentPayload(REQUESTED_TAG, requestId)))
            done = done && entrySaved && markerSaved
        }
        return done
    }

    /**
     * The elevation limits every consumer must use: the ACTIVE friction in the credential section,
     * read on this store's coordinator. Pending looser targets and UI caches never count. When the
     * credentials or their recovery profile cannot be read, no turn is available and the countdown
     * is the longest allowed.
     */
    fun activeLimits(): ElevationLimits {
        val friction = credentials.read()?.recoveryProfile?.friction ?: return FAIL_CLOSED_LIMITS
        return ElevationLimits(friction.countdownMillis, friction.dailyLimit)
    }

    /** Routine friction receipts awaiting delivery, in commit order. Null when the record is unreadable. */
    fun frictionReceipts(): List<StoredFrictionReceipt>? = section(Section.RECEIPTS)?.let(AuthorityCodec::decodeReceipts)

    internal fun sectionBytesForTest(section: Section): ByteArray? = section(section)

    /** Test seam: commit raw bytes into one section, bypassing the decode check, to model a section that stops decoding. */
    /**
     * Runs [block] as one serialized operation on this store's coordinator: its own store calls go
     * ahead, every other thread's store calls wait for the whole of it (up to the coordinator wait,
     * then refused), and a nested operation is refused with [refused].
     */
    internal fun <T> serialized(refused: T, block: () -> T): T = coordinator.operation(refused, block)

    /**
     * An Android-only effect: any store call from inside [block] is refused. Inside a [serialized]
     * operation it runs on that operation's thread. Called from any other thread, it waits for the whole
     * running operation first, so it never runs in the middle of one; past the wait limit it throws
     * [EffectRefused] without running [block].
     */
    internal fun <T> androidEffect(block: () -> T): T = coordinator.effect(block)

    /** An [androidEffect] that did not run: another thread's operation held the store past the wait limit. */
    internal class EffectRefused : IllegalStateException("store busy: effect not run")

    internal fun replaceSectionForTest(section: Section, bytes: ByteArray): Boolean = coordinator.run(false) {
        val current = load() ?: return@run false
        save(current, Snapshot(current.sequence + 1, current.with(section, bytes).sections))
    }

    /**
     * True when any record copy or fence exists, in any schema: the store was set up, so a record that
     * does not read is damaged, not absent. A refused coordinator counts as present (fail toward attention).
     */
    fun recordPresent(): Boolean = coordinator.run(true) { copyA.exists() || copyB.exists() || fenceA.exists() || fenceB.exists() }

    /** Recovery receipt addresses, kept as a permanent audit. Null when the record is unreadable. */
    fun recoveryAddresses(): List<StoredRecoveryAddresses>? = section(Section.RECOVERY)?.let(AuthorityCodec::decodeRecovery)?.audit

    /**
     * Lost-journal reconciliation on the same record and coordinator as every other writer. Credentials
     * and the journal share one record here, so a confirmed loss is a readable record whose waiting
     * section no longer decodes. A wholly unreadable record is never a confirmed loss: the credentials
     * are gone too, and the phone stays held. Ordinary reads and writes delegate to [waitingStore].
     */
    val reconciliation: WaitingReconciliationStore = object : WaitingReconciliationStore {
        override fun read(): WaitingState? = waitingStore.read()
        override fun compareAndSet(expected: WaitingState, replacement: WaitingState): Boolean = waitingStore.compareAndSet(expected, replacement)

        /** Reserves the episode ID durably and records it in the same record; the same ID until completion. */
        override fun lostRecord(): LostWaitingRecord? = coordinator.run(null) {
            val s = load() ?: return@run null
            val waiting = s.sections[Section.WAITING.ordinal]
            if (AuthorityCodec.decodeWaiting(waiting) != null) return@run null
            val recovery = AuthorityCodec.decodeRecovery(s.sections[Section.RECOVERY.ordinal]) ?: return@run null
            val digest = hex(MessageDigest.getInstance("SHA-256").digest(waiting))
            recovery.loss?.takeIf { it.digest == digest }?.let { return@run LostWaitingRecord(it.id) }
            val id = reserve("loss") { "loss-" + hex(entropy(16)) } ?: return@run null
            val marked = commitHeld { now ->
                if (!now.sections[Section.WAITING.ordinal].contentEquals(waiting)) null
                else now.with(Section.RECOVERY, AuthorityCodec.encodeRecovery(RecoverySection(LossEpisode(id, digest), recovery.audit)))
            }
            if (marked) LostWaitingRecord(id) else null
        }

        /**
         * One write: the still-lost episode and the exact credential record are compared; the vacant
         * journal with its single recovery receipt replaces the lost section; the loss marker is cleared;
         * and the receipt's addresses, frozen from [expectedCredentials], join the permanent audit.
         */
        override fun completeReconciliation(lost: LostWaitingRecord, expectedCredentials: CredentialState,
            replacement: WaitingState): Boolean = commit { s ->
            val waiting = s.sections[Section.WAITING.ordinal]
            if (AuthorityCodec.decodeWaiting(waiting) != null) return@commit null
            val recovery = AuthorityCodec.decodeRecovery(s.sections[Section.RECOVERY.ordinal]) ?: return@commit null
            val loss = recovery.loss ?: return@commit null
            if (loss.id != lost.id || loss.digest != hex(MessageDigest.getInstance("SHA-256").digest(waiting)) ||
                !s.matches(Section.CREDENTIALS, AuthorityCodec.encodeCredentials(expectedCredentials))) return@commit null
            val profile = expectedCredentials.recoveryProfile ?: return@commit null
            val receipt = replacement.recoveryReceipts.singleOrNull() ?: return@commit null
            val generations = expectedCredentials.people.mapValues { it.value.generation }
            if (replacement.storageGeneration != lost.id || replacement.revision != 0L || replacement.operation != null ||
                replacement.texts.isNotEmpty() || receipt.key != "recovered:${lost.id.length}:${lost.id}" ||
                receipt.mode != expectedCredentials.mode || receipt.recipientGenerations != generations ||
                receipt.friction != profile.friction || receipt.state != RecoveredState.ACTIVE ||
                recovery.audit.any { it.key == receipt.key }) return@commit null
            val addresses = StoredRecoveryAddresses(receipt.key, generations, expectedCredentials.people.mapValues { it.value.contact })
            s.with(Section.WAITING, AuthorityCodec.encodeWaiting(replacement))
                .with(Section.RECOVERY, AuthorityCodec.encodeRecovery(RecoverySection(null, recovery.audit + addresses)))
        }
    }

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
        /** Null (an encoder refusal) leaves an empty section, which no decoder accepts, so the commit is refused. */
        fun with(section: Section, bytes: ByteArray?) = Snapshot(sequence, sections.toMutableList().also { it[section.ordinal] = bytes ?: ByteArray(0) })
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

    private fun fenceValue(record: AtomicRecord): Long? = record.read()?.takeIf { it.size == 8 }?.let { ByteBuffer.wrap(it).long }
    private fun fenceBytes(sequence: Long): ByteArray = ByteBuffer.allocate(8).putLong(sequence).array()

    /**
     * Both copies when they agree at or above the higher valid fence (or with no fence left); else
     * the copy at exactly that fence; else null.
     */
    private fun load(): Snapshot? {
        val fence = listOfNotNull(fenceValue(fenceA), fenceValue(fenceB)).maxOrNull()
        val copies = listOfNotNull(copyA.read(), copyB.read()).mapNotNull { bytes -> Snapshot.decode(bytes)?.let { it to bytes } }
        if (copies.size == 2 && copies[0].second.contentEquals(copies[1].second) &&
            (fence == null || copies[0].first.sequence >= fence)) return copies[0].first
        if (fence == null) return null
        val atFence = copies.filter { it.first.sequence == fence }
        val first = atFence.firstOrNull() ?: return null
        return if (atFence.all { it.second.contentEquals(first.second) }) first.first else null
    }

    /** Every section of [s] decodes, so an acknowledged record always reads back. */
    private fun readable(s: Snapshot): Boolean = Section.entries.all { readable(s, it) }

    private fun readable(s: Snapshot, section: Section): Boolean {
        val bytes = s.sections[section.ordinal]
        return when (section) {
            Section.CREDENTIALS -> AuthorityCodec.decodeCredentials(bytes) != null
            Section.WAITING -> AuthorityCodec.decodeWaiting(bytes) != null
            Section.ELEVATION -> AuthorityCodec.decodeElevation(bytes, matchingInventory) != null
            Section.RECEIPTS -> AuthorityCodec.decodeReceipts(bytes) != null
            Section.APPROVALS -> AuthorityCodec.decodeApprovals(bytes) != null
            Section.UPDATE -> AuthorityCodec.decodeUpdate(bytes) != null
            Section.RECOVERY -> AuthorityCodec.decodeRecovery(bytes) != null
        }
    }

    /**
     * One transaction: [change] returns the replacement or null to refuse before any write. True
     * means acknowledged; false means not acknowledged (see the class notes), never "unchanged".
     */
    private fun commit(change: (Snapshot) -> Snapshot?): Boolean = coordinator.run(false) { commitHeld(change) }

    /** [commit] for a caller that already holds the coordinator. */
    private fun commitHeld(change: (Snapshot) -> Snapshot?): Boolean =
        prepare(change)?.let(::write) ?: false

    /**
     * [commit] with [finalCheck] run once, after every load, decode, comparison and repair (R2,
     * PLA-207): [change] compares against the loaded record and the files are repaired to it before
     * the check. After the check only the durable write and its read-back remain. The check may read
     * the record under comparison; any write it tries is refused, and the coordinator keeps every
     * other store call out until this one ends, so the comparison still holds when the write starts.
     */
    private fun commitChecked(finalCheck: () -> Boolean, change: (Snapshot) -> Snapshot?): Boolean = coordinator.run(false) {
        val prepared = prepare(change) ?: return@run false
        val compared = prepared.previous ?: return@run false
        coordinator.viewing(compared, finalCheck) && write(prepared)
    }

    /** Load, [change], decode what changed and repair the files to the loaded record; null refuses before any write. */
    private fun prepare(change: (Snapshot) -> Snapshot?): PreparedWrite? {
        val current = load() ?: return null
        if (current.sequence == Long.MAX_VALUE) return null
        val next = change(current) ?: return null
        // Only sections this transaction replaced must decode; untouched ones are kept byte for byte.
        val changed = Section.entries.filter { !next.sections[it.ordinal].contentEquals(current.sections[it.ordinal]) }
        if (changed.any { !readable(next, it) }) return null
        return prepareSave(current, Snapshot(current.sequence + 1, next.sections))
    }

    /**
     * Repair every file to [current] before a new write, copies first. A failed repair aborts the
     * write; any file it touched holds [current] or is unreadable, so [current] still reads.
     */
    private fun heal(current: Snapshot): Boolean {
        val bytes = current.encode(); val fence = fenceBytes(current.sequence)
        for ((record, payload) in listOf(copyA to bytes, copyB to bytes, fenceA to fence, fenceB to fence))
            // No extra sync for files that already match: the copy writes that follow sync this directory,
            // which also makes these entries durable, and nothing is acknowledged without those syncs.
            if (record.read()?.contentEquals(payload) != true && !record.write(payload) {}) return false
        return true
    }

    /**
     * Repair, then copy A, copy B, fence A, fence B. Both copies written and their directory synced is
     * the commit point, and the only way to a true result. If a copy step fails (including its directory
     * sync), both copy files are put back to exactly what they held where possible and the result is
     * false, which means not acknowledged: either record may read afterwards (PLA-160). The result is read from disk: true when [next] reads back. False is
     * not acknowledged: the store tries to resolve (restore, then read), but when the directory cannot
     * be read [next] may still have committed, so false never promises [previous]. A migration names
     * the copy that holds no old record, so the old one survives until both new copies are written.
     */
    private fun save(previous: Snapshot?, next: Snapshot, spare: AtomicRecord? = null): Boolean =
        prepareSave(previous, next, spare)?.let(::write) ?: false

    /** A repaired record and the copy files' exact prior bytes: everything [save] reads before it writes. */
    private class PreparedWrite(val previous: Snapshot?, val next: Snapshot, val first: AtomicRecord,
        val other: AtomicRecord, val before: Pair<ByteArray?, ByteArray?>)

    /** The reads and repairs of [save]; null when the repair or the snapshot of the copies failed. */
    private fun prepareSave(previous: Snapshot?, next: Snapshot, spare: AtomicRecord? = null): PreparedWrite? {
        if (previous != null && !heal(previous)) return null
        val (first, other) = if (spare === copyB) copyB to copyA else copyA to copyB
        // Exactly what each copy file held, legacy formats included, so a failure can put it back.
        val before = try { first.snapshot() to other.snapshot() } catch (_: IOException) { return null }
        return PreparedWrite(previous, next, first, other, before)
    }

    /** The writes of [save]: copies, fences, then the read-back that decides the result. */
    private fun write(p: PreparedWrite): Boolean {
        val next = p.next; val first = p.first; val other = p.other; val before = p.before
        val bytes = next.encode()
        val fence = fenceBytes(next.sequence)
        faults.at(WritePoint.BEFORE_COPY_A, dir)
        val copied = first.write(bytes) { faults.at(WritePoint.AFTER_COPY_A, dir) } &&
            other.write(bytes) { faults.at(WritePoint.AFTER_COPY_B, dir) }
        if (!copied) {
            // Not committed: a copy whose rename was not synced is not durable, even if both read back
            // (PLA-188 R1/R3), so this is never acknowledged. Put both files back as they were where
            // possible. False only means "not acknowledged" (PLA-160): fresh reads decide.
            other.restore(before.second); first.restore(before.first)
            return false
        }
        fenceA.write(fence) { faults.at(WritePoint.AFTER_FENCE_A, dir) }
        fenceB.write(fence) { faults.at(WritePoint.AFTER_FENCE, dir) }
        return load()?.encode()?.contentEquals(bytes) == true
    }

    /**
     * Serializes the store. Every call takes [operationLock] and then [lock]; an [operation] holds
     * [operationLock] from start to end, so its own store calls run inside it while every other
     * thread's calls wait for the whole operation. During an [effect] of that operation every store
     * call from its thread is refused, so an Android effect cannot interleave a store write. An effect
     * from outside an operation also takes [operationLock], so it waits for any running operation.
     */
    private class Coordinator {
        private val lock = ReentrantLock()
        private val operationLock = ReentrantLock()
        /** The thread running an Android-only effect. Only the [operationLock] holder writes it. */
        @Volatile private var effectThread: Thread? = null
        private var viewed: Snapshot? = null
        /** A nested call from the thread already inside a transaction or an effect is refused, as is a wait past the limit. */
        fun <T> run(refused: T, block: () -> T): T {
            if (lock.isHeldByCurrentThread || effectThread === Thread.currentThread()) return refused
            val joined = operationLock.isHeldByCurrentThread
            if (!joined && !acquire(operationLock)) return refused
            try {
                if (!acquire(lock)) return refused
                try { return block() } finally { lock.unlock() }
            } finally {
                if (!joined) operationLock.unlock()
            }
        }
        /** One serialized multi-call operation. Not reentrant: a call from inside a transaction, an effect or another operation is refused. */
        fun <T> operation(refused: T, block: () -> T): T {
            if (operationLock.isHeldByCurrentThread || lock.isHeldByCurrentThread) return refused
            if (!acquire(operationLock)) return refused
            try { return block() } finally { operationLock.unlock() }
        }
        /**
         * Runs [block] holding [operationLock], with every store call from this thread refused. Inside an
         * operation it joins it; from another thread it waits for that operation, and throws [EffectRefused] past the limit.
         */
        fun <T> effect(block: () -> T): T {
            val joined = operationLock.isHeldByCurrentThread
            if (!joined && !acquire(operationLock)) throw EffectRefused()
            val before = effectThread
            effectThread = Thread.currentThread()
            try { return block() } finally {
                effectThread = before
                if (!joined) operationLock.unlock()
            }
        }
        private fun acquire(target: ReentrantLock): Boolean = try {
            target.tryLock(coordinatorWaitMillis, java.util.concurrent.TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt(); false
        }
        /** Inside a transaction only: lets [block] read [snapshot]. Writes from it are still refused. */
        fun viewing(snapshot: Snapshot, block: () -> Boolean): Boolean {
            check(lock.isHeldByCurrentThread)
            viewed = snapshot
            try { return block() } finally { viewed = null }
        }
        /** The snapshot under comparison, for a final check on this thread only; never inside an [effect]. */
        fun view(): Snapshot? = if (lock.isHeldByCurrentThread && effectThread !== Thread.currentThread()) viewed else null
    }

    companion object {
        /** Longest wait for another thread's transaction before a call is refused (not ready). */
        @Volatile internal var coordinatorWaitMillis: Long = 10_000
        /** 2 adds the app approval section; 3 stores people patches; 4 commits at the fence with canonical maps; 5 adds Update; 6 adds Recovery. */
        private const val SCHEMA = 6
        /** Cancel-intent files keep the schema 4 format; the Update section did not change them. */
        private const val INTENT_SCHEMA = 4
        private val BUSY = setOf(WaitingPhase.COMMITTING, WaitingPhase.ROLLING_BACK)
        private val FAIL_CLOSED_LIMITS = ElevationLimits(86_400_000L, 0)
        private val TERMINAL = setOf(WaitingPhase.COMPLETED, WaitingPhase.CANCELLED)
        private const val ATTEMPTS = 16
        private const val CLEAR_TAG: Byte = 'C'.code.toByte()
        private const val REQUESTED_TAG: Byte = 'R'.code.toByte()
        private const val INTENT_ENTRY = "intent-clear-"
        private const val INTENT_MARKER = "intent-requested-"
        private val coordinators = ConcurrentHashMap<String, Coordinator>()
        /** Creates [dir] if needed so symlinks resolve; falls back to the normalized absolute path. */
        private fun canonical(dir: File): String = try {
            dir.mkdirs(); dir.canonicalPath
        } catch (_: IOException) {
            dir.absoluteFile.normalize().path
        }
    }
}

/** The hex SHA-256 of [requestId] that names its cancel-intent files, in every schema since 3. */
internal fun intentHash(requestId: String): String =
    MessageDigest.getInstance("SHA-256").digest(requestId.toByteArray()).joinToString("") { "%02x".format(it) }

/** A single checksummed file replaced by synced temp-file rename, then read back. */
internal class AtomicRecord(private val dir: File, name: String, private val schema: Int,
    private val directorySync: () -> DirectorySync = { PlatformDirectorySync }) {
    private val file = File(dir, "$name.rec")

    fun exists() = file.exists()
    fun delete(): Boolean = !file.exists() || file.delete()

    fun read(): ByteArray? = try {
        if (file.isFile) org.plainphone.device.storage.RecordCodec.decode(file.readBytes(), schema) else null
    } catch (_: IOException) {
        null
    }

    /**
     * [afterRename] runs between the rename and the readback. False unless the directory entry was
     * synced too: a rename is not durable until its directory is (PLA-143 R1). The rename may still be
     * visible after a false result, so callers decide from what reads back.
     */
    fun write(payload: ByteArray, afterRename: () -> Unit): Boolean = try {
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("no directory")
        val temp = File(dir, "${file.name}.tmp")
        java.io.FileOutputStream(temp).use { out ->
            out.write(org.plainphone.device.storage.RecordCodec.encode(schema, payload))
            out.fd.sync()
        }
        java.nio.file.Files.move(temp.toPath(), file.toPath(),
            java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
        val synced = try { directorySync().sync(dir); true } catch (_: IOException) { false }
        afterRename()
        synced && read()?.contentEquals(payload) == true
    } catch (_: IOException) {
        false
    }

    /**
     * [write], or if the file already holds [payload], a directory sync alone: content found on disk
     * may come from an earlier write whose rename was never synced, so it is not durable yet (PLA-188 R2).
     */
    fun ensure(payload: ByteArray): Boolean =
        if (read()?.contentEquals(payload) == true) syncDirectory() else write(payload) {}

    /** The file's raw bytes, or null if it does not exist. Throws if it exists but cannot be read. */
    fun snapshot(): ByteArray? = if (file.exists()) file.readBytes() else null

    /** Puts back exactly [raw] (null: no file), with the directory synced. True only if that is durable. */
    fun restore(raw: ByteArray?): Boolean = try {
        when {
            raw == null -> (!file.exists() || file.delete()) && !file.exists() && syncDirectory()
            file.exists() && file.readBytes().contentEquals(raw) -> syncDirectory()
            else -> {
                val temp = File(dir, "${file.name}.tmp")
                java.io.FileOutputStream(temp).use { out -> out.write(raw); out.fd.sync() }
                java.nio.file.Files.move(temp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                syncDirectory() && file.readBytes().contentEquals(raw)
            }
        }
    } catch (_: IOException) {
        false
    }

    private fun syncDirectory(): Boolean = try { directorySync().sync(dir); true } catch (_: IOException) { false }
}
