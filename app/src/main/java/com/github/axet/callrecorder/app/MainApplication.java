package com.github.axet.callrecorder.app;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.preference.PreferenceManager;

import com.github.axet.androidlibrary.widgets.ThemeUtils;
import com.github.axet.callrecorder.R;

public class MainApplication extends com.github.axet.audiolibrary.app.MainApplication {
    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
    }

    @Override
    public int getUserTheme() {
        return getTheme(this, R.style.RecThemeLight, R.style.RecThemeDark);
    }

}
