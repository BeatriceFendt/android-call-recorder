package com.github.axet.callrecorder.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class OnExternalReceiver extends BroadcastReceiver {
    boolean isExternal(Context context) {
        PackageManager pm = context.getPackageManager();
        try {
            PackageInfo pi = pm.getPackageInfo(context.getPackageName(), 0);
            ApplicationInfo ai = pi.applicationInfo;
            return (ai.flags & ApplicationInfo.FLAG_EXTERNAL_STORAGE) == ApplicationInfo.FLAG_EXTERNAL_STORAGE;
        } catch (PackageManager.NameNotFoundException ignore) {
        }
        return false;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!isExternal(context))
            return;
        RecordingService.startIfEnabled(context);
    }
}