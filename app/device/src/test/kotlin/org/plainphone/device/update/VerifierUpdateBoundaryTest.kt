// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
@file:Suppress("INVISIBLE_MEMBER", "INVISIBLE_REFERENCE")
package org.plainphone.device.update

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.plainphone.core.*
import org.plainphone.policy.*
import org.plainphone.device.authority.*
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * The PLA-207 reviewer's integration tests (Verifier, OpenAI model), kept as a regression suite.
 * Adapted for PLA-210 where the merged policy fix changed the expected path; each change is noted.
 * Production visibility and wiring stay unchanged: Update is still disabled.
 */
class VerifierUpdateBoundaryTest {
    @get:Rule val tmp = TemporaryFolder()
    private inner class Fixture(val mode: Mode) {
        val person = TrustedPersonId("Sam")
        val self = ApprovalBinding(PackageName("org.plainphone"), setOf(CertDigest("ab".repeat(32))))
        val dir = tmp.newFolder()
        var time = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        var fault: (WritePoint) -> Unit = {}
        val store = JointAuthorityStore(dir) { p, _ -> fault(p) }
        val port = FakeInstallerPort()
        val staging = UpdateStaging(File(tmp.newFolder(), "staging"))
        val commits = CommitMemory(File(tmp.newFolder(), "commits"))
        var selfChecks = 0
        val inspector = ApkInspector { f -> ApkFacts(self.packageName, self.signingCerts, f.readText().toLong()) }
        var installer = newInstaller()
        fun newInstaller() = StagedUpdateInstaller(port, inspector, staging, commits, self) { selfChecks++; false }
        val tokens = mutableMapOf<FreshAuthority, PrivilegedAction>()
        val authority = PersonAuthorityConsumer { token, action, holder -> holder == person && tokens.remove(token) == action }
        fun auth(action: PrivilegedAction) = FreshAuthority().also { tokens[it] = action }
        val dialer = PackageName("test.dialer")
        val sms = PackageName("test.sms")
        val settings = PackageName("test.settings")
        val input = PolicyInput("model", "build", self.packageName, sms,
            listOf(self.packageName, dialer, sms, settings).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
            BuildInventory("model", "build", 1, NeverHideList(setOf(sms)), setOf(sms), setOf(settings)))
        val effects = object : SessionEffects {
            override fun revokeAllRelaxations() = true
            override fun protectedStateCheck() = true
            override fun readBackPolicy() = true
            override fun open(access: SessionAccess) = error("Update must not open packages")
        }
        var nextId = 0
        var controller = newController()
        var protocol = UpdateProtocol(store.update, installer, self) { time.boot }
        fun newController() = ElevationController(store.elevationStore, { store.credentials.read() }, authority,
            RunningClock { time }, { ElevationLimits() }, { input }, { BatteryState(50, false) }, effects,
            AppApprovalIdSource { "request-${++nextId}" })
        init {
            val credential = PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32), 600_000),
                SecretVerifier(ByteArray(16), ByteArray(32), 600_000))
            assertTrue(store.initialize(CredentialState(mode, mapOf(person to credential)),
                WaitingState(storageGeneration = "g"), ElevationRecord()))
            controller.updateProtocol = protocol
            assertEquals(ElevationOutcome.IDLE, protocol.onStart(controller))
        }
        fun advance(ms: Long) {
            time = time.copy(proven = time.proven.copy(totalMillis = time.proven.totalMillis + ms),
                boot = time.boot.copy(elapsedMillis = time.boot.elapsedMillis + ms))
        }
        fun stage(version: Long): StagedUpdateBinding {
            val part = staging.receive()!!; part.writeText(version.toString()); assertTrue(staging.seal(part))
            assertNotNull(installer.prepare())
            return installer.finalFacts()!!.staged.binding
        }
        fun grant(binding: StagedUpdateBinding) {
            val request = controller.prepare(person, ElevationTarget.Update(binding))!!
            assertEquals(ElevationOutcome.COUNTING, controller.request(request, auth(request.action)))
            advance(600_000)
            assertEquals(ElevationOutcome.STARTED, controller.start(request.id, request.target))
        }
        fun supervised(binding: StagedUpdateBinding): UpdateOutcome {
            val request = controller.prepareSupervisedUpdate(person, binding)!!
            return controller.submitSupervisedUpdate(request, auth(request.action))
        }
        fun restart() {
            installer = newInstaller(); controller = newController(); protocol = UpdateProtocol(store.update, installer, self) { time.boot }
            controller.updateProtocol = protocol
            assertEquals(ElevationOutcome.IDLE, protocol.onStart(controller))
        }
    }

    @Test fun durableUseMustNotLandAfterItsDeadlineBecauseStorageBlocked() {
        val f = Fixture(Mode.SELF_MANAGED)
        f.grant(f.stage(2))
        val deadline = f.store.elevationStore.read()!!.state.grant!!.deadline.elapsedMillis
        val debits = f.store.elevationStore.read()!!.state.ledger.debits
        f.fault = { p -> if (p == WritePoint.BEFORE_COPY_A) f.advance(700_000) }
        val outcome = f.controller.submitUpdate()
        println("deadline=$deadline durableUseTime=${f.time.boot.elapsedMillis} outcome=$outcome calls=${f.port.calls.filter { it.startsWith("commit") }}")
        assertTrue(f.time.boot.elapsedMillis > deadline)
        assertFalse("Storage stalled before the first durable copy; expired authority must not install", f.port.calls.any { it.startsWith("commit") })
        // PLA-210: the use stays spent (debit kept, grant gone, no refund) and the result is FAILED.
        assertEquals(UpdateOutcome.FAILED, outcome)
        val after = f.store.elevationStore.read()!!.state
        assertNull(after.grant)
        assertEquals(debits, after.ledger.debits)
        assertEquals(UpdatePhase.FAILED, f.store.update.readUpdate()!!.submission!!.phase)
    }

    @Test fun lateOldInstallMustStillRunItsSelfCheckAfterNewSubmission() {
        val f = Fixture(Mode.TRUSTED)
        assertEquals(UpdateOutcome.SUBMITTED, f.supervised(f.stage(2)))
        val oldId = f.store.update.readUpdate()!!.submission!!.sessionId
        assertTrue(f.port.sessions.getValue(oldId).committed)
        f.restart() // Old v2 install still pending, installed version is v1.
        assertTrue(f.port.sessions.containsKey(oldId))
        // PLA-210: the committed session reads back pending, so the record stays SUBMITTED (was FAILED)
        // and no second submission can replace it while v2's outcome is unknown.
        assertEquals(UpdatePhase.SUBMITTED, f.store.update.readUpdate()!!.submission!!.phase)
        val next = f.stage(3)
        f.port.commitThrows = true
        assertNull(f.controller.prepareSupervisedUpdate(f.person, next))
        assertEquals(oldId, f.store.update.readUpdate()!!.submission!!.sessionId)
        f.port.commitThrows = false
        f.port.installed = 2 // Old pending v2 now finishes.
        f.port.sessions.remove(oldId)
        val result = f.protocol.onResult(oldId, true)
        f.restart()
        println("lateResult=$result installed=${f.port.installed} recorded=${f.store.update.readUpdate()!!.submission!!.versionCode} selfChecks=${f.selfChecks} needsAttention=${f.controller.updateNeedsAttention()}")
        assertEquals(UpdateOutcome.INSTALLED, result)
        assertEquals(2, f.store.update.readUpdate()!!.submission!!.versionCode)
        assertEquals("Installed v2 runs its required self-check exactly once", 1, f.selfChecks)
        assertTrue(f.controller.updateNeedsAttention()) // The supplied self-check fails on purpose.
        assertEquals(1, f.port.calls.count { it.startsWith("commit") })
    }

    @Test fun stalledRequestWriteMustRespectOverallDeadline() {
        val loopback = InetAddress.getLoopbackAddress()
        val server = ServerSocket(0, 1, loopback)
        server.receiveBufferSize = 1024
        val accepted = CountDownLatch(1); val release = CountDownLatch(1)
        val serverThread = thread(isDaemon = true) {
            try { server.accept().use { accepted.countDown(); release.await(5, TimeUnit.SECONDS) } } catch (_: Exception) {}
        }
        val staging = UpdateStaging(File(tmp.newFolder(), "staging"))
        // Same allowed path characters as the public parser. Loopback is a test transport substitution only.
        val path = "/" + "a".repeat(16 * 1024 * 1024)
        // PLA-210: the public parser now refuses an over-long path, and the download refuses an
        // over-long request before it connects.
        assertNull(UpdateSource.parse("http://192.168.1.2:${server.localPort}$path"))
        val source = UpdateSource(loopback, server.localPort, path)
        assertNull(UpdateDownload(staging, 1000, 200_000_000).fetch(source))
        assertFalse("refused before connecting", accepted.await(300, TimeUnit.MILLISECONDS))
        // With the request bound lifted, the overall deadline still ends a write the server never reads.
        val finished = CountDownLatch(1)
        val worker = thread(isDaemon = true) {
            try { UpdateDownload(staging, 1000, 200_000_000, maxRequestBytes = Int.MAX_VALUE).fetch(source) } finally { finished.countDown() }
        }
        try {
            assertTrue(accepted.await(3, TimeUnit.SECONDS))
            val withinLimit = finished.await(1200, TimeUnit.MILLISECONDS)
            println("200ms overall limit, finished within 1200ms after connect=$withinLimit")
            assertTrue("A server that never reads keeps request write blocked beyond the overall limit", withinLimit)
        } finally {
            release.countDown(); server.close(); worker.join(3000); serverThread.join(3000)
        }
    }
    @Test fun unacknowledgedJointWriteCannotReplayOrRefund() {
        val f = Fixture(Mode.SELF_MANAGED)
        f.grant(f.stage(2))
        val debits = f.store.elevationStore.read()!!.state.ledger.debits
        val result = SearchDenial(WritePoint.AFTER_COPY_B).run(f.dir,
            { faults -> f.fault = { point -> faults.at(point, f.dir) } }) { f.controller.submitUpdate() }
        assertEquals(UpdateOutcome.NOT_READY, result)
        assertEquals(UpdatePhase.SUBMITTED, f.store.update.readUpdate()!!.submission!!.phase)
        assertNull(f.store.elevationStore.read()!!.state.grant)
        assertEquals(debits, f.store.elevationStore.read()!!.state.ledger.debits)
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate())
        f.restart()
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate())
        assertEquals(debits, f.store.elevationStore.read()!!.state.ledger.debits)
        assertFalse(f.port.calls.any { it.startsWith("commit") })
    }

    @Test fun unacknowledgedResultIsReconciledWithoutAnotherInstall() {
        val f = Fixture(Mode.TRUSTED)
        assertEquals(UpdateOutcome.SUBMITTED, f.supervised(f.stage(2)))
        val id = f.store.update.readUpdate()!!.submission!!.sessionId
        f.port.installed = 2
        val result = SearchDenial(WritePoint.AFTER_COPY_B).run(f.dir,
            { faults -> f.fault = { point -> faults.at(point, f.dir) } }) { f.protocol.onResult(id, true) }
        assertEquals(UpdateOutcome.NOT_READY, result)
        assertEquals(UpdatePhase.INSTALLED, f.store.update.readUpdate()!!.submission!!.phase)
        assertEquals(UpdateSelfCheck.PENDING, f.store.update.readUpdate()!!.submission!!.selfCheck)
        assertEquals(0, f.selfChecks)
        f.restart()
        assertEquals(1, f.selfChecks)
        assertTrue(f.controller.updateNeedsAttention())
        assertEquals(1, f.port.calls.count { it.startsWith("commit") })
    }

}
