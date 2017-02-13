package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;

import java.io.File;
import java.util.Map;

@TargetApi(18)
public class FormatM4A extends MuxerMP4 {

    public FormatM4A(EncoderInfo info, File out) {
        Map<String, MediaCodecInfo> map = MuxerMP4.findEncoder("audio/mp4");
        if (map.isEmpty())
            throw new RuntimeException("mp4 not supported");
        MediaFormat format = MuxerMP4.getDefault("audio/mp4a-latm", map);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64000);
        create(info, format, out);
    }

}
