// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.content.Context
import android.os.SystemClock
import java.io.File
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.device.runtime.AndroidRuntimeFacts
import org.plainphone.device.runtime.FileClockStateStore
import org.plainphone.device.runtime.Pbkdf2KeyDerivation
import org.plainphone.device.runtime.SecureEntropy
import org.plainphone.policy.ClockRecord
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.PinResult
import org.plainphone.policy.PinVault
import org.plainphone.policy.PrivilegedAction
import org.plainphone.policy.ProvenRunningClock
import org.plainphone.policy.WaitingState

/**
 * On-device check of the vault runtime, run as `-e phase vault` (no Device Owner needed): live boot
 * time and battery facts, the proven clock in device-protected storage, and a real PinVault with
 * PBKDF2 at 600,000 iterations on this hardware, timed. It also checks the clock compare-and-set
 * across two store instances and the fresh-epoch repair of a damaged clock record (PLA-248). The PIN is synthetic. The folder is removed at the end.
 */
class VaultRuntimeCheck(private val ctx: Context, private val log: (String) -> Unit) {
    fun check() {
        val de = ctx.createDeviceProtectedStorageContext()
        val dir = File(de.filesDir, "pla92-vault-check")
        dir.deleteRecursively()
        try {
            val facts = AndroidRuntimeFacts(ctx)
            val boot = facts.bootTime()
            expect(boot != null, "boot time read (${boot?.bootId})")
            val battery = facts.battery()
            expect(battery != null, "battery read (percent=${battery?.percent} charging=${battery?.charging})")
            val store = JointAuthorityStore(dir)
            val clock = ProvenRunningClock(FileClockStateStore(File(dir, "clock")), facts::bootTime, store::reserveEpoch)
            val first = clock.checkpoint()
            expect(first != null && first.proven.epoch.startsWith("epoch-"), "clock checkpoint in a reserved epoch")
            SystemClock.sleep(1_100)
            val second = clock.checkpoint()
            expect(second != null && second.proven.epoch == first!!.proven.epoch &&
                second.proven.totalMillis - first.proven.totalMillis in 1_000..10_000, "clock advanced ~1 s (${second?.proven?.totalMillis})")
            val other = FileClockStateStore(File(dir, "clock"))
            val held = other.read()
            expect(held != null && held.proven == second!!.proven, "second store instance reads the same record")
            expect(other.compareAndSet(held, held!!), "compare-and-set through the second instance (OS lock on clock.lock) succeeds")
            expect(!other.compareAndSet(ClockRecord(first!!.proven, first.boot), held), "stale compare-and-set refused")
            File(dir, "clock/clock.rec").writeBytes(byteArrayOf(1, 2, 3))
            val repaired = clock.checkpoint()
            expect(repaired != null && repaired.proven.epoch != first!!.proven.epoch && repaired.proven.totalMillis == 0L,
                "damaged clock record repaired to a fresh zero epoch (${repaired?.proven?.totalMillis})")
            val vault = PinVault(store.credentials, Pbkdf2KeyDerivation, SecureEntropy, clock)
            val person = TrustedPersonId("synthetic")
            var started = SystemClock.elapsedRealtime()
            val credential = vault.prepareCredential("246810".toCharArray()).credential
            note("prepareCredential (PIN + recovery code, 2 x 600000 iterations) ms=${SystemClock.elapsedRealtime() - started}")
            expect(credential.pin.iterations == 600_000, "600000 iterations")
            expect(store.initialize(CredentialState(Mode.SELF_MANAGED, mapOf(person to credential)),
                WaitingState(storageGeneration = "live"), ElevationRecord()), "joint record initialized")
            val action = PrivilegedAction("probe", "probe")
            started = SystemClock.elapsedRealtime()
            val right = vault.authenticate(person, "246810".toCharArray(), action)
            note("authenticate (1 x 600000 iterations) ms=${SystemClock.elapsedRealtime() - started}")
            expect(right is PinResult.Authorized, "right PIN authorized")
            expect(vault.authenticate(person, "246811".toCharArray(), action) is PinResult.Wrong, "wrong PIN refused")
            expect(JointAuthorityStore(dir).credentials.read()!!.people.getValue(person).pinCounter.failures == 1, "failure counter durable")
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun expect(ok: Boolean, what: String) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw AssertionError(what)
    }

    private fun note(line: String) = log("PLA92 $line")
}
