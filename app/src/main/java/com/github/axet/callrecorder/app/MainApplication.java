package com.github.axet.callrecorder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.support.v7.preference.PreferenceManager;

import com.github.axet.callrecorder.R;

public class MainApplication extends com.github.axet.audiolibrary.app.MainApplication {
    public static final String PREFERENCE_DELETE = "delete";
    public static final String PREFERENCE_FORMAT = "format";
    public static final String PREFERENCE_CALL = "call";
    public static final String PREFERENCE_OPTIMIZATION = "optimization";
    public static final String PREFERENCE_DETAILS_CONTACT = "_contact";
    public static final String PREFERENCE_SOURCE = "source";

    @Override
    public void onCreate() {
        super.onCreate();
        PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
    }

    @Override
    public int getUserTheme() {
        return getTheme(this, R.style.RecThemeLight, R.style.RecThemeDark);
    }

    public static String getContact(Context context, Uri f) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CONTACT;
        return shared.getString(p, null);
    }

    public static void setContact(Context context, Uri f, String id) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CONTACT;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, id);
        editor.commit();
    }
}
