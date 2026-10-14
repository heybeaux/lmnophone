// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.approval

import org.junit.Assert.*
import org.junit.Test
import org.plainphone.core.*
import org.plainphone.policy.*
import org.plainphone.device.AppAdditionResult
import org.plainphone.device.authority.PinAccessResult

class PreviewPermissionAdditionTest {
    private val camera = PermissionName("android.permission.CAMERA")
    private val mic = PermissionName("android.permission.RECORD_AUDIO")
    private val identity = ApprovalBinding(PackageName("org.example.fixture"), setOf(CertDigest("a".repeat(64))))
    private val snapshot = AppPermissionSnapshot(identity, 1, listOf(
        RequestedPermissionGroup("camera", "Camera", listOf(camera)), RequestedPermissionGroup("mic", "Microphone", listOf(mic))))
    private val choices = mapOf("camera" to PermissionChoice.ALLOW, "mic" to PermissionChoice.DENY)
    private val events = mutableListOf<String>()
    private var auth: PinAccessResult = PinAccessResult.Open
    private var eligible = true
    private var active = true
    private var opened = true
    private var closed = true
    private var result = GrantWrite.DONE
    private var postRead = mapOf(camera to PermissionReadback.GRANTED, mic to PermissionReadback.DENIED)
    private var writeThrows = false
    private var refused = false
    private fun subject() = PreviewPermissionAddition(
        authenticate = { _, _ -> events += "pin"; auth },
        eligible = { events += "eligible"; eligible },
        snapshot = { snapshot },
        open = { events += "unhide"; opened },
        close = { events += "rehide"; closed },
        write = { _, write -> events += write.permission.value; if (writeThrows) error("port"); PermissionAttempt(result, refused) },
        read = { AppPermissionRead(snapshot, postRead) },
        save = { events += "save"; true },
        apply = { events += "apply"; true }, checkpoint = { true }, rollback = { true })
    private fun add() = subject().add(snapshot, choices, charArrayOf('1')) { active }

