// THROWAWAY SPIKE (PLA-11). Home screen, launch tester and Wi-Fi notice. Not MVP code.
package com.plainphone.spike.emu

import android.app.Activity
import android.app.ActivityManager
import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView
    private lateinit var netView: TextView
    private lateinit var forget: Button
    private val ui = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        col.addView(TextView(this).apply { text = "Plainphone emu spike (throwaway)"; textSize = 22f })
        netView = TextView(this).apply { textSize = 18f }
        forget = button("Forget this network") { Emu.log("forget: " + Wifi.forgetCurrent(this)); refresh() }
        col.addView(netView); col.addView(forget)
        col.addView(button("Phone") { launchPkg("com.google.android.dialer") })
        col.addView(button("Messages") { launchPkg("com.google.android.apps.messaging") })
        col.addView(button("Camera") { tryStart("camera", Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)) })
        col.addView(button("Emergency") {
            tryStart("emergency", Intent(Floor.EMERGENCY_DIAL).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        })
        col.addView(button("Wi-Fi") { tryStart("wifi", Intent(this, WifiActivity::class.java)) })
        col.addView(button("Start lock task") { Emu.prefs(this).edit().putBoolean("locked", true).commit(); lock() })
        col.addView(button("Stop lock task") { Emu.prefs(this).edit().putBoolean("locked", false).commit(); runCatching { stopLockTask() }; refresh() })
        statusView = TextView(this).apply { textSize = 11f }
        col.addView(statusView)
        setContentView(ScrollView(this).apply { addView(col) })
        handle(intent)
    }

    private fun button(label: String, onClick: () -> Unit) = Button(this).apply {
        text = label; textSize = 18f; setOnClickListener { onClick() }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    override fun onResume() {
        super.onResume()
        if (Emu.prefs(this).getBoolean("locked", false)) lock()
        refresh()
        ui.postDelayed(tick, 1000)
    }

    override fun onPause() { super.onPause(); ui.removeCallbacks(tick) }

    private val tick = object : Runnable { override fun run() { refresh(); ui.postDelayed(this, 1000) } }

    private fun refresh() {
        val msg = Emu.netMessage()
        netView.text = msg ?: ""
        forget.visibility = if (msg == null) android.view.View.GONE else android.view.View.VISIBLE
        statusView.text = Commands.status(this)
    }

    private fun lock() {
        val am = getSystemService(ActivityManager::class.java)
        val permitted = Emu.dpm(this).isLockTaskPermitted(packageName)
        if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE && permitted) {
            startLockTask(); Emu.log("startLockTask() called")
        } else Emu.log("lock(): state=${am.lockTaskModeState} permitted=$permitted")
    }

    private fun launchPkg(pkg: String) {
        val i = packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) Emu.log("no launch intent for $pkg") else tryStart("app $pkg", i)
    }

    private fun tryStart(label: String, i: Intent) {
        try { startActivity(i); Emu.log("launch [$label] returned normally: $i") }
        catch (t: Throwable) { Emu.log("launch [$label] threw ${t.javaClass.simpleName}: ${t.message}") }
    }

    @Deprecated("spike")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        Emu.log("activity result req=$requestCode result=$resultCode")
    }

    private fun handle(intent: Intent?) {
        when (intent?.getStringExtra("op")) {
            "lt-start" -> lock()
            "lt-stop" -> { runCatching { stopLockTask() }; refresh() }
            "launch" -> { val s = intent.getStringExtra("spec").orEmpty(); tryStart(s, buildIntent(s)) }
            "browser-role-request" -> {
                val rm = getSystemService(RoleManager::class.java)
                Emu.log("browser role available=${rm.isRoleAvailable(RoleManager.ROLE_BROWSER)} held=${rm.isRoleHeld(RoleManager.ROLE_BROWSER)}")
                @Suppress("DEPRECATION")
                runCatching { startActivityForResult(rm.createRequestRoleIntent(RoleManager.ROLE_BROWSER), 7) }
                    .onFailure { Emu.log("role request threw $it") }
            }
        }
        intent?.removeExtra("op")
    }

    companion object {
        /** spec: action=... data=... comp=pkg/cls pkg=... type=... text=... newtask=1 chooser=1 ct=1 */
        fun buildIntent(spec: String): Intent {
            val kv = spec.split(Regex("\\s+")).filter { "=" in it }.associate { it.substringBefore("=") to it.substringAfter("=") }
            var i = Intent(kv["action"] ?: Intent.ACTION_VIEW)
            kv["data"]?.let { i.data = Uri.parse(it) }
            kv["type"]?.let { if (i.data != null) i.setDataAndType(i.data, it) else i.type = it }
            kv["comp"]?.let { i.component = ComponentName.unflattenFromString(it) }
            kv["pkg"]?.let { i.setPackage(it) }
            kv["cat"]?.let { i.addCategory(it) }
            kv["text"]?.let { i.putExtra(Intent.EXTRA_TEXT, it.replace('_', ' ')) }
            if (kv["ct"] == "1") i.putExtras(Bundle().apply { putBinder("android.support.customtabs.extra.SESSION", null) })
            if (kv["chooser"] == "1") i = Intent.createChooser(i, "Spike chooser test")
            if (kv["newtask"] == "1") i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return i
        }
    }
}
