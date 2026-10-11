// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.counts

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.os.UserManager
import android.provider.Telephony
import android.telecom.TelecomManager
import android.util.Log
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.SynchronousQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import org.plainphone.PlainphoneAdminReceiver
import org.plainphone.device.CommunicationCountPermissions

/** Shared with the actual Home launch path: a count never describes a different entry. */
object CommunicationEntryIntents {
    fun phone() = Intent(Intent.ACTION_DIAL)
    fun messages() = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MESSAGING)
}

/** Activity-owned worker and observers. Nothing reads a row value or stores a count. */
class AndroidCommunicationCounts(context: Context, changed: () -> Unit) : AutoCloseable {
    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    // At most two blocked queries, with no queued backlog. A later trigger retries when a worker frees.
    private val executors = CountKind.entries.associateWith {
        ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, SynchronousQueue<Runnable>())
    }
    private val cancellations = Executors.newFixedThreadPool(2)
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
    private fun access(kind: CountKind): CountAccess {
        val pm = app.packageManager
        val role = when (kind) {
            CountKind.CALLS -> app.getSystemService(TelecomManager::class.java).defaultDialerPackage
            CountKind.MESSAGES -> Telephony.Sms.getDefaultSmsPackage(app)
        }
        val installed = runCatching {
            val info = pm.getApplicationInfo(kind.supportedPackage, 0)
            info.enabled && info.flags and ApplicationInfo.FLAG_INSTALLED != 0
        }.getOrDefault(false)
        val intent = if (kind == CountKind.CALLS) CommunicationEntryIntents.phone() else CommunicationEntryIntents.messages()
        val home = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.let {
            it.packageName.takeIf { _ -> it.enabled && it.applicationInfo.enabled }
        }
        return CountAccess(app.getSystemService(UserManager::class.java).isUserUnlocked,
            app.checkSelfPermission(kind.permission) == PackageManager.PERMISSION_GRANTED, role, installed, home)
    }
    val controller = CountController(
        CountPreferences(object : CountPreferenceStore {
            override fun read(key: String): Boolean? = if (prefs.contains(key)) prefs.getBoolean(key, false) else null
            // SharedPreferences writes the single boolean atomically. An interrupted disk write can
            // retain the last durable choice; no count or freshness timestamp ever reaches storage.
            override fun write(key: String, value: Boolean) { prefs.edit().putBoolean(key, value).apply() }
        }),
        object : CountWork {
            override fun schedule(delayMillis: Long, block: () -> Unit): CountCancellation {
                val task = Runnable(block)
                main.postDelayed(task, delayMillis)
                return CountCancellation { main.removeCallbacks(task) }
            }
        },
        CountReader { kind, complete ->
            val signal = CancellationSignal()
            val future = try {
                executors.getValue(kind).submit {
                    cancellation.set(signal)
                    try {
                        val result = if (!signal.isCanceled && runCatching { access(kind).allowed(kind) }.getOrDefault(false)) source.read(kind) else null
                        main.post { complete(result) }
                    } finally { cancellation.remove() }
                }
            } catch (_: RejectedExecutionException) {
                main.post { complete(null) }
                null
            }
            CountCancellation {
                future?.cancel(true)
                // Cancellation may itself cross Binder. Never put it on the UI timer thread.
                runCatching { cancellations.execute { signal.cancel() } }
            }
        }, ::access, SystemClock::elapsedRealtime,
        { uri, descendants, callback ->
            val observer = object : ContentObserver(main) { override fun onChange(selfChange: Boolean) = callback() }
            app.contentResolver.registerContentObserver(Uri.parse(uri), descendants, observer)
            CountCancellation { app.contentResolver.unregisterContentObserver(observer) }
        },
        { CommunicationCountPermissions(app, ComponentName(app, PlainphoneAdminReceiver::class.java)).grant() },
        { main.post { changed() } },
    )
    private val packages = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (CountKind.entries.any { it.supportedPackage == intent.data?.schemeSpecificPart }) controller.externalChange()
        }
    }
    init {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addAction(Intent.ACTION_PACKAGE_REMOVED); addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(packages, filter, Context.RECEIVER_NOT_EXPORTED)
        else app.registerReceiver(packages, filter)
    }
    override fun close() {
        app.unregisterReceiver(packages)
        controller.close(); executors.values.forEach { it.shutdownNow() }; cancellations.shutdown()
    }
}
