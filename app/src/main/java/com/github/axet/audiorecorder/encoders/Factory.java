package com.github.axet.audiorecorder.encoders;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Build;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.app.RawSamples;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

public class Factory {

    public static CharSequence[] getEncodingTexts(Context context) {
        String[] aa = context.getResources().getStringArray(R.array.encodings_text);
        ArrayList<String> ll = new ArrayList<>(Arrays.asList(aa));
        if (Build.VERSION.SDK_INT >= 18)
            ll.add(".m4a");
        ll.add(".mka");
        ll.add(".ogg");
        return ll.toArray(new String[]{});
    }

    public static String[] getEncodingValues(Context context) {
        String[] aa = context.getResources().getStringArray(R.array.encodings_values);
        ArrayList<String> ll = new ArrayList<>(Arrays.asList(aa));
        if (Build.VERSION.SDK_INT >= 18)
            ll.add("m4a");
        ll.add("mka");
        ll.add("ogg");
        return ll.toArray(new String[]{});
    }

    public static Encoder getEncoder(String ext, EncoderInfo info, File out) {
        if (ext.equals("wav")) {
            return new FormatWAV(info, out);
        }
        if (ext.equals("3gp")) {
            return new Format3GP(info, out);
        }
        if (ext.equals("m4a")) {
            return new FormatM4A(info, out);
        }
        if (ext.equals("mka")) {
            return new FormatMKA(info, out);
        }
        return null;
    }

    public static long getEncoderRate(String ext, int rate) {
        if (ext.equals("m4a") || ext.equals("mka")) {
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;

            return y / 60;
        }

        if (ext.equals("mka")) {
            long y1 = 365723; // one minute sample 16000Hz
            long x1 = 16000; // at 16000
            long y2 = 493743; // one minute sample
            long x2 = 44000; // at 44000
            long x = rate;
            long y = (x - x1) * (y2 - y1) / (x2 - x1) + y1;
            return y / 60;
        }

        // default raw
        int c = RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1;
        return c * rate;
    }
}
