// THROWAWAY SPIKE (PLA-86). Not MVP code.
package org.plainphone.spike.updater;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;

public class Status extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        Log.w(c, "status session=" + i.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1) + " status=" + i.getIntExtra(PackageInstaller.EXTRA_STATUS, 99)
            + " message=" + i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE));
    }
}
