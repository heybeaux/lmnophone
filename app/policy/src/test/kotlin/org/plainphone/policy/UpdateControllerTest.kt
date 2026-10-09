// SPDX-License-Identifier: Apache-2.0
package org.plainphone.policy

import org.plainphone.core.*
import kotlin.test.*

class UpdateControllerTest {
    private class Crash : Error()
    private class F(val enabled: Boolean = true, val mode: Mode = Mode.SELF_MANAGED) {
        val person = TrustedPersonId("holder")
        val self = ApprovalBinding(PackageName("test.home"), setOf(CertDigest("ab".repeat(32))))
        val binding = StagedUpdateBinding("cd".repeat(32), 2)
        var time = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
        val clock = RunningClock { time }
        val events = mutableListOf<String>()
        var hook: (String) -> Unit = {}
        fun event(name: String) { events += name; hook(name) }
        var credentials = CredentialState(mode, mapOf(person to PersonCredential(
            SecretVerifier(ByteArray(16), ByteArray(32), 600_000), SecretVerifier(ByteArray(16), ByteArray(32), 600_000))))
        val tokens = mutableMapOf<FreshAuthority, PrivilegedAction>()
        val authority = PersonAuthorityConsumer { fresh, action, holder ->
            event("pin"); holder == person && tokens.remove(fresh) == action
        }
        fun auth(action: PrivilegedAction) = FreshAuthority().also { tokens[it] = action }
        var record = ElevationRecord()
        var update = UpdateRecord()
        var facts: UpdateFinalFacts? = UpdateFinalFacts(7, StagedApkFacts(self, binding, true), 1)
        var installed: Long? = 1
        var commits = 0
        var checks = 0
        var checkPasses = true
        var closurePasses = true
        var abandonPasses = true
        var checkCalls = 1
        var resultWritesPass = true
        val elevationStore = object : ElevationStore() {
            override fun read() = record
            override fun compareAndSet(expected: ElevationRecord?, replacement: ElevationRecord, expectedCredentials: CredentialState?): Boolean {
                if (record !== expected || (expectedCredentials != null && credentials !== expectedCredentials)) return false
                record = replacement; return true
            }
        }
        val store = object : UpdateUseStore {
            override val elevationStore = this@F.elevationStore
            override fun readUpdate() = update
            override fun compareAndSetUpdate(expected: UpdateRecord, replacement: UpdateRecord): Boolean {
                event("result-write"); if (!resultWritesPass || update !== expected) return false
                update = replacement; return true
            }
            override fun commitUpdateUse(expectedElevation: ElevationRecord, replacementElevation: ElevationRecord,
                expectedCredentials: CredentialState, expectedUpdate: UpdateRecord, submission: UpdateSubmission,
                finalCheck: () -> Boolean): Boolean {
                event("before-joint")
                fun matches() = record === expectedElevation && credentials === expectedCredentials && update === expectedUpdate
                if (!matches()) return false
                event("blocking-work")
                repeat(checkCalls) { if (!finalCheck()) return false }
                if (!matches()) return false
                event("before-write"); if (!matches()) return false
                record = replacementElevation; update = UpdateRecord(expectedUpdate.revision + 1, submission)
                event("after-write"); return true
            }
        }
        val installer = object : UpdateInstaller {
            override fun abandonOwnSessions(): Boolean { event("abandon"); return abandonPasses }
            override fun finalFacts(): UpdateFinalFacts? { event("facts"); return facts }
            override fun commit(sessionId: Int) {
                event("before-commit"); assertEquals(7, sessionId)
                assertEquals(UpdatePhase.SUBMITTED, update.submission?.phase); assertNull(record.state.grant)
                commits++; event("after-commit")
            }
            override fun installedVersionCode(): Long? { event("installed"); return installed }
            override fun postUpdateSelfCheck(): Boolean { event("self-check"); checks++; return checkPasses }
        }
        val dialer = PackageName("test.dialer")
        val sms = PackageName("test.sms")
        val settings = PackageName("test.settings")
        var input = PolicyInput("model", "build", self.packageName, sms,
            listOf(self.packageName, dialer, sms, settings).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)),
            BuildInventory("model", "build", 1, NeverHideList(setOf(sms)), setOf(sms), setOf(settings)))
        val effects = object : SessionEffects {
            override fun revokeAllRelaxations(): Boolean { event("revoke"); return closurePasses }
            override fun protectedStateCheck() = closurePasses
            override fun readBackPolicy() = closurePasses
            override fun open(access: SessionAccess) = error("Update must not open packages")
        }
        var nextId = 0
        var controller = makeController()
        var protocol = UpdateProtocol(store, installer, self)
        fun makeController() = ElevationController(elevationStore, { credentials }, authority, clock,
            { ElevationLimits() }, { input }, { BatteryState(50, false) }, effects, AppApprovalIdSource { "grant-${++nextId}" })
        init { if (enabled) controller.updateProtocol = protocol }
        fun startUp() = if (enabled) protocol.onStart(controller) else controller.onStart()
        fun advance(ms: Long) { time = time.copy(proven = time.proven.copy(totalMillis = time.proven.totalMillis + ms),
            boot = time.boot.copy(elapsedMillis = time.boot.elapsedMillis + ms)) }
        fun grant(): ElevationRequest {
            assertEquals(ElevationOutcome.IDLE, startUp())
            val r = assertNotNull(controller.prepare(person, ElevationTarget.Update(binding)))
            assertEquals(ElevationOutcome.COUNTING, controller.request(r, auth(r.action)))
            advance(600_000); assertEquals(ElevationOutcome.STARTED, controller.start(r.id, r.target)); return r
        }
        fun restart(): ElevationOutcome {
            controller = makeController(); protocol = UpdateProtocol(store, installer, self)
            controller.updateProtocol = protocol; return startUp()
        }
    }
    @Test fun `production Update stays disabled`() {
        val f = F(false); f.startUp()
        assertNull(f.controller.prepare(f.person, ElevationTarget.Update(f.binding)))
        assertNull(f.controller.prepareSupervisedUpdate(f.person, f.binding))
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(0, f.commits)
    }
    @Test fun `owned grant submits once in fresh facts joint write commit order`() {
        val f = F(); f.grant(); val debit = f.record.state.ledger.debits; f.events.clear()
        assertEquals(UpdateOutcome.SUBMITTED, f.controller.submitUpdate())
        assertEquals(listOf("revoke", "facts", "before-joint", "blocking-work", "facts", "before-write", "after-write", "before-commit", "after-commit"), f.events)
        assertEquals(debit, f.record.state.ledger.debits)
        assertEquals(BootTime("boot", 1_200_000), f.update.submission!!.elevationDeadline)
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(1, f.commits)
    }
    @Test fun `replaced bytes package signer version signature and session refuse at final boundary`() {
        for (mutation in 0..5) {
            val f = F(); f.grant(); val before = f.record
            f.hook = { if (it == "blocking-work") { val old = f.facts!!; f.facts = when (mutation) {
                0 -> old.copy(staged = old.staged.copy(binding = f.binding.copy(sha256 = "ef".repeat(32))))
                1 -> old.copy(staged = old.staged.copy(identity = ApprovalBinding(PackageName("test.other"), f.self.signingCerts)))
                2 -> old.copy(staged = old.staged.copy(identity = ApprovalBinding(f.self.packageName, setOf(CertDigest("ef".repeat(32))))))
                3 -> old.copy(staged = old.staged.copy(binding = f.binding.copy(versionCode = 3)))
                4 -> old.copy(staged = old.staged.copy(signatureValid = false))
                else -> old.copy(sessionId = 8)
            } } }
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate(), "mutation $mutation")
            assertSame(before, f.record); assertNull(f.update.submission); assertEquals(0, f.commits)
        }
    }
    @Test fun `same version lower staged version and changed installed version refuse`() {
        for (version in listOf(2L, 3L)) for (boundary in listOf(false, true)) {
            val f = F(); f.grant()
            if (boundary) f.hook = { if (it == "blocking-work") f.facts = f.facts!!.copy(installedVersionCode = version) }
            else f.facts = f.facts!!.copy(installedVersionCode = version)
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(0, f.commits)
        }
    }
    @Test fun `staging delay pauses boot and credential structure mode policy record changes refuse`() {
        for (change in listOf("delay", "deadline", "boot", "rollback", "credential", "revision", "structure", "mode", "policy", "record")) {
            val f = F(); f.grant(); val debit = f.record.state.ledger.debits
            f.hook = { event -> if (event == if (change == "delay") "facts" else "blocking-work") { when (change) {
                "delay", "deadline" -> f.advance(600_000)
                "boot" -> f.time = f.time.copy(boot = BootTime("next", 0))
                "rollback" -> f.time = f.time.copy(boot = BootTime("boot", 1))
                "credential" -> f.credentials = f.credentials.replacing(f.person, f.credentials.people.getValue(f.person).copy(generation = 1))
                "revision" -> f.credentials = f.credentials.replacing(f.person, f.credentials.people.getValue(f.person))
                "structure" -> f.credentials = CredentialState(f.mode, f.credentials.people, structureGeneration = 1)
                "mode" -> f.credentials = CredentialState(Mode.TRUSTED, f.credentials.people)
                "policy" -> f.input = f.input.copy(lockedBoot = true)
                "record" -> f.record = f.record.replacing(f.record.state)
            } } }
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate(), change)
            assertEquals(0, f.commits); assertNull(f.update.submission); assertEquals(debit, f.record.state.ledger.debits)
        }
    }
    @Test fun `pause after use can commit after deadline but preserves deadline and debit`() {
        val f = F(); f.grant(); val debit = f.record.state.ledger.debits
        f.hook = { if (it == "after-write") f.advance(700_000) }
        assertEquals(UpdateOutcome.SUBMITTED, f.controller.submitUpdate()); assertEquals(1, f.commits)
        assertEquals(debit, f.record.state.ledger.debits); assertEquals(1_200_000, f.update.submission!!.elevationDeadline!!.elapsedMillis)
    }
    @Test fun `crashes on each side of joint write and commit never replay on restart`() {
        for (point in listOf("before-joint", "before-write", "after-write", "before-commit", "after-commit")) {
            val f = F(); f.grant(); val debit = f.record.state.ledger.debits
            f.hook = { if (it == point) throw Crash() }; assertFailsWith<Crash> { f.controller.submitUpdate() }
            val wrote = point in listOf("after-write", "before-commit", "after-commit")
            assertEquals(wrote, f.update.submission != null); assertEquals(wrote, f.record.state.grant == null)
            val commits = f.commits; f.hook = {}; f.events.clear(); assertEquals(ElevationOutcome.IDLE, f.restart())
            assertEquals(listOf("abandon", "revoke"), f.events.take(2)); assertEquals(debit, f.record.state.ledger.debits)
            assertNull(f.record.state.grant); assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(commits, f.commits)
            if (wrote) assertEquals(UpdatePhase.FAILED, f.update.submission!!.phase)
        }
    }
    @Test fun `installer exception stays spent and uncertain until readback`() {
        val f = F(); f.grant(); val debit = f.record.state.ledger.debits
        f.hook = { if (it == "before-commit") throw IllegalStateException("installer unavailable") }
        assertEquals(UpdateOutcome.COMMIT_UNCERTAIN, f.controller.submitUpdate())
        assertNull(f.record.state.grant); assertEquals(UpdatePhase.SUBMITTED, f.update.submission!!.phase)
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(debit, f.record.state.ledger.debits)
        f.hook = {}; f.installed = 2; f.restart()
        assertEquals(UpdatePhase.INSTALLED, f.update.submission!!.phase); assertEquals(1, f.checks)
    }
    @Test fun `startup version equality alone means installed with separate self check outcome`() {
        for (version in listOf(null, 1L, 2L, 3L)) for (passes in listOf(false, true)) {
            val f = F(); f.grant(); f.controller.submitUpdate(); f.installed = version; f.checkPasses = passes
            val debit = f.record.state.ledger.debits; f.restart()
            assertEquals(if (version == 2L) UpdatePhase.INSTALLED else UpdatePhase.FAILED, f.update.submission!!.phase)
            assertEquals(if (version == 2L) 1 else 0, f.checks)
            if (version == 2L) assertEquals(if (passes) UpdateSelfCheck.PASSED else UpdateSelfCheck.FAILED, f.update.submission!!.selfCheck)
            assertEquals(1, f.commits); assertEquals(debit, f.record.state.ledger.debits)
        }
    }
    @Test fun `failure statuses and duplicate results cannot refund or reclassify`() {
        val f = F(); f.grant(); f.controller.submitUpdate(); val debit = f.record.state.ledger.debits
        assertEquals(UpdateOutcome.REFUSED, f.protocol.onResult(8, false))
        assertEquals(UpdateOutcome.FAILED, f.protocol.onResult(7, false)); f.installed = 2
        assertEquals(UpdateOutcome.REFUSED, f.protocol.onResult(7, true))
        assertEquals(UpdatePhase.FAILED, f.update.submission!!.phase); assertEquals(debit, f.record.state.ledger.debits); assertEquals(1, f.commits)
    }
    @Test fun `restored foreign grant never supplies controller ownership`() {
        val f = F(); val r = f.grant(); val saved = f.record; f.restart(); f.record = saved
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate())
        assertEquals(ElevationOutcome.REFUSED, f.controller.start(r.id, r.target)); assertEquals(0, f.commits)
    }
    @Test fun `package closure failure retains original deadline debit and restart revokes only authority`() {
        val f = F(); val r = f.grant(); val deadline = f.record.state.grant!!.deadline; val debit = f.record.state.ledger.debits
        f.closurePasses = false
        assertEquals(ElevationOutcome.NOT_READY, f.controller.endPackageAccess(r.id, SessionEnd.LEFT_PACKAGES))
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); f.closurePasses = true
        assertEquals(ElevationOutcome.ACCESS_CLOSED, f.controller.tick())
        assertEquals(deadline, f.record.state.grant!!.deadline); assertEquals(debit, f.record.state.ledger.debits)
        f.restart(); assertNull(f.record.state.grant); assertEquals(debit, f.record.state.ledger.debits)
    }
    @Test fun `supervised final hash precedes PIN use within joint write then installer commits once`() {
        val f = F(mode = Mode.TRUSTED); f.startUp()
        val r = assertNotNull(f.controller.prepareSupervisedUpdate(f.person, f.binding)); val fresh = f.auth(r.action); f.events.clear()
        assertEquals(UpdateOutcome.SUBMITTED, f.controller.submitSupervisedUpdate(r, fresh))
        assertTrue(f.events.indexOf("pin") > f.events.lastIndexOf("facts")); assertTrue(f.events.indexOf("pin") < f.events.indexOf("after-write"))
        assertTrue(f.events.indexOf("after-write") < f.events.indexOf("before-commit"))
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitSupervisedUpdate(r, fresh))
        assertTrue(f.record.state.ledger.debits.isEmpty()); assertEquals(1, f.commits)
    }
    @Test fun `supervised changed facts credentials mode boot or stale PIN refuse`() {
        for (change in listOf("facts", "credentials", "mode", "boot", "pin")) {
            val f = F(mode = Mode.TRUSTED); f.startUp()
            val r = assertNotNull(f.controller.prepareSupervisedUpdate(f.person, f.binding)); val fresh = f.auth(r.action)
            f.hook = { if (it == "blocking-work") when (change) {
                "facts" -> f.facts = f.facts!!.copy(staged = f.facts!!.staged.copy(binding = f.binding.copy(versionCode = 3)))
                "credentials" -> f.credentials = f.credentials.replacing(f.person, f.credentials.people.getValue(f.person).copy(generation = 1))
                "mode" -> f.credentials = CredentialState(Mode.SELF_MANAGED, f.credentials.people)
                "boot" -> f.time = f.time.copy(boot = BootTime("next", 0))
                "pin" -> f.tokens.clear()
            } }
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitSupervisedUpdate(r, fresh), change)
            assertNull(f.update.submission); assertEquals(0, f.commits)
        }
    }
    @Test fun `joint transaction compares both records again after final check and never adds writes on refusal`() {
        for (change in listOf("credentials", "elevation", "update")) {
            val f = F(); f.grant(); val old = f.record
            f.hook = { if (it == "before-write") when (change) {
                "credentials" -> f.credentials = f.credentials.replacing(f.person, f.credentials.people.getValue(f.person))
                "elevation" -> f.record = f.record.replacing(f.record.state)
                "update" -> f.update = UpdateRecord(f.update.revision + 1)
            } }
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate(), change)
            assertEquals(old.state.ledger.debits, f.record.state.ledger.debits)
            assertNotNull(f.record.state.grant); assertNull(f.update.submission); assertEquals(0, f.commits)
        }
    }
    @Test fun `store omission or duplicate final callback never reaches installer`() {
        for (calls in listOf(0, 2)) {
            val f = F(); f.grant(); f.checkCalls = calls
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(0, f.commits)
        }
    }
    @Test fun `failed abandonment prevents startup and direct elevation startup cannot enable Update`() {
        val f = F(); f.abandonPasses = false
        assertEquals(ElevationOutcome.NOT_READY, f.startUp()); assertEquals(listOf("abandon"), f.events)
        assertNull(f.controller.prepare(f.person, ElevationTarget.Update(f.binding)))
        f.abandonPasses = true; f.startUp(); f.controller.onStart()
        assertNull(f.controller.prepare(f.person, ElevationTarget.Update(f.binding)))
    }
    @Test fun `outstanding submission prevents another turn and failed package closure latches revocation`() {
        val f = F(); f.grant(); f.controller.submitUpdate()
        assertNull(f.controller.prepare(f.person, ElevationTarget.Update(f.binding)))
        val g = F(); g.grant(); val debit = g.record.state.ledger.debits; g.closurePasses = false
        assertEquals(UpdateOutcome.REFUSED, g.controller.submitUpdate())
        assertTrue(g.elevationStore.revocationPending); assertEquals(debit, g.record.state.ledger.debits)
    }
    @Test fun `ambiguous store exception cannot retry even when it happened before the write`() {
        for (point in listOf("before-write", "after-write")) {
            val f = F(); f.grant(); f.hook = { if (it == point) throw IllegalStateException("storage failure") }
            assertEquals(UpdateOutcome.NOT_READY, f.controller.submitUpdate()); f.hook = {}
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate()); assertEquals(0, f.commits)
            f.restart(); assertNull(f.record.state.grant); assertEquals(1, f.record.state.ledger.debits.size)
        }
    }
    @Test fun `installer exception after submission may still install and never retries`() {
        val f = F(); f.grant(); f.hook = { if (it == "after-commit") { f.installed = 2; throw IllegalStateException("lost reply") } }
        assertEquals(UpdateOutcome.COMMIT_UNCERTAIN, f.controller.submitUpdate()); f.hook = {}; f.restart()
        assertEquals(UpdatePhase.INSTALLED, f.update.submission!!.phase); assertEquals(1, f.commits)
    }
    @Test fun `success status requires equal installed version and self check can resume after a crash`() {
        for (version in listOf(1L, 2L, 3L)) {
            val f = F(); f.grant(); f.controller.submitUpdate(); f.installed = version
            assertEquals(if (version == 2L) UpdateOutcome.INSTALLED else UpdateOutcome.FAILED, f.protocol.onResult(7, true))
        }
        val f = F(); f.grant(); f.controller.submitUpdate(); f.installed = 2
        f.hook = { if (it == "self-check") throw Crash() }; assertFailsWith<Crash> { f.restart() }
        assertEquals(UpdatePhase.INSTALLED, f.update.submission!!.phase)
        assertEquals(UpdateSelfCheck.PENDING, f.update.submission!!.selfCheck)
        f.hook = {}; f.restart(); assertEquals(UpdateSelfCheck.PASSED, f.update.submission!!.selfCheck); assertEquals(1, f.commits)
    }
    @Test fun `failed result persistence keeps startup unavailable until reconciliation succeeds`() {
        val f = F(); f.grant(); f.controller.submitUpdate(); f.installed = 2; f.resultWritesPass = false
        assertEquals(ElevationOutcome.NOT_READY, f.restart())
        assertEquals(UpdatePhase.SUBMITTED, f.update.submission!!.phase)
        assertEquals(UpdateOutcome.REFUSED, f.controller.submitUpdate())
        f.resultWritesPass = true; assertEquals(ElevationOutcome.IDLE, f.restart()); assertEquals(1, f.commits)
    }
    @Test fun `supervised crashes around joint write and commit never reuse the old request or PIN`() {
        for (point in listOf("before-write", "after-write", "before-commit", "after-commit")) {
            val f = F(mode = Mode.TRUSTED); f.startUp()
            val r = assertNotNull(f.controller.prepareSupervisedUpdate(f.person, f.binding)); val fresh = f.auth(r.action)
            f.hook = { if (it == point) throw Crash() }; assertFailsWith<Crash> { f.controller.submitSupervisedUpdate(r, fresh) }
            val commits = f.commits; f.hook = {}; f.restart()
            assertEquals(UpdateOutcome.REFUSED, f.controller.submitSupervisedUpdate(r, fresh)); assertEquals(commits, f.commits)
            assertTrue(f.record.state.ledger.debits.isEmpty())
        }
    }

}
