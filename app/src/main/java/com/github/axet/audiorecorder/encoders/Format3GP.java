package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaFormat;

import java.io.File;

@TargetApi(21)
public class Format3GP extends ContainerMP4 {

    public Format3GP(EncoderInfo info, File out) {
        final int kBitRates[] = {6600, 8850, 12650, 14250, 15850, 18250, 19850, 23050, 23850};

        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/amr-wb");
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 23850); // set maximum

        create(info, format, out);
    }
}
