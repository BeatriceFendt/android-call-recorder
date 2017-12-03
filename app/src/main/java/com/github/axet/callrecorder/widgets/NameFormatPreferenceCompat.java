package com.github.axet.callrecorder.widgets;

import android.content.Context;
import android.util.AttributeSet;

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
        CharSequence[] text = getEntries();
        CharSequence[] values = getEntryValues();
        for (int i = 0; i < text.length; i++) {
            String t = text[i].toString();
            String v = values[i].toString();
            if (v.equals(str))
                return t;
        }
        return Storage.getFormatted(str, 1512340435083l, "+1 (334) 333-33-33", "Contact ID", MainApplication.CALL_IN);
    }
}
