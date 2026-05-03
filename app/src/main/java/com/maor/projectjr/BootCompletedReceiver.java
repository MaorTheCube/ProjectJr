package com.maor.projectjr;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

public class BootCompletedReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            return;
        }

        SharedPreferences prefs = context.getSharedPreferences(MainActivity.PREFS, Context.MODE_PRIVATE);
        boolean alwaysShare = prefs.getBoolean(LocationSharingService.PREF_ALWAYS_SHARE_LOCATION, false);
        if (!alwaysShare) {
            return;
        }

        Intent serviceIntent = new Intent(context, LocationSharingService.class);
        serviceIntent.setAction(LocationSharingService.ACTION_START_PERSISTENT);
        context.startForegroundService(serviceIntent);
    }
}
