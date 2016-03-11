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

    @Override
    public void onCreate() {
        super.onCreate();

        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
    }
}
