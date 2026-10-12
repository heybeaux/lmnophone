// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import org.plainphone.boot.HomeGate
import org.plainphone.boot.PolicyRuntime
import org.plainphone.update.PostponeStatus

/**
 * The setup script's way in, over adb only: the manifest requires android.permission.DUMP, which the
 * adb shell holds and ordinary apps can't get.
 *
 * - `ACTION_SET_STARTER --esa packages a,b,c` saves the starter list, then re-applies.
 * - `ACTION_APPLY` re-applies the saved list.
 * - `ACTION_RELEASE_UPDATE_POLICY` clears the postpone update policy until the app restarts, so the
 *   driver can then remove Device Owner without leaving it behind (PLA-447). Result 0 when cleared.
 *
 * Both go through [PolicyRuntime] (log tag `PlainphoneBoot`, reason `setup`). The result code is 0 when
 * the re-apply read back and Home may show.
 */
class SetupCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val runtime = PolicyRuntime.get(context)
        if (intent.action == ACTION_RELEASE_UPDATE_POLICY) {
            runtime.releaseUpdatePolicy { status ->
                pending.resultCode = if (status == PostponeStatus.RELEASED) 0 else 1
                pending.finish()
            }
            return
        }
        try {
            if (intent.action == ACTION_SET_STARTER) {
                val packages = intent.getStringArrayExtra(EXTRA_PACKAGES)?.toList().orEmpty()
                require(packages.isNotEmpty()) { "no packages" }
                Preview2Device(context.applicationContext).saveStarter(packages)
                Log.i(Preview2Device.TAG, "PREVIEW2 STARTER $packages")
            }
        } catch (e: Exception) {
            Log.e(Preview2Device.TAG, "PREVIEW2 COMMAND failed ${intent.action}", e)
            pending.resultCode = 1
            pending.finish()
            return
        }
        runtime.reapply("setup") {
            pending.resultCode = if (runtime.gate.value == HomeGate.READY) 0 else 1
            pending.finish()
        }
    }

    companion object {
        const val ACTION_SET_STARTER = "org.plainphone.preview.SET_STARTER"
        const val ACTION_APPLY = "org.plainphone.preview.APPLY"
        const val ACTION_RELEASE_UPDATE_POLICY = "org.plainphone.preview.RELEASE_UPDATE_POLICY"
        const val EXTRA_PACKAGES = "packages"
    }
}
