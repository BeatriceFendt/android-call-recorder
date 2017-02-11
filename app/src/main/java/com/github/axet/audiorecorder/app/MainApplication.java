package com.github.axet.audiorecorder.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.app.LibraryApplication;
import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.audiorecorder.R;

public class MainApplication extends Application {
    public static final String PREFERENCE_STORAGE = "storage_path";
    public static final String PREFERENCE_RATE = "sample_rate";
    public static final String PREFERENCE_CALL = "call";
    public static final String PREFERENCE_SILENT = "silence";
    public static final String PREFERENCE_ENCODING = "encoding";
    public static final String PREFERENCE_LAST = "last_recording";
    public static final String PREFERENCE_THEME = "theme";
    public static final String PREFERENCE_CHANNELS = "channels";

    @Override
    public void onCreate() {
        super.onCreate();

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);

        Context context = this;
        context.setTheme(getUserTheme());
    }

    public static int getTheme(Context context, int light, int dark) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String theme = shared.getString(PREFERENCE_THEME, "");
        if (theme.equals("Theme_Dark")) {
            return dark;
        } else {
            return light;
        }
    }

    public static int getActionbarColor(Context context) {
        int colorId = MainApplication.getTheme(context, R.attr.colorPrimary, R.attr.secondBackground);
        int color = ThemeUtils.getThemeColor(context, colorId);
        return color;
    }

    public int getUserTheme() {
        return getTheme(this, R.style.AppThemeLight, R.style.AppThemeDark);
    }

    public String formatFree(long free, long left) {
        String str = "";

        long diff = left;

        int diffSeconds = (int) (diff / 1000 % 60);
        int diffMinutes = (int) (diff / (60 * 1000) % 60);
        int diffHours = (int) (diff / (60 * 60 * 1000) % 24);
        int diffDays = (int) (diff / (24 * 60 * 60 * 1000));

        if (diffDays > 0) {
            str = getResources().getQuantityString(R.plurals.days, diffDays, diffDays);
        } else if (diffHours > 0) {
            str = getResources().getQuantityString(R.plurals.hours, diffHours, diffHours);
        } else if (diffMinutes > 0) {
            str = getResources().getQuantityString(R.plurals.minutes, diffMinutes, diffMinutes);
        } else if (diffSeconds > 0) {
            str = getResources().getQuantityString(R.plurals.seconds, diffSeconds, diffSeconds);
        }

        return getString(R.string.title_header, LibraryApplication.formatSize(this, free), str);
    }

    public static int getChannels(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        int i = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_CHANNELS, "1"));
        return i;
    }

    public static int getMode(Context context) {
        switch (getChannels(context)) {
            case 1:
                return AudioFormat.CHANNEL_IN_MONO;
            case 2:
                return AudioFormat.CHANNEL_IN_STEREO;
            default:
                throw new RuntimeException("unknown mode");
        }
    }
}
