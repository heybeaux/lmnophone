// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Both keys are explicitly accepted by DevicePolicyManager.setSystemSetting (API 28+).
 * https://developer.android.com/reference/android/app/admin/DevicePolicyManager#setSystemSetting(android.content.ComponentName,%20java.lang.String,%20java.lang.String)
 * No WRITE_SETTINGS grant, Settings activity, or app-window brightness override.
 */
class AndroidBrightness(context: Context, private val admin: ComponentName) : BrightnessPort {
    private val app = context.applicationContext
    private val dpm = app.getSystemService(DevicePolicyManager::class.java)
    override fun isOwner() = dpm.isDeviceOwnerApp(app.packageName)
    override fun read(): BrightnessReading {
        val raw = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
        val mode = Settings.System.getInt(app.contentResolver, Settings.System.SCREEN_BRIGHTNESS_MODE)
        check(mode == Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL || mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
        return BrightnessReading(raw, mode == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
    }
    override fun write(next: BrightnessReading) {
        check(isOwner())
        val before = read()
        if (before.raw != next.raw) dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS, next.raw.toString())
        if (before.automatic != next.automatic) dpm.setSystemSetting(admin, Settings.System.SCREEN_BRIGHTNESS_MODE,
            (if (next.automatic) Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC else Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL).toString())
    }
}
