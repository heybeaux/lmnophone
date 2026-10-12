// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.runtime.AndroidRuntimeFacts
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.device.runtime.Pbkdf2KeyDerivation
import org.plainphone.device.runtime.SecureEntropy
import org.plainphone.policy.BatteryState
import org.plainphone.policy.CredentialState
import org.plainphone.policy.EnrollmentWitness
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.IncomingSelfManagedEnrollment
import org.plainphone.policy.ModeSwitchEffects
import org.plainphone.policy.PeopleChangeController
import org.plainphone.policy.PeopleChangeOutcome
import org.plainphone.policy.PersonAuthorityConsumer
import org.plainphone.policy.PersonContact
import org.plainphone.policy.PinVault
import org.plainphone.policy.ProvenRunningClock
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SelfManagedEnrollmentConfirmations
import org.plainphone.policy.WaitingChangeController
import org.plainphone.policy.WaitingOutcome
import org.plainphone.policy.WaitingPhase
import org.plainphone.policy.WaitingSkipResult
import org.plainphone.policy.WaitingSkipSupport

/**
 * On-device persistence check of a supervised-to-self-managed switch on the joint record (PLA-97), in
 * device-protected storage, with the real PBKDF2 vault and proven clock. The Android effects are
 * synthetic (they record the target list), so this says nothing about real reset protection, DPM
 * readback or which account a wipe accepts. No Device Owner needed. Run each phase in a new process;
 * reboot between phases to test restart:
 *
 * - `-e phase mode-seed`: set up alex and jo (supervised), write one synthetic debit, start the switch to a
 *   synthetic self-managed holder, and skip the wait with jo's recovery code: COMMITTING, jo rotated.
 * - `-e phase mode-apply`: the COMMITTING record survived; one executor step swaps to the self-managed
 *   holder (APPLIED) with the target list; the ledger is unchanged.
 * - `-e phase mode-complete`: the swap survived; the next step completes it once; a stale step does nothing.
 * - `-e phase mode-cleanup`: removes the folder.
 *
 * PINs, codes and accounts are synthetic and never logged.
 */
class ModeSwitchCheck(private val ctx: Context, private val log: (String) -> Unit) {
    private val dir = File(ctx.createDeviceProtectedStorageContext().filesDir, "pla97-mode-check")
    private val storeDir = File(dir, "authority")
    private val state = File(dir, "state.txt")
    private val alex = TrustedPersonId("alex"); private val jo = TrustedPersonId("jo"); private val sam = TrustedPersonId("sam")
    private val friction = FrictionSettings(1_200_000, 2)
    private val battery = BatteryState(80, false)

    private class Effects : ModeSwitchEffects {
        var applied: Set<String>? = null
        val calls = mutableListOf<String>()
        override fun revokeTemporaryAuthority(): Boolean { calls += "revoke"; return true }
        override fun readBackProtectedState(): Boolean { calls += "protected"; return true }
        override fun setResetProtection(userIds: Set<String>): Boolean { calls += "set"; applied = userIds; return true }
        override fun broadcastResetProtectionChanged(): Boolean { calls += "broadcast"; return true }
        override fun readBackResetProtection(userIds: Set<String>): Boolean { calls += "readback"; return applied == userIds }
    }

    private inner class Wired {
        val store = JointAuthorityStore(storeDir)
        val facts = AndroidRuntimeFacts(ctx)
        val clock = ProvenRunningClock(FileClockStateStore(File(dir, "clock")), facts::bootTime, store::reserveEpoch)
        val vault = PinVault(store.credentials, Pbkdf2KeyDerivation, SecureEntropy, clock)
        val effects = Effects()
        val waiting = WaitingChangeController(store.waitingStore, { store.credentials.read() }, clock, vault,
            store.identities, store.cancelIntents, WaitingSkipSupport(vault, store.skipCommitter))
        val people = PeopleChangeController(store.waitingStore, store.credentials, store.modeCommitter, effects, clock,
            PersonAuthorityConsumer { _, _, _ -> false })
    }

