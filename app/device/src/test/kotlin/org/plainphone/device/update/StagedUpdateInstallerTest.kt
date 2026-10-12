// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.CertDigest
import org.plainphone.core.PackageName
import org.plainphone.policy.ApprovalBinding
import java.io.File
import java.security.MessageDigest

class StagedUpdateInstallerTest {
    @get:Rule val tmp = TemporaryFolder()

    private val self = PackageName("org.plainphone")
    private val key = CertDigest("ab".repeat(32))
    private val pinned = ApprovalBinding(self, setOf(key))
    private val port = FakeInstallerPort()
    private val staging by lazy { UpdateStaging(File(tmp.root, "update-staging")) }
    private val commitDir by lazy { File(tmp.root, "update-commits") }
    private val commits by lazy { CommitMemory(commitDir) }
    private var inspections = 0
    private var duringInspect: () -> Unit = {}
    private var overrideFacts: String? = null
    /** Test APKs are text "package;signer,signer;version"; "badsig" stands for a failed signature check. */
    private val inspector = ApkInspector { apk ->
        inspections++
        val text = overrideFacts ?: apk.readText()
        duringInspect()
        if (text.startsWith("badsig")) null else text.split(";").let { (p, s, v) ->
            ApkFacts(PackageName(p), s.split(",").map { CertDigest(it) }.toSet(), v.toLong())
        }
    }
    private var selfChecks = 0
    private var selfCheckResult: () -> Boolean = { true }
    private val installer by lazy {
        StagedUpdateInstaller(port, inspector, staging, commits, pinned) { selfChecks++; selfCheckResult() }
    }

    private fun apk(pkg: String = self.value, signers: String = key.value, version: Long = 2) = "$pkg;$signers;$version"
    private fun stage(text: String): File {
        val part = staging.receive() ?: error("receive failed")
        part.writeText(text)
        assertTrue(staging.seal(part))
        return staging.staged() ?: error("not staged")
    }
    private fun sha(text: String) = MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    private fun prepared(text: String = apk()): Int {
        stage(text)
        return installer.prepare() ?: error("prepare failed")
    }

    @Test fun preparedSessionHoldsExactlyTheStagedBytesForTheSelfPackage() {
        val id = prepared()
        val session = port.sessions.getValue(id)
        assertEquals(self.value, session.appPackageName)
        assertEquals(listOf("base.apk"), session.entries.keys.toList())
        assertEquals(apk(), String(session.entries.getValue("base.apk")))
    }

    @Test fun finalFactsHashTheSessionAndTakeIdentityAndVersionFromTheSameStagedFile() {
        val id = prepared()
        port.installed = 1
        val facts = installer.finalFacts()!!
        assertEquals(id, facts.sessionId)
        assertEquals(sha(apk()), facts.staged.binding.sha256)
        assertEquals(2L, facts.staged.binding.versionCode)
        assertEquals(pinned, facts.staged.identity)
        assertTrue(facts.staged.signatureValid)
        assertEquals(1L, facts.installedVersionCode)
        // Re-reading is allowed and gives the same facts, without a new session.
        assertEquals(facts, installer.finalFacts())
        assertEquals(1, port.calls.count { it == "create" })
    }

    @Test fun finalFactsWithoutAPreparedSessionAreNullAndCreateNothing() {
        stage(apk())
        assertNull(installer.finalFacts())
        assertFalse(port.calls.any { it == "create" || it.startsWith("write") })
    }

    @Test fun stagedFileSubstitutedAfterPrepareIsRefused() {
        prepared()
        stage(apk(version = 3))
        assertNull(installer.finalFacts())
    }

    @Test fun sessionBytesSubstitutedAfterPrepareAreRefused() {
        val id = prepared()
        port.sessions.getValue(id).entries["base.apk"] = apk(version = 3).toByteArray()
        assertNull(installer.finalFacts())
    }

    @Test fun replacedOrMissingSessionIsRefusedAndNeverRecreated() {
        val id = prepared()
        val bytes = port.sessions.getValue(id).entries.getValue("base.apk")
        port.sessions.remove(id)
        val other = port.leftover(self.value)
        port.sessions.getValue(other).entries["base.apk"] = bytes
        assertNull(installer.finalFacts())
        assertEquals(1, port.calls.count { it == "create" })
    }

