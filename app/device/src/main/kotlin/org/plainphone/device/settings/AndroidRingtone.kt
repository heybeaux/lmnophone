// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.Settings

/**
 * Uses the platform ringtone catalog, restricted to internal audio. No picker or media permission.
 * https://developer.android.com/reference/android/media/RingtoneManager
 * https://developer.android.com/reference/android/media/Ringtone
 */
class AndroidRingtone(context: Context) : RingtonePort {
    private val app = context.applicationContext
    private val resolver = app.contentResolver
    private val handler = Handler(Looper.getMainLooper())
    private var playing: Ringtone? = null
    private val stop = Runnable { stopPreview() }

    private fun normalized(uri: Uri): Uri = resolver.uncanonicalize(uri) ?: uri
    private fun internal(uri: Uri) = uri.scheme == "content" && uri.authority == "media" &&
        uri.pathSegments.take(3) == listOf("internal", "audio", "media") && uri.pathSegments.size == 4

    override fun choices(): List<RingtoneChoice> {
        val manager = RingtoneManager(app).apply { setType(RingtoneManager.TYPE_RINGTONE) }
        return manager.cursor.use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val uri = normalized(manager.getRingtoneUri(cursor.position))
                    if (internal(uri)) {
                        val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                        if (!title.isNullOrBlank()) add(RingtoneChoice(uri.toString(), title))
                    }
                }
            }
        }
    }

    // Read the actual setting, never the symbolic default URI or a cached selected title.
    override fun read(): String? = Settings.System.getString(resolver, Settings.System.RINGTONE)
        ?.takeIf { it.isNotBlank() }?.let { normalized(Uri.parse(it)).toString() }

    override fun title(uri: String): String? = resolver.query(Uri.parse(uri),
        arrayOf(MediaStore.Audio.Media.TITLE), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    }

    override fun canWrite() = Settings.System.canWrite(app)
    override fun write(uri: String) {
        check(canWrite())
        require(internal(Uri.parse(uri)))
        RingtoneManager.setActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE, Uri.parse(uri))
    }

    override fun preview(uri: String) {
        stopPreview()
        require(internal(Uri.parse(uri)))
        val audio = app.getSystemService(AudioManager::class.java)
        if (audio.ringerMode != AudioManager.RINGER_MODE_NORMAL || audio.getStreamVolume(AudioManager.STREAM_RING) == 0) return
        val tone = RingtoneManager.getRingtone(app, Uri.parse(uri)) ?: return
        playing = tone
        tone.audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        tone.isLooping = false
        handler.postDelayed(stop, 5_000)
        try { tone.play() } catch (e: Exception) { stopPreview(); throw e }
    }

    override fun stopPreview() {
        handler.removeCallbacks(stop)
        val tone = playing
        playing = null
        tone?.stop()
    }
}
