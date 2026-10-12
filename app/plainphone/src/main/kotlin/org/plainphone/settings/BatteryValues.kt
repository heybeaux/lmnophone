// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

fun batteryPercent(level: Int, scale: Int): Int? =
    if (scale <= 0 || level !in 0..scale) null else (level.toLong() * 100 / scale).toInt()

fun batteryStatus(status: Int?): org.plainphone.ui.user.BatteryStatus? = when (status) {
    android.os.BatteryManager.BATTERY_STATUS_FULL -> org.plainphone.ui.user.BatteryStatus.Charged
    android.os.BatteryManager.BATTERY_STATUS_CHARGING -> org.plainphone.ui.user.BatteryStatus.Charging
    android.os.BatteryManager.BATTERY_STATUS_DISCHARGING,
    android.os.BatteryManager.BATTERY_STATUS_NOT_CHARGING -> org.plainphone.ui.user.BatteryStatus.NotCharging
    else -> null
}