    fun seed() {
        dir.deleteRecursively()
        val w = Wired()
        val codes = mutableMapOf<TrustedPersonId, String>()
        val holders = listOf(alex, jo).associateWith { id ->
            w.vault.prepareCredential("246810".toCharArray()).also { codes[id] = it.recoveryCode }.credential
                .copy(contact = PersonContact("synthetic-${id.value}", "synthetic-number-${id.value}"))
        }
        val credentials = CredentialState(Mode.TRUSTED, holders,
            recoveryProfile = RecoveryProfile(holders.keys.associateWith { "account-${it.value}" }, "account-backup", friction))
        expect(InitialProvisioning(w.store).provision(credentials, "live") == ProvisionOutcome.CREATED, "supervised genesis created")
        // One synthetic turn debit, which the switch and both reboots must keep (PLA-441 note).
        val genesis = checkNotNull(w.store.elevationStore.read())
        val debited = org.plainphone.policy.ElevationRecord(1, org.plainphone.policy.ElevationState(org.plainphone.policy.ElevationLedger(
            listOf(org.plainphone.policy.ElevationDebit("pla97-debit", org.plainphone.core.RunningTime("pla97-epoch", 1_000))))))
        expect(w.store.elevationStore.compareAndSet(genesis, debited, w.store.credentials.read()), "one synthetic debit written")
        val joBefore = checkNotNull(w.store.credentials.read()).people.getValue(jo).generation
        val incoming = checkNotNull(IncomingSelfManagedEnrollment.confirm(sam, "account-sam",
            w.vault.prepareCredential("135791".toCharArray()), SelfManagedEnrollmentConfirmations(true, true, true, true, true, true),
            EnrollmentWitness.CurrentPerson(alex)))
        val start = checkNotNull(w.waiting.prepareSelfManaged(alex, incoming))
        val fresh = (w.vault.authenticate(alex, "246810".toCharArray(), start.action) as org.plainphone.policy.PinResult.Authorized).authority
        expect(w.waiting.startModeSwitch(start, fresh, true) == WaitingOutcome.PENDING, "switch to self-managed pending")
        val skip = w.waiting.skip(start.requestId, jo, codes.getValue(jo).toCharArray(), "975318".toCharArray(), true, battery)
        expect(skip is WaitingSkipResult.Committing, "jo's recovery code skipped the wait")
        val op = Wired().store.waitingStore.read()?.operation
        expect(op?.phase == WaitingPhase.COMMITTING && op.skipper == jo, "COMMITTING with skipper jo")
        val disk = Wired().store
        val rotated = checkNotNull(disk.credentials.read()).people.getValue(jo).generation
        expect(rotated == joBefore + 1 && op?.skipperGeneration == rotated, "jo rotated exactly once and bound to the skip")
        val old = Wired().vault.authenticate(jo, "246810".toCharArray(), org.plainphone.policy.PrivilegedAction("probe", "probe"))
        expect(old !is org.plainphone.policy.PinResult.Authorized, "jo's replaced PIN no longer works")
        state.writeText("${start.requestId}\n${digest(Wired().store.credentials.read()!!)}\n${elevationDigest(disk)}\n${bootId()}\n")
        note("seeded; credentials ${digest(disk.credentials.read()!!).take(16)}")
    }

    fun apply() {
        val (id, before, elevation, seedBoot) = state.readLines()
        expect(bootId() != seedBoot, "a reboot since the seed (${seedBoot.take(8)} -> ${bootId().take(8)})")
        val w = Wired()
        val creds = w.store.credentials.read()
        expect(creds != null && digest(creds) == before, "seeded credentials survived exactly")
        expect(w.store.waitingStore.read()?.operation?.phase == WaitingPhase.COMMITTING, "COMMITTING survived")
        expect(w.people.tick(id, WaitingPhase.COMMITTING).outcome == PeopleChangeOutcome.APPLIED, "swap APPLIED")
        expect(w.effects.calls == listOf("revoke", "protected", "set", "broadcast", "readback"), "effect order ${w.effects.calls}")
        expect(w.effects.applied == setOf("account-sam", "account-backup"), "target reset list")
        val disk = Wired().store
        val after = disk.credentials.read()
        expect(after?.mode == Mode.SELF_MANAGED && after.people.keys == setOf(sam) && after.appliedPeopleChange?.requestId == id,
            "self-managed holder with applied marker read back")
        expect(elevationDigest(disk) == elevation, "elevation record unchanged")
        expect(disk.elevationStore.read()?.state?.ledger?.debits?.map { it.id } == listOf("pla97-debit"), "the debit survived the swap")
        state.writeText("$id\n${digest(after!!)}\n$elevation\n${bootId()}\n")
        note("applied; credentials ${digest(after).take(16)}")
    }

    fun complete() {
        val (id, swapped, elevation, applyBoot) = state.readLines()
        expect(bootId() != applyBoot, "a reboot since the swap (${applyBoot.take(8)} -> ${bootId().take(8)})")
        val w = Wired()
        val creds = w.store.credentials.read()
        expect(creds != null && digest(creds) == swapped, "swapped credentials survived exactly")
        expect(w.people.tick(id, WaitingPhase.COMMITTING).outcome == PeopleChangeOutcome.COMPLETED, "COMPLETED")
        expect("set" !in w.effects.calls, "no reset write on completion")
        val disk = Wired().store
        expect(disk.waitingStore.read()?.operation?.phase == WaitingPhase.COMPLETED, "COMPLETED read back")
        expect(digest(disk.credentials.read()!!) == swapped, "credentials unchanged by completion")
        expect(elevationDigest(disk) == elevation, "elevation record unchanged")
        expect(disk.elevationStore.read()?.state?.ledger?.debits?.map { it.id } == listOf("pla97-debit"), "the debit survived completion")
        val stale = Wired()
        val credentialsBefore = digest(stale.store.credentials.read()!!)
        expect(stale.people.tick(id, WaitingPhase.COMMITTING).outcome == PeopleChangeOutcome.IGNORED &&
            stale.effects.calls.isEmpty() && digest(Wired().store.credentials.read()!!) == credentialsBefore, "stale step ignored with no effect or write")
        note("boot ${AndroidRuntimeFacts(ctx).bootTime()?.bootId}")
    }

    fun cleanup() {
        dir.deleteRecursively()
        expect(!dir.exists(), "check folder removed")
    }

    /** The kernel boot ID, which changes on every reboot. */
    private fun bootId(): String = File("/proc/sys/kernel/random/boot_id").readText().trim()

    /** Canonical encodings of the decoded sections, not raw file bytes. */
    private fun digest(c: CredentialState): String = hex(checkNotNull(AuthorityCodec.encodeCredentials(c)))
    private fun elevationDigest(store: JointAuthorityStore): String = hex(checkNotNull(AuthorityCodec.encodeElevation(checkNotNull(store.elevationStore.read()))))
    private fun hex(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun expect(ok: Boolean, what: String) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw AssertionError(what)
    }

    private fun note(line: String) = log("PLA97 $line")
}
