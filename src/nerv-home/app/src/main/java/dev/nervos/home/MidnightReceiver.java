package dev.nervos.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;

/** Just after midnight: read the pedometer once to start the new day at zero, then re-arm for tomorrow. Also runs after a reboot. */
public class MidnightReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Context app = context.getApplicationContext();
        Steps.scheduleMidnight(app);
        if (Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return; // reboot: just re-arm; Steps handles the reset

        SensorManager sm = app.getSystemService(SensorManager.class);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null) return;
        PendingResult pending = goAsync();
        Handler h = new Handler(Looper.getMainLooper());
        SensorEventListener once = new SensorEventListener() {
            boolean done;

            @Override
            public void onSensorChanged(SensorEvent e) {
                if (done) return;
                done = true;
                Steps.record(app, (long) e.values[0], true);
                sm.unregisterListener(this);
                pending.finish();
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {}
        };
        sm.registerListener(once, s, SensorManager.SENSOR_DELAY_NORMAL);
        h.postDelayed(() -> { // no reading in time: give up quietly, the next reading of the day starts the count
            sm.unregisterListener(once);
            try { pending.finish(); } catch (IllegalStateException ignored) {}
        }, 8000);
    }
}
