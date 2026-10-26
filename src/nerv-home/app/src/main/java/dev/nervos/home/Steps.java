package dev.nervos.home;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Today's steps from the phone's hardware pedometer. The sensor reports steps since boot, counted by a low-power
 * chip whether or not anything is listening, so NERV Home only reads it while the home screen is visible plus
 * once just after midnight (to mark where the new day starts). Survives reboots.
 */
final class Steps implements SensorEventListener {
    interface Listener { void onSteps(int steps); }

    private static final String PREFS = "steps", DAY = "day", BASE = "base", CARRY = "carry", LAST = "last";
    private final Context context;
    private final Listener listener;
    private boolean listening;

    Steps(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    void start() {
        SensorManager sm = context.getSystemService(SensorManager.class);
        Sensor s = sm.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        if (s == null || listening) return;
        listening = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
    }

    void stop() {
        if (listening) context.getSystemService(SensorManager.class).unregisterListener(this);
        listening = false;
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        listener.onSteps(record(context, (long) e.values[0], false));
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    /**
     * Fold a counter reading into today's total. newDay=true (the midnight read) makes this reading the new day's
     * starting point. If the first reading of a day comes later, steps walked before it are counted from then on.
     */
    static int record(Context c, long counter, boolean newDay) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String today = LocalDate.now(ZoneId.systemDefault()).toString();
        long base = p.getLong(BASE, counter), carry = p.getLong(CARRY, 0), last = p.getLong(LAST, counter);
        if (newDay || !today.equals(p.getString(DAY, ""))) {
            base = counter;
            carry = 0;
        } else if (counter < last) {
            // the counter restarted after a reboot: keep what was walked before it
            carry += Math.max(0, last - base);
            base = 0;
        }
        p.edit().putString(DAY, today).putLong(BASE, base).putLong(CARRY, carry).putLong(LAST, counter).apply();
        return (int) (carry + counter - base);
    }

    /** Last known total for today without touching the sensor (0 if nothing recorded today yet). */
    static int cached(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!LocalDate.now(ZoneId.systemDefault()).toString().equals(p.getString(DAY, ""))) return 0;
        return (int) (p.getLong(CARRY, 0) + p.getLong(LAST, 0) - p.getLong(BASE, 0));
    }

    /** One inexact wake-up a few minutes after midnight to start the new day's count. */
    static void scheduleMidnight(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long midnight = LocalDate.now(ZoneId.systemDefault()).plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, MidnightReceiver.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.setWindow(AlarmManager.RTC_WAKEUP, midnight, 5 * 60_000L, pi);
    }
}
