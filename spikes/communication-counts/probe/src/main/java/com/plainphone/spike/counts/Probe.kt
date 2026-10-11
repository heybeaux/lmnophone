// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package com.plainphone.spike.counts

import android.app.Activity
import android.app.PendingIntent
import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.database.ContentObserver
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.os.UserManager
import android.util.Log
import android.widget.TextView
import java.io.File

/** THROWAWAY. No content/number/name/date columns; never log exception messages or URIs. */
class Admin : DeviceAdminReceiver()
class ProbeActivity : Activity() {
    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        setContentView(TextView(this).apply { text = "THROWAWAY metadata probe"; textSize = 24f })
        Probe.command(this, intent)
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); Probe.command(this, intent) }
}
class Control : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Probe.command(context, intent) }
}
object Probe {
    private val perms = listOf("android.permission.READ_CALL_LOG", "android.permission.READ_SMS")
    private var observing = false
    private val observers = mutableListOf<ContentObserver>()
    private fun log(s: String) = Log.i("CountsProbe", "t=${SystemClock.elapsedRealtime()} pid=${Process.myPid()} $s")
    private fun count(c: Context, label: String, uri: String, where: String?) {
        val start = SystemClock.elapsedRealtime()
        try {
            c.contentResolver.query(Uri.parse(uri), QueryPlan.projection.toTypedArray(), where, null, null).use {
                log("query=$label count=${it?.count ?: "null"} ms=${SystemClock.elapsedRealtime()-start}")
            }
        } catch (e: Exception) { log("query=$label error=${e.javaClass.simpleName} ms=${SystemClock.elapsedRealtime()-start}") }
    }
    private fun snapshot(c: Context) {
        log("unlocked=${c.getSystemService(UserManager::class.java).isUserUnlocked} grants=${perms.map { c.checkSelfPermission(it) }}")
        count(c, "calls-new", "content://call_log/calls", QueryPlan.callsNew)
        count(c, "calls-unacknowledged", "content://call_log/calls", QueryPlan.callsUnacknowledged)
        count(c, "calls-unread", "content://call_log/calls", QueryPlan.callsUnread)
        count(c, "sms-unread", "content://sms", QueryPlan.sms)
        count(c, "mms-unread", "content://mms", QueryPlan.mms)
    }
    private fun histogram(c: Context) {
        // Diagnose flag transitions without even projecting flag values. Aggregate only.
        for (type in 1..6) for (new in 0..1) for (read in listOf("0", "1", "null")) {
            count(c, "call-$type-$new-$read", "content://call_log/calls", "type = $type AND new = $new AND is_read ${if (read == "null") "IS NULL" else "= $read"}")
        }
        for (box in 1..1) for (read in 0..1) for (seen in 0..1) {
            count(c,"sms-$box-$read-$seen","content://sms","type = $box AND read = $read AND seen = $seen")
            count(c,"mms-$box-$read-$seen","content://mms","msg_box = $box AND read = $read AND seen = $seen")
        }
    }
    private fun watch(c: Context) {
        if (observing) return
        val app = c.applicationContext
        listOf("calls" to "content://call_log/calls", "combined" to "content://mms-sms/", "sms" to "content://sms", "mms" to "content://mms").forEach { (label, uri) ->
            try {
                val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) { log("observer=$label"); snapshot(app) }
                }
                app.contentResolver.registerContentObserver(Uri.parse(uri), true, observer)
                observers += observer
                log("watch=$label ok")
            } catch(e: Exception) { log("watch=$label error=${e.javaClass.simpleName}") }
        }
        observing = true
    }
    fun command(c: Context, i: Intent) {
        val cmd = i.getStringExtra("cmd") ?: "query"
        log("command=$cmd action=${i.action?.substringAfterLast('.')}")
        try {
            val dpm = c.getSystemService(DevicePolicyManager::class.java)
            val admin = ComponentName(c, Admin::class.java)
            when (cmd) {
                "grant", "deny", "default" -> {
                    val state = when(cmd) { "grant" -> 1; "deny" -> 2; else -> 0 }
                    val target = i.getStringExtra("target") ?: c.packageName
                    val selected = i.getStringExtra("perm")
                    perms.filter { selected == null || it.endsWith(selected) }.forEach {
                        log("permission=${it.substringAfterLast('.')} state=$state before")
                        val result = dpm.setPermissionGrantState(admin, target, it, state)
                        log("permission=${it.substringAfterLast('.')} result=$result state=${dpm.getPermissionGrantState(admin,target,it)}")
                    }
                }
                "watch" -> watch(c)
                "hist" -> histogram(c)
                "install" -> {
                    val installer = c.packageManager.packageInstaller
                    val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
                    val mode = i.getStringExtra("mode") ?: "default"
                    when(mode) { "none" -> params.setWhitelistedRestrictedPermissions(emptySet()); "both" -> params.setWhitelistedRestrictedPermissions(perms.toSet()) }
                    // Test fixture only: retain removable testOnly DPC while exercising sessions.
                    // This flag changes test-APK acceptance, not restricted permission exemptions.
                    if (i.getBooleanExtra("allowTest", false)) {
                        val f = params.javaClass.getField("installFlags")
                        f.setInt(params, f.getInt(params) or 0x4) // INSTALL_ALLOW_TEST
                    }
                    params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                    val id = installer.createSession(params)
                    installer.openSession(id).use { s ->
                        val path = i.getStringExtra("path") ?: c.applicationInfo.sourceDir
                        File(path).inputStream().use { source -> s.openWrite("base.apk",0,-1).use { out -> source.copyTo(out); s.fsync(out) } }
                        val callback = Intent(c, Control::class.java).putExtra("cmd", "installed")
                        val pending = PendingIntent.getBroadcast(c,id,callback,PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                        s.commit(pending.intentSender)
                    }
                    log("install=$mode committed")
                }
                "installed" -> log("install-status=${i.getIntExtra(PackageInstaller.EXTRA_STATUS,-999)} legacy=${i.getIntExtra("android.content.pm.extra.LEGACY_STATUS",-999)}")
            }
        } catch (e: Exception) { log("command-error=${e.javaClass.simpleName}") }
        snapshot(c)
    }
}

/** THROWAWAY: shell lends only test-APK acceptance, never install/grant/restriction privileges. */
class InstallerInstrumentation : android.app.Instrumentation() {
    private var arguments = Bundle()
    override fun onCreate(arguments: Bundle?) { this.arguments = arguments ?: Bundle(); start() }
    override fun onStart() {
        uiAutomation.adoptShellPermissionIdentity("android.permission.INSTALL_TEST_ONLY_PACKAGE")
        try {
            val i = Intent().putExtra("cmd", "install").putExtra("allowTest", true)
                .putExtra("mode", arguments.getString("mode", "default"))
            arguments.getString("path")?.let { i.putExtra("path", it) }
            Probe.command(targetContext, i)
        } finally { uiAutomation.dropShellPermissionIdentity() }
        finish(Activity.RESULT_OK, Bundle())
    }
}
