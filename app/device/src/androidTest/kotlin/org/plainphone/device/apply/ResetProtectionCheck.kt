// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.apply

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import org.plainphone.device.DpmResetProtectionPort
import org.plainphone.device.ResetProtectionEffects

/**
 * On-device check of [ResetProtectionEffects] over [DpmResetProtectionPort], run as `-e phase reset`
 * of the same runner with this test APK as a testOnly Device Owner. IDs are synthetic numbers, not
 * real accounts. It checks only what Android stores and reads back, never which account a wipe
 * would accept. The policy is cleared at the end and the clear is read back.
 *
 * Emulator images have no persistent data block, so Android throws UnsupportedOperationException for
 * every reset-protection call (seen on API 35). There the check proves the effects fail closed: set and
 * readback report false, so a people change stays COMMITTING with the old holders.
 */
class ResetProtectionCheck(private val ctx: Context, private val log: (String) -> Unit) {
    private val admin by lazy { ComponentName(ctx, CheckAdmin::class.java) }
    private val dpm by lazy { ctx.getSystemService(DevicePolicyManager::class.java) }
    private val effects by lazy { ResetProtectionEffects(DpmResetProtectionPort(ctx, admin)) }

    private val backup = "100000000000000000099"
    private val before = setOf("100000000000000000001", "100000000000000000002", backup)
    private val after = setOf("100000000000000000003", "100000000000000000002", backup)

    fun check() {
        expect(dpm.isDeviceOwnerApp(ctx.packageName), "test APK is Device Owner")
        val unsupported = try { dpm.getFactoryResetProtectionPolicy(admin); null } catch (e: UnsupportedOperationException) { e }
        if (unsupported != null) {
            note("reset protection unsupported here: ${unsupported.message}")
            expect(!effects.setResetProtection(before), "set fails closed")
            expect(effects.broadcastResetProtectionChanged(), "broadcast still sent")
            expect(!effects.readBackResetProtection(before), "readback fails closed")
            note("RESULT reset-protection=UNSUPPORTED (fail-closed verified)")
            return
        }
        try {
            note("start: policy=${describe()}")
            apply("forward", before)
            expect(!effects.readBackResetProtection(before - backup), "readback without backup fails")
            expect(!effects.readBackResetProtection(before + "100000000000000000004"), "readback with an extra id fails")
            apply("hand-over", after)
            expect(!effects.readBackResetProtection(before), "old list no longer reads back")
            // Idempotent repeat, as a retried tick would do.
            apply("repeat", after)
            expect(!effects.setResetProtection(emptySet()), "empty list refused")
            expect(effects.readBackResetProtection(after), "refused empty write left the list alone")
        } finally {
            runCatching {
                dpm.setFactoryResetProtectionPolicy(admin, null)
                val cleared = dpm.getFactoryResetProtectionPolicy(admin) == null
                note("cleanup: policy=${describe()}")
                expect(cleared, "policy cleared")
            }.onFailure { note("cleanup failed: $it"); throw it }
        }
    }

    private fun apply(label: String, ids: Set<String>) {
        expect(effects.setResetProtection(ids), "$label set")
        expect(effects.broadcastResetProtectionChanged(), "$label broadcast sent")
        expect(effects.readBackResetProtection(ids), "$label readback")
        val raw = dpm.getFactoryResetProtectionPolicy(admin)
        expect(raw != null && raw.isFactoryResetProtectionEnabled && raw.factoryResetProtectionAccounts.toSet() == ids,
            "$label independent DPM read")
        note("$label: ${describe()}")
    }

    private fun describe(): String = dpm.getFactoryResetProtectionPolicy(admin)?.let {
        "enabled=${it.isFactoryResetProtectionEnabled} accounts=${it.factoryResetProtectionAccounts.sorted()}"
    } ?: "none"

    private fun expect(ok: Boolean, what: String) {
        note((if (ok) "ok   " else "FAIL ") + what)
        if (!ok) throw AssertionError(what)
    }

    private fun note(line: String) = log("PLA92 $line")
}
