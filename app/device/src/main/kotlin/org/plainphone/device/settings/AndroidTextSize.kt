// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.content.Context
import android.provider.Settings

/**
 * WRITE_SETTINGS must be granted separately; this adapter never opens Android Settings.
 * https://developer.android.com/reference/android/provider/Settings.System#canWrite(android.content.Context)
 */
class AndroidTextSize(context: Context) : TextSizePort {
    private val app = context.applicationContext
    override fun canWrite() = Settings.System.canWrite(app)
    override fun read() = Settings.System.getFloat(app.contentResolver, Settings.System.FONT_SCALE)
    override fun write(scale: Float): Boolean {
        check(canWrite())
        require(scale in 1f..2f)
        return Settings.System.putFloat(app.contentResolver, Settings.System.FONT_SCALE, scale)
    }
}
