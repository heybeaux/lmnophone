// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.authority

import android.content.Context
import java.io.File

/**
 * On-device check that identity reservations work with the real directory sync, run as
 * `-e phase reserve`. Reservations refuse when a directory cannot be synced (PLA-143 R1), so this
 * proves the platform sync works in device-protected storage on this build and that reserved values
 * survive a fresh store. Values are random; nothing personal is involved. The folder is removed at the end.
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
