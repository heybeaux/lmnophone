// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone

import android.app.Instrumentation
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Bundle

/**
 * PLA-555 spare probe. Never shipped in the app. Runs in the Device Owner process.
 * `phase=probe`: tries each Device Owner grant write on one visible app and reports Android's answer.
 * `phase=read`: reads only. `laterMs`: re-read after that delay.
 * `phase=restore`: sets every listed permission back to DEFAULT (no fixed state) and reports the readback.
 * Arguments: `pkg`, `perms` (comma separated), `phase`; probe also takes `state` (granted|denied).
 */
class Pla555PermissionProbe : Instrumentation() {
    private lateinit var args: Bundle
    override fun onCreate(arguments: Bundle) {
        super.onCreate(arguments)
        args = arguments
        start()
    }

    override fun onStart() {
        val result = Bundle()
        try {
            val dpm = targetContext.getSystemService(DevicePolicyManager::class.java)
            val admin = ComponentName(targetContext, PlainphoneAdminReceiver::class.java)
            val pkg = args.getString("pkg")!!
            val perms = args.getString("perms")!!.split(",")
            val pm = targetContext.packageManager
            fun state(p: String) = "policy=${dpm.getPermissionGrantState(admin, pkg, p)} granted=${pm.checkPermission(p, pkg) == PackageManager.PERMISSION_GRANTED}"
            result.putString("canGrantSensors", dpm.canAdminGrantSensorsPermissions().toString())
            val target = when (args.getString("phase")) {
                "probe" -> if (args.getString("state") == "denied") DevicePolicyManager.PERMISSION_GRANT_STATE_DENIED
                    else DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED
                "restore" -> DevicePolicyManager.PERMISSION_GRANT_STATE_DEFAULT
                "read" -> null
                else -> error("phase")
            }
            if (target == null) { perms.forEach { result.putString(it, state(it)) }; finish(Activity_RESULT_OK, result); return }
            val later = args.getString("laterMs")?.toLong()
            for (p in perms) {
                val before = state(p)
                val accepted = dpm.setPermissionGrantState(admin, pkg, p, target)
                result.putString(p, "before[$before] set=$target accepted=$accepted after[${state(p)}]")
            }
            if (later != null) { Thread.sleep(later); perms.forEach { result.putString("later.$it", state(it)) } }
            finish(Activity_RESULT_OK, result)
        } catch (e: Throwable) {
            result.putString("error", e.toString())
            finish(1, result)
        }
    }

    private companion object { const val Activity_RESULT_OK = -1 }
}
