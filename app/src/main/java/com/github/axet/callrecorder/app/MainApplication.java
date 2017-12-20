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
    public static final String PREFERENCE_DETAILS_CALL = "_call";
    public static final String PREFERENCE_SOURCE = "source";
    public static final String PREFERENCE_FILTER_IN = "filter_in";
    public static final String PREFERENCE_FILTER_OUT = "filter_out";
    public static final String PREFERENCE_DONE_NOTIFICATION = "done_notification";
    public static final String PREFERENCE_MIXERPATHS = "mixer_paths";

    public static final String CALL_OUT = "out";
    public static final String CALL_IN = "in";

    @Override
    public void onCreate() {
        super.onCreate();
        final SharedPreferences defaultValueSp = getSharedPreferences(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, Context.MODE_PRIVATE);
        if (!defaultValueSp.getBoolean(PreferenceManager.KEY_HAS_SET_DEFAULT_VALUES, false)) {
            PreferenceManager.setDefaultValues(this, R.xml.pref_general, false);
            MixerPaths m = new MixerPaths();
            if (m.isSupported() && !m.isEnabled()) {
                SharedPreferences.Editor e = defaultValueSp.edit();
                e.putString(MainApplication.PREFERENCE_ENCODING, Storage.EXT_3GP);
                e.commit();
            }
        }
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

    public static String getCall(Context context, Uri f) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CALL;
        return shared.getString(p, null);
    }

    public static void setCall(Context context, Uri f, String id) {
        final SharedPreferences shared = android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        String p = getFilePref(f) + PREFERENCE_DETAILS_CALL;
        SharedPreferences.Editor editor = shared.edit();
        editor.putString(p, id);
        editor.commit();
    }
}
