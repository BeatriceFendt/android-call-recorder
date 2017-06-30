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

    public Uri getNewFile(String phone, String contact, String call) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(com.github.axet.audiolibrary.app.MainApplication.PREFERENCE_ENCODING, "");

        String format = "%s";
        format = shared.getString(MainApplication.PREFERENCE_FORMAT, format);

        if (contact != null && !contact.isEmpty()) {
            format = format.replaceAll("%c", contact);
        } else {
            if (phone != null && !phone.isEmpty())
                format = format.replaceAll("%c", phone);
            else
                format = format.replaceAll("%c", "");
        }

        if (phone != null && !phone.isEmpty()) {
            format = format.replaceAll("%p", phone);
        } else {
            format = format.replaceAll("%p", "");
        }

        format = format.replaceAll("%T", "" + System.currentTimeMillis() / 1000);
        format = format.replaceAll("%s", SIMPLE.format(new Date()));
        format = format.replaceAll("%I", ISO8601.format(new Date()));

        if (call == null || call.isEmpty()) {
            format = format.replaceAll("%i", "");
        } else {
            switch (call) {
                case MainApplication.CALL_IN:
                    format = format.replaceAll("%i", "↓");
                    break;
                case MainApplication.CALL_OUT:
                    format = format.replaceAll("%i", "↑");
                    break;
            }
        }

        format = format.replaceAll("  ", " ");

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

    @Override
    public Uri move(File ff, Uri tt) {
        Uri t = super.move(ff, tt);
        if (t == null)
            return null;
        Uri f = Uri.fromFile(ff);
        String c = MainApplication.getContact(context, f);
        MainApplication.setContact(context, t, c); // copy contact to migrated file
        String call = MainApplication.getCall(context, f);
        MainApplication.setCall(context, t, call); // copy call to migrated file
        return t;
    }

    @Override
    public Uri rename(Uri f, String tt) {
        Uri t = super.rename(f, tt);
        if (t == null)
            return null;
        String c = MainApplication.getContact(context, f);
        MainApplication.setContact(context, t, c); // copy contact to new name
        String call = MainApplication.getCall(context, f);
        MainApplication.setCall(context, t, call); // copy call to new name
        return t;
    }
}
