// Copyright 2026 LMNOPhone contributors
// SPDX-License-Identifier: Apache-2.0
// THROWAWAY spike code (PLA-458). Not product code; do not import.
package org.plainphone.spike458;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

/** A synthetic AAC-LC 24 kbps mono clip in an MPEG-4 (.m4a) container. No microphone is used. */
public final class VoiceAac {
    private VoiceAac() {}

    static final int SAMPLE_RATE = 16000;
    static final int BIT_RATE = 24000;

    public static byte[] synthM4a(File out, int seconds) throws IOException {
        SpikeSafety.requireDuration(seconds);
        MediaFormat fmt = MediaFormat.createAudioFormat(MediaFormat.MIMETYPE_AUDIO_AAC, SAMPLE_RATE, 1);
        fmt.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        fmt.setInteger(MediaFormat.KEY_BIT_RATE, BIT_RATE);
        MediaCodec codec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_AUDIO_AAC);
        codec.configure(fmt, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        codec.start();
        MediaMuxer muxer = new MediaMuxer(out.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        int track = -1;
        int totalSamples = seconds * SAMPLE_RATE;
        int fed = 0;
        boolean inputDone = false;
        MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
        try {
            while (true) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10000);
                    if (in >= 0) {
                        ByteBuffer buf = codec.getInputBuffer(in);
                        buf.clear();
                        int n = Math.min(Math.min(1024, buf.remaining() / 2), totalSamples - fed);
                        long ptsUs = fed * 1000000L / SAMPLE_RATE;
                        if (n <= 0) {
                            codec.queueInputBuffer(in, 0, 0, ptsUs, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            for (int i = 0; i < n; i++) {
                                double t = (fed + i) / (double) SAMPLE_RATE;
                                double f = ((int) t % 2 == 0) ? 440.0 : 660.0;
                                short s = (short) (6000 * Math.sin(2 * Math.PI * f * t));
                                buf.put((byte) (s & 0xff));
                                buf.put((byte) ((s >> 8) & 0xff));
                            }
                            codec.queueInputBuffer(in, 0, n * 2, ptsUs, 0);
                            fed += n;
                        }
                    }
                }
                int outIx = codec.dequeueOutputBuffer(info, 10000);
                if (outIx == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    track = muxer.addTrack(codec.getOutputFormat());
                    muxer.start();
                } else if (outIx >= 0) {
                    if ((info.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == 0 && info.size > 0 && track >= 0) {
                        ByteBuffer ob = codec.getOutputBuffer(outIx);
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        muxer.writeSampleData(track, ob, info);
                    }
                    codec.releaseOutputBuffer(outIx, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) break;
                }
            }
        } finally {
            codec.stop();
            codec.release();
            if (track >= 0) muxer.stop();
            muxer.release();
        }
        if (track < 0) throw new IOException("AAC encoder produced no output format");
        return java.nio.file.Files.readAllBytes(out.toPath());
    }
}
