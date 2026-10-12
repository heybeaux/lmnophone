// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.plainphone.core.BootTime
import org.plainphone.core.BuildInventory
import org.plainphone.core.ClockReading
import org.plainphone.core.Mode
import org.plainphone.core.NeverHideList
import org.plainphone.core.PackageName
import org.plainphone.core.RunningClock
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.AppApprovalIdSource
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationController
import org.plainphone.policy.EnrollmentConfirmations
import org.plainphone.policy.EnrollmentWitness
import org.plainphone.policy.FloorIdentity
import org.plainphone.policy.FloorRole
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IdentitySource
import org.plainphone.policy.IncomingPersonEnrollment
import org.plainphone.policy.IncomingSelfManagedEnrollment
import org.plainphone.policy.InstalledPackage
import org.plainphone.policy.KeyDerivation
import org.plainphone.policy.ModeSwitchEffects
import org.plainphone.policy.PeopleChangeController
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PolicyInput
import org.plainphone.policy.PrivilegedAction
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretEntropy
import org.plainphone.policy.SelfManagedEnrollmentConfirmations
import org.plainphone.policy.SessionAccess
import org.plainphone.policy.SessionEffects
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingStart

/** Synthetic mode-switch effects; [applied] is what a reset-protection readback would see. */
internal class ModeSwitchFakeEffects : ModeSwitchEffects {
    var applied: Set<String>? = null
    val calls = mutableListOf<String>()
    /** Step name that returns false, or null. */
    var failAt: String? = null
    /** Called before each step; may throw [SimulatedCrash]. */
    var onCall: ((String) -> Unit)? = null
    override fun revokeTemporaryAuthority() = step("revoke") {}
    override fun readBackProtectedState() = step("protected") {}
    override fun setResetProtection(userIds: Set<String>) = step("set") { applied = userIds }
    override fun broadcastResetProtectionChanged() = step("broadcast") {}
    override fun readBackResetProtection(userIds: Set<String>) = step("readback") {} && applied == userIds
    private fun step(name: String, block: () -> Unit): Boolean {
        calls += name; onCall?.invoke(name)
        if (failAt == name) return false
        block(); return true
    }
    companion object { val STEPS = listOf("revoke", "protected", "set", "broadcast", "readback") }
}

/**
 * A provisioned joint record in [dir] with the real waiting, people-change and elevation
 * controllers (PLA-97). Supervised: alex and jo; self-managed: sam. Incoming PINs are 555555.
 */
internal class ModeSwitchRig(mode: Mode, val dir: File, val friction: FrictionSettings = FrictionSettings(20 * MINUTE, 2)) {
    val pin get() = CharArray(6) { '1' }
    val newPin get() = CharArray(6) { '5' }
    var now = ClockReading(RunningTime("epoch", 0), BootTime("boot", 0))
    val clock = RunningClock { now }
    var faults = StorageFaults { _, _ -> }
    var sequence = 0
    val effects = ModeSwitchFakeEffects()
    lateinit var store: JointAuthorityStore
    lateinit var vault: PinVault
    lateinit var waiting: WaitingChangeController
    lateinit var people: PeopleChangeController

    init {
        wire()
        val holders = (if (mode == Mode.TRUSTED) listOf(ALEX, JO) else listOf(SAM)).associateWith { id ->
            vault.prepareCredential(pin).credential.let {
                if (mode == Mode.TRUSTED) it.copy(contact = PersonContact("synthetic-${id.value}", "synthetic-number-${id.value}")) else it
            }
        }
        val credentials = CredentialState(mode, holders,
            recoveryProfile = RecoveryProfile(holders.keys.associateWith { "account-${it.value}" }, "account-backup", friction))
        assertEquals(ProvisionOutcome.CREATED, InitialProvisioning(store).provision(credentials, "live"))
    }

