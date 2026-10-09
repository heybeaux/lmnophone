// THROWAWAY SPIKE FIXTURE (PLA-11, extended PLA-23). An in-app URL bar and WebView with no web intent filter.
// PLA-23: EXTRA_TEXT loads a URL directly; extra client=0 leaves the WebView without a WebViewClient, so link taps
// leave the app as ACTION_VIEW (what many apps do) instead of navigating inside the WebView.
package com.plainphone.fx.common

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.LinearLayout

class UrlBarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("PPFX", "PPFX opened pkg=$packageName urlbar")
        val web = WebView(this)
        if (intent.getStringExtra("client") != "0") web.webViewClient = object : WebViewClient() {
            override fun onPageStarted(v: WebView, url: String, favicon: Bitmap?) { Log.i("PPFX", "PPFX webview started $url") }
            override fun onPageFinished(v: WebView, url: String) { Log.i("PPFX", "PPFX webview finished $url title=${v.title}") }
        }
        val bar = EditText(this).apply {
            setSingleLine(); hint = "Type a web address"
            setOnEditorActionListener { v, _, _ -> web.loadUrl(v.text.toString()); true }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar); addView(web, LinearLayout.LayoutParams(-1, -1))
        })
        val url = intent.getStringExtra(Intent.EXTRA_TEXT)
        if (url != null) { bar.setText(url); web.loadUrl(url) } else web.loadData("<h1>URL-bar fixture</h1>", "text/html", "utf-8")
    }
}
