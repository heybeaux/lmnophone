// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.play

import android.app.Activity
import android.app.Instrumentation
import android.os.Build
import android.os.Bundle
import java.io.File
import org.plainphone.core.PackageName
import org.plainphone.device.facts.AndroidPackageSystemPort
import org.plainphone.device.facts.LivePolicyInputAssembler
import org.plainphone.device.storage.FilePolicyStore
import org.plainphone.policy.EmergencyFloor

/** Read-only preflight in the androidTest APK. Never provisions policy, credentials or sessions. */
class PlayPolicyPreflightInstrumentation : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(Build.HARDWARE in setOf("ranchu", "goldfish")) { "emulator only" }
            val ctx = targetContext
            val dp = ctx.createDeviceProtectedStorageContext()
            val roots = listOf(ctx.filesDir, dp.filesDir)
            val records = roots.flatMap { root -> root.walkTopDown().filter {
                it.isFile && it.name in setOf("policy.rec", "policy.lkg")
            }.toList() }
            // The preview has no store wiring. Inspect all existing standard records if any;
            // otherwise read an absent standard store. Reading never creates a record or directory.
            val dirs = records.map { it.parentFile }.distinct()
            check(dirs.size <= 1) { "ambiguous policy stores" }
            val dir = dirs.singleOrNull() ?: File(dp.filesDir, "policy")
            val live = LivePolicyInputAssembler(AndroidPackageSystemPort(ctx), FilePolicyStore(dir),
                emptyList(), PackageName(ctx.packageName),
                PackageName("com.google.android.apps.messaging")).assemble()
            val floor = EmergencyFloor.compute(live.input.installed, live.input.identities)
            result.putString("policyRecords", records.size.toString())
            result.putString("health", live.input.health.name)
            result.putString("inventory", if (live.input.inventory == null) "absent" else "present")
            result.putString("floorUnreadable", live.unreadableFloorRoles.joinToString(",") { it.name })
            result.putString("hasSystemDialer", floor.hasSystemDialer.toString())
            result.putString("result", "READ_ONLY_PREFLIGHT_COMPLETE")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("result", "PREFLIGHT_FAILED:${e.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
