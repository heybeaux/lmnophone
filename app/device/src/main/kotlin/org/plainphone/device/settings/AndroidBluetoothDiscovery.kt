// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.*
import android.content.pm.PackageManager
import android.os.Build

/** Discovery only. No Location queries, bonding, settings intents or radio-state writes. */
class AndroidBluetoothDiscovery(context: Context) : BluetoothDiscoveryPort {
    private val app = context.applicationContext
    private val adapter get() = app.getSystemService(BluetoothManager::class.java)?.adapter
    private var receiver: BroadcastReceiver? = null
    fun onOpen() {
        AndroidBluetooth(app).onOpen()
        if (Build.VERSION.SDK_INT < 31) return
        try {
            val dpm = app.getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(app.packageName) &&
                app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED)
                dpm.setPermissionGrantState(ComponentName(app.packageName, "org.plainphone.PlainphoneAdminReceiver"),
                    app.packageName, Manifest.permission.BLUETOOTH_SCAN, DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
        } catch (_: Exception) { /* available() independently reads permission back. */ }
    }
    override fun available(): Boolean = Build.VERSION.SDK_INT >= 31 &&
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
        app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
        adapter?.state == BluetoothAdapter.STATE_ON

    override fun start(onEvent: (BluetoothDiscoveryEvent) -> Unit): Boolean {
        if (Build.VERSION.SDK_INT < 31 ||
            app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED ||
            !available()) return false
        val listener = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                try {
                    if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED ||
                        !available()) { onEvent(BluetoothDiscoveryEvent.Unavailable); return }
                    when (intent.action) {
                        BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> onEvent(BluetoothDiscoveryEvent.Finished)
                        BluetoothDevice.ACTION_FOUND -> {
                            @Suppress("DEPRECATION")
                            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE) ?: return
                            onEvent(BluetoothDiscoveryEvent.Found(device.address, device.name,
                                device.bondState != BluetoothDevice.BOND_NONE))
                        }
                    }
                } catch (_: Exception) { onEvent(BluetoothDiscoveryEvent.Unavailable) }
            }
        }
        val filter = IntentFilter().apply {
            addAction(BluetoothDevice.ACTION_FOUND)
            addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
            addAction(BluetoothAdapter.ACTION_STATE_CHANGED)
        }
        // Exported on purpose: the Bluetooth app sends these as uid 1002, not system, so a
        // RECEIVER_NOT_EXPORTED filter is never a target (API 35 emulator, PLA-488). They are
        // protected broadcasts, so other apps still can't send them here.
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(listener, filter, Context.RECEIVER_EXPORTED)
        else { @Suppress("UnspecifiedRegisterReceiverFlag") app.registerReceiver(listener, filter) }
        receiver = listener
        return adapter?.startDiscovery() == true
    }
    override fun stop() {
        val previous = receiver ?: return
        receiver = null
        try { app.unregisterReceiver(previous) }
        finally {
            if (Build.VERSION.SDK_INT >= 31 &&
                app.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED)
                adapter?.cancelDiscovery()
        }
    }
}
