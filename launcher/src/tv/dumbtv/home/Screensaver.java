package tv.dumbtv.home;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Handler;
import android.os.Looper;
import android.service.dreams.DreamService;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** The home screen's widgets and theme, without the app dock or other controls. */
public final class Screensaver extends DreamService {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private Theme theme;
    private FrameLayout root;
    private ImageView wall;
    private MatrixRain rain;
    private View shade;
    private Board board;
    private String wallKey;
    private int applied = -1;
    private boolean active;

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            refresh();
        }
    };

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        setInteractive(false); // The first remote press dismisses the screensaver, without opening an app.
        setFullscreen(true);
        setScreenBright(true);
        cfg = Config.get(this);
        root = new FrameLayout(this);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        rain = new MatrixRain(this);
        rain.setVisibility(View.GONE);
        root.addView(rain, new FrameLayout.LayoutParams(MATCH, MATCH));
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        board = new Board(this, true);
        board.setPadding(dp(40), dp(30), dp(40), dp(30));
        root.addView(board, new FrameLayout.LayoutParams(MATCH, MATCH));
        setContentView(root);
    }

    @Override
    public void onDreamingStarted() {
        super.onDreamingStarted();
        active = true;
        IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, filter);
        refresh();
        board.show();
    }

    @Override
    public void onDreamingStopped() {
        stop();
        super.onDreamingStopped();
    }

    @Override
    public void onDestroy() {
        stop();
        ui.removeCallbacksAndMessages(null);
        io.shutdownNow();
        super.onDestroy();
    }

    private void stop() {
        if (!active) return;
        active = false;
        unregisterReceiver(time);
        board.hide();
        rain.stop();
        wall.setImageDrawable(null);
        board.setBackdrop(null);
        wallKey = null;
    }

    private void refresh() {
        if (!active) return;
        cfg.refresh();
        if (applied != cfg.version()) {
            applied = cfg.version();
            theme = Theme.of(this, cfg.get(Config.THEME));
            root.setBackgroundColor(cfg.cover());
            board.setup(cfg, theme);
        }
        board.minute();
        background();
    }

    private void background() {
        boolean live = theme.rain && "theme".equals(cfg.get(Config.BACKGROUND));
        rain.setVisibility(live ? View.VISIBLE : View.GONE);
        if (live) rain.start();
        else rain.stop();
        File file = live ? null : cfg.wallpaper();
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            board.setBackdrop(null);
            return;
        }
        int width = getResources().getDisplayMetrics().widthPixels;
        int height = getResources().getDisplayMetrics().heightPixels;
        io.execute(() -> {
            Backdrop backdrop = Backdrop.load(file, width, height, Config.credit(file));
            ui.post(() -> {
                if (!active || !key.equals(wallKey)) return;
                wall.setImageBitmap(backdrop == null ? null : backdrop.full);
                board.setBackdrop(backdrop);
            });
        });
    }

    private int dp(float value) {
        return Ui.dp(this, value);
    }
}
