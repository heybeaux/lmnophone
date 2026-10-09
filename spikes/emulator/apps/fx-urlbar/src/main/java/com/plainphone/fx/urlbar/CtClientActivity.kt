// THROWAWAY SPIKE FIXTURE (PLA-23). A real Custom Tabs client (androidx.browser), using the usual pattern:
// ask CustomTabsClient for a provider; if there is none, fall back (fallback=view: plain ACTION_VIEW; fallback=webview:
// the app's own WebView). With a provider it calls CustomTabsIntent.launchUrl.
package com.plainphone.fx.urlbar

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.browser.customtabs.CustomTabsClient
import androidx.browser.customtabs.CustomTabsIntent
import com.plainphone.fx.common.UrlBarActivity

class CtClientActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val url = Uri.parse(intent.getStringExtra(Intent.EXTRA_TEXT) ?: "https://example.com/ct-client")
        val provider = CustomTabsClient.getPackageName(this, emptyList())
        Log.i("PPFX", "PPFX ctclient provider=$provider url=$url fallback=${intent.getStringExtra("fallback")}")
        try {
            when {
                provider != null -> CustomTabsIntent.Builder().build().apply { intent.setPackage(provider) }.launchUrl(this, url)
                intent.getStringExtra("fallback") == "webview" ->
                    startActivity(Intent(this, UrlBarActivity::class.java).putExtra(Intent.EXTRA_TEXT, url.toString()))
                else -> CustomTabsIntent.Builder().build().launchUrl(this, url)
            }
            Log.i("PPFX", "PPFX ctclient launch returned normally")
        } catch (t: Throwable) {
            Log.i("PPFX", "PPFX ctclient launch threw ${t.javaClass.simpleName}: ${t.message}")
        }
        finish()
    }
}
