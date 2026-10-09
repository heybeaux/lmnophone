// THROWAWAY SPIKE (PLA-11, reused by PLA-18). Link-catcher: shows "This link is blocked" with the host only. Ignores extras.
package com.plainphone.spike.emu

import android.app.Activity
import android.net.Uri
import android.os.Bundle
import android.widget.TextView

class LinkActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val raw = intent?.dataString.orEmpty()
        val host = if (raw.length > 2000) null else runCatching { Uri.parse(raw).host }.getOrNull()
        Emu.log("LINK-CATCHER host=$host scheme=${intent?.data?.scheme} hasCtSession=${intent?.extras?.containsKey("android.support.customtabs.extra.SESSION")}")
        setContentView(TextView(this).apply {
            text = "This link is blocked\n\n${host ?: "(unreadable link)"}"; textSize = 26f; setPadding(48, 200, 48, 48)
        })
    }
}
