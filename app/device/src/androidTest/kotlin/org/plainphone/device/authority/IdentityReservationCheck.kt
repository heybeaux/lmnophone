// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.content.Context
import java.io.File
import org.plainphone.core.Mode
import org.plainphone.core.TrustedPersonId
import org.plainphone.policy.CancelIntent
import org.plainphone.policy.CredentialState
import org.plainphone.policy.ElevationLedger
import org.plainphone.policy.ElevationRecord
import org.plainphone.policy.ElevationState
import org.plainphone.policy.FailureCounter
import org.plainphone.policy.FrictionSettings
import org.plainphone.policy.PersonCredential
import org.plainphone.policy.RecoveryProfile
import org.plainphone.policy.SecretVerifier
import org.plainphone.policy.WaitingState

/**
 * On-device check that identity reservations work with the real directory sync, run as
 * `-e phase reserve`. Reservations refuse when a directory cannot be synced (PLA-143 R1), so this
 * proves the platform sync works in device-protected storage on this build and that reserved values
 * survive a fresh store. It also writes the joint record and a cancel intent, because every record
 * write now fails when its directory sync fails. Values are synthetic. The folder is removed at the end.
 */
class IdentityReservationCheck(private val ctx: Context, private val log: (String) -> Unit) {
    fun check() {
        val de = ctx.createDeviceProtectedStorageContext()
        val dir = File(de.filesDir, "pla92-identity-check")
        dir.deleteRecursively()
        try {
            PlatformDirectorySync.sync(de.filesDir)
            note("ok   platform directory sync on ${de.filesDir.name}")
            val store = JointAuthorityStore(dir)
            val first = List(3) { store.identities.reserve() }
            val person = store.reservePerson()
            expect(person != null, "person reserved")
            expect(first.map { it.requestId }.toSet().size == 3 && first.map { it.cancelCode }.toSet().size == 3, "three distinct identities")
            expect(first.all { it.cancelCode.length == 8 && it.cancelCode.all(Char::isDigit) }, "8-digit cancel codes")
            val again = JointAuthorityStore(dir).identities.reserve()
            expect(again.requestId !in first.map { it.requestId } && again.cancelCode !in first.map { it.cancelCode }, "fresh store reserves new values")
            val files = File(dir, "reserved").list()!!.size
            expect(files == 9, "nine reservation files ($files)")
            records(dir)
        } finally {
            dir.deleteRecursively()
        }
    }

    /** Initialize, compare-and-set, cancel request; each read back through a fresh store. */
    private fun records(dir: File) {
        val self = TrustedPersonId("synthetic")
        fun creds(revision: Long, failures: Int) = CredentialState(Mode.SELF_MANAGED,
            mapOf(self to PersonCredential(SecretVerifier(ByteArray(16), ByteArray(32) { 1 }, 600_000),
                SecretVerifier(ByteArray(16) { 2 }, ByteArray(32) { 3 }, 600_000), FailureCounter(failures))),
            revision = revision, recoveryProfile = RecoveryProfile(mapOf(self to "synthetic-account"), null, FrictionSettings(1_200_000, 1)))
        val store = JointAuthorityStore(dir)
        expect(store.initialize(creds(0, 0), WaitingState(storageGeneration = "live"), ElevationRecord(0, ElevationState(ElevationLedger()))),
            "joint record initialized")
        expect(store.credentials.compareAndSet(store.credentials.read()!!, creds(1, 4)), "credential compare-and-set")
        expect(JointAuthorityStore(dir).credentials.read()?.revision == 1L, "fresh store reads revision 1")
        expect(store.cancelIntents.initialize("synthetic-request") && store.cancelIntents.request("synthetic-request"), "cancel intent written")
        expect(JointAuthorityStore(dir).cancelIntents.read("synthetic-request") == CancelIntent.REQUESTED, "fresh store reads REQUESTED")
    }

    private fun expect(ok: Boolean, what: String) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw AssertionError(what)
    }

    private fun note(line: String) = log("PLA92 $line")
}
