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
 *   --es op commit|abandon-mine|list-mine|clear-owner  --es apk <name in external files dir>  --es checksum right|wrong  --es kill before|after
 *   PLA-140 X6: --es op commit --es then abandon-all|abandon-uncommitted --ei delay <ms>; abandon-mine --es which all|uncommitted
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
                abandon(c, pi, !"uncommitted".equals(i.getStringExtra("which")));
                return;
            }
            if ("list-mine".equals(op)) {
                for (PackageInstaller.SessionInfo s : pi.getMySessions()) Log.w(c, "mine " + describe(s));
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
            String then = i.getStringExtra("then");
            if (then != null) {
                // PLA-140 X6: does abandoning an already-committed, still-installing session cancel the install?
                long delay = i.getIntExtra("delay", 0);
                if (delay > 0) Thread.sleep(delay);
                if (then.endsWith("-when-committed")) {
                    // Poll until the platform reports the session committed (or gone), then act.
                    long start = System.nanoTime(); PackageInstaller.SessionInfo info;
                    while ((info = pi.getSessionInfo(id)) != null && !info.isCommitted()) Thread.sleep(1);
                    Log.w(c, "X6 session " + id + (info == null ? " gone" : " committed=true") + " after " + (System.nanoTime() - start) / 1000 + " us");
                    if (info == null || then.startsWith("poll")) return;
                    then = then.replace("-when-committed", "");
                }
                Log.w(c, "X6 " + then + " after " + delay + " ms");
                abandon(c, pi, "abandon-all".equals(then));
                for (PackageInstaller.SessionInfo left : pi.getMySessions()) Log.w(c, "still mine " + describe(left));
                return;
            }
            if ("after".equals(i.getStringExtra("kill"))) { Log.w(c, "killing right after commit returned"); android.os.Process.killProcess(android.os.Process.myPid()); }
            try (OutputStream out = s.openWrite("base.apk", 0, bytes.length)) { Log.w(c, "write after commit allowed?!"); }
            catch (Exception e) { Log.w(c, "write after commit refused: " + e.getClass().getSimpleName()); }
        } catch (Exception e) {
            Log.w(c, "error " + e);
        }
    }
    /** Abandon own sessions: all of them, or only those not yet committed. Logs isCommitted for each. */
    static void abandon(Context c, PackageInstaller pi, boolean all) {
        for (PackageInstaller.SessionInfo s : pi.getMySessions()) {
            if (!all && s.isCommitted()) { Log.w(c, "kept committed " + describe(s)); continue; }
            try { pi.abandonSession(s.getSessionId()); Log.w(c, "abandoned " + describe(s)); }
            catch (Exception e) { Log.w(c, "abandon threw " + e + " for " + describe(s)); }
        }
    }
    static String describe(PackageInstaller.SessionInfo s) {
        return "session=" + s.getSessionId() + " committed=" + s.isCommitted() + " active=" + s.isActive() + " progress=" + s.getProgress();
    }
    private static String hex(byte[] b) { StringBuilder s = new StringBuilder(); for (byte x : b) s.append(String.format("%02x", x)); return s.toString(); }
}
