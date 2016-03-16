package com.github.axet.audiorecorder.app;

import android.app.Application;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.github.axet.audiorecorder.R;

public class MainApplication extends Application {
    public static final String PREFERENCE_STORAGE = "storage_path";
    public static final String PREFERENCE_RATE = "sample_rate";
    public static final String PREFERENCE_CALL = "call";
    public static final String PREFERENCE_SILENT = "silence";
    public static final String PREFERENCE_ENCODING = "encoding";
    public static final String PREFERENCE_LAST = "last_recording";

    @Override
    public void onCreate() {
        super.onCreate();

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
    }

    static public String formatTime(int tt) {
        return String.format("%02d", tt);
    }

    public static String formatSize(long s) {
        if (s > 0.1 * 1024 * 1024) {
            float f = s / 1024f / 1024f;
            return String.format("%.1f MB", f);
        } else {
            float f = s / 1024f;
            return String.format("%.1f kb", f);
        }
    }

    static public String formatDuration(long diff) {
        int diffMilliseconds = (int) (diff % 1000);
        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        String str = "";

        if (diffDays > 0)
            str = diffDays + "d " + formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else if (diffHours > 0)
            str = formatTime(diffHours) + ":" + formatTime(diffMinutes) + ":" + formatTime(diffSeconds);
        else
            str = formatTime(diffMinutes) + ":" + formatTime(diffSeconds);

        return str;
    }
}
