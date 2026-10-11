// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.UserManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.device.CommunicationCountPermissions

/** Activity-owned worker and observers. Nothing reads a row value or stores a count. */
class AndroidCommunicationCounts(context: Context, changed: () -> Unit) : AutoCloseable {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private val cancellation = ThreadLocal<CancellationSignal>()
    private val prefs = app.createDeviceProtectedStorageContext().getSharedPreferences("home_preferences", Context.MODE_PRIVATE)
    private val source = CommunicationCountSource(object : CountQueryPort {
        override fun isUserUnlocked() = app.getSystemService(UserManager::class.java).isUserUnlocked
        override fun hasPermission(permission: String) = app.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
        override fun query(query: CountQuery): CountCursor? {
            check(Looper.myLooper() != Looper.getMainLooper())
            cancellation.get()?.throwIfCanceled()
            val cursor = app.contentResolver.query(Uri.parse(query.uri), query.projection.toTypedArray(),
                query.selection, query.selectionArgs, query.sortOrder, cancellation.get()) ?: return null
            return object : CountCursor {
                override val count: Int get() = cursor.count
                override fun close() = cursor.close()
            }
        }
    }) { source, outcome, type ->
        Log.i("CommunicationCounts", "$source $outcome${type?.let { " $it" } ?: ""}")
    }
    val controller = CountController(
        CountPreferences(object : CountPreferenceStore {
            override fun read(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
            override fun write(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        }),
        object : CountWork {
            override fun schedule(delayMillis: Long, block: () -> Unit): CountCancellation {
                val signal = CancellationSignal()
                val future = executor.schedule({
                    cancellation.set(signal)
                    try { if (!signal.isCanceled) block() } finally { cancellation.remove() }
                }, delayMillis, TimeUnit.MILLISECONDS)
                return CountCancellation { signal.cancel(); future.cancel(true) }
            }
        }, source::read,
        { uri, descendants, callback ->
            val observer = object : ContentObserver(main) { override fun onChange(selfChange: Boolean) = callback() }
            app.contentResolver.registerContentObserver(Uri.parse(uri), descendants, observer)
            CountCancellation { app.contentResolver.unregisterContentObserver(observer) }
        },
        { CommunicationCountPermissions(app, ComponentName(app, PlainphoneAdminReceiver::class.java)).grant() },
        { main.post { changed() } },
    )
    override fun close() { controller.close(); executor.shutdownNow() }
}
