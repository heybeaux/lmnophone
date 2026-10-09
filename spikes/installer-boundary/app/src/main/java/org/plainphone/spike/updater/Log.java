// THROWAWAY SPIKE (PLA-86). Not MVP code.
package org.plainphone.spike.updater;

import android.content.Context;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.FileWriter;

/** Appends to files/log.txt so evidence survives the process being killed and replaced. */
final class Log {
    static synchronized void w(Context c, String what) {
        long vc = -1;
        try { vc = c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode(); } catch (PackageManager.NameNotFoundException ignored) { }
        String line = System.currentTimeMillis() + " pid=" + android.os.Process.myPid() + " vc=" + vc + " " + what;
        android.util.Log.i("PLA86", line);
        try (FileWriter f = new FileWriter(new File(c.getFilesDir(), "log.txt"), true)) { f.write(line + "\n"); f.flush(); } catch (Exception ignored) { }
    }
}
