// Copyright 2026 Plainphone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.runtime

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.SystemClock
import android.provider.Settings
import org.plainphone.core.BootTime
import org.plainphone.policy.BatteryState

/** Live phone facts the running clock and the waiting controller read. */
class AndroidRuntimeFacts(private val context: Context) {
    /**
     * This boot as Android counts it, with time since boot including sleep. Null when the boot count
     * cannot be read, which grants no time.
     */
    fun bootTime(): BootTime? {
        val count = try {
            Settings.Global.getInt(context.contentResolver, Settings.Global.BOOT_COUNT)
        } catch (_: Settings.SettingNotFoundException) {
            return null
        }
        return BootTime("boot-$count", SystemClock.elapsedRealtime())
    }

    /** From the sticky battery broadcast; null when it is missing or out of range. */
    fun battery(): BatteryState? {
        val sticky = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return null
        val level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level < 0 || scale <= 0) return null
        val status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        return BatteryState((level * 100 / scale).coerceIn(0, 100), charging)
    }
}
