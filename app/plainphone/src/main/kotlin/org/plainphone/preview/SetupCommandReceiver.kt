// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.preview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlin.concurrent.thread

/**
 * The setup script's way in, over adb only: the manifest requires android.permission.DUMP, which the
 * adb shell holds and ordinary apps can't get.
 *
 * - `ACTION_SET_STARTER --esa packages a,b,c` saves the starter list, then applies.
 * - `ACTION_APPLY` applies the saved list again.
 */
class SetupCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val device = Preview2Device(context.applicationContext)
        thread(name = "preview2-setup") {
            try {
                when (intent.action) {
                    ACTION_SET_STARTER -> {
                        val packages = intent.getStringArrayExtra(EXTRA_PACKAGES)?.toList().orEmpty()
                        require(packages.isNotEmpty()) { "no packages" }
                        device.saveStarter(packages)
                        Log.i(Preview2Device.TAG, "PREVIEW2 STARTER $packages")
                        device.apply()
                    }
                    ACTION_APPLY -> device.apply()
                }
                pending.resultCode = 0
            } catch (e: Exception) {
                Log.e(Preview2Device.TAG, "PREVIEW2 COMMAND failed ${intent.action}", e)
                pending.resultCode = 1
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SET_STARTER = "org.plainphone.preview.SET_STARTER"
        const val ACTION_APPLY = "org.plainphone.preview.APPLY"
        const val EXTRA_PACKAGES = "packages"
    }
}
