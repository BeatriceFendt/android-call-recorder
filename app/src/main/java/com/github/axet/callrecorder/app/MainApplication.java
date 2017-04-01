package com.github.axet.callrecorder.app;

import android.preference.PreferenceManager;

import com.github.axet.callrecorder.R;

public class MainApplication extends com.github.axet.audiolibrary.app.MainApplication {
    public static final String PREFERENCE_DELETE = "delete";
    public static final String PREFERENCE_FORMAT = "format";
    public static final String PREFERENCE_CALL = "call";
    public static final String PREFERENCE_OPTIMIZATION = "optimization";

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
