package com.rk.tunnel;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import java.io.File;

public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(action)
                && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) return;
        SharedPreferences prefs = context.getSharedPreferences("ohli_tunel_settings", Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean("auto_boot", false);
        File token = new File(context.getFilesDir(), "ohli-tunnel-token");
        if (!enabled || !token.exists() || token.length() < 20) return;
        prefs.edit().putBoolean("run_requested", true).apply();
        Intent service = new Intent(context, TunnelService.class);
        service.setAction(TunnelService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                context.startForegroundService(service);
            } else {
                context.startService(service);
            }
        } catch (RuntimeException ignored) {
            // Some device policies may block background startup; the user can start it in-app.
        }
    }
}
