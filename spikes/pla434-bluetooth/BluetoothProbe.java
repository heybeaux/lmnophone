// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike434;

import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.bluetooth.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;

/** THROWAWAY: location-off discovery and pairing under existing lock task. */
public class BluetoothProbe extends Instrumentation {
    private final Set<String> found = new HashSet<>();
    private volatile BluetoothDevice peer;
    private volatile boolean ended;
    private Bundle args;
    private void out(String key, String value) {
        Bundle b = new Bundle(); b.putString(key, value); sendStatus(0, b);
    }
    @Override public void onCreate(Bundle args) { this.args = args; start(); }
    @Override public void onStart() {
        Context c = getTargetContext();
        BluetoothAdapter a = c.getSystemService(BluetoothManager.class).getAdapter();
        if ("peer".equals(args.getString("mode"))) {
            out("setName", String.valueOf(a.setName("PLA434 Test Peer")));
            out("bondedCount", String.valueOf(a.getBondedDevices().size()));
            finish(Activity.RESULT_OK, new Bundle()); return;
        }
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent i) {
                if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(i.getAction())) ended = true;
                if (BluetoothDevice.ACTION_FOUND.equals(i.getAction())) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
                    if (d != null) {
                        found.add(d.getAddress());
                        String name = d.getName();
                        out("found", "count=" + found.size() + ",named=" + (name != null && !name.isEmpty()));
                        if ("PLA434 Test Peer".equals(name)) peer = d;
                    }
                }
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(i.getAction()))
                    out("bond", String.valueOf(i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)));
            }
        };
        try {
            DevicePolicyManager dpm = c.getSystemService(DevicePolicyManager.class);
            out("deviceOwner", String.valueOf(dpm.isDeviceOwnerApp(c.getPackageName())));
            dpm.setPermissionGrantState(new ComponentName(c, "org.plainphone.PlainphoneAdminReceiver"),
                c.getPackageName(), "android.permission.BLUETOOTH_SCAN", DevicePolicyManager.PERMISSION_GRANT_STATE_GRANTED);
            out("scanPermission", String.valueOf(c.checkSelfPermission("android.permission.BLUETOOTH_SCAN")));
            c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(10000);
            int lock = c.getSystemService(ActivityManager.class).getLockTaskModeState();
            out("lockTask", String.valueOf(lock));
            int location = Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.LOCATION_MODE, -1);
            out("location", String.valueOf(location));
            out("bondedBefore", String.valueOf(a.getBondedDevices().size()));
            if (lock != ActivityManager.LOCK_TASK_MODE_LOCKED || location != 0) throw new IllegalStateException("precondition");
            IntentFilter f = new IntentFilter();
            f.addAction(BluetoothDevice.ACTION_FOUND); f.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            c.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            out("startDiscovery", String.valueOf(a.startDiscovery()));
            for (int j=0; j<35 && !ended; j++) Thread.sleep(1000);
            out("discoveryEnded", String.valueOf(ended));
            out("foundCount", String.valueOf(found.size()));
            out("peerFound", String.valueOf(peer != null));
            a.cancelDiscovery();
            if (peer != null && "true".equals(args.getString("pair"))) {
                out("createBond", String.valueOf(peer.createBond()));
                for (int j=0; j<60 && peer.getBondState()!=BluetoothDevice.BOND_BONDED; j++) Thread.sleep(1000);
                out("bondReadback", String.valueOf(peer.getBondState()));
            }
            out("bondedAfter", String.valueOf(a.getBondedDevices().size()));
        } catch (Exception e) { out("error", e.getClass().getSimpleName()+":"+e.getMessage()); }
        finally {
            try { a.cancelDiscovery(); c.unregisterReceiver(receiver); } catch (Exception ignored) {}
            finish(Activity.RESULT_OK, new Bundle());
        }
    }
}
