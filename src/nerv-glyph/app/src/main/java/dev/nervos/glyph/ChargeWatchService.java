package dev.nervos.glyph;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.os.IBinder;
import android.provider.Settings;
import android.util.Log;

/**
 * Opens the A.T. Field charging screen (screen + Glyph Matrix) when a charger is plugged in. Android no longer wakes
 * apps for plug-in broadcasts, so this foreground service only holds a receiver: it uses no CPU until the charger is
 * connected. Opening a screen from the background needs the user's "Display over other apps" permission.
 * Without notification permission its notice stays hidden.
 */
public class ChargeWatchService extends Service {
    static final String PREFS = "nerv", KEY_CHARGE_FX = "charge_fx";
    private static final String TAG = "ChargeWatch";

    private final BroadcastReceiver power = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ChargeFeedback.play(context, UnitReceiver.current(context));
            if (!Settings.canDrawOverlays(context)) {
                Log.w(TAG, "plugged in, but 'Display over other apps' is off: can't show the A.T. Field");
                return;
            }
            startActivity(new Intent(context, ChargeFxActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_NO_ANIMATION));
        }
    };

    /** Start or stop the watcher to match the user's setting (default: on). */
    static void sync(Context context) {
        Intent intent = new Intent(context, ChargeWatchService.class);
        if (context.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_CHARGE_FX, true)) context.startForegroundService(intent);
        else context.stopService(intent);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("watch", "Charging A.T. Field", NotificationManager.IMPORTANCE_MIN));
        Notification notice = new Notification.Builder(this, "watch")
                .setSmallIcon(R.drawable.toy_atfield)
                .setContentTitle("A.T. Field armed")
                .setContentText("Plays when you plug in")
                .build();
        startForeground(1, notice, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        registerReceiver(power, new IntentFilter(Intent.ACTION_POWER_CONNECTED), Context.RECEIVER_NOT_EXPORTED);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(power);
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
