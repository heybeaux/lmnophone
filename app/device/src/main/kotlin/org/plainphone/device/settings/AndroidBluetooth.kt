// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.device.settings

import android.Manifest
import android.app.admin.DevicePolicyManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/**
 * Reads bonded devices only: no scan, Location, pairing, connection or Bluetooth-state writes.
 * https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getBondedDevices()
 * Names are ephemeral display data and must never be logged or persisted.
 */
class AndroidBluetooth(context: Context) : BluetoothPort {
    private val app = context.applicationContext
    private fun hasPermission() = app.checkSelfPermission(if (Build.VERSION.SDK_INT >= 31)
        Manifest.permission.BLUETOOTH_CONNECT else Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED

    fun onOpen() {
        if (Build.VERSION.SDK_INT < 31 || hasPermission()) return
        try {
            val dpm = app.getSystemService(DevicePolicyManager::class.java)
            if (dpm.isDeviceOwnerApp(app.packageName)) {
                dpm.setPermissionGrantState(ComponentName(app.packageName, "org.plainphone.PlainphoneAdminReceiver"),
                    app.packageName, Manifest.permission.BLUETOOTH_CONNECT,
                    DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED)
            }
        } catch (_: Exception) { /* The subsequent independent permission check decides availability. */ }
    }

    override fun read(): BluetoothFacts {
        if (Build.VERSION.SDK_INT >= 31) {
            if (app.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED)
                throw SecurityException()
        } else if (app.checkSelfPermission(Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
            throw SecurityException()
        }
        val adapter = app.getSystemService(BluetoothManager::class.java)?.adapter
            ?: return BluetoothFacts(BluetoothAdapterState.Absent)
        fun state() = when (adapter.state) {
            BluetoothAdapter.STATE_ON -> BluetoothAdapterState.On
            BluetoothAdapter.STATE_TURNING_ON -> BluetoothAdapterState.TurningOn
            BluetoothAdapter.STATE_TURNING_OFF -> BluetoothAdapterState.TurningOff
            else -> BluetoothAdapterState.Off
        }
        val before = state()
        if (before != BluetoothAdapterState.On) return BluetoothFacts(before)
        val names = adapter.bondedDevices.map { it.name }
        // If Bluetooth changed during the read, do not show the now-stale paired list.
        val after = state()
        return BluetoothFacts(after, if (after == BluetoothAdapterState.On) names else emptyList())
    }
}
