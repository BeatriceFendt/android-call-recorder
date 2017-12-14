package com.github.axet.callrecorder.app;

import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.preference.PreferenceManager;

import com.github.axet.audiolibrary.encoders.Factory;

import java.io.File;
import java.io.FileFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class Storage extends com.github.axet.audiolibrary.app.Storage {
    public static String TAG = Storage.class.getSimpleName();

    public static CharSequence[] getEncodingTexts(Context context) {
        CharSequence[] ee = Factory.getEncodingTexts(context);
        ArrayList<CharSequence> ll = new ArrayList<>(Arrays.asList(ee));
        ll.add(".3gp (MediaRecorder AMRNB 8kHz)");
        if (Build.VERSION.SDK_INT >= 10)
            ll.add(".3gp (MediaRecorder AMRWB 16kHz)");
        if (Build.VERSION.SDK_INT >= 10)
            ll.add(".aac (MediaRecorder AAC)");
        return ll.toArray(new CharSequence[]{});
    }

    public static String[] getEncodingValues(Context context) {
        String[] ee = Factory.getEncodingValues(context);
        ArrayList<String> ll = new ArrayList<>(Arrays.asList(ee));
        ll.add("3gp");
        if (Build.VERSION.SDK_INT >= 10)
            ll.add("3gp16");
        if (Build.VERSION.SDK_INT >= 10)
            ll.add("aac");
        return ll.toArray(new String[]{});
    }

    public static String getFormatted(String format, long now, String phone, String contact, String call) {
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

        format = format.replaceAll("%T", "" + now / 1000);
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

        return format;
    }

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
        if (ff == null)
            return null;
        if (ff.length == 0)
            return null;
        return ff[0];
    }

    public Uri getNewFile(long now, String phone, String contact, String call) {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(com.github.axet.audiolibrary.app.MainApplication.PREFERENCE_ENCODING, "");

        if (ext.startsWith(Factory.EXT_3GP))
            ext = Factory.EXT_3GP; // replace 3gp16 -> 3gp

        String format = "%s";
        format = shared.getString(MainApplication.PREFERENCE_FORMAT, format);

        format = getFormatted(format, now, phone, contact, call);

        Uri parent = getStoragePath();
        String s = parent.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = getFile(parent);
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
