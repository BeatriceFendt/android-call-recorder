package com.github.axet.callrecorder.services;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class OnUpgradeReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        RecordingService.startIfEnabled(context);
    }
}