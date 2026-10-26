package dev.nervos.glyph;

import android.content.SharedPreferences;
import android.os.SystemClock;

import com.nothing.ketchum.GlyphToy;

/**
 * Activity Limit: an Eva's five minutes of internal power. Long press starts or pauses the countdown, and resets it
 * once it has run out. The ring drains with the time; the last minute blinks; at zero the emergency frame flashes.
 * The countdown keeps going while you look at other toys and is picked up again when you come back.
 */
public class LimitToy extends NervToy {
    private static final long FULL = 5 * 60_000L;
    /** running: end time on the elapsed-realtime clock; paused: time left. Kept in prefs so cycling toys doesn't lose it. */
    private long endAt, left = FULL;
    private boolean running;

    @Override
    protected void onStart() {
        SharedPreferences p = prefs();
        running = p.getBoolean("limit_running", false);
        endAt = p.getLong("limit_end", 0);
        left = p.getLong("limit_left", FULL);
    }

    @Override
    protected void onEvent(String event) {
        if (!GlyphToy.EVENT_CHANGE.equals(event)) return;
        long now = SystemClock.elapsedRealtime();
        if (running) {
            left = Math.max(0, endAt - now);
            running = false;
        } else if (left == 0) {
            left = FULL; // run out: long press refills the battery
        } else {
            endAt = now + left;
            running = true;
        }
        prefs().edit().putBoolean("limit_running", running).putLong("limit_end", endAt).putLong("limit_left", left).apply();
    }

    private SharedPreferences prefs() {
        return getSharedPreferences(ChargeWatchService.PREFS, MODE_PRIVATE);
    }

    @Override
    protected long draw(int[] px, long now) {
        long rem = running ? Math.max(0, endAt - SystemClock.elapsedRealtime()) : left;
        if (running && rem == 0) {
            running = false;
            left = 0;
            prefs().edit().putBoolean("limit_running", false).putLong("limit_left", 0).apply();
        }
        boolean blinkOn = (now / 500) % 2 == 0;
        if (rem == 0) {
            // activity limit reached: 0:00 and the emergency frame, flashing
            int v = blinkOn ? 255 : 30;
            clock(px, 0, v);
            Px.octagon(px, 11.8f, v);
            Px.octagon(px, 10.3f, blinkOn ? 120 : 0);
            return 500;
        }
        int v = running && rem < 60_000 && !blinkOn ? 60 : 255;
        clock(px, rem, v);
        Px.arc(px, 11.5f, rem / (float) FULL, running ? 150 : 70);
        return running ? 250 : 0;
    }

    /** m:ss across the middle at double size: one minute digit, a colon, two second digits. */
    private static void clock(int[] px, long ms, int v) {
        long secs = (ms + 999) / 1000;
        int m = (int) Math.min(9, secs / 60), s = (int) (secs % 60);
        Px.digit(px, m, 1, 8, 2, v);
        for (int dy = 0; dy < 2; dy++) for (int dx = 0; dx < 2; dx++) {
            Px.plot(px, 8 + dx, 10 + dy, v);
            Px.plot(px, 8 + dx, 14 + dy, v);
        }
        Px.digit(px, s / 10, 11, 8, 2, v);
        Px.digit(px, s % 10, 18, 8, 2, v);
    }
}
