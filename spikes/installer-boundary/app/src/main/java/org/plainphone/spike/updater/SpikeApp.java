// THROWAWAY SPIKE (PLA-86). Not MVP code.
package org.plainphone.spike.updater;

import android.app.Application;
import android.content.pm.PackageInstaller;

public class SpikeApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        StringBuilder s = new StringBuilder();
        for (PackageInstaller.SessionInfo i : getPackageManager().getPackageInstaller().getMySessions()) s.append(i.getSessionId()).append(i.isSealed() ? ":sealed " : ":open ");
        Log.w(this, "process start; my sessions=[" + s.toString().trim() + "]");
    }
}
