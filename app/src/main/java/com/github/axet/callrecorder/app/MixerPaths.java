package com.github.axet.callrecorder.app;

import android.util.Log;

import com.github.axet.androidlibrary.app.SuperUser;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// https://gitlab.com/axet/android-call-recorder/merge_requests/4
//
public class MixerPaths {
    public static String TAG = MixerPaths.class.getSimpleName();
    public static String NAME = "mixer_paths.xml";
    public static String PATH = "/system/etc/" + NAME;

    public static Pattern P = Pattern.compile("VOC_REC.*value=\"(\\d+)\"");

    String xml;

    public MixerPaths() {
        load();
    }

    public void load() {
        try {
            xml = IOUtils.toString(new FileReader(PATH));
        } catch (IOException e) {
            Log.d(TAG, "Unable to read mixers", e);
        }
    }

    public void save() {
        try {
            File f = File.createTempFile(NAME, "tmp");
            FileWriter out = new FileWriter(f);
            IOUtils.write(xml, out);
            out.close();
            File t = new File(PATH);
            SuperUser.su("mount -o remount,rw /system");
            SuperUser.mv(f, t);
            SuperUser.su("chmod ao+r " + PATH);
            SuperUser.su("chown root:root " + PATH);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isCompatible() {
        if (!SuperUser.isRooted())
            return false;
        if (xml == null || xml.isEmpty())
            return false;
        Matcher m = P.matcher(xml);
        if (m.find()) {
            return true;
        }
        return false;
    }

    public boolean isEnabled() {
        Matcher m = P.matcher(xml);
        while (m.find()) {
            String v = m.group(1);
            if (!v.equals("1"))
                return false;
        }
        return true;
    }

    public void setEnabled(boolean b) {
        Matcher m = P.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        while (m.find()) {
            m.appendReplacement(sb, m.group().replaceFirst(Pattern.quote(m.group(1)), b ? "1" : "0"));
        }
        m.appendTail(sb);
        xml = sb.toString();
    }
}
