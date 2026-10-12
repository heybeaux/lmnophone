// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike444;
import android.app.*;
import android.app.admin.DevicePolicyManager;
import android.content.*;
import android.net.wifi.*;
import android.os.*;
import android.provider.Settings;
import java.util.*;
/** THROWAWAY. Assigned emulator only. No network names in output. */
public class ForgetProbe extends BroadcastReceiver {
 void out(String k,Object v){android.util.Log.i("WifiForgetProbe",k+"="+v);}
 public void onReceive(Context c,Intent i){PendingResult p=goAsync();new Thread(()->{
  DevicePolicyManager d=c.getSystemService(DevicePolicyManager.class);
  ComponentName a=new ComponentName(c,"org.plainphone.PlainphoneAdminReceiver");
  WifiManager w=c.getSystemService(WifiManager.class);int old=Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1);int added=-1;
  try{
   if(!Build.HARDWARE.equals("ranchu")||Build.VERSION.SDK_INT!=35||!d.isDeviceOwnerApp(c.getPackageName()))throw new IllegalStateException();
   out("locked",c.getSystemService(ActivityManager.class).getLockTaskModeState()==1);
   if(c.getSystemService(ActivityManager.class).getLockTaskModeState()!=1)throw new IllegalStateException();
   out("currentIdReadableLocationOff",w.getConnectionInfo().getNetworkId()>=0);
   d.setLocationEnabled(a,true);Thread.sleep(1000);
   int original=w.getConnectionInfo().getNetworkId();if(original<0)throw new IllegalStateException();
   List<WifiConfiguration> before=w.getConfiguredNetworks();out("savedCountBefore",before.size());
   WifiConfiguration x=new WifiConfiguration();x.SSID="\"Jo-forget-probe\"";x.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
   if(before.stream().anyMatch(v->x.SSID.equals(v.SSID)))throw new IllegalStateException();
   added=w.addNetwork(x);out("ownAdd",added>=0);if(added<0)throw new IllegalStateException();
   out("ownAddedCount",w.getConfiguredNetworks().size());out("ownRemove",w.removeNetwork(added));added=-1;
   out("ownRemovedCount",w.getConfiguredNetworks().size());
   out("selectOriginal",w.enableNetwork(original,true));Thread.sleep(2000);
   int external=i.getIntExtra("external",-1);
   if(external<0||external==original||before.stream().noneMatch(v->v.networkId==external))throw new IllegalStateException();
   out("externalRemove",w.removeNetwork(external));Thread.sleep(1000);
   out("savedCountAfter",w.getConfiguredNetworks().size());
   out("originalStillSaved",w.getConfiguredNetworks().stream().anyMatch(v->v.networkId==original));
   out("originalStillConnected",w.getConnectionInfo().getNetworkId()==original&&w.getConnectionInfo().getSupplicantState()==SupplicantState.COMPLETED);
   out("lockedAfter",c.getSystemService(ActivityManager.class).getLockTaskModeState()==1);out("complete",true);
  }catch(Exception e){out("error",e.getClass().getSimpleName());}
  finally{try{if(added>=0)w.removeNetwork(added);d.setLocationEnabled(a,old!=0);out("locationRestored",Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1)==old);}catch(Exception e){out("cleanupError",e.getClass().getSimpleName());}p.finish();}
 }).start();}
}
