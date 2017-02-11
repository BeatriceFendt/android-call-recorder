package com.github.axet.audiorecorder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.preference.PreferenceManager;

public class Sound extends com.github.axet.androidlibrary.app.Sound {

    public Sound(Context context) {
        super(context);
    }

    public void silent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.silent();
        }
    }

    public void unsilent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            super.unsilent();
        }
    }

    public AudioTrack generateTrack(int sampleRate, short[] buf, int len) {
        int end = len;

        int c = 0;

        switch (MainApplication.getChannels(context)) {
            case 1:
                c = AudioFormat.CHANNEL_OUT_MONO;
                break;
            case 2:
                c = AudioFormat.CHANNEL_OUT_STEREO;
                break;
            default:
                throw new RuntimeException("unknown mode");
        }

        // old phones bug.
        // http://stackoverflow.com/questions/27602492
        //
        // with MODE_STATIC setNotificationMarkerPosition not called
        AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate,
                c, RawSamples.AUDIO_FORMAT,
                len * (Short.SIZE / 8), AudioTrack.MODE_STREAM);
        track.write(buf, 0, len);
        if (track.setNotificationMarkerPosition(end) != AudioTrack.SUCCESS)
            throw new RuntimeException("unable to set marker");
        return track;
    }
}