    @Test fun extraSessionEntryIsRefused() {
        val id = prepared()
        port.sessions.getValue(id).entries["split.apk"] = byteArrayOf(9)
        assertNull(installer.finalFacts())
    }

    @Test fun sessionForAnotherPackageNameIsRefused() {
        val id = prepared()
        val old = port.sessions.remove(id)!!
        port.sessions[id] = FakeInstallerPort.Session(id, "org.other").also { it.entries.putAll(old.entries) }
        assertNull(installer.finalFacts())
    }

    @Test fun alreadyCommittedSessionIsRefused() {
        val id = prepared()
        port.sessions.getValue(id).committed = true
        assertNull(installer.finalFacts())
    }

    @Test fun stagedFileChangedWhileItsFactsAreReadIsRefused() {
        prepared()
        duringInspect = { staging.staged()!!.let { it.setWritable(true, true); it.writeText(apk(version = 9)); it.setWritable(false, false) } }
        assertNull(installer.finalFacts())
    }

    @Test fun wrongPackageSignerOrSignatureIsRefusedBeforeASessionExists() {
        for (bad in listOf(apk(pkg = "org.other"), apk(signers = "cd".repeat(32)),
                apk(signers = key.value + "," + "cd".repeat(32)), "badsig;x;2")) {
            staging.clear()
            stage(bad)
            assertNull(bad, installer.prepare())
        }
        assertFalse(port.calls.any { it == "create" })
    }

    @Test fun wrongPackageSignerOrSignatureFoundAtTheFinalReadIsRefused() {
        // The final read inspects the file again rather than trusting what prepare() saw.
        for (bad in listOf(apk(pkg = "org.other"), apk(signers = "cd".repeat(32)), "badsig;x;2")) {
            staging.clear()
            val port = FakeInstallerPort()
            val installer = StagedUpdateInstaller(port, inspector, staging, commits, pinned) { true }
            stage(apk())
            installer.prepare()!!
            overrideFacts = bad
            assertNull(bad, installer.finalFacts())
            overrideFacts = null
            assertNotNull(installer.finalFacts())
        }
    }

    @Test fun versionIsTheStagedFilesOwnVersion() {
        prepared(apk(version = 41))
        assertEquals(41L, installer.finalFacts()!!.staged.binding.versionCode)
    }

    @Test fun noSessionWriteAfterTheFinalRead() {
        prepared()
        assertNotNull(installer.finalFacts())
        stage(apk(version = 3))
        assertNull(installer.prepare())
        val lastWrite = port.calls.indexOfLast { it.startsWith("write") }
        val firstRead = port.calls.indexOfFirst { it.startsWith("read") }
        assertTrue(port.calls.toString(), lastWrite in 0 until firstRead)
    }

    @Test fun secondPrepareNeverWritesIntoOrReplacesTheSession() {
        prepared()
        stage(apk(version = 3))
        assertNull(installer.prepare())
        assertEquals(1, port.calls.count { it.startsWith("write") })
        assertEquals(1, port.calls.count { it == "create" })
    }

    @Test fun delayedStagingIsNeverPickedUp() {
        // Nothing staged yet: no session.
        assertNull(installer.prepare())
        assertFalse(port.calls.any { it == "create" })
        // A download that finishes after the final read replaces the staged file; the session stays, the facts fail.
        prepared()
        assertNotNull(installer.finalFacts())
        stage(apk(version = 5))
        assertNull(installer.finalFacts())
        assertEquals(1, port.calls.count { it.startsWith("write") })
    }

    @Test fun commitOnceAfterPassingFinalFacts() {
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        assertEquals(listOf("commit $id"), port.calls.filter { it.startsWith("commit") })
        try { installer.commit(id); fail("second commit") } catch (_: IllegalStateException) {}
        assertEquals(1, port.calls.count { it.startsWith("commit") })
    }

    @Test fun commitRefusedWithoutPassingFinalFactsOrForAnotherSession() {
        val id = prepared()
        try { installer.commit(id); fail("commit before final facts") } catch (_: IllegalStateException) {}
        installer.finalFacts()!!
        try { installer.commit(id + 1); fail("other session") } catch (_: IllegalStateException) {}
        port.sessions.getValue(id).entries["base.apk"] = byteArrayOf(0)
        assertNull(installer.finalFacts())
        try { installer.commit(id); fail("commit after failed final facts") } catch (_: IllegalStateException) {}
        assertFalse(port.calls.any { it.startsWith("commit") })
    }

