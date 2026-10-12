// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458). Not product code; do not import.
package org.plainphone.spike458;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

/** Smallest helpers the spike needs: a synthetic AMR-NB clip and an m-send-req PDU encoder. */
public final class VoiceMms {
    private VoiceMms() {}

    /**
     * Encodes {@code seconds} of a quiet two-tone signal as AMR-NB 12.2 kbps in the RFC 4867
     * storage format ("#!AMR\n" + frames), using the platform encoder. No microphone is used.
     */
    public static byte[] synthAmr(int seconds) throws IOException {
        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AMR_NB, 8000, 1);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, 12200);
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AMR_NB);
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write("#!AMR\n".getBytes("US-ASCII"));
        int totalSamples = seconds * 8000;
        int fed = 0;
        boolean inputDone = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        while (true) {
            if (!inputDone) {
                int in = codec.dequeueInputBuffer(10000);
                if (in >= 0) {
                    ByteBuffer buf = codec.getInputBuffer(in);
                    buf.clear();
                    int n = Math.min(160, totalSamples - fed);
                    if (n <= 0) {
                        codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        inputDone = true;
                    } else {
                        for (int i = 0; i < n; i++) {
                            double t = (fed + i) / 8000.0;
                            double f = ((int) t % 2 == 0) ? 440.0 : 660.0;
                            short s = (short) (6000 * Math.sin(2 * Math.PI * f * t));
                            buf.put((byte) (s & 0xff));
                            buf.put((byte) ((s >> 8) & 0xff));
                        }
                        codec.queueInputBuffer(in, 0, n * 2, fed * 125L, 0);
                        fed += n;
                    }
                }
            }
            int outIx = codec.dequeueOutputBuffer(info, 10000);
            if (outIx >= 0) {
                if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0) {
                    ByteBuffer ob = codec.getOutputBuffer(outIx);
                    byte[] frame = new byte[info.size];
                    ob.position(info.offset);
                    ob.get(frame);
                    out.write(frame);
                }
                codec.releaseOutputBuffer(outIx, false);
                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
            }
        }
        codec.stop();
        codec.release();
        return out.toByteArray();
    }

    /**
     * Minimal OMA MMS 1.2 m-send-req (OMA-TS-MMS_ENC): Message-Type, Transaction-Id, Version,
     * From (insert-address-token), To, Content-Type multipart/mixed, then one body part.
     */
    public static byte[] sendReq(String to, String transactionId, String partType, String partName,
            byte[] data) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        o.write(0x8C); o.write(0x80);                 // X-Mms-Message-Type: m-send-req
        o.write(0x98); text(o, transactionId);        // X-Mms-Transaction-ID
        o.write(0x8D); o.write(0x92);                 // X-Mms-MMS-Version: 1.2
        o.write(0x89); o.write(0x01); o.write(0x81);  // From: insert-address-token
        o.write(0x97); text(o, to + "/TYPE=PLMN");    // To
        o.write(0x84); o.write(0xA3);                 // Content-Type: application/vnd.wap.multipart.mixed (last header)
        uintvar(o, 1);                                // one part
        ByteArrayOutputStream ct = new ByteArrayOutputStream();
        text(ct, partType);                           // media type as text, e.g. audio/amr
        ct.write(0x85); text(ct, partName);           // Name parameter (WSP 0x05)
        ByteArrayOutputStream h = new ByteArrayOutputStream();
        valueLength(h, ct.size()); ct.writeTo(h);     // Content-type-value, general form
        h.write(0xC0); h.write('"'); h.write(("<" + partName + ">").getBytes("US-ASCII")); h.write(0); // Content-ID
        h.write(0x8E); text(h, partName);             // Content-Location
        uintvar(o, h.size());
        uintvar(o, data.length);
        h.writeTo(o);
        o.write(data);
        return o.toByteArray();
    }

    static void text(ByteArrayOutputStream o, String s) throws IOException {
        byte[] b = s.getBytes("US-ASCII");
        if (b.length > 0 && (b[0] & 0xff) >= 0x80) o.write(0x7F);
        o.write(b);
        o.write(0);
    }

    static void valueLength(ByteArrayOutputStream o, int len) {
        if (len < 31) {
            o.write(len);
        } else {
            o.write(31);
            uintvar(o, len);
        }
    }

    static void uintvar(ByteArrayOutputStream o, long v) {
        int n = 1;
        while ((v >>> (7 * n)) != 0) n++;
        for (int i = n - 1; i >= 0; i--) {
            int b = (int) ((v >>> (7 * i)) & 0x7f);
            o.write(i == 0 ? b : (b | 0x80));
        }
    }
}
