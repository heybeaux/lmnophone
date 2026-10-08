// THROWAWAY SPIKE FIXTURE (PLA-11). An in-app URL bar and WebView with no web intent filter.
package com.plainphone.fx.common

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.webkit.WebView
import android.widget.EditText
import android.widget.LinearLayout

class UrlBarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.i("PPFX", "PPFX opened pkg=$packageName urlbar")
        val web = WebView(this)
        val bar = EditText(this).apply {
            setSingleLine(); hint = "Type a web address"
            setOnEditorActionListener { v, _, _ -> web.loadUrl(v.text.toString()); true }
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(bar); addView(web, LinearLayout.LayoutParams(-1, -1))
        })
        web.loadData("<h1>URL-bar fixture</h1>", "text/html", "utf-8")
    }
}
