// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.settings

import android.app.Activity
import android.app.Instrumentation
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.wifi.WifiManager
import android.provider.Settings
import org.plainphone.device.settings.AndroidWifi
import org.plainphone.device.settings.WifiController

/** Owned API 35 emulator only. No setting writes; onOpen grants only our own fine location. */
class WifiReadCheck : Instrumentation() {
    override fun onCreate(arguments: Bundle) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            check(android.os.Build.HARDWARE in listOf("ranchu", "goldfish"))
            val app = targetContext
            val mode = Settings.Secure.getString(app.contentResolver, Settings.Secure.LOCATION_MODE)
            val source = AndroidWifi(app)
            val controller = WifiController(source)
            val beforePermission = app.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
            val platform = app.getSystemService(WifiManager::class.java)
            @Suppress("DEPRECATION")
            val rawNamed = try { platform.connectionInfo?.ssid?.let { it != WifiManager.UNKNOWN_SSID } == true } catch (_: SecurityException) { false }
            val rawScanReadable = try { platform.scanResults; true } catch (_: SecurityException) { false }
            result.putString("platformBefore", "named=$rawNamed scanReadable=$rawScanReadable")
            controller.refresh()
            result.putString("before", "fineGranted=${beforePermission == PackageManager.PERMISSION_GRANTED} connected=${controller.state.connected} named=${controller.state.network != null} scanReadable=${controller.state.nearby != null}")
            check(controller.state.connected)
            if (beforePermission != PackageManager.PERMISSION_GRANTED) {
                check(controller.state.network == null && controller.state.nearby == null)
            }
            source.onOpen()
            controller.refresh()
            check(app.checkSelfPermission("android.permission.ACCESS_FINE_LOCATION") == PackageManager.PERMISSION_GRANTED)
            check(controller.state.connected && controller.state.network == "AndroidWifi")
            check(controller.state.nearby?.contains("AndroidWifi") == true)
            check(Settings.Secure.getString(app.contentResolver, Settings.Secure.LOCATION_MODE) == mode)
            result.putString("after", "fineGranted=true connected=true name=AndroidWifi scanContainsAndroidWifi=true locationModeUnchanged=true")
            result.putString("outcome", "PASS")
            finish(Activity.RESULT_OK, result)
        } catch (e: Exception) {
            result.putString("outcome", "FAIL ${e.javaClass.simpleName}")
            finish(Activity.RESULT_CANCELED, result)
        }
    }
}
