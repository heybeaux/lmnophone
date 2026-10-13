// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.voice.send

import android.Manifest
import android.app.PendingIntent
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.Process
import android.telephony.ServiceState
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager
import java.io.File
import java.io.FileNotFoundException
import java.security.SecureRandom
import org.plainphone.device.voice.VoiceDraftStore
import org.plainphone.device.voice.VoiceFormat

/**
 * Sends through the phone's own MMS service (SmsManager.sendMultimediaMessage), as in the PLA-458 spike.
 * The message is written to an app-private file that only the phone service may read, for that one send.
 * Nothing here logs: no number, no audio, no address.
 */
class AndroidMmsPort(context: Context, private val channel: VoiceSendChannel = VoiceSendChannel.Practice) : MmsPort {
    private val app = context.applicationContext

    private fun subId(): Int = SubscriptionManager.getDefaultDataSubscriptionId()

    private fun smsManager(): SmsManager {
        val id = subId()
        val base = app.getSystemService(SmsManager::class.java) ?: @Suppress("DEPRECATION") SmsManager.getDefault()
        if (!SubscriptionManager.isValidSubscriptionId(id)) return base
        return if (Build.VERSION.SDK_INT >= 31) base.createForSubscriptionId(id)
            else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(id)
    }

    override fun carrier(): CarrierMms? = try {
        val values = smsManager().carrierConfigValues
        val max = if (values.containsKey(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE))
            values.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE) else VoiceSendLimits.DEFAULT_MAX_MESSAGE_BYTES
        CarrierMms(values.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true), max)
    } catch (_: Exception) { null }

    override fun hasSignal(): Boolean? = try {
        if (app.checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED ||
            app.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            null
        } else {
            val base = app.getSystemService(TelephonyManager::class.java)
            val tm = if (SubscriptionManager.isValidSubscriptionId(subId())) base.createForSubscriptionId(subId()) else base
            when (tm.serviceState?.state) {
                ServiceState.STATE_IN_SERVICE -> true
                ServiceState.STATE_OUT_OF_SERVICE, ServiceState.STATE_EMERGENCY_ONLY, ServiceState.STATE_POWER_OFF -> false
                else -> null
            }
        }
    } catch (_: SecurityException) { null } catch (_: Exception) { null }

    /**
     * SEND_SMS is hard-restricted: it holds only when the install allowlisted it (adb install, or the updater's
     * default allowlist) and the Device Owner granted it. The readback decides.
     */
    override fun permitted(): Boolean {
        if (granted()) return true
        try {
            val dpm = app.getSystemService(DevicePolicyManager::class.java)
            val admin = dpm.activeAdmins?.firstOrNull { it.packageName == app.packageName }
            if (admin != null && dpm.isDeviceOwnerApp(app.packageName)) {
                dpm.setPermissionGrantState(admin, app.packageName, Manifest.permission.SEND_SMS,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        } catch (_: Exception) { /* Readback decides. */ }
        return granted()
    }

    private fun granted() = app.checkSelfPermission(Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED

    override fun send(personId: String, attemptId: String, pdu: ByteArray) {
        val file = VoiceSendFiles.file(app, attemptId) ?: throw IllegalArgumentException("bad attempt")
        file.parentFile?.mkdirs()
        file.writeBytes(pdu)
        val uri = VoiceSendFiles.uri(app, attemptId)
        app.grantUriPermission(PHONE_PACKAGE, uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val intent = Intent(app, VoiceSendResultReceiver::class.java)
            .putExtra(EXTRA_PERSON, personId)
            .putExtra(EXTRA_ATTEMPT, attemptId)
            .putExtra(EXTRA_CHANNEL, channel.key)
            .setIdentifier(attemptId)
        // Mutable: the MMS service adds its answer (EXTRA_MMS_DATA) to this explicit intent.
        val sent = PendingIntent.getBroadcast(app, attemptId.hashCode(), intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
        smsManager().sendMultimediaMessage(app, uri, null, null, sent)
    }

    override fun release(attemptId: String) {
        try {
            app.revokeUriPermission(VoiceSendFiles.uri(app, attemptId), Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } catch (_: Exception) { }
        try { VoiceSendFiles.file(app, attemptId)?.delete() } catch (_: Exception) { }
    }

    companion object {
        /** The MMS service broker runs in the phone process and passes the grant on to the MMS service. */
        const val PHONE_PACKAGE = "com.android.phone"
        const val EXTRA_PERSON = "org.plainphone.voice.send.PERSON"
        const val EXTRA_ATTEMPT = "org.plainphone.voice.send.ATTEMPT"
        const val EXTRA_CHANNEL = "org.plainphone.voice.send.CHANNEL"
    }
}

/** One [VoiceSender] per draft channel in this process, so a result that arrives after the screen closed still counts. */
object VoiceSendEngine {
    private val senders = HashMap<VoiceSendChannel, VoiceSender>()
    private val random = SecureRandom()
    private val processId by lazy { "p${Process.myPid()}-${hex(4)}" }

    fun get(context: Context, channel: VoiceSendChannel = VoiceSendChannel.Practice): VoiceSender =
        synchronized(this) { senders.getOrPut(channel) { build(context.applicationContext, channel) } }

    private fun build(app: Context, channel: VoiceSendChannel): VoiceSender {
        val drafts = VoiceDraftStore(File(app.noBackupFilesDir, channel.draftDir), VoiceFormat.AacM4a) {
            System.currentTimeMillis()
        }
        return VoiceSender(VoiceSendStore(File(app.noBackupFilesDir, channel.recordDir)), drafts,
            AndroidMmsPort(app, channel), processId, { System.currentTimeMillis() }) { hex(16) }
    }

    private fun hex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
}

/** The sentIntent target. Not exported: only our own PendingIntent reaches it. */
class VoiceSendResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val person = intent.getStringExtra(AndroidMmsPort.EXTRA_PERSON) ?: return
        val attempt = intent.getStringExtra(AndroidMmsPort.EXTRA_ATTEMPT) ?: return
        val channel = VoiceSendChannel.fromExtra(intent.getStringExtra(AndroidMmsPort.EXTRA_CHANNEL))
        if (channel == null) {
            AndroidMmsPort(context).release(attempt)
            return
        }
        val data = try { intent.getByteArrayExtra(MmsSendResult.EXTRA_MMS_DATA) } catch (_: Exception) { null }
        VoiceSendEngine.get(context, channel).onResult(person, attempt, resultCode, data)
    }
}

/**
 * A FileProvider-style, read-only provider for the messages being sent: `<attempt>.pdu` in
 * noBackupFilesDir/voice-send-out. Not exported; each file is readable only through a per-send grant.
 */
class VoiceSendFiles : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "application/vnd.wap.mms-message"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        if (mode != "r") throw FileNotFoundException("read only")
        val ctx = context ?: throw FileNotFoundException("no context")
        val attempt = uri.lastPathSegment?.removeSuffix(".pdu") ?: throw FileNotFoundException("no name")
        if (uri.pathSegments.size != 1 || uri.lastPathSegment != "$attempt.pdu") throw FileNotFoundException("bad name")
        val f = file(ctx, attempt)?.takeIf { it.isFile } ?: throw FileNotFoundException("not found")
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, s: String?, a: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?): Int =
        throw UnsupportedOperationException()

    companion object {
        private val ATTEMPT = Regex("[0-9a-f]{32}")
        const val OUT_DIR = "voice-send-out"
        fun authority(context: Context) = "${context.packageName}.voicesend"
        fun uri(context: Context, attempt: String): Uri = Uri.parse("content://${authority(context)}/$attempt.pdu")
        fun file(context: Context, attempt: String): File? =
            if (ATTEMPT.matches(attempt)) File(File(context.noBackupFilesDir, OUT_DIR), "$attempt.pdu") else null
    }
}
