package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

@TargetApi(21)
public class MuxerMP4 implements Encoder {
    EncoderInfo info;
    MediaCodec encoder;
    MediaMuxer muxer;
    int audioTrackIndex;
    long NumSamples;


    public static Map<String, MediaCodecInfo> findEncoder(String mime) {
        Map<String, MediaCodecInfo> map = new HashMap<>();

        mime = mime.toLowerCase();

        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);

            if (!codecInfo.isEncoder()) {
                continue;
            }

            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                String t = types[j].toLowerCase();
                if (t.startsWith(mime)) {
                    map.put(t, codecInfo);
                }
            }
        }
        return map;
    }

    public static String prefered(String pref, Map<String, MediaCodecInfo> map) {
        pref = pref.toLowerCase();
        Iterator i = map.keySet().iterator();
        while (i.hasNext()) {
            String m = (String) i.next();
            m = m.toLowerCase();
            if (m.startsWith(pref))
                return m;
        }
        i = map.keySet().iterator();
        while (i.hasNext()) {
            String m = (String) i.next();
            return m;
        }
        return null;
    }

    public static MediaFormat getDefault(String pref, Map<String, MediaCodecInfo> map) {
        String p = prefered(pref, map);
        MediaFormat format = map.get(p).getCapabilitiesForType(p).getDefaultFormat();
        return format;
    }

    public void create(EncoderInfo info, MediaFormat format, File out) {
        this.info = info;

        try {
            encoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void encode(short[] buf) {
        for (int offset = 0; offset < buf.length; ) {
            int len = buf.length - offset;

            int inputIndex = encoder.dequeueInputBuffer(-1);
            if (inputIndex < 0)
                throw new RuntimeException("unable to open encoder input buffer");

            ByteBuffer input = encoder.getInputBuffer(inputIndex);
            input.clear();

            len = Math.min(len, input.limit() / 2);

            for (int i = 0; i < len; i++)
                input.putShort(buf[i]);

            int bytes = len * 2;

            long ts = getCurrentTimeStamp();
            encoder.queueInputBuffer(inputIndex, 0, bytes, ts, 0);
            NumSamples += len / info.channels;
            offset += len;

            while (encode())
                ;// do encode()
        }
    }

    boolean encode() {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
            return false;

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audioTrackIndex = muxer.addTrack(encoder.getOutputFormat());
            muxer.start();
        }

        if (outputIndex >= 0) {
            ByteBuffer output = encoder.getOutputBuffer(outputIndex);
            output.position(outputInfo.offset);
            output.limit(outputInfo.offset + outputInfo.size);

            muxer.writeSampleData(audioTrackIndex, output, outputInfo);

            encoder.releaseOutputBuffer(outputIndex, false);
        }

        return true;
    }

    public void flush() {
        end();
        encode();

        encoder.stop();
        muxer.stop();
    }

    public void close() {
        encoder.release();
        muxer.release();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 * 1000 / info.sampleRate;
    }

    void end() {
        int inputIndex = encoder.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer input = encoder.getInputBuffer(inputIndex);
            input.clear();
            encoder.queueInputBuffer(inputIndex, 0, 0, getCurrentTimeStamp(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
    }

    public EncoderInfo getInfo() {
        return info;
    }

}