// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.storage

import java.io.File
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.file.StandardOpenOption
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.PackageName
import org.plainphone.device.*
import org.plainphone.device.boot.*
import org.plainphone.policy.*

/** Reviewer-only regression probes. Assertions describe required behavior, not current behavior. */
class Pla232ReviewTest {
    @get:Rule val tmp = TemporaryFolder()
    private val pp = PackageName("org.plainphone")
    private val dialer = PackageName("com.android.dialer")
    private val messages = PackageName("com.example.messages")
    private val play = PackageName("com.android.vending")
    private fun plan(locked: Boolean = false) = PolicyEngine.decide(PolicyInput(
        model = "model", fingerprint = "build", plainphone = pp, messages = messages,
        installed = listOf(InstalledPackage(pp, false, 1), InstalledPackage(dialer, true, 1),
            InstalledPackage(messages, true, 1), InstalledPackage(play, true, 1)),
        identities = listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, FloorRole.SYSTEM_DIALER.source)),
        lockedBoot = locked,
    ))
    private fun port() = FakeDevicePolicyPort(mapOf(pp.value to visible(), dialer.value to visible(),
        messages.value to visible(), play.value to visible()))

    private fun denyDirectoryRead(dir: File) {
        assertTrue(dir.setReadable(false, false))
        try {
            FileChannel.open(dir.toPath(), StandardOpenOption.READ).use { }
            fail("Control: directory open must fail for this reproduction")
        } catch (_: IOException) { }
    }

    @Test fun recordMustNotAcknowledgeFailedDirectorySync() {
        val dir = tmp.newFolder("record")
        val file = RecordFile(dir, "review", 1, false) { denyDirectoryRead(dir) }
        try { assertFalse("rename readback is not a directory sync", file.save(byteArrayOf(1))) }
        finally { dir.setReadable(true, true) }
    }

    @Test fun journalMustNotAcknowledgeFailedDirectorySync() {
        val dir = tmp.newFolder("journal")
        val journal = FileJournalStore(dir, null, "model", "build") { denyDirectoryRead(dir) }
        try { assertFalse(journal.compareAndSet(SessionJournal.Damaged, SessionJournal.Empty)) }
        finally { dir.setReadable(true, true) }
    }

    @Test fun lockedBootMustRestorePlainphoneAfterLostList() {
        val p = port()
        val journal = FileJournalStore(tmp.newFolder("boot"), null, "model", "build")
        val outcome = BootReapply(journal, p, setOf(play), { plan(it) }, RestrictionFilter.TEST_BUILD).run(true)
        assertTrue(outcome.ready)
        assertTrue("first Home frame needs Plainphone membership", pp in p.lockTask)
    }

    @Test fun failedSessionRevokeMustStillRepairEmergencyFloor() {
        val p = port().apply { hidesStick = false; state[dialer] = hiddenApp(); lockTask = setOf(pp, play) }
        val journal = FileJournalStore(tmp.newFolder("revoke"), null, "model", "build")
        val outcome = BootReapply(journal, p, setOf(play), { plan(it) }, RestrictionFilter.TEST_BUILD).run(true)
        assertFalse(outcome.ready)
        assertFalse("failed Play hide cannot prevent an emergency repair", p.hidden(dialer.value))
    }

    @Test fun readbackMustCheckExpectedUnhiddenPackages() {
        val p = port().apply { state[messages] = hiddenApp() }
        val wanted = plan()
        val inconsistent = wanted.copy(unhide = wanted.unhide - messages, neverHide = wanted.neverHide - messages,
            steps = listOf(ApplyStep(ApplyStage.HIDES)))
        assertFalse("expected still requires Messages visible", ReadbackPolicyApplier(p).apply(inconsistent).complete)
    }

    @Test fun unreadableFlipMustRetryTheReadOnce() {
        val p = port().apply { state[dialer] = hiddenApp() }
        var reads = 0
        val recovering = object : DevicePolicyPort by p {
            override fun flushComponentEnabled(): Boolean? = if (++reads == 1) null else p.flushComponentEnabled()
        }
        val result = ReadbackPolicyApplier(recovering).apply(plan())
        assertTrue("transient unreadable state must get the specified retry", result.flipFailures.isEmpty())
        assertEquals(1, p.flips.size)
    }
}
