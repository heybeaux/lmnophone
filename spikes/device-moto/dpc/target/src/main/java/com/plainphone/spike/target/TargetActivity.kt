package com.plainphone.spike.target

import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

class TargetActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val v = packageManager.getPackageInfo(packageName, 0).longVersionCode
        Log.i("PPSPIKE", "TargetActivity opened, versionCode=$v")
        setContentView(TextView(this).apply { text = "PP Target v$v"; textSize = 32f })
    }
}
