// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.requests

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.*
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import android.telephony.SmsManager
import android.telephony.SubscriptionManager
import org.plainphone.policy.*
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** SMS sent-intents, not delivery receipts. Multipart Sent needs RESULT_OK for every part.
 * https://developer.android.com/reference/android/telephony/SmsManager#sendMultipartTextMessage
 */
class AndroidRequestSms(private val app: Context) : RequestSmsPort {
    private fun manager(): SmsManager {
        val base = app.getSystemService(SmsManager::class.java)
        val sub = SubscriptionManager.getDefaultSmsSubscriptionId()
        return if (SubscriptionManager.isValidSubscriptionId(sub)) {
            if (Build.VERSION.SDK_INT >= 31) base.createForSubscriptionId(sub)
            else @Suppress("DEPRECATION") SmsManager.getSmsManagerForSubscriptionId(sub)
        } else base
    }
    override fun problem(): RequestStatus? = when {
        app.checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED -> RequestStatus.NoPermission
        Settings.Global.getInt(app.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) != 0 -> RequestStatus.NoSignal
        else -> null
    }
    override fun parts(text: String): List<String> = manager().divideMessage(app.getString(org.plainphone.device.R.string.request_sms_text, text))
    override fun send(number: String, id: String, parts: List<String>) {
        val callbacks = ArrayList(parts.indices.map { index ->
            val intent = Intent(app, RequestSmsResultReceiver::class.java)
                .setIdentifier("$id:$index").putExtra("id", id).putExtra("part", index)
            PendingIntent.getBroadcast(app, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        })
        manager().sendMultipartTextMessage(number, null, ArrayList(parts), callbacks, null)
    }
}
object RequestEngine {
    private var current: AppRequests? = null
    @Synchronized fun get(context: Context): AppRequests = current ?: run {
        val app = context.applicationContext.also { require(!it.isDeviceProtectedStorage) }
        AppRequests(RequestFileStore(File(app.noBackupFilesDir, "app-requests")), AndroidRequestSms(app),
            UUID.randomUUID().toString(), System::currentTimeMillis, { LocalDate.now().toEpochDay() }).also { current = it }
    }
}
class RequestSmsResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra("id") ?: return
        val part = intent.getIntExtra("part", -1)
        val result = when (resultCode) {
            Activity.RESULT_OK -> RequestPart.Accepted
            SmsManager.RESULT_ERROR_RADIO_OFF, SmsManager.RESULT_ERROR_NO_SERVICE -> RequestPart.NoSignal
            else -> RequestPart.Failed
        }
        val pending = goAsync()
        Thread {
            try { RequestEngine.get(context).result(id, part, result) } finally { pending.finish() }
        }.start()
    }
}
