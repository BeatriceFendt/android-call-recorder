package com.github.axet.callrecorder.widgets;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.AttributeSet;

import com.github.axet.audiolibrary.encoders.Factory;
import com.github.axet.callrecorder.app.MainApplication;
import com.github.axet.callrecorder.app.Storage;

public class NameFormatPreferenceCompat extends com.github.axet.androidlibrary.widgets.NameFormatPreferenceCompat {
    public NameFormatPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public NameFormatPreferenceCompat(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public NameFormatPreferenceCompat(Context context) {
        this(context, null);
    }

    @Override
    public String getFormatted(String str) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(getContext());
        String ext = shared.getString(com.github.axet.audiolibrary.app.MainApplication.PREFERENCE_ENCODING, "");
        ext = Storage.filterMediaRecorder(ext);
        return Storage.getFormatted(str, 1512340435083l, "+1 (334) 333-33-33", "Contact Name", MainApplication.CALL_IN) + "." + ext;
    }
}
