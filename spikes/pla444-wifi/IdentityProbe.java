// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike444;
import android.content.*;
import android.net.wifi.*;
import android.provider.Settings;
public class IdentityProbe extends BroadcastReceiver {
 public void onReceive(Context c,Intent i){
  if(!android.os.Build.HARDWARE.equals("ranchu"))return;
  WifiManager w=c.getSystemService(WifiManager.class);
  android.util.Log.i("WifiIdentityProbe","location="+Settings.Secure.getInt(c.getContentResolver(),Settings.Secure.LOCATION_MODE,-1));
  android.util.Log.i("WifiIdentityProbe","infoId="+w.getConnectionInfo().getNetworkId()+" completed="+(w.getConnectionInfo().getSupplicantState()==SupplicantState.COMPLETED));
  java.util.Set<Integer> current=new java.util.HashSet<>();
  for(WifiConfiguration v:w.getConfiguredNetworks())if(v.status==WifiConfiguration.Status.CURRENT)current.add(v.networkId);
  android.util.Log.i("WifiIdentityProbe","distinctCurrentCount="+current.size()+" currentIds="+current);
 }
}
