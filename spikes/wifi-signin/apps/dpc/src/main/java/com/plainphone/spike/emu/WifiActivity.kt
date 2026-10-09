// THROWAWAY SPIKE (PLA-11, reused by PLA-18). Plainphone's own Wi-Fi screen (WifiManager.addNetwork as Device Owner).
package com.plainphone.spike.emu

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class WifiActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val out = TextView(this).apply { textSize = 14f }
        val ssid = EditText(this).apply { hint = "Network name"; setSingleLine() }
        val psk = EditText(this).apply { hint = "Password (blank = open)"; setSingleLine() }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(32, 96, 32, 32) }
        col.addView(TextView(this).apply { text = "Wi-Fi (Plainphone)"; textSize = 24f })
        col.addView(ssid); col.addView(psk)
        col.addView(Button(this).apply {
            text = "Join"
            setOnClickListener {
                val r = Wifi.add(this@WifiActivity, ssid.text.toString(), psk.text.toString().ifEmpty { null })
                Emu.log("wifi screen: $r"); out.text = r + "\n" + Wifi.list(this@WifiActivity)
            }
        })
        col.addView(out)
        out.text = Wifi.list(this)
        setContentView(col)
    }
}
