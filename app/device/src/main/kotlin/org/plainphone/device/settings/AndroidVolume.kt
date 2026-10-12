// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.content.Context
import android.media.AudioManager

/** https://developer.android.com/reference/android/media/AudioManager#setStreamVolume(int,int,int)
 * No Settings activity, new permission, ringer-mode change, or audible feedback flag.
 */
class AndroidVolume(context: Context, private val stream: Int) : VolumePort {
    private val audio = context.applicationContext.getSystemService(AudioManager::class.java)
    init { require(stream == AudioManager.STREAM_RING || stream == AudioManager.STREAM_MUSIC) }
    override fun read() = VolumeReading(audio.getStreamVolume(stream),
        audio.getStreamMinVolume(stream), audio.getStreamMaxVolume(stream))
    override fun write(level: Int) {
        // Do not turn a pre-existing silent/vibrate mode off as a side effect of this page.
        if (stream == AudioManager.STREAM_RING) {
            check(audio.ringerMode == AudioManager.RINGER_MODE_NORMAL)
            check(level >= 1)
        }
        audio.setStreamVolume(stream, level, 0)
    }
}