    @Test fun fineRefusalRestoresEarlierCoarseWriteBeforeRehiding() {
        val coarse = PermissionName("android.permission.ACCESS_COARSE_LOCATION")
        val fine = PermissionName("android.permission.ACCESS_FINE_LOCATION")
        val selected = AppPermissionSnapshot(identity, 1, listOf(
            RequestedPermissionGroup("location", "Location", listOf(coarse, fine))))
        val states = mutableMapOf(coarse to PolicyGrant.DEFAULT, fine to PolicyGrant.DEFAULT)
        val port = object : PermissionGrantPort {
            var blocked = false
            override fun signers(name: PackageName) = identity.signingCerts
            override fun policyState(name: PackageName, permission: PermissionName) = states[permission]
            override fun granted(name: PackageName, permission: PermissionName) = states[permission] == PolicyGrant.GRANTED
            override fun uninstallBlocked(name: PackageName) = blocked
            override fun setUninstallBlocked(name: PackageName, blocked: Boolean): Boolean { this.blocked = blocked; return true }
            override fun setPolicyState(name: PackageName, permission: PermissionName, state: PolicyGrant): Boolean {
                if (permission == fine && state != PolicyGrant.DEFAULT) return false
                states[permission] = state
                return true
            }
        }
        val attempts = PermissionGrantAttempts(port)
        val transaction = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { true }, { selected },
            { true }, { events += "rehide"; true }, attempts::write,
            { AppPermissionRead(selected, PermissionGrants(port).read(identity, selected.permissions)) },
            { error("must not approve") }, { error("must not apply") }, attempts::checkpoint, attempts::rollback)
        assertTrue(transaction.add(selected, mapOf("location" to PermissionChoice.ALLOW), charArrayOf('1')) { true }
            is AppAdditionResult.PermissionFailed)
        assertTrue("rehide" in events)
        assertEquals("failed approval must restore the accepted COARSE write", PolicyGrant.DEFAULT, states[coarse])
        assertEquals(PolicyGrant.DEFAULT, states[fine])
    }

    @Test fun cancellationAndSaveFailureRestoreBeforeReturningAndCloseEvenIfRestoreFails() {
        for (cancel in listOf(false, true)) for (restore in listOf(false, true)) {
            var active = true
            val events = mutableListOf<String>()
            val subject = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { true }, { snapshot },
                { events += "open"; true }, { events += "close"; true },
                { _, _ -> if (cancel) active = false; PermissionAttempt(GrantWrite.DONE) },
                { AppPermissionRead(snapshot, postRead) }, { false }, { error("no apply") }, { true },
                { events += "restore"; restore })
            val outcome = subject.add(snapshot, choices, charArrayOf('1')) { active }
            assertEquals(if (!restore) AppAdditionResult.CheckFailed else if (cancel) AppAdditionResult.Cancelled
                else AppAdditionResult.SaveFailed, outcome)
            assertTrue("restore" in events)
            assertEquals("close", events.last())
            if (!cancel) assertEquals(listOf("open", "close", "open", "restore", "close"), events)
        }
    }

    @Test fun locationDenialRevokesFineBeforeCoarseButAllowGrantsCoarseBeforeFine() {
        val coarse = PermissionName("android.permission.ACCESS_COARSE_LOCATION")
        val fine = PermissionName("android.permission.ACCESS_FINE_LOCATION")
        val selected = AppPermissionSnapshot(identity, 1, listOf(RequestedPermissionGroup("location", "Location", listOf(coarse, fine))))
        for (choice in PermissionChoice.entries) {
            val writes = mutableListOf<PermissionName>()
            val subject = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { true }, { selected },
                { true }, { true }, { _, item -> writes += item.permission; PermissionAttempt(GrantWrite.DONE) },
                { AppPermissionRead(selected, selected.permissions.associateWith {
                    if (choice == PermissionChoice.ALLOW) PermissionReadback.GRANTED else PermissionReadback.DENIED }) },
                { true }, { true }, { true }, { true })
            assertEquals(AppAdditionResult.Added, subject.add(selected, mapOf("location" to choice), charArrayOf('1')) { true })
            assertEquals(if (choice == PermissionChoice.DENY) listOf(fine, coarse) else listOf(coarse, fine), writes)
        }
    }

    @Test fun changingManifestDuringTheWindowClosesWithoutCommit() {
        var reads = 0
        val subject = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { true },
            { if (++reads == 1) snapshot else snapshot.copy(versionCode = 2) },
            { events += "unhide"; true }, { events += "rehide"; true },
            { _, _ -> error("stale version must not get a grant") },
            { AppPermissionRead(snapshot.copy(versionCode = 2), emptyMap()) },
            { error("must not commit") }, { error("must not apply") }, { true }, { true })
        assertTrue(subject.add(snapshot, choices, charArrayOf('1')) { true } is AppAdditionResult.PermissionFailed)
        assertTrue("rehide" in events)
    }
    @Test fun pauseDuringSlowSnapshotDoesNotWrite() {
        var reads = 0
        var writes = 0
        val subject = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { true },
            { reads++; if (reads > 1) active = false; snapshot },
            { true }, { true }, { _, _ -> writes++; PermissionAttempt(GrantWrite.DONE) },
            { AppPermissionRead(snapshot, emptyMap()) }, { error("must not commit") }, { error("must not apply") }, { true }, { true })
        assertEquals(AppAdditionResult.Cancelled, subject.add(snapshot, choices, charArrayOf('1')) { active })
        assertEquals(0, writes)
    }
    @Test fun roleChangeAfterSnapshotBlocksTheNextGrant() {
        var reads = 0
        var writes = 0
        val subject = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { eligible },
            { reads++; if (reads > 1) eligible = false; snapshot },
            { true }, { true }, { _, _ -> writes++; PermissionAttempt(GrantWrite.DONE) },
            { AppPermissionRead(snapshot, emptyMap()) }, { error("must not commit") }, { error("must not apply") }, { true }, { true })
        val result = subject.add(snapshot, choices, charArrayOf('1')) { true }
        assertTrue(result is AppAdditionResult.PermissionFailed)
        assertEquals(0, writes)
    }
    @Test fun freshPinThenVisibleThenDenyBeforeAllowThenRehideBeforeMembership() {
        assertEquals(AppAdditionResult.Added, add())
        assertTrue(events.indexOf("pin") < events.indexOf("unhide"))
        assertTrue(events.indexOf("unhide") < events.indexOf(mic.value))
        assertTrue(events.indexOf(mic.value) < events.indexOf(camera.value))
        assertTrue(events.indexOf(camera.value) < events.indexOf("rehide"))
        assertTrue(events.indexOf("rehide") < events.indexOf("save"))
        assertTrue(events.indexOf("save") < events.indexOf("apply"))
    }
    @Test fun wrongPinExcludedOrCancelledNeverUnhideOrWrite() {
        auth = PinAccessResult.Wrong(0); assertTrue(add() is AppAdditionResult.PinRejected)
        assertFalse("unhide" in events)
        events.clear(); auth = PinAccessResult.Open; eligible = false
        assertNotEquals(AppAdditionResult.Added, add()); assertFalse("unhide" in events)
        events.clear(); eligible = true; active = false
        assertEquals(AppAdditionResult.Cancelled, add()); assertFalse("unhide" in events)
    }
    @Test fun everyNonDoneWriteIsFailureAndStillClosesAndRereads() {
        GrantWrite.entries.filter { it != GrantWrite.DONE }.forEach { failure ->
            events.clear(); result = failure
            val outcome = add() as AppAdditionResult.PermissionFailed
            assertEquals(listOf("mic"), outcome.groups)
            assertEquals(postRead, outcome.readback!!.states)
            assertTrue("rehide" in events); assertFalse("save" in events)
            assertTrue(outcome.unavailable.isEmpty())
        }
    }
    @Test fun refusalIsOnlyUnavailableAfterActualAllowRefusalAndDeniedReadback() {
        val subject = PreviewPermissionAddition({ _, _ -> PinAccessResult.Open }, { true }, { snapshot },
            { true }, { true }, { _, write -> PermissionAttempt(if (write.permission == camera) GrantWrite.NOT_DONE else GrantWrite.DONE, write.permission == camera) },
            { AppPermissionRead(snapshot, mapOf(camera to PermissionReadback.DENIED, mic to PermissionReadback.DENIED)) }, { error("no save") }, { error("no apply") }, { true }, { true })
        val failure = subject.add(snapshot, choices, charArrayOf('1')) { true } as AppAdditionResult.PermissionFailed
        assertEquals(setOf("camera"), failure.unavailable)
    }
    @Test fun readbackMismatchOrChangedManifestDoesNotCommitAndInputsAreCleared() {
        postRead = mapOf(camera to PermissionReadback.UNKNOWN, mic to PermissionReadback.DENIED)
        assertTrue(add() is AppAdditionResult.PermissionFailed); assertFalse("save" in events)
        val pin = charArrayOf('1')
        val partial = choices - "camera"
        assertNotEquals(AppAdditionResult.Added, subject().add(snapshot, partial, pin) { true })
        assertTrue(pin.all { it == '\u0000' })
    }
    @Test fun failedOpenAndThrowingWriteStillCloseAndFailedCloseIsAttention() {
        opened = false; assertNotEquals(AppAdditionResult.Added, add()); assertTrue("rehide" in events)
        opened = true; writeThrows = true; events.clear()
        assertNotEquals(AppAdditionResult.Added, add()); assertTrue("rehide" in events)
        writeThrows = false; closed = false
        assertEquals(AppAdditionResult.CheckFailed, add()); assertFalse("save" in events)
    }
}
