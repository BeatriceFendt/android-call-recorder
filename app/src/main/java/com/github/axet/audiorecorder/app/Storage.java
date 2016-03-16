package com.github.axet.audiorecorder.app;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.preference.PreferenceManager;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class Storage {
    public static final String TMP_REC = "recorind.data";
    public static final String RECORDINGS = "recordings";

    Context context;

    public Storage(Context context) {
        this.context = context;
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

    public boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(context, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    public File getLocalStorage() {
        return new File(context.getApplicationInfo().dataDir, RECORDINGS);
    }

    public boolean isLocalStorageEmpty() {
        return getLocalStorage().listFiles().length == 0;
    }

    public boolean isExternalStoragePermitted() {
        return permitted(PERMISSIONS);
    }

    public File getStoragePath() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");
        if (permitted(PERMISSIONS)) {
            return new File(path);
        } else {
            return getLocalStorage();
        }
    }

    public void migrateLocalStorage() {
        if (!permitted(PERMISSIONS)) {
            return;
        }

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String path = shared.getString(MainApplication.PREFERENCE_STORAGE, "");

        File l = getLocalStorage();
        File t = new File(path);
        File[] ff = l.listFiles();

        if (ff == null)
            return;

        for (File f : ff) {
            File tt = getNextFile(t, f);
            move(f, tt);
        }
    }

    public File getNewFile() {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        File parent = getStoragePath();
        if (!parent.exists()) {
            if (!parent.mkdirs())
                throw new RuntimeException("Unable to create: " + parent);
        }

        return getNextFile(parent, s.format(new Date()), ext);
    }

    public static String getNameNoExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            fileName = fileName.substring(0, i);
        }
        return fileName;
    }

    public static String getExt(File f) {
        String fileName = f.getName();

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            return fileName.substring(i + 1);
        }
        return "";
    }

    File getNextFile(File parent, File f) {
        String fileName = f.getName();

        String extension = "";

        int i = fileName.lastIndexOf('.');
        if (i > 0) {
            extension = fileName.substring(i + 1);
            fileName = fileName.substring(0, i);
        }

        return getNextFile(parent, fileName, extension);
    }

    File getNextFile(File parent, String name, String ext) {
        String fileName;
        if (ext.isEmpty())
            fileName = name;
        else
            fileName = String.format("%s.%s", name, ext);

        File file = new File(parent, fileName);

        int i = 1;
        while (file.exists()) {
            fileName = String.format("%s (%d).%s", name, i, ext);
            file = new File(parent, fileName);
            i++;
        }

//        try {
//            file.createNewFile();
//        } catch (IOException e) {
//            throw new RuntimeException("Unable to create: " + file, e);
//        }

        return file;
    }

    public List<File> scan(File dir) {
        ArrayList<File> list = new ArrayList<>();

        File[] ff = dir.listFiles();
        if (ff == null)
            return list;

        for (File f : ff) {
            if (f.length() > 0) {
                String[] ee = context.getResources().getStringArray(R.array.encodings_values);
                String n = f.getName().toLowerCase();
                for (String e : ee) {
                    if (n.endsWith("." + e))
                        list.add(f);
                }
            }
        }

        return list;
    }

    public File getTempRecording() {
        return new File(context.getApplicationInfo().dataDir, TMP_REC);
    }

    public FileOutputStream open(File f) {
        File tmp = f;
        File parent = tmp.getParentFile();
        if (!parent.exists() && !parent.mkdirs()) {
            throw new RuntimeException("unable to create: " + parent);
        }
        if (!parent.isDirectory())
            throw new RuntimeException("target is not a dir: " + parent);
        try {
            return new FileOutputStream(tmp, true);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void delete(File f) {
        f.delete();
    }

    public void move(File f, File to) {
        try {
            InputStream in = new FileInputStream(f);
            OutputStream out = new FileOutputStream(to);

            byte[] buf = new byte[1024];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
            f.delete();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
