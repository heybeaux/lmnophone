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
        apply = { events += "apply"; true })
    private fun add() = subject().add(snapshot, choices, charArrayOf('1')) { active }

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
            { AppPermissionRead(snapshot, mapOf(camera to PermissionReadback.DENIED, mic to PermissionReadback.DENIED)) }, { error("no save") }, { error("no apply") })
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
