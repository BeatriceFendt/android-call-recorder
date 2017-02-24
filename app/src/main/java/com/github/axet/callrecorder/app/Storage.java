package com.github.axet.callrecorder.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Storage extends com.github.axet.audiolibrary.app.Storage {
    public Storage(Context context) {
        super(context);
    }

    @Override
    public File getNewFile() {
        String name = "";

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(com.github.axet.audiolibrary.app.MainApplication.PREFERENCE_ENCODING, "");

        String format = shared.getString(MainApplication.PREFERENCE_FORMAT, "%s");

        if (format.equals("%s") || name.isEmpty()) {
            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
            name = s.format(new Date());
        }

        File parent = getStoragePath();
        if (!parent.exists()) {
            if (!parent.mkdirs())
                throw new RuntimeException("Unable to create: " + parent);
        }

        return getNextFile(parent, name, ext);
    }
}
