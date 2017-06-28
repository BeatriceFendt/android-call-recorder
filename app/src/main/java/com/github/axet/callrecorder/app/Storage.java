package com.github.axet.callrecorder.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.util.Log;

import java.io.File;
import java.io.FileFilter;
import java.text.SimpleDateFormat;
import java.util.Date;

public class Storage extends com.github.axet.audiolibrary.app.Storage {
    public static String TAG = Storage.class.getSimpleName();

    public Storage(Context context) {
        super(context);
    }

    public boolean recordingNextPending() {
        File tmp = getTempRecording();
        if (tmp.exists())
            return true;
        File parent = tmp.getParentFile();
        File[] ff = parent.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith(TMP_REC);
            }
        });
        return ff != null && ff.length > 0;
    }

    public File getTempNextRecording() {
        File tmp = super.getTempRecording();
        if (tmp.exists())
            return tmp;
        File parent = tmp.getParentFile();
        File[] ff = parent.listFiles(new FileFilter() {
            @Override
            public boolean accept(File pathname) {
                return pathname.getName().startsWith(TMP_REC);
            }
        });
        return ff != null && ff.length > 0 ? ff[0] : tmp;
    }

    public Uri getNewFile(String phone, String contact) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(com.github.axet.audiolibrary.app.MainApplication.PREFERENCE_ENCODING, "");

        String format = "%s";
        format = shared.getString(MainApplication.PREFERENCE_FORMAT, format);

        if (phone != null && !phone.isEmpty()) {
            format = format.replaceAll("%p", phone);
        } else {
            format = format.replaceAll("%p", "");
        }

        if (contact != null && !contact.isEmpty())
            format = format.replaceAll("%c", contact);
        else
            format = format.replaceAll("%c", "");

        format = format.replaceAll("%T", "" + System.currentTimeMillis() / 1000);
        format = format.replaceAll("%s", SIMPLE.format(new Date()));
        format = format.replaceAll("%I", ISO8601.format(new Date()));

        format = format.trim();

        Uri parent = getStoragePath();
        String s = parent.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = new File(parent.getPath());
            if (!f.exists() && !f.mkdirs())
                throw new RuntimeException("Unable to create: " + f);
        }
        return getNextFile(parent, format, ext);
    }
}
