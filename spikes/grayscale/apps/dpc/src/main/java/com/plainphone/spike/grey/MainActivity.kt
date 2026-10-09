// THROWAWAY SPIKE (PLA-38). Home screen with a greyscale toggle and lock task. Not MVP code.
package com.plainphone.spike.grey

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView

class MainActivity : Activity() {
    private lateinit var statusView: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        col.addView(TextView(this).apply { text = "Plainphone grey spike (throwaway)"; textSize = 22f })
        // Colour swatches so a screenshot shows whether the display is grey.
        for (c in listOf(Color.RED, Color.GREEN, Color.BLUE)) col.addView(TextView(this).apply {
            text = "swatch"; setBackgroundColor(c); setTextColor(Color.WHITE); textSize = 20f; setPadding(16, 16, 16, 16)
        })
        col.addView(Button(this).apply { text = "Grey on"; setOnClickListener { Grey.log(Grey.setGrey(this@MainActivity, true)); refresh() } })
        col.addView(Button(this).apply { text = "Grey off"; setOnClickListener { Grey.log(Grey.setGrey(this@MainActivity, false)); refresh() } })
        statusView = TextView(this).apply { textSize = 12f }
        col.addView(statusView)
        setContentView(col)
        handle(intent)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); handle(intent) }

    override fun onResume() {
        super.onResume()
        if (getSharedPreferences("grey", 0).getBoolean("locked", false)) lock()
        refresh()
    }

    private fun handle(i: Intent?) {
        when (i?.getStringExtra("op")) {
            "lt-start" -> lock()
            "lt-stop" -> try { stopLockTask(); Grey.log("stopLockTask ok") } catch (t: Throwable) { Grey.log("stopLockTask $t") }
        }
        refresh()
    }

    private fun lock() {
        try { startLockTask(); Grey.log("startLockTask ok") } catch (t: Throwable) { Grey.log("startLockTask $t") }
    }

    private fun refresh() { if (::statusView.isInitialized) statusView.text = Grey.status(this) }
}
