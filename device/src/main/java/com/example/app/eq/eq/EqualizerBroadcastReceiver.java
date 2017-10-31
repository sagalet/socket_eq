package com.example.app.eq.eq;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class EqualizerBroadcastReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent i = new Intent(context, EqualizerService.class);
        context.startService(i);
    }
}