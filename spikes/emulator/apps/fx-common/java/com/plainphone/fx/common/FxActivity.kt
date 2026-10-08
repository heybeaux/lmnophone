// THROWAWAY SPIKE FIXTURE (PLA-11). Shows and logs which fixture opened, and with what data.
package com.plainphone.fx.common

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

open class FxActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val line = "PPFX opened pkg=$packageName action=${intent?.action} data=${intent?.dataString}"
        Log.i("PPFX", line)
        setContentView(TextView(this).apply { text = line; textSize = 22f; setPadding(32, 160, 32, 32) })
    }
}
