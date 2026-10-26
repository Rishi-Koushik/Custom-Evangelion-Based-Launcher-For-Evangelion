package dev.rishi.quickstar;

import android.content.Intent;
import android.graphics.drawable.Icon;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;
import android.widget.Toast;

/** Quick settings tile that opens the control centre. */
public class ControlTile extends TileService {
    @Override
    public void onStartListening() {
        Tile t = getQsTile();
        if (t == null) return;
        t.setLabel("Control centre");
        t.setIcon(Icon.createWithResource(this, R.drawable.ic_tile));
        t.setState(ControlA11yService.instance != null ? Tile.STATE_ACTIVE : Tile.STATE_INACTIVE);
        t.updateTile();
    }

    @Override
    public void onClick() {
        final ControlA11yService s = ControlA11yService.instance;
        if (s == null) {
            Toast.makeText(this, "Turn the control centre on in Quickstar first", Toast.LENGTH_LONG).show();
            startActivityAndCollapse(new Intent(this, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        s.openAfterShade();
    }
}
