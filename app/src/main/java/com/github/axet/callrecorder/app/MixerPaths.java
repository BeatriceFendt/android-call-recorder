package com.github.axet.callrecorder.app;

import android.util.Log;

import com.github.axet.androidlibrary.app.SuperUser;

import org.apache.commons.io.IOUtils;

import java.io.FileReader;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// https://gitlab.com/axet/android-call-recorder/merge_requests/4
//
public class MixerPaths {
    public static final String TAG = MixerPaths.class.getSimpleName();
    public static final String PATH = SuperUser.SYSTEM + "/etc/mixer_paths.xml";

    public static final String TRUE = "1";
    public static final String FALSE = "0";

    public static Pattern P = Pattern.compile("VOC_REC.*value=\"(\\d+)\"");

    String xml;

    public MixerPaths() {
        load();
    }

    public void load() {
        try {
            xml = null;
            xml = IOUtils.toString(new FileReader(PATH));
        } catch (IOException e) {
            Log.d(TAG, "Unable to read mixers", e);
        }
    }

    public void save() {
        String args = "";
        args += SuperUser.REMOUNT_SYSTEM + "\n";
        args += MessageFormat.format(SuperUser.SUCAT, PATH, xml.trim()) + "\n";
        SuperUser.su(args);
    }

    public void save(boolean b) {
        setEnabled(b);
        save();
        load();
        if (b != isEnabled()) {
            throw new RuntimeException("Unable to write changes");
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
            if (!v.equals(TRUE))
                return false;
        }
        return true;
    }

    public void setEnabled(boolean b) {
        Matcher m = P.matcher(xml);
        StringBuffer sb = new StringBuffer(xml.length());
        while (m.find()) {
            m.appendReplacement(sb, m.group().replaceFirst(Pattern.quote(m.group(1)), b ? TRUE : FALSE));
        }
        m.appendTail(sb);
        xml = sb.toString();
    }
}
