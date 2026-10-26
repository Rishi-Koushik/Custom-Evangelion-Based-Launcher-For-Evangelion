package dev.rishi.quickstar;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.WindowManager;
import android.widget.Toast;

/** Shows the control centre inside the app, so it can be tried before the accessibility service is on. */
public class PreviewActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().getAttributes().layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
        ControlCfg cfg = ControlCfg.load(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND | WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        getWindow().getAttributes().dimAmount = cfg.dim / 100f;
        getWindow().getAttributes().setBlurBehindRadius(cfg.blur);
        setContentView(new ControlPanel(this, cfg, new ControlPanel.Host() {
            @Override
            public void close() {
                finish();
            }

            @Override
            public void start(Intent i) {
                try {
                    startActivity(i);
                } catch (RuntimeException ignored) {
                    // no handler for this settings screen
                }
                finish();
            }

            @Override
            public void power() {
                Toast.makeText(PreviewActivity.this, "The power menu needs the control centre switched on", Toast.LENGTH_SHORT).show();
            }
        }));
        getWindow().setBackgroundBlurRadius(cfg.blur);
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}