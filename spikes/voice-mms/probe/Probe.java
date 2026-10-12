// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458). Driven by: adb shell am start -n <pkg>/org.plainphone.spike458.Probe --es cmd <cmd>
package org.plainphone.spike458;

import android.app.Activity;
import android.app.PendingIntent;
import android.app.admin.DevicePolicyManager;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.database.ContentObserver;
import android.database.Cursor;
import android.media.MediaExtractor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.PersistableBundle;
import android.provider.Telephony;
import android.telephony.CarrierConfigManager;
import android.telephony.SmsManager;
import android.telephony.SubscriptionManager;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;

/** No SMS role: holds SEND_SMS + READ_SMS (+ READ_PHONE_STATE for carrier config) only. */
public class Probe extends Activity {
    static final String MESSAGES = "com.google.android.apps.messaging";
    static final String FAKE_TO = "5550100"; // fictional 555-01xx number; the emulator has no MMSC anyway
    private static ContentObserver observer;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        handle(getIntent());
    }

    @Override protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        handle(i);
    }

    private void handle(Intent i) {
        String cmd = i.getStringExtra("cmd");
        Log.i("cmd=" + cmd);
        if (cmd == null) return;
        try {
            SpikeSafety.requireEmulatorHardware(android.os.Build.HARDWARE);
            switch (cmd) {
                case "env": env(); break;
                case "config": config(); break;
                case "encode": encode(i.getIntExtra("seconds", 5)); break;
                case "sendA": sendA(i.getBooleanExtra("override", false)); break;
                case "sendB": sendB(i.getStringExtra("mode"), i.getStringExtra("to") != null ? i.getStringExtra("to") : FAKE_TO, i.getBooleanExtra("noaddr", false)); break;
                case "watch": watch(); break;
                case "list": dumpMms(this, "list"); break;
                case "play": play(); break;
                case "parts": parts(); break;
                case "lockpkgs": lockPkgs(i.getBooleanExtra("messages", false)); break;
                case "lock": startLockTask(); Log.i("startLockTask called"); break;
                case "unlock": stopLockTask(); Log.i("stopLockTask called"); break;
                default: Log.i("unknown cmd " + cmd);
            }
        } catch (Throwable t) {
            Log.e("cmd " + cmd + " threw", t);
        }
    }

    private void env() {
        RoleManager rm = getSystemService(RoleManager.class);
        Log.i("env sdk=" + android.os.Build.VERSION.SDK_INT
                + " defaultSms=" + Telephony.Sms.getDefaultSmsPackage(this)
                + " probeHoldsSmsRole=" + rm.isRoleHeld(RoleManager.ROLE_SMS)
                + " SEND_SMS=" + checkSelfPermission(android.Manifest.permission.SEND_SMS)
                + " READ_SMS=" + checkSelfPermission(android.Manifest.permission.READ_SMS)
                + " defaultSmsSubId=" + SubscriptionManager.getDefaultSmsSubscriptionId());
    }

    private void config() {
        SmsManager sms = getSystemService(SmsManager.class);
        Bundle cfg = sms.getCarrierConfigValues();
        StringBuilder sb = new StringBuilder("SmsManager.getCarrierConfigValues:");
        for (String k : new java.util.TreeSet<String>(cfg.keySet())) sb.append(' ').append(k).append('=').append(cfg.get(k));
        Log.i(sb.toString());
        Log.i("MMS_CONFIG_MAX_MESSAGE_SIZE=" + cfg.getInt(SmsManager.MMS_CONFIG_MAX_MESSAGE_SIZE, -1)
                + " MMS_CONFIG_MMS_ENABLED=" + cfg.getBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, false));
        try {
            CarrierConfigManager ccm = getSystemService(CarrierConfigManager.class);
            PersistableBundle pb = ccm.getConfigForSubId(SubscriptionManager.getDefaultSmsSubscriptionId());
            Log.i("CarrierConfigManager KEY_MMS_MAX_MESSAGE_SIZE_INT=" + pb.getInt(CarrierConfigManager.KEY_MMS_MAX_MESSAGE_SIZE_INT, -1)
                    + " KEY_MMS_MMS_ENABLED_BOOL=" + pb.getBoolean(CarrierConfigManager.KEY_MMS_MMS_ENABLED_BOOL)
                    + " KEY_MMS_MAX_IMAGE_WIDTH_INT=" + pb.getInt(CarrierConfigManager.KEY_MMS_MAX_IMAGE_WIDTH_INT, -1)
                    + " KEY_MMS_UA_PROF_URL_STRING=" + pb.getString(CarrierConfigManager.KEY_MMS_UA_PROF_URL_STRING));
        } catch (Throwable t) {
            Log.e("CarrierConfigManager failed", t);
        }
    }

    private File share(String name) {
        File dir = new File(getFilesDir(), "share");
        dir.mkdirs();
        return new File(dir, name);
    }

    private void encode(int seconds) throws Exception {
        byte[] amr = VoiceMms.synthAmr(seconds);
        try (FileOutputStream o = new FileOutputStream(share("voice.amr"))) { o.write(amr); }
        Log.i("encode seconds=" + seconds + " bytes=" + amr.length + " firstFrameToc=0x"
                + Integer.toHexString(amr.length > 6 ? amr[6] & 0xff : -1) + " sha256=" + sha(amr));
    }

    private void sendA(boolean override) throws Exception {
        File amr = share("voice.amr");
        if (!amr.exists()) encode(5);
        byte[] data = java.nio.file.Files.readAllBytes(amr.toPath());
        byte[] pdu = VoiceMms.sendReq(FAKE_TO, "T" + Long.toHexString(System.currentTimeMillis()), "audio/amr", "voice.amr", data);
        try (FileOutputStream o = new FileOutputStream(share("send.pdu"))) { o.write(pdu); }
        Log.i("sendA pdu bytes=" + pdu.length + " sha256=" + sha(pdu));
        dumpMms(this, "before-sendA");
        Uri uri = Files.uriFor("send.pdu");
        grantUriPermission("com.android.phone", uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        PendingIntent sent = PendingIntent.getBroadcast(this, 1, new Intent(this, Sent.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_MUTABLE);
        Bundle overrides = null;
        if (override) {
            overrides = new Bundle();
            overrides.putBoolean(SmsManager.MMS_CONFIG_MMS_ENABLED, true);
        }
        getSystemService(SmsManager.class).sendMultimediaMessage(this, uri, null, overrides, sent);
        Log.i("sendA sendMultimediaMessage returned (no exception) overrides=" + overrides);
    }

    private void sendB(String mode, String to, boolean noAddr) {
        SpikeSafety.requireFakeRecipient(to);
        if (!"send".equals(mode) && !"sendto".equals(mode) && !"chooser".equals(mode)) {
            throw new IllegalArgumentException("Use send, sendto or chooser");
        }
        File amr = share("voice.amr");
        if (!amr.isFile()) throw new IllegalStateException("Run encode before sendB");
        Uri uri = Files.uriFor("voice.amr");
        Intent i;
        if ("sendto".equals(mode)) {
            i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + to));
        } else {
            i = new Intent(Intent.ACTION_SEND);
            i.setType("audio/amr");
            if (!noAddr) i.putExtra("address", to);
        }
        i.putExtra(Intent.EXTRA_STREAM, uri);
        i.setClipData(ClipData.newRawUri("voice", uri));
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        if (!"chooser".equals(mode)) i.setPackage(MESSAGES);
        Log.i("sendB mode=" + mode + " to=" + to + " noAddr=" + noAddr + " exists=" + amr.exists() + " resolves=" + getPackageManager().queryIntentActivities(i, 0));
        watch();
        startActivity("chooser".equals(mode) ? Intent.createChooser(i, null) : i);
        Log.i("sendB startActivity returned");
    }

    private void watch() {
        if (observer != null) return;
        observer = new ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override public void onChange(boolean self, Uri uri) {
                Log.i("observer onChange " + uri);
                dumpMms(Probe.this, "observer");
            }
        };
        getContentResolver().registerContentObserver(Uri.parse("content://mms"), true, observer);
        getContentResolver().registerContentObserver(Uri.parse("content://mms-sms"), true, observer);
        Log.i("watching content://mms and content://mms-sms");
    }

    static void dumpMms(Context c, String label) {
        try (Cursor m = c.getContentResolver().query(Uri.parse("content://mms"),
                new String[] {"_id", "thread_id", "msg_box", "m_type", "date", "creator", "ct_t", "resp_st", "read"},
                null, null, "_id ASC")) {
            if (m == null) { Log.i(label + " content://mms query returned null"); return; }
            Log.i(label + " content://mms rows=" + m.getCount());
            while (m.moveToNext()) {
                long id = m.getLong(0);
                StringBuilder sb = new StringBuilder(label + " mms _id=" + id + " thread=" + m.getLong(1)
                        + " msg_box=" + m.getInt(2) + " m_type=" + m.getInt(3) + " date=" + m.getLong(4)
                        + " creator=" + m.getString(5) + " ct_t=" + m.getString(6) + " resp_st=" + m.getString(7)
                        + " read=" + m.getInt(8));
                try (Cursor p = c.getContentResolver().query(Uri.parse("content://mms/" + id + "/part"),
                        new String[] {"_id", "ct", "name", "cl"}, null, null, null)) {
                    while (p != null && p.moveToNext()) {
                        sb.append(" | part _id=").append(p.getLong(0)).append(" ct=").append(p.getString(1))
                                .append(" name=").append(p.getString(2)).append(" cl=").append(p.getString(3));
                    }
                }
                try (Cursor a = c.getContentResolver().query(Uri.parse("content://mms/" + id + "/addr"),
                        new String[] {"type"}, null, null, null)) {
                    sb.append(" | addrTypes=");
                    while (a != null && a.moveToNext()) sb.append(a.getInt(0)).append(',');
                }
                Log.i(sb.toString());
            }
        } catch (Throwable t) {
            Log.e(label + " dumpMms threw", t);
        }
    }

    private void parts() {
        try (Cursor p = getContentResolver().query(Uri.parse("content://mms/part"),
                new String[] {"_id", "mid", "ct", "name"}, null, null, "_id ASC")) {
            Log.i("parts content://mms/part rows=" + (p == null ? "null" : p.getCount()));
            while (p != null && p.moveToNext()) {
                Log.i("parts part _id=" + p.getLong(0) + " mid=" + p.getLong(1) + " ct=" + p.getString(2) + " name=" + p.getString(3));
            }
        }
    }

    private void play() throws Exception {
        long messageId = -1;
        try (Cursor messages = getContentResolver().query(Uri.parse("content://mms/inbox"),
                new String[] {"_id"}, "m_type=132", null, "_id DESC")) {
            if (messages != null && messages.moveToFirst()) messageId = messages.getLong(0);
        }
        if (messageId < 0) throw new IllegalStateException("No downloaded inbox MMS; run the seeder first");
        long partId = -1;
        try (Cursor p = getContentResolver().query(Uri.parse("content://mms/" + messageId + "/part"),
                new String[] {"_id", "ct", "mid"}, "ct LIKE 'audio/%'", null, "_id DESC")) {
            Log.i("play inbox mms=" + messageId + " audio parts found=" + (p == null ? "null" : p.getCount()));
            if (p != null && p.moveToFirst()) partId = p.getLong(0);
        }
        if (partId < 0) throw new IllegalStateException("The inbox MMS has no audio part");
        Uri part = Uri.parse("content://mms/part/" + partId);
        byte[] bytes;
        try (InputStream in = getContentResolver().openInputStream(part)) {
            bytes = readAll(in);
        }
        Log.i("play read " + part + " bytes=" + bytes.length + " header=" + new String(bytes, 0, Math.min(6, bytes.length), "US-ASCII").trim()
                + " sha256=" + sha(bytes));
        MediaExtractor ex = new MediaExtractor();
        ex.setDataSource(this, part, null);
        Log.i("play extractor tracks=" + ex.getTrackCount() + " format0=" + (ex.getTrackCount() > 0 ? ex.getTrackFormat(0) : null));
        ex.release();
        final MediaPlayer mp = new MediaPlayer();
        mp.setDataSource(this, part);
        mp.prepare();
        Log.i("play MediaPlayer prepared durationMs=" + mp.getDuration());
        mp.start();
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() {
                Log.i("play after 1500ms isPlaying=" + mp.isPlaying() + " positionMs=" + mp.getCurrentPosition());
                mp.release();
            }
        }, 1500);
    }

    private void lockPkgs(boolean withMessages) {
        DevicePolicyManager dpm = getSystemService(DevicePolicyManager.class);
        ComponentName admin = new ComponentName(this, Admin.class);
        String[] pkgs = withMessages ? new String[] {getPackageName(), MESSAGES} : new String[] {getPackageName()};
        dpm.setLockTaskPackages(admin, pkgs);
        Log.i("lockpkgs isDeviceOwner=" + dpm.isDeviceOwnerApp(getPackageName()) + " set="
                + java.util.Arrays.toString(dpm.getLockTaskPackages(admin)));
    }

    static byte[] readAll(InputStream in) throws java.io.IOException {
        java.io.ByteArrayOutputStream o = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) o.write(buf, 0, n);
        return o.toByteArray();
    }

    static String sha(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x));
        return sb.substring(0, 16);
    }
}
