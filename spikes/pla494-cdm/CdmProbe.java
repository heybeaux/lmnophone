// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike494;

import android.app.*;
import android.bluetooth.*;
import android.companion.*;
import android.content.*;
import android.os.*;
import android.provider.Settings;
import java.util.concurrent.*;

/** THROWAWAY (PLA-494): CDM association chooser and createBond() from inside lock task. Public APIs only. */
public class CdmProbe extends Instrumentation {
    private volatile Activity top;
    private final CountDownLatch done = new CountDownLatch(1);
    private volatile AssociationInfo info;
    private void out(String key, String value) { Bundle b = new Bundle(); b.putString(key, value); sendStatus(0, b); }
    @Override public void onCreate(Bundle args) { start(); }
    @Override public void onStart() {
        final Context c = getTargetContext();
        ((Application) c.getApplicationContext()).registerActivityLifecycleCallbacks(new Application.ActivityLifecycleCallbacks() {
            public void onActivityResumed(Activity a) { top = a; out("resumed", a.getClass().getSimpleName()); }
            public void onActivityCreated(Activity a, Bundle b) {} public void onActivityStarted(Activity a) {}
            public void onActivityPaused(Activity a) { out("paused", a.getClass().getSimpleName()); }
            public void onActivityStopped(Activity a) {} public void onActivitySaveInstanceState(Activity a, Bundle b) {}
            public void onActivityDestroyed(Activity a) {}
        });
        BluetoothAdapter adapter = c.getSystemService(BluetoothManager.class).getAdapter();
        CompanionDeviceManager cdm = c.getSystemService(CompanionDeviceManager.class);
        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override public void onReceive(Context context, Intent i) {
                if (BluetoothDevice.ACTION_PAIRING_REQUEST.equals(i.getAction()))
                    out("pairingRequestSeen", "variant=" + i.getIntExtra(BluetoothDevice.EXTRA_PAIRING_VARIANT, -1));
                if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(i.getAction()))
                    out("bond", String.valueOf(i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1)));
            }
        };
        try {
            c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            Thread.sleep(8000);
            out("lockTask", String.valueOf(c.getSystemService(ActivityManager.class).getLockTaskModeState()));
            out("location", String.valueOf(Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.LOCATION_MODE, -1)));
            out("bondedBefore", String.valueOf(adapter.getBondedDevices().size()));
            out("associationsBefore", String.valueOf(cdm.getMyAssociations().size()));
            out("topActivity", String.valueOf(top != null));
            IntentFilter f = new IntentFilter(BluetoothDevice.ACTION_PAIRING_REQUEST);
            f.addAction(BluetoothDevice.ACTION_BOND_STATE_CHANGED);
            c.registerReceiver(receiver, f, Context.RECEIVER_EXPORTED);
            AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder().build()).setSingleDevice(false).build();
            cdm.associate(request, c.getMainExecutor(), new CompanionDeviceManager.Callback() {
                @Override public void onAssociationPending(IntentSender sender) {
                    out("associationPending", "true");
                    try { top.startIntentSenderForResult(sender, 494, null, 0, 0, 0); out("chooserStarted", "true"); }
                    catch (Exception e) { out("chooserStartError", e.getClass().getSimpleName()); }
                }
                @Override public void onAssociationCreated(AssociationInfo a) { info = a; out("associationCreated", "id=" + a.getId()); done.countDown(); }
                @Override public void onFailure(CharSequence error) { out("associationFailure", String.valueOf(error)); done.countDown(); }
            });
            out("associationWait", String.valueOf(done.await(150, TimeUnit.SECONDS)));
            if (info != null && info.getDeviceMacAddress() != null) {
                BluetoothDevice d = adapter.getRemoteDevice(info.getDeviceMacAddress().toString().toUpperCase());
                out("deviceClassMajor", d.getBluetoothClass() == null ? "null" : Integer.toHexString(d.getBluetoothClass().getMajorDeviceClass()));
                out("createBond", String.valueOf(d.createBond()));
                for (int j = 0; j < 60 && d.getBondState() != BluetoothDevice.BOND_BONDED; j++) Thread.sleep(1000);
                out("bondReadback", String.valueOf(d.getBondState()));
            }
            out("bondedAfter", String.valueOf(adapter.getBondedDevices().size()));
            out("associationsAfter", String.valueOf(cdm.getMyAssociations().size()));
            out("lockTaskAfter", String.valueOf(c.getSystemService(ActivityManager.class).getLockTaskModeState()));
        } catch (Exception e) { out("error", e.getClass().getSimpleName() + ":" + e.getMessage()); }
        finally {
            try { c.unregisterReceiver(receiver); } catch (Exception ignored) {}
            finish(Activity.RESULT_OK, new Bundle());
        }
    }
}
