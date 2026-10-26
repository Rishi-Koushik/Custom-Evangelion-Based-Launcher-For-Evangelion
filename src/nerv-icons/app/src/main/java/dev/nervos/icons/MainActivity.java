package dev.nervos.icons;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** Only shown if the user opens the pack itself; launchers read res/xml/appfilter.xml directly. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        TextView t = new TextView(this);
        t.setText("NERV ICONS\n\nLawnchair → Settings → General → Icon pack → NERV Icons");
        t.setTextColor(Color.parseColor("#A3FF3A"));
        t.setBackgroundColor(Color.parseColor("#0B0612"));
        t.setTextSize(20);
        t.setGravity(Gravity.CENTER);
        t.setPadding(48, 48, 48, 48);
        setContentView(t);
    }
}
