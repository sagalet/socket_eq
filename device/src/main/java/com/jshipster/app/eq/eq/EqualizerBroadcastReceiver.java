package com.jshipster.app.eq.eq;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class EqualizerBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.i("BootTest", "receive intent=" + intent);
        Intent i = new Intent(context, EqualizerService.class);
        context.startService(i);
    }
}