package dev.nervos.glyph;

import android.os.BatteryManager;
import android.os.PowerManager;

import com.nothing.ketchum.GlyphToy;

import java.util.Calendar;

/**
 * Sync Ratio: battery level with a ring gauge (long press: the time). In battery saver it shows the NERV
 * emergency frame, a blinking octagon border. Always-on capable: then it draws one static frame per minute.
 */
public class SyncToy extends NervToy {
    private boolean clock;

    @Override
    protected void onEvent(String event) {
        if (GlyphToy.EVENT_CHANGE.equals(event)) clock = !clock;
    }

    @Override
    protected long draw(int[] px, long now) {
        boolean saver = getSystemService(PowerManager.class).isPowerSaveMode();
        if (clock) {
            Calendar c = Calendar.getInstance();
            Px.number(px, two(c.get(Calendar.HOUR_OF_DAY)), 2, 2, 255);
            Px.number(px, two(c.get(Calendar.MINUTE)), 13, 2, 255);
        } else {
            int level = getSystemService(BatteryManager.class).getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY);
            Px.number(px, String.valueOf(level), 8, 2, 255);
            if (!saver) Px.arc(px, 11.5f, level / 100f, 140);
        }
        if (saver) {
            boolean lit = aod || (now / 600) % 2 == 0;
            Px.octagon(px, 11.8f, lit ? 255 : 40);
            if (!clock) Px.octagon(px, 10.3f, lit ? 120 : 0);
            return 600;
        }
        return clock ? 15_000 : 30_000; // nothing moves, so refresh rarely
    }

    private static String two(int n) {
        return n < 10 ? "0" + n : String.valueOf(n);
    }
}