    @Test fun thrownCommitIsNeverRetriedOrAbandoned() {
        val id = prepared()
        installer.finalFacts()!!
        port.commitThrows = true
        try { installer.commit(id); fail("should throw") } catch (_: SecurityException) {}
        port.commitThrows = false
        try { installer.commit(id); fail("retry") } catch (_: IllegalStateException) {}
        assertEquals(1, port.calls.count { it.startsWith("commit") })
        assertTrue(installer.abandonOwnSessions())
        assertFalse(port.calls.contains("abandon $id"))
    }

    @Test fun startupAbandonsOnlyUncommittedSessions() {
        val open = port.leftover(self.value)
        val noName = port.leftover(null)
        val committed = port.leftover(self.value, committed = true)
        stage(apk())
        assertTrue(installer.abandonOwnSessions())
        assertEquals(setOf(committed), port.sessions.keys)
        assertTrue(port.calls.containsAll(listOf("abandon $open", "abandon $noName")))
        assertFalse(port.calls.contains("abandon $committed"))
        assertNull("staging is cleared at startup", staging.staged())
    }

    @Test fun startupKeepsASessionThisProcessCommittedBeforeIsCommittedTurnsTrue() {
        port.commitShowsAtOnce = false
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        assertFalse(port.sessions.getValue(id).committed)
        assertTrue(installer.abandonOwnSessions())
        assertFalse(port.calls.contains("abandon $id"))
        assertTrue(id in port.sessions)
    }

    @Test fun restartedProcessAbandonsTheUncommittedSessionOfTheLastProcess() {
        val id = prepared()
        val restarted = StagedUpdateInstaller(port, inspector, staging, commits, pinned) { true }
        assertTrue(restarted.abandonOwnSessions())
        assertTrue(port.calls.contains("abandon $id"))
        assertTrue(port.sessions.isEmpty())
        // The restarted process has no session to read facts from or commit.
        assertNull(restarted.finalFacts())
        try { restarted.commit(id); fail("commit after restart") } catch (_: IllegalStateException) {}
    }

    @Test fun abandonedSessionCannotBeUsedByTheSameInstance() {
        val id = prepared()
        installer.finalFacts()!!
        assertTrue(installer.abandonOwnSessions())
        assertNull(installer.finalFacts())
        try { installer.commit(id); fail("commit after abandon") } catch (_: IllegalStateException) {}
        // A new download can be prepared into a new session.
        val next = prepared(apk(version = 3))
        assertTrue(next != id)
    }

    @Test fun startupAbandonmentFailuresAreNotReady() {
        port.leftover(self.value)
        port.listWorks = false
        assertFalse(installer.abandonOwnSessions())
        port.listWorks = true
        port.abandonWorks = false
        assertFalse(installer.abandonOwnSessions())
        port.abandonWorks = true
        port.abandonLeaves = true
        assertFalse("a session that is still there after abandon is not ready", installer.abandonOwnSessions())
    }

    @Test fun effectFailuresDuringPrepare() {
        stage(apk())
        port.createWorks = false
        assertNull(installer.prepare())
        port.createWorks = true
        port.writeWorks = false
        assertNull(installer.prepare())
        assertTrue("a half-written session is abandoned", port.sessions.isEmpty())
        port.writeWorks = true
        assertNotNull(installer.prepare())
    }

    @Test fun effectFailuresDuringFinalFactsAreNull() {
        prepared()
        port.readWorks = false
        assertNull(installer.finalFacts())
        port.readWorks = true
        port.listWorks = false
        assertNull(installer.finalFacts())
        port.listWorks = true
        port.installed = null
        assertNull(installer.finalFacts())
        port.installed = 1
        assertNotNull(installer.finalFacts())
        duringInspect = { throw RuntimeException("parser") }
        assertNull(installer.finalFacts())
        duringInspect = {}
        staging.staged()!!.delete()
        assertNull(installer.finalFacts())
    }

