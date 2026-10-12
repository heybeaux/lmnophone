// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458). Must hold the SMS role while it runs (the provider ignores writes otherwise).
package org.plainphone.spike458;

import android.app.Activity;
import android.content.ContentValues;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Telephony;
import java.io.OutputStream;

/** Writes one inbox MMS (m-retrieve-conf) from a fictional number with a single audio/amr part. */
public class Seeder extends Activity {
    static final String FROM = "5550100";

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        try {
            Log.i("seeder defaultSms=" + Telephony.Sms.getDefaultSmsPackage(this));
            byte[] amr = VoiceMms.synthAmr(getIntent().getIntExtra("seconds", 4));
            long now = System.currentTimeMillis() / 1000L;
            long thread = Telephony.Threads.getOrCreateThreadId(this, FROM);
            ContentValues m = new ContentValues();
            m.put("thread_id", thread);
            m.put("date", now);
            m.put("date_sent", now);
            m.put("msg_box", 1);
            m.put("read", 0);
            m.put("seen", 0);
            m.put("m_type", 132);
            m.put("v", 18);
            m.put("ct_t", "application/vnd.wap.multipart.mixed");
            m.put("m_id", "pla458-seed-" + now);
            m.put("tr_id", "pla458-seed-" + now);
            m.put("text_only", 0);
            Uri msg = getContentResolver().insert(Uri.parse("content://mms/inbox"), m);
            Log.i("seeder inserted msg=" + msg);
            if (msg == null) return;
            String id = msg.getLastPathSegment();
            ContentValues from = new ContentValues();
            from.put("address", FROM);
            from.put("type", 137);
            from.put("charset", 106);
            Log.i("seeder addr from=" + getContentResolver().insert(Uri.parse("content://mms/" + id + "/addr"), from));
            ContentValues to = new ContentValues();
            to.put("address", "insert-address-token");
            to.put("type", 151);
            to.put("charset", 106);
            Log.i("seeder addr to=" + getContentResolver().insert(Uri.parse("content://mms/" + id + "/addr"), to));
            ContentValues p = new ContentValues();
            p.put("mid", Long.parseLong(id));
            p.put("ct", "audio/amr");
            p.put("cid", "<voice.amr>");
            p.put("cl", "voice.amr");
            p.put("name", "voice.amr");
            Uri part = getContentResolver().insert(Uri.parse("content://mms/" + id + "/part"), p);
            Log.i("seeder part=" + part);
            try (OutputStream o = getContentResolver().openOutputStream(part)) { o.write(amr); }
            Log.i("seeder wrote audio bytes=" + amr.length + " sha256=" + Probe_sha(amr));
        } catch (Throwable t) {
            Log.e("seeder threw", t);
        } finally {
            finish();
        }
    }

    static String Probe_sha(byte[] b) throws Exception {
        StringBuilder sb = new StringBuilder();
        for (byte x : java.security.MessageDigest.getInstance("SHA-256").digest(b)) sb.append(String.format("%02x", x));
        return sb.substring(0, 16);
    }
}
