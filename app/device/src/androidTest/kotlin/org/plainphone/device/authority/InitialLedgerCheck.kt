// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.content.Context
import java.io.File
import java.security.MessageDigest
import org.plainphone.core.Mode
import org.plainphone.core.RunningTime
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.runtime.AndroidRuntimeFacts
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.device.runtime.Pbkdf2KeyDerivation
import org.plainphone.device.runtime.SecureEntropy
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationDebit
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.PinVault
import org.plainphone.policy.ProvenRunningClock

/**
 * On-device check of [InitialProvisioning] in device-protected storage (PLA-97), no Device Owner needed:
 *
 * - `-e phase ledger-seed`: provision a supervised and a self-managed record, each with an empty
 *   debit ledger; spend one synthetic debit in the self-managed record; a second setup is refused.
 * - `-e phase ledger-after`: run in a new process, after a kill or a reboot. Both records read back
 *   unchanged (empty supervised ledger, one self-managed debit), setup is still refused, and no file
 *   changed. Run it as often as wanted.
 * - `-e phase ledger-cleanup`: removes the folder.
 *
 * The PINs and accounts are synthetic.
 */
class InitialLedgerCheck(private val ctx: Context, private val log: (String) -> Unit) {
    private val dir = File(ctx.createDeviceProtectedStorageContext().filesDir, "pla97-ledger-check")
    private val supervisedDir = File(dir, "supervised")
    private val selfDir = File(dir, "self-managed")
    private val digests = File(dir, "digests.txt")

    fun seed() {
        dir.deleteRecursively()
        for ((mode, folder) in listOf(Mode.TRUSTED to supervisedDir, Mode.SELF_MANAGED to selfDir)) {
            val store = JointAuthorityStore(folder)
            expect(InitialProvisioning(store).provision(credentials(store, mode)) == ProvisionOutcome.CREATED, "$mode genesis created")
            val record = JointAuthorityStore(folder).elevationStore.read()
            expect(record != null && record.revision == 0L && record.state.ledger.debits.isEmpty(), "$mode ledger empty, revision 0")
            expect(InitialProvisioning(store).provision(credentials(store, mode)) == ProvisionOutcome.EXISTING, "$mode second setup refused")
        }
        val store = JointAuthorityStore(selfDir)
        val genesis = store.elevationStore.read()!!
        val epoch = RunningTime("pla97-epoch", 1_000)
        val debited = ElevationRecord(1, ElevationState(ElevationLedger(listOf(ElevationDebit("pla97-debit", epoch)))))
        expect(store.elevationStore.compareAndSet(genesis, debited, store.credentials.read()), "one synthetic debit written")
        digests.writeText(snapshot())
        note("digests ${digests.readText().replace('\n', ' ')}")
    }

    fun after() {
        val before = digests.readText()
        val supervised = JointAuthorityStore(supervisedDir)
        val self = JointAuthorityStore(selfDir)
        expect(supervised.credentials.read()?.mode == Mode.TRUSTED, "supervised credentials read back")
        val s = supervised.elevationStore.read()
        expect(s != null && s.revision == 0L && s.state.ledger.debits.isEmpty(), "supervised ledger still empty")
        expect(self.credentials.read()?.mode == Mode.SELF_MANAGED, "self-managed credentials read back")
        val m = self.elevationStore.read()
        expect(m != null && m.revision == 1L && m.state.ledger.debits.map { it.id to it.at } ==
            listOf("pla97-debit" to RunningTime("pla97-epoch", 1_000)), "self-managed debit survived")
        expect(InitialProvisioning(supervised).provision(credentials(supervised, Mode.TRUSTED)) == ProvisionOutcome.EXISTING, "supervised setup refused")
        expect(InitialProvisioning(self).provision(credentials(self, Mode.SELF_MANAGED)) == ProvisionOutcome.EXISTING, "self-managed setup refused")
        expect(snapshot() == before, "no record file changed")
        note("boot ${AndroidRuntimeFacts(ctx).bootTime()?.bootId}")
    }

    fun cleanup() {
        dir.deleteRecursively()
        expect(!dir.exists(), "check folder removed")
    }

    private fun credentials(store: JointAuthorityStore, mode: Mode): CredentialState {
        val facts = AndroidRuntimeFacts(ctx)
        val clock = ProvenRunningClock(FileClockStateStore(File(dir, "clock")), facts::bootTime, store::reserveEpoch)
        val vault = PinVault(store.credentials, Pbkdf2KeyDerivation, SecureEntropy, clock)
        return CredentialState(mode, mapOf(TrustedPersonId("synthetic") to vault.prepareCredential("246810".toCharArray()).credential))
    }

    private fun snapshot(): String = listOf(supervisedDir, selfDir).flatMap { folder ->
        folder.listFiles()!!.filter { it.isFile && it.name.endsWith(".rec") }.sortedBy { it.name }.map { f ->
            "${folder.name}/${f.name}=" + MessageDigest.getInstance("SHA-256").digest(f.readBytes())
                .take(8).joinToString("") { "%02x".format(it) }
        }
    }.joinToString("\n")

    private fun expect(ok: Boolean, what: String) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw AssertionError(what)
    }

    private fun note(line: String) = log("PLA97 $line")
}
