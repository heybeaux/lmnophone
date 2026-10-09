// THROWAWAY SPIKE (PLA-86). Not MVP code.
package org.plainphone.spike.updater;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.Checksum;
import android.content.pm.PackageInstaller;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.util.Collections;

/**
 * adb shell am broadcast -n org.plainphone.spike.updater/.Command -a org.plainphone.spike.updater.RUN
 *   --es op commit|abandon-mine|clear-owner  --es apk <name in external files dir>  --es checksum right|wrong  --es kill before|after
 */
public class Command extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String op = i.getStringExtra("op");
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        try {
            if ("clear-owner".equals(op)) {
                // Cleanup only: updated builds are not test-only, so adb cannot remove the admin.
                ((android.app.admin.DevicePolicyManager) c.getSystemService(Context.DEVICE_POLICY_SERVICE)).clearDeviceOwnerApp(c.getPackageName());
                Log.w(c, "cleared device owner");
                return;
            }
            if ("abandon-mine".equals(op)) {
                for (PackageInstaller.SessionInfo s : pi.getMySessions()) { pi.abandonSession(s.getSessionId()); Log.w(c, "abandoned " + s.getSessionId()); }
                return;
            }
            File apk = new File(c.getExternalFilesDir(null), i.getStringExtra("apk"));
            byte[] bytes = java.nio.file.Files.readAllBytes(apk.toPath());
            PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
            p.setAppPackageName(c.getPackageName());
            p.setSize(bytes.length);
            int id = pi.createSession(p);
            PackageInstaller.Session s = pi.openSession(id);
            try (OutputStream out = s.openWrite("base.apk", 0, bytes.length)) { out.write(bytes); s.fsync(out); }
            String checksum = i.getStringExtra("checksum");
            if (checksum != null) {
                byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
                if ("wrong".equals(checksum)) digest[0] ^= 1;
                s.setChecksums("base.apk", Collections.singletonList(new Checksum(Checksum.TYPE_WHOLE_SHA256, digest)), null);
                Log.w(c, "setChecksums " + checksum);
            }
            byte[] readBack;
            try (InputStream in = s.openRead("base.apk")) { readBack = in.readAllBytes(); }
            Log.w(c, "session " + id + " written; readback sha256=" + hex(MessageDigest.getInstance("SHA-256").digest(readBack)));
            if ("before".equals(i.getStringExtra("kill"))) { Log.w(c, "killing before commit"); android.os.Process.killProcess(android.os.Process.myPid()); }
            Intent status = new Intent(c, Status.class);
            PendingIntent pending = PendingIntent.getBroadcast(c, id, status, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Log.w(c, "commit() call");
            s.commit(pending.getIntentSender());
            Log.w(c, "commit() returned");
            if ("after".equals(i.getStringExtra("kill"))) { Log.w(c, "killing right after commit returned"); android.os.Process.killProcess(android.os.Process.myPid()); }
            try (OutputStream out = s.openWrite("base.apk", 0, bytes.length)) { Log.w(c, "write after commit allowed?!"); }
            catch (Exception e) { Log.w(c, "write after commit refused: " + e.getClass().getSimpleName()); }
        } catch (Exception e) {
            Log.w(c, "error " + e);
        }
    }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
