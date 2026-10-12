// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike437;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.wifi.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;
/** THROWAWAY: assigned API 35 emulator only. Output contains no network names. */
public class WifiProbe extends BroadcastReceiver {
    void out(String k, Object v) { android.util.Log.i("WifiJoinProbe",k+"="+String.valueOf(v)); }
    private Context context; private PendingResult pending;
    public void onReceive(Context c, Intent i) { context=c; pending=goAsync(); new Thread(this::runProbe).start(); }
    boolean connected(WifiManager w, int id) {
        ConnectivityManager cm=context.getSystemService(ConnectivityManager.class);
        boolean transport=Arrays.stream(cm.getAllNetworks()).anyMatch(n->{ NetworkCapabilities c=cm.getNetworkCapabilities(n); return c!=null&&c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI); });
        boolean location=Settings.Secure.getInt(context.getContentResolver(),Settings.Secure.LOCATION_MODE,-1)!=0;
        return transport && (!location || w.getConnectionInfo().getNetworkId()==id);
    }
    boolean waitConnected(WifiManager w,int id) throws Exception { for(int i=0;i<30;i++){if(connected(w,id))return true;Thread.sleep(1000);}return false; }
    public void runProbe() {
        Context c=context; DevicePolicyManager d=c.getSystemService(DevicePolicyManager.class);
        ComponentName admin=new ComponentName(c,"org.plainphone.PlainphoneAdminReceiver");
        WifiManager w=c.getSystemService(WifiManager.class); int before=Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1); int added=-1;
        try {
            if(!Build.HARDWARE.equals("ranchu") || Build.VERSION.SDK_INT!=35)throw new IllegalStateException();
            if(!d.isDeviceOwnerApp(c.getPackageName()))throw new IllegalStateException();

            c.startActivity(c.getPackageManager().getLaunchIntentForPackage(c.getPackageName()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); Thread.sleep(1000);
            out("lockTask",c.getSystemService(ActivityManager.class).getLockTaskModeState()); if(c.getSystemService(ActivityManager.class).getLockTaskModeState()!=1)throw new IllegalStateException(); out("locationBefore",before);
            d.setLocationEnabled(admin,true); out("locationOn",Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1));
            List<ScanResult> scan=w.getScanResults();out("namedScanCount",scan.stream().filter(s->s.SSID!=null&&!s.SSID.isEmpty()).count());
            List<WifiConfiguration> saved=w.getConfiguredNetworks();out("savedBeforeCount",saved.size());
            ScanResult target=scan.stream().filter(s->s.SSID!=null&&!s.SSID.isEmpty()&&!s.capabilities.contains("PSK")&&!s.capabilities.contains("SAE")&&!s.capabilities.contains("EAP")&&!s.capabilities.contains("WEP")).findFirst().orElseThrow(IllegalStateException::new);
            WifiConfiguration config=new WifiConfiguration();config.SSID="\"Sam probe\"";config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
            if(saved.stream().anyMatch(x->config.SSID.equals(x.SSID)))throw new IllegalStateException();
            added=w.addNetwork(config);out("addNewAccepted",added>=0);if(added<0)throw new IllegalStateException();
            out("addedRemoved",w.removeNetwork(added));added=-1;
            final String targetSsid="\""+target.SSID+"\"";
            WifiConfiguration existing=saved.stream().filter(x->targetSsid.equals(x.SSID)).findFirst().orElseThrow(IllegalStateException::new);
            int id=existing.networkId;out("joinUsesExistingUnmodifiedConfig",true);
            out("enableAccepted",w.enableNetwork(id,true));out("joined",waitConnected(w,id));
            d.setLocationEnabled(admin,false);out("locationOff",Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1));Thread.sleep(3000);out("stayedConnected",connected(w,id));
            out("wifiOffAccepted",w.setWifiEnabled(false));Thread.sleep(3000);out("wifiOnAccepted",w.setWifiEnabled(true));out("autoReconnected",waitConnected(w,id));
            d.setLocationEnabled(admin,true);out("reconnectedIdentityConfirmed",connected(w,id));
            out("savedAfterCount",w.getConfiguredNetworks().size());out("lockTaskAfter",c.getSystemService(ActivityManager.class).getLockTaskModeState());
            out("outcome","COMPLETE");
        } catch(Exception e){out("error",e.getClass().getSimpleName());}
        finally {try{if(added>=0)out("addedRemoved",w.removeNetwork(added));d.setLocationEnabled(admin,before!=0);out("locationRestored",Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1)==before);}catch(Exception e){out("cleanupError",e.getClass().getSimpleName());}pending.finish();}
    }
}
