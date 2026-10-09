// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.CertDigest
import org.plainphone.core.Mode
import org.plainphone.core.NeverHideList
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
import org.plainphone.policy.ApprovalBinding
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationCountdown
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationGrant
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationPersonBinding
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.ElevationTarget
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.PermissionChoice
import org.plainphone.policy.PermissionGrantCapability
import org.plainphone.policy.PermissionManifest
import org.plainphone.policy.PermissionName
import org.plainphone.policy.PermissionSelection
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RuntimePermission
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.SessionJournal
import org.plainphone.policy.SessionPhase
import org.plainphone.policy.StagedUpdateBinding
import org.plainphone.policy.WaitingState

class ElevationApprovalStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private val dir: File by lazy { tmp.newFolder("authority") }
    private val self = TrustedPersonId("self")
    private val play = PackageName("com.android.vending")
    private val settings = PackageName("test.settings")
    private val inventory = BuildInventory("model", "build", 1, NeverHideList(setOf(play)), emptySet(), setOf(settings))
    private var faults = StorageFaults { _, _ -> }
    private fun open(matching: BuildInventory? = inventory) = JointAuthorityStore(dir, { point, d -> faults.at(point, d) }, matching)

    private val a = ApprovalBinding(PackageName("test.a"), setOf(CertDigest("cd".repeat(32)), CertDigest("ab".repeat(32))))
    private val b = ApprovalBinding(PackageName("test.b"), setOf(CertDigest("ef".repeat(32))))
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val boot = BootTime("boot-1", 1_000)
    private val person = ElevationPersonBinding(self, 0, 0)

    private fun credentials(revision: Long = 0) = CredentialState(Mode.SELF_MANAGED,
        mapOf(self to PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000),
            SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000), FailureCounter())), revision = revision)

    private fun record(id: String, identity: ApprovalBinding) = AppApprovalRecord(id, PermissionSelection(
        PermissionManifest(identity, 7, "build", listOf(
            RuntimePermission(camera, "Camera", listOf("Taking photos", "Video calls"), PermissionGrantCapability.SUPPORTED),
            RuntimePermission(mic, "Microphone", emptyList(), PermissionGrantCapability.UNSUPPORTED))),
        mapOf(mic to PermissionChoice.DENY, camera to PermissionChoice.ALLOW)))
    private fun receipt(id: String) = AppApprovalReceipt(id, self, mapOf(self to 0L))

    private fun grantRecord(revision: Long = 1, target: ElevationTarget = ElevationTarget.Play, open: Boolean = false,
        approved: Set<ApprovalBinding> = emptySet(), journal: SessionJournal = SessionJournal.Empty): ElevationRecord {
        val duration = if (target == ElevationTarget.Play || target is ElevationTarget.Update) 600_000L else 300_000L
        val grant = ElevationGrant.restore("grant-1", target, boot, BootTime(boot.bootId, boot.elapsedMillis + duration), open, approved)!!
        val ledger = ElevationLedger(listOf(ElevationDebit("old", RunningTime("e", 3)), ElevationDebit("grant-1", RunningTime("e", 9))))
        return ElevationRecord(revision, ElevationState(ledger, grant = grant), journal, person)
    }
    private fun idle() = ElevationRecord(0, ElevationState(ElevationLedger(listOf(ElevationDebit("old", RunningTime("e", 3))))))

    private fun preparing(revision: Long = 1) = AppApprovalState(revision, pending = AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.PREPARING))
    private fun committed(from: AppApprovalState) = AppApprovalState(from.revision + 1, mapOf(a.packageName to record("r-a", a)),
        AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.COMMITTED), listOf(receipt("r-a")))
    private fun consumed(from: ElevationRecord) = ElevationRecord(from.revision + 1, ElevationState(from.state.ledger,
        grant = ElevationGrant.restore(from.state.grant!!.id, from.state.grant!!.target, from.state.grant!!.startedAt,
            from.state.grant!!.deadline, false, from.state.grant!!.approved + a)), SessionJournal.Empty, from.person)

    /** A store holding a PREPARING approval and a live closed Play grant, as the controller leaves it. */
    private fun prepared(grant: ElevationRecord = grantRecord()): JointAuthorityStore {
        val store = open()
        assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), idle()))
        assertTrue(store.elevationStore.compareAndSet(idle(), grant, null))
        assertTrue(store.approvals.compareAndSet(AppApprovalState(), preparing(), store.credentials.read()))
        return store
    }
    private fun commit(store: JointAuthorityStore, check: () -> Boolean = { true },
        approval: AppApprovalState = preparing(), elevation: ElevationRecord = grantRecord(),
        replacement: AppApprovalState = committed(approval), replacementElevation: ElevationRecord = consumed(elevation),
        creds: CredentialState = credentials()) =
        store.approvals.commitElevationApproval(approval, replacement, elevation, replacementElevation, creds, check)

    private fun assertOriginalPair(store: JointAuthorityStore) {
        assertEquals(AppApprovalPhase.PREPARING, store.approvals.read()!!.pending!!.phase)
        assertEquals(emptySet<ApprovalBinding>(), store.elevationStore.read()!!.state.grant!!.approved)
    }
    private fun assertReplacementPair(store: JointAuthorityStore) {
        val approvals = store.approvals.read()!!
        assertEquals(AppApprovalPhase.COMMITTED, approvals.pending!!.phase)
        assertEquals(listOf("r-a"), approvals.receipts.map { it.id })
        assertEquals(setOf(a), store.elevationStore.read()!!.state.grant!!.approved)
    }

    // --- codec ---

    @Test fun liveElevationDataRoundTripsEveryField() {
        val countdown = ElevationRecord(4, ElevationState(ElevationLedger(listOf(ElevationDebit("c", RunningTime("e", 1)))),
            countdown = ElevationCountdown.restore("c", ElevationTarget.Update(StagedUpdateBinding("0f".repeat(32), 12)), boot, 600_000)), person = person)
        val active = grantRecord(open = true, approved = setOf(b), journal = SessionJournal.restore("grant-1", WindowKind.PLAY,
            setOf(play), BootTime(boot.bootId, boot.elapsedMillis + 600_000), SessionPhase.OPEN, inventory))
        val approve = grantRecord(target = ElevationTarget.Approve(a))
        for (r in listOf(countdown, active, approve)) {
            val bytes = checkNotNull(AuthorityCodec.encodeElevation(r))
            val back = checkNotNull(AuthorityCodec.decodeElevation(bytes, inventory))
            assertArrayEquals(bytes, AuthorityCodec.encodeElevation(back))
            assertEquals(r.person, back.person)
            assertEquals(r.journal, back.journal)
            assertEquals(r.state.grant?.target, back.state.grant?.target)
            assertEquals(r.state.grant?.approved, back.state.grant?.approved)
            assertEquals(r.state.countdown?.target, back.state.countdown?.target)
        }
    }

    @Test fun settingsJournalWithoutMatchingInventoryIsDamagedNotEmpty() {
        val active = grantRecord(target = ElevationTarget.Settings, open = true, journal = SessionJournal.restore("grant-1",
            WindowKind.SETTINGS, setOf(settings), BootTime(boot.bootId, boot.elapsedMillis + 300_000), SessionPhase.OPENING, inventory))
        val bytes = AuthorityCodec.encodeElevation(active)!!
        assertNull(AuthorityCodec.decodeElevation(bytes, null))
    }

    @Test fun grantWithAlteredDeadlineDecodesAsNull() {
        val bytes = AuthorityCodec.encodeElevation(grantRecord())!!
        // Push the stored deadline one millisecond later; the grant's fixed duration no longer holds.
        val tampered = bytes.let { original ->
            val marker = java.nio.ByteBuffer.allocate(8).putLong(boot.elapsedMillis + 600_000).array()
            val at = (0..original.size - 8).last { i -> (0 until 8).all { original[i + it] == marker[it] } }
            original.copyOf().also { it[at + 7] = (it[at + 7] + 1).toByte() }
        }
        checkNotNull(AuthorityCodec.decodeElevation(bytes, inventory))
        assertNull(AuthorityCodec.decodeElevation(tampered, inventory))
    }

    @Test fun approvalStateRoundTripsEveryFieldCanonically() {
        val withdrawing = AppApprovalState(9, mapOf(b.packageName to record("r-b", b), a.packageName to record("r-a", a)),
            AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.WITHDRAWING,
                AppApprovalReceipt("w-a", self, mapOf(self to 0L), AppApprovalReceiptKind.WITHDRAWN, "r-a")),
            listOf(receipt("r-b"), receipt("r-a")),
            mapOf(PackageName("test.gone") to ApprovalBinding(PackageName("test.gone"), setOf(CertDigest("01".repeat(32))))))
        val bytes = AuthorityCodec.encodeApprovals(withdrawing)!!
        val back = AuthorityCodec.decodeApprovals(bytes)!!
        assertArrayEquals(bytes, AuthorityCodec.encodeApprovals(back))
        assertEquals(9L, back.revision)
        assertEquals(setOf(a.packageName, b.packageName), back.apps.keys)
        assertEquals(listOf(camera, mic), back.apps.getValue(a.packageName).permissions.manifest.permissions.map { it.name })
        assertEquals(PermissionChoice.DENY, back.apps.getValue(a.packageName).permissions.choices[mic])
        assertEquals(listOf("Taking photos", "Video calls"), back.apps.getValue(a.packageName).permissions.manifest.permissions[0].features)
        assertEquals(a, back.apps.getValue(a.packageName).identity)
        assertEquals(AppApprovalPhase.WITHDRAWING, back.pending!!.phase)
        assertEquals("w-a", back.pending!!.withdrawalReceipt!!.id)
        assertEquals(listOf("r-b", "r-a"), back.receipts.map { it.id })
        assertEquals(setOf(PackageName("test.gone")), back.removedApps.keys)
        // Map iteration order is not stored; the same state built in another order encodes identically.
        val reordered = AppApprovalState(9, mapOf(a.packageName to record("r-a", a), b.packageName to record("r-b", b)),
            withdrawing.pending, withdrawing.receipts, withdrawing.removedApps)
        assertArrayEquals(bytes, AuthorityCodec.encodeApprovals(reordered))
    }

    @Test fun malformedApprovalBytesDecodeAsNull() {
        val bytes = AuthorityCodec.encodeApprovals(committed(preparing()))!!
        assertNull(AuthorityCodec.decodeApprovals(bytes.copyOf(bytes.size - 1)))
        assertNull(AuthorityCodec.decodeApprovals(bytes + 0))
        assertNull(AuthorityCodec.decodeApprovals(ByteArray(0)))
    }

    // --- store ---

    @Test fun approvalViewSharesTheExactElevationStoreInstance() {
        val store = open()
        assertSame(store.elevationStore, store.approvals.elevationStore)
    }

    @Test fun enrollmentStartsAnEmptyApprovalStateAndMissingRecordReadsNull() {
        val store = open()
        assertNull(store.approvals.read())
        assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), idle()))
        assertEquals(0L, open().approvals.read()!!.revision)
    }

    @Test fun approvalCompareAndSetChecksCredentialsAndPreservesOtherSections() {
        val store = prepared()
        val others = listOf(Section.CREDENTIALS, Section.WAITING, Section.ELEVATION, Section.RECEIPTS).map { store.sectionBytesForTest(it)!! }
        val current = store.approvals.read()!!
        assertFalse(store.approvals.compareAndSet(current, committed(current), credentials(5)))
        assertFalse(store.approvals.compareAndSet(AppApprovalState(), committed(current), null))
        assertTrue(store.approvals.compareAndSet(current, committed(current), null))
        assertEquals(AppApprovalPhase.COMMITTED, open().approvals.read()!!.pending!!.phase)
        listOf(Section.CREDENTIALS, Section.WAITING, Section.ELEVATION, Section.RECEIPTS).forEachIndexed { i, s ->
            assertArrayEquals(others[i], store.sectionBytesForTest(s))
        }
    }

    @Test fun combinedCommitWritesBothRecordsInOneTransactionAndCallsTheCheckOnce() {
        val store = prepared()
        var calls = 0
        assertTrue(commit(store, { calls++; true }))
        assertEquals(1, calls)
        assertReplacementPair(open())
    }

    @Test fun falseFinalCheckLeavesBothRecordsUnchanged() {
        val store = prepared()
        assertFalse(commit(store, { false }))
        assertOriginalPair(open())
    }

    @Test fun staleApprovalElevationOrCredentialsFailBeforeTheCheck() {
        val store = prepared()
        var calls = 0
        val check = { calls++; true }
        assertFalse(commit(store, check, approval = preparing(revision = 2)))
        assertFalse(commit(store, check, elevation = grantRecord(revision = 2)))
        assertFalse(commit(store, check, creds = credentials(revision = 1)))
        assertEquals(0, calls)
        assertOriginalPair(open())
    }

    @Test fun finalCheckReadsTheCurrentRecordButCannotWrite() {
        val store = prepared()
        var seen: ElevationRecord? = null
        var innerWrite: Boolean? = null
        assertTrue(commit(store, {
            seen = store.elevationStore.read()
            innerWrite = store.elevationStore.compareAndSet(grantRecord(), grantRecord(revision = 2), null)
            true
        }))
        assertEquals(1L, seen!!.revision)
        assertEquals(false, innerWrite)
        assertEquals(2L, open().elevationStore.read()!!.revision)
    }

    @Test fun replacementMayNotMoveTheDeadlineOrDropADebitOrSkipARevision() {
        val store = prepared()
        val before = grantRecord()
        val g = before.state.grant!!
        val moved = ElevationRecord(2, ElevationState(before.state.ledger, grant = ElevationGrant.restore(g.id, g.target,
            BootTime(boot.bootId, boot.elapsedMillis + 1), BootTime(boot.bootId, boot.elapsedMillis + 600_001), false, setOf(a))), person = person)
        val dropped = ElevationRecord(2, ElevationState(ElevationLedger(listOf(ElevationDebit("grant-1", RunningTime("e", 9)))),
            grant = ElevationGrant.restore(g.id, g.target, g.startedAt, g.deadline, false, setOf(a))), person = person)
        val skipped = ElevationRecord(3, consumed(before).state, person = person)
        var calls = 0
        for (bad in listOf(moved, dropped, skipped)) assertFalse(commit(store, { calls++; true }, replacementElevation = bad))
        assertFalse(commit(store, { calls++; true }, replacement = AppApprovalState(5, preparing().apps, preparing().pending)))
        assertEquals(0, calls)
        assertOriginalPair(open())
    }

    // --- exact consumption shape (PLA-127 P2) ---

    private fun refusedWithoutCheck(store: JointAuthorityStore, approval: AppApprovalState = preparing(),
        elevation: ElevationRecord = grantRecord(), replacement: AppApprovalState = committed(approval),
        replacementElevation: ElevationRecord) {
        var calls = 0
        assertFalse(commit(store, { calls++; true }, approval, elevation, replacement, replacementElevation))
        assertEquals(0, calls)
    }

    @Test fun playCommitMustAddExactlyTheApprovedPackage() {
        val store = prepared()
        val before = grantRecord()
        val g = before.state.grant!!
        fun withApproved(set: Set<ApprovalBinding>) = ElevationRecord(2, ElevationState(before.state.ledger,
            grant = ElevationGrant.restore(g.id, g.target, g.startedAt, g.deadline, false, set)), person = person)
        // Only the revision advanced: COMMITTED would land while the grant shows nothing used.
        refusedWithoutCheck(store, replacementElevation = withApproved(emptySet()))
        refusedWithoutCheck(store, replacementElevation = withApproved(setOf(b)))
        refusedWithoutCheck(store, replacementElevation = withApproved(setOf(a, b)))
        refusedWithoutCheck(store, replacementElevation = ElevationRecord(2, ElevationState(before.state.ledger)))
        refusedWithoutCheck(store, replacementElevation = ElevationRecord(2, withApproved(setOf(a)).state, person = ElevationPersonBinding(self, 1, 0)))
        assertOriginalPair(open())
        assertTrue(commit(store))
        assertReplacementPair(open())
    }

    @Test fun playGrantThatAlreadyApprovedThePackageCannotApproveItAgain() {
        val already = grantRecord(approved = setOf(a))
        val store = prepared(already)
        refusedWithoutCheck(store, elevation = already, replacementElevation = ElevationRecord(2, already.state, person = person))
        assertEquals(setOf(a), open().elevationStore.read()!!.state.grant!!.approved)
        assertEquals(AppApprovalPhase.PREPARING, open().approvals.read()!!.pending!!.phase)
    }

    @Test fun approveCommitMustRemoveTheMatchingGrant() {
        val exact = grantRecord(target = ElevationTarget.Approve(a))
        val store = prepared(exact)
        refusedWithoutCheck(store, elevation = exact, replacementElevation = ElevationRecord(2, exact.state, person = person))
        assertTrue(commit(store, elevation = exact, replacementElevation = ElevationRecord(2, ElevationState(exact.state.ledger))))
        val after = open()
        assertNull(after.elevationStore.read()!!.state.grant)
        assertNull(after.elevationStore.read()!!.person)
        assertEquals(exact.state.ledger.debits, after.elevationStore.read()!!.state.ledger.debits)
        assertEquals(AppApprovalPhase.COMMITTED, after.approvals.read()!!.pending!!.phase)
    }

    @Test fun grantForAnotherAppOrAnotherTargetCannotCommitThisApproval() {
        for (target in listOf(ElevationTarget.Approve(b), ElevationTarget.Settings,
            ElevationTarget.Update(StagedUpdateBinding("0f".repeat(32), 12)))) {
            dir.deleteRecursively()
            val other = grantRecord(target = target)
            val store = prepared(other)
            refusedWithoutCheck(store, elevation = other, replacementElevation = ElevationRecord(2, ElevationState(other.state.ledger)))
            assertEquals(target, open().elevationStore.read()!!.state.grant!!.target)
        }
    }

    @Test fun unrelatedApprovalFieldsMustBeKept() {
        val base = AppApprovalState(1, mapOf(b.packageName to record("r-b", b)),
            AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.PREPARING), listOf(receipt("r-b")),
            mapOf(PackageName("test.gone") to ApprovalBinding(PackageName("test.gone"), setOf(CertDigest("01".repeat(32))))))
        val store = open()
        assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), idle()))
        assertTrue(store.elevationStore.compareAndSet(idle(), grantRecord(), null))
        assertTrue(store.approvals.compareAndSet(AppApprovalState(), base, null))
        val op = AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.COMMITTED)
        val good = AppApprovalState(2, base.apps + (a.packageName to record("r-a", a)), op, base.receipts + receipt("r-a"), base.removedApps)
        val droppedApp = AppApprovalState(2, mapOf(a.packageName to record("r-a", a)), op, base.receipts + receipt("r-a"), base.removedApps)
        val droppedReceipt = AppApprovalState(2, good.apps, op, listOf(receipt("r-a")), base.removedApps)
        val droppedRemoval = AppApprovalState(2, good.apps, op, good.receipts, emptyMap())
        val extraReceipt = AppApprovalState(2, good.apps, op, good.receipts + receipt("r-x"), base.removedApps)
        for (bad in listOf(droppedApp, droppedReceipt, droppedRemoval, extraReceipt)) {
            refusedWithoutCheck(store, approval = base, replacement = bad, replacementElevation = consumed(grantRecord()))
        }
        assertTrue(commit(store, approval = base, replacement = good))
        assertEquals(setOf(a.packageName, b.packageName), open().approvals.read()!!.apps.keys)
    }

    // --- decode limits (PLA-127 P2) ---

    @Test fun commitPastTheReceiptLimitIsRefusedWithBothOriginalsIntact() {
        val full = AppApprovalState(1, pending = AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.PREPARING),
            receipts = (1..4096).map { receipt("old-$it") })
        val store = open()
        assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), idle()))
        assertTrue(store.elevationStore.compareAndSet(idle(), grantRecord(), null))
        assertTrue(store.approvals.compareAndSet(AppApprovalState(), full, null))
        val next = AppApprovalState(2, mapOf(a.packageName to record("r-a", a)),
            AppApprovalOperation(record("r-a", a), receipt("r-a"), AppApprovalPhase.COMMITTED), full.receipts + receipt("r-a"))
        assertFalse(commit(store, approval = full, replacement = next))
        val reopened = open()
        assertEquals(4096, reopened.approvals.read()!!.receipts.size)
        assertEquals(AppApprovalPhase.PREPARING, reopened.approvals.read()!!.pending!!.phase)
        assertEquals(emptySet<ApprovalBinding>(), reopened.elevationStore.read()!!.state.grant!!.approved)
    }

    @Test fun everyNewCollectionPastTheDecodeLimitIsRefusedNotStoredUnreadable() {
        val store = open()
        assertTrue(store.initialize(credentials(), WaitingState(storageGeneration = "live"), idle()))
        val many = (0..4096).map { ApprovalBinding(PackageName("test.p$it"), setOf(CertDigest("ab".repeat(32)))) }
        val manyCerts = ApprovalBinding(a.packageName, (0..4096).map { CertDigest("%064x".format(it)) }.toSet())
        val bigManifest = AppApprovalRecord("r-big", PermissionSelection(PermissionManifest(a, 1, "build",
            (0..4096).map { RuntimePermission(PermissionName("p.n$it"), "L", emptyList(), PermissionGrantCapability.SUPPORTED) }),
            (0..4096).associate { PermissionName("p.n$it") to PermissionChoice.DENY }))
        val manyFeatures = AppApprovalRecord("r-feat", PermissionSelection(PermissionManifest(a, 1, "build",
            listOf(RuntimePermission(camera, "Camera", (0..4096).map { "f$it" }, PermissionGrantCapability.SUPPORTED))),
            mapOf(camera to PermissionChoice.ALLOW)))
        val approvals = listOf(
            AppApprovalState(1, many.associate { it.packageName to record("r-${it.packageName}", it) }),
            AppApprovalState(1, removedApps = many.associateBy { it.packageName }),
            AppApprovalState(1, mapOf(a.packageName to record("r-a", manyCerts))),
            AppApprovalState(1, mapOf(a.packageName to bigManifest)),
            AppApprovalState(1, mapOf(a.packageName to manyFeatures)))
        for (bad in approvals) assertFalse(store.approvals.compareAndSet(AppApprovalState(), bad, null))
        val g = grantRecord().state.grant!!
        val wide = ElevationRecord(1, ElevationState(grantRecord().state.ledger,
            grant = ElevationGrant.restore(g.id, g.target, g.startedAt, g.deadline, false, many.toSet())), person = person)
        assertFalse(store.elevationStore.compareAndSet(idle(), wide, null))
        val reopened = open()
        assertEquals(0L, reopened.approvals.read()!!.revision)
        assertEquals(0L, reopened.elevationStore.read()!!.revision)
    }

    @Test fun crashAtEveryWritePointExposesTheOriginalOrTheCompleteReplacementPair() {
        for (point in WritePoint.entries) {
            dir.deleteRecursively()
            val store = prepared()
            faults = StorageFaults { at, _ -> if (at == point) throw SimulatedCrash() }
            try { commit(store) } catch (_: SimulatedCrash) {}
            faults = StorageFaults { _, _ -> }
            val reopened = open()
            if (point == WritePoint.BEFORE_COPY_A) assertOriginalPair(reopened)
            // Never a COMMITTED approval without the consumed grant, or the reverse.
            else if (reopened.approvals.read()!!.pending!!.phase == AppApprovalPhase.PREPARING) assertOriginalPair(reopened)
            else assertReplacementPair(reopened)
        }
    }

    @Test fun readbackFailureReportsFalseAndKeepsTheOriginalPair() {
        val store = prepared()
        faults = StorageFaults { at, d -> if (at == WritePoint.AFTER_COPY_A) File(d, "authority-a.rec").writeBytes(ByteArray(3)) }
        assertFalse(commit(store))
        faults = StorageFaults { _, _ -> }
        assertOriginalPair(open())
    }

    @Test fun throwingFinalCheckWritesNothing() {
        val store = prepared()
        try { commit(store, { throw SimulatedCrash() }) } catch (_: SimulatedCrash) {}
        assertOriginalPair(open())
        assertTrue(commit(store))
    }
}