    @Test fun stagedFileThatIsNoLongerPrivateIsRefused() {
        prepared()
        staging.staged()!!.setWritable(true, false)
        assertNull(installer.finalFacts())
    }

    @Test fun installedVersionAndSelfCheck() {
        port.installed = 12
        assertEquals(12L, installer.installedVersionCode())
        port.installed = null
        assertNull(installer.installedVersionCode())
        assertTrue(installer.postUpdateSelfCheck())
        assertTrue(installer.postUpdateSelfCheck())
        assertEquals(2, selfChecks)
        selfCheckResult = { throw IllegalStateException("crashed") }
        assertFalse(installer.postUpdateSelfCheck())
        selfCheckResult = { false }
        assertFalse(installer.postUpdateSelfCheck())
    }

    // R1 (PLA-207/PLA-210): committed-session readback through getSessionInfo.

    @Test fun committedSessionReadsPendingWhilePresentAndFalseOnlyWhenGone() {
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        assertEquals(true, installer.committedSessionPending(id))
        port.sessions.remove(id)
        assertEquals(false, installer.committedSessionPending(id))
    }

    @Test fun presentSessionNeverReadsGone() {
        // Present, isCommitted still false (the ~16 ms lag), and remembered: pending.
        port.commitShowsAtOnce = false
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        assertEquals(true, installer.committedSessionPending(id))
        // Present, never committed by this app: unknown, never false.
        val open = port.leftover(self.value)
        assertNull(installer.committedSessionPending(open))
        // Present but bound to another package: unknown, never false.
        val other = port.leftover("org.other", committed = true)
        assertNull(installer.committedSessionPending(other))
    }

    @Test fun failedSessionReadIsUnknown() {
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        port.infoThrows = true
        assertNull(installer.committedSessionPending(id))
    }

    @Test fun unreadableCommitMemoryMakesAnUncommittedReadingUnknown() {
        port.commitShowsAtOnce = false
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        val restarted = StagedUpdateInstaller(port, inspector, staging, CommitMemory(File(tmp.root, "missing/nested")), pinned) { true }
        File(tmp.root, "missing").apply { mkdirs(); setReadable(false); setExecutable(false) }
        try {
            assertNull(restarted.committedSessionPending(id))
        } finally {
            File(tmp.root, "missing").apply { setReadable(true); setExecutable(true) }
        }
    }

    // P3: commit memory is durable, so a second instance (or process) never abandons during the lag.

    @Test fun anotherInstanceKeepsASessionCommittedBeforeIsCommittedTurnsTrue() {
        port.commitShowsAtOnce = false
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        assertFalse(port.sessions.getValue(id).committed)
        val second = StagedUpdateInstaller(port, inspector, staging, CommitMemory(commitDir), pinned) { true }
        assertTrue(second.abandonOwnSessions())
        assertFalse(port.calls.contains("abandon $id"))
        assertTrue(id in port.sessions)
        assertEquals(true, second.committedSessionPending(id))
    }

    @Test fun commitIsRefusedWhenItCannotBeRemembered() {
        val id = prepared()
        installer.finalFacts()!!
        commitDir.mkdirs()
        commitDir.setWritable(false)
        try {
            try { installer.commit(id); fail("commit without a durable marker") } catch (_: IllegalStateException) {}
            assertFalse(port.calls.contains("commit $id"))
            // Never retried: the session stays unusable for commit.
            try { installer.commit(id); fail("second commit") } catch (_: IllegalStateException) {}
        } finally {
            commitDir.setWritable(true)
        }
        assertFalse(port.calls.contains("commit $id"))
    }

    @Test fun unreadableCommitMemoryAbandonsNothing() {
        val open = port.leftover(self.value)
        commitDir.mkdirs()
        commitDir.setReadable(false)
        try {
            assertFalse(installer.abandonOwnSessions())
            assertFalse(port.calls.contains("abandon $open"))
        } finally {
            commitDir.setReadable(true)
        }
    }

    @Test fun markersOfGoneSessionsArePrunedAtStartup() {
        val id = prepared()
        installer.finalFacts()!!
        installer.commit(id)
        assertTrue(File(commitDir, "$id").exists())
        port.sessions.remove(id)
        assertTrue(installer.abandonOwnSessions())
        assertFalse(File(commitDir, "$id").exists())
    }
}
