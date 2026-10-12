// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import org.plainphone.ui.user.BatteryUiState

/** Registered only while the information page is visible; Android supplies the sticky first value. */
class BatterySource(private val context: Context, private val changed: (BatteryUiState) -> Unit) : AutoCloseable {
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) { changed(values(intent)) }
    }
    init { changed(values(context.registerReceiver(receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))) }
    override fun close() = context.unregisterReceiver(receiver)
    private fun values(intent: Intent?): BatteryUiState {
        val status = intent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        return BatteryUiState(batteryPercent(intent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1,
            intent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1), when (status) {
                BatteryManager.BATTERY_STATUS_CHARGING, BatteryManager.BATTERY_STATUS_FULL -> true
                BatteryManager.BATTERY_STATUS_DISCHARGING, BatteryManager.BATTERY_STATUS_NOT_CHARGING -> false
                else -> null
            })
    }
}
