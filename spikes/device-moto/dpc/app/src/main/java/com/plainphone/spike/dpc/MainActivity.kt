// THROWAWAY SPIKE (PLA-5). Home screen + release button + launch tester.
package com.plainphone.spike.dpc

import android.app.Activity
import android.app.ActivityManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private var releaseArmedAt = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        statusView = TextView(this).apply { textSize = 13f }
        col.addView(TextView(this).apply { text = "Plainphone spike (throwaway)"; textSize = 24f })
        col.addView(statusView)
        col.addView(button("RELEASE: clear all + remove Device Owner (tap twice)", Color.rgb(160, 0, 0)) { onRelease() })
        col.addView(button("Phone") { launchPkg("com.google.android.dialer") })
        col.addView(button("Messages") { launchPkg("com.google.android.apps.messaging") })
        col.addView(button("Camera") { launchPkg("com.motorola.camera3") })
        col.addView(button("Emergency dialer screen (does not call)") {
            tryStart("emergency", Intent("com.android.phone.EmergencyDialer.DIAL").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        col.addView(button("Ask for microphone permission") { requestPermissions(arrayOf(android.Manifest.permission.RECORD_AUDIO), 1) })
        col.addView(button("Start lock task") { Spike.prefs(this).edit().putBoolean("locked", true).commit(); lock() })
        col.addView(button("Stop lock task") { Spike.prefs(this).edit().putBoolean("locked", false).commit(); runCatching { stopLockTask() } ; refresh() })
        setContentView(ScrollView(this).apply { addView(col) })
        handle(intent)
    }

    private fun button(label: String, color: Int? = null, onClick: () -> Unit) = Button(this).apply {
        text = label
        textSize = 18f
        gravity = Gravity.CENTER
        color?.let { setTextColor(it) }
        setOnClickListener { onClick() }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    override fun onResume() {
        super.onResume()
        if (Spike.prefs(this).getBoolean("locked", false)) lock()
        refresh()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        Spike.log("permission result ${permissions.toList()} -> ${grantResults.toList()}")
    }

    private fun refresh() {
        statusView.text = Commands.status(this)
    }

    private fun lock() {
        val am = getSystemService(ActivityManager::class.java)
        val permitted = Spike.dpm(this).isLockTaskPermitted(packageName)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE && permitted) {
            startLockTask()
            Spike.log("startLockTask() called")
        } else {
            Spike.log("lock(): state=${am.lockTaskModeState} permitted=$permitted")
        }
        Handler(Looper.getMainLooper()).postDelayed({ refresh() }, 500)
    }

    private fun onRelease() {
        val now = System.currentTimeMillis()
        if (now - releaseArmedAt > 3000) {
            releaseArmedAt = now
            statusView.text = "Tap RELEASE again within 3 s to confirm."
            return
        }
        statusView.text = Release.run(this, this)
    }

    private fun launchPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) Spike.log("no launch intent for $pkg") else tryStart("app $pkg", i)
    }

    private fun tryStart(label: String, i: Intent) {
        try {
            startActivity(i)
            Spike.log("launch [$label] startActivity returned normally: $i")
        } catch (t: Throwable) {
            Spike.log("launch [$label] threw ${t.javaClass.simpleName}: ${t.message}")
        }
    }

    /** Ops relayed from CommandReceiver, so launches happen from this (allowed) activity. */
    private fun handle(intent: Intent?) {
        when (intent?.getStringExtra("op")) {
            "lt-start" -> lock()
            "lt-stop" -> { runCatching { stopLockTask() }; refresh() }
            "crash" -> Handler(Looper.getMainLooper()).post { throw RuntimeException("PPSPIKE deliberate crash") }
            "launch" -> launchSpec(intent.getStringExtra("spec").orEmpty())
        }
        intent?.removeExtra("op")
    }

    private fun launchSpec(spec: String) {
        val kv = spec.split(Regex("\\s+")).filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }
        var i = Intent(kv["action"] ?: Intent.ACTION_VIEW)
        kv["data"]?.let { i.data = Uri.parse(it) }
        kv["type"]?.let { if (i.data != null) i.setDataAndType(i.data, it) else i.type = it }
        kv["comp"]?.let { i.component = ComponentName.unflattenFromString(it) }
        kv["pkg"]?.let { i.setPackage(it) }
        kv["text"]?.let { i.putExtra(Intent.EXTRA_TEXT, it.replace('_', ' ')) }
        if (kv["chooser"] == "1") i = Intent.createChooser(i, "Spike share test")
        if (kv["newtask"] == "1") i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        tryStart(spec, i)
    }
}
