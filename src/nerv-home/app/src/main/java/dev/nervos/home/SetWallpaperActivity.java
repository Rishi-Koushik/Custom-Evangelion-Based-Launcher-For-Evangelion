package dev.nervos.home;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/** Sets the current unit's wallpapers: the Tokyo-3 skyline on the home screen, its poster on the lock screen. */
public class SetWallpaperActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        new Thread(() -> {
            String shown = Walls.apply(this, UnitStore.current(this), UnitStore.xdark(this))
                    ? "NERV wallpaper set" : "Couldn't set wallpaper";
            runOnUiThread(() -> {
                Toast.makeText(this, shown, Toast.LENGTH_SHORT).show();
                finish();
            });
        }).start();
    }
}