    /** Restart: new store, vault and controllers over the same directory. */
    fun wire() {
        store = JointAuthorityStore(dir, { point, d -> faults.at(point, d) }, INVENTORY)
        vault = PinVault(store.credentials, KeyDerivation { chars, salt, _ ->
            MessageDigest.getInstance("SHA-256").digest(chars.concatToString().toByteArray() + salt)
        }, SecretEntropy { n -> ByteArray(n) { (++sequence).toByte() } }, clock)
        waiting = WaitingChangeController(store.waitingStore, { store.credentials.read() }, clock, vault, store.identities, store.cancelIntents)
        people = PeopleChangeController(store.waitingStore, store.credentials, store.modeCommitter, effects, clock, vault)
    }
    fun disk() = JointAuthorityStore(dir, INVENTORY)
    fun credentials() = disk().credentials.read()!!
    fun journal() = disk().waitingStore.read()!!
    fun ledger() = disk().elevationStore.read()!!
    fun auth(person: TrustedPersonId, action: PrivilegedAction, code: CharArray = pin) =
        (vault.authenticate(person, code, action) as PinResult.Authorized).authority
    fun at(elapsed: Long) { now = ClockReading(RunningTime("epoch", elapsed), BootTime("boot", elapsed)) }
    fun start(prepared: WaitingStart?, starter: TrustedPersonId): String {
        val r = checkNotNull(prepared)
        assertEquals(WaitingOutcome.PENDING, waiting.startModeSwitch(r, auth(starter, r.action), true))
        return r.requestId
    }
    fun toSelfManaged() = start(waiting.prepareSelfManaged(ALEX, checkNotNull(IncomingSelfManagedEnrollment.confirm(SAM, "account-sam",
        vault.prepareCredential(newPin), SelfManagedEnrollmentConfirmations(true, true, true, true, true, true),
        EnrollmentWitness.CurrentPerson(ALEX)))), ALEX)
    /** The incoming supervised holder's recovery code from [toSupervised]. */
    var incomingCode: String? = null
    fun toSupervised() = start(waiting.prepareSupervised(SAM, checkNotNull(IncomingPersonEnrollment.confirm(ALEX,
        PersonContact("synthetic-alex", "synthetic-number-alex"), "account-alex",
        vault.prepareCredential(newPin).also { incomingCode = it.recoveryCode },
        EnrollmentConfirmations(true, true, true, true, true, true, true, true, true), EnrollmentWitness.Helper))), SAM)
    fun mature(id: String) { at(now.proven.totalMillis + DAY); assertEquals(WaitingOutcome.COMMITTING, waiting.tick(id, BATTERY)) }
    fun elevations() = ElevationController(store.elevationStore, { store.credentials.read() }, vault, clock, store::activeLimits,
        { POLICY }, { BATTERY }, SESSIONS, AppApprovalIdSource { "turn-${++sequence}" })
    /**
     * Runs [block] with one crash at [point] of the next store write that reaches it. Fails unless
     * that hook fired and [block] ended with exactly that crash (PLA-432 R1): any other exception,
     * or none, is a test failure.
     */
    fun crashAt(point: WritePoint, block: () -> Unit) {
        val crash = SimulatedCrash(); var hits = 0
        faults = StorageFaults { at, _ -> if (at == point && hits++ == 0) throw crash }
        val thrown = try { runCatching(block).exceptionOrNull() } finally { faults = StorageFaults { _, _ -> } }
        assertEquals("$point hook hits", 1, hits)
        assertSame("$point ended with its own crash", crash, thrown)
    }

    /** As [crashAt], for one crash before effect [step]. */
    fun crashBefore(step: String, block: () -> Unit) {
        val crash = SimulatedCrash(); var hits = 0
        effects.onCall = { if (it == step && hits++ == 0) throw crash }
        val thrown = try { runCatching(block).exceptionOrNull() } finally { effects.onCall = null }
        assertEquals("$step hook hits", 1, hits)
        assertSame("$step ended with its own crash", crash, thrown)
    }

    companion object {
        const val MINUTE = 60_000L
        const val DAY = 86_400_000L
        val ALEX = TrustedPersonId("alex")
        val JO = TrustedPersonId("jo")
        val SAM = TrustedPersonId("sam")
        val BATTERY = BatteryState(80, false)
        private val home = PackageName("test.home")
        private val sms = PackageName("test.sms")
        private val dialer = PackageName("test.dialer")
        private val play = PackageName("com.android.vending")
        val INVENTORY = BuildInventory("model", "build", 1, NeverHideList(setOf(sms, play)), setOf(sms), setOf(PackageName("test.settings")))
        val POLICY = PolicyInput("model", "build", home, sms, listOf(home, sms, dialer, play).map { InstalledPackage(it, true, 1) },
            listOf(FloorIdentity(dialer, FloorRole.SYSTEM_DIALER, IdentitySource.OS_MANAGED)), INVENTORY)
        val SESSIONS = object : SessionEffects {
            override fun revokeAllRelaxations() = true
            override fun open(access: SessionAccess) = true
            override fun protectedStateCheck() = true
            override fun readBackPolicy() = true
        }
    }
}
