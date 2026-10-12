// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
package org.plainphone.spike437;
import android.content.*;
import android.net.wifi.WifiManager;
import android.os.Build;
/** THROWAWAY UI setup. Shell-only fixture. Disconnects; never rewrites a saved network. */
public class DisconnectReceiver extends BroadcastReceiver {
    private static volatile boolean disconnecting;
    public void onReceive(Context c, Intent i) {
        if(!Build.HARDWARE.equals("ranchu") || Build.VERSION.SDK_INT!=35)return;
        disconnecting=i.getBooleanExtra("disconnect",false);
        if(!disconnecting)return;
        final WifiManager w=c.getSystemService(WifiManager.class);
        new Thread(()->{try{for(int n=0;n<120&&disconnecting;n++){w.disconnect();Thread.sleep(500);}}catch(Exception ignored){}finally{disconnecting=false;}}).start();
    }
}
