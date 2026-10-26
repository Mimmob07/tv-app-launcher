package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.graphics.Rect;
import android.graphics.drawable.GradientDrawable;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * The home screen. The top {@link #BOARD_SHARE} of the screen is the widget {@link Board}; the
 * rest is the dock: the focused tile's name, Options and Settings, and one scrolling row with
 * the apps followed by the HDMI inputs.
 */
public final class Home extends Activity {
    /** How long Menu is held to open Options while the child lock is on. */
    static final long UNLOCK_MS = 3000;
    /** Share of the screen height the widgets get; the app row has the rest. */
    static final float BOARD_SHARE = 0.8f;
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final List<TextView> chips = new ArrayList<>();
    private Config cfg;
    private Theme theme;
    private TvInputManager tv;
    private int applied = -1, appsGeneration;
    private boolean resumed, landed, unlockOpened;
    private FrameLayout root;
    private ImageView wall;
    private MatrixRain rain;
    private View shade, divider;
    private Board board;
    private TextView caption, credit;
    private HorizontalScrollView dock;
    private Strip apps, inputs;
    private Map<String, Long> recent = new HashMap<>();
    private Tile moving;
    private String wallKey;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            loadApps(data == null ? null : data.getSchemeSpecificPart());
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    private final TvInputManager.TvInputCallback inputWatch = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            loadInputs();
        }

        @Override
        public void onInputAdded(String id) {
            loadInputs();
        }

        @Override
        public void onInputRemoved(String id) {
            loadInputs();
        }

        @Override
        public void onInputUpdated(String id) {
            loadInputs();
        }
    };

    /** The tiles of one kind in the dock row. */
    private static final class Strip {
        final String kind;
        final LinearLayout tiles;
        List<Item> all = new ArrayList<>();

        Strip(String kind, LinearLayout tiles) {
            this.kind = kind;
            this.tiles = tiles;
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        cfg = Config.get(this);
        theme = Theme.of(this, cfg.get(Config.THEME));
        Future<Map<String, Long>> times = recentFor(Catalog.APPS) == null ? null
                : io.submit(() -> Recent.times(this));
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        setContentView(build());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        if (tv != null) tv.registerCallback(inputWatch, ui);
        firstApps(catalog, times);
        loadInputs();
    }

    /**
     * Waits for the app list and loads the art of the tiles that fit on screen, so the first
     * frame is complete instead of popping the banners in a moment later.
     */
    private void firstApps(Future<List<Item>> catalog, Future<Map<String, Long>> times) {
        try {
            apps.all = catalog.get();
            if (times != null) recent = times.get();
        } catch (ExecutionException e) {
            loadApps(null);
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            loadApps(null);
            return;
        }
        int fit = getResources().getDisplayMetrics().widthPixels / (cardSize()[0] + dp(14)) + 1;
        for (Item it : Catalog.visible(cfg, Catalog.APPS, apps.all, recentFor(Catalog.APPS))) {
            if (fit-- <= 0) break;
            Catalog.art(getPackageManager(), it);
            it.artRequested = true;
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        cfg.refresh();
        cfg.visited();
        if (cfg.version() != applied) apply();
        else background();
        refreshRecent();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        board.show();
        tick();
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        unregisterReceiver(time);
        board.hide();
        rain.stop();
        if (moving != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (tv != null) tv.unregisterCallback(inputWatch);
        io.shutdownNow();
        super.onDestroy();
    }

    /**
     * Android keeps a hidden window's GPU context (about 35 MB on a TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object global = wmg.getMethod("getInstance").invoke(null);
            wmg.getMethod("trimMemory", int.class).invoke(global, TRIM_MEMORY_COMPLETE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not reachable on this build: the context simply stays
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moving != null) stopMoving();
        if (resumed) focusFirst();
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shift(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shift(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    break;
                default:
                    stopMoving();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU && locked()) {
            // Repeats keep arriving while the button is held; the first one past the hold opens it.
            if (e.getRepeatCount() == 0) {
                unlockOpened = false;
            } else if (!unlockOpened && e.getEventTime() - e.getDownTime() >= UNLOCK_MS) {
                unlockOpened = true;
                startActivity(new Intent(this, Options.class));
            }
            return true;
        }
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile) menu((Tile) f);
            else startActivity(new Intent(this, Options.class));
            return true;
        }
        return super.onKeyDown(code, e);
    }

    private View build() {
        root = new FrameLayout(this);
        root.setBackgroundColor(cfg.cover());
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));
        rain = new MatrixRain(this);
        rain.setVisibility(View.GONE);
        root.addView(rain, new FrameLayout.LayoutParams(MATCH, MATCH));
        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        // Darkens the bottom of the picture so the dock reads over any wallpaper.
        View scrim = new View(this);
        scrim.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x66000000, 0xA6000000}));
        int screenH = getResources().getDisplayMetrics().heightPixels;
        root.addView(scrim, new FrameLayout.LayoutParams(MATCH, Math.round(screenH * 0.34f), Gravity.BOTTOM));

        LinearLayout page = new LinearLayout(this) {
            @Override
            public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_DOWN && chips.contains(focused)) {
                    View below = nearest(focused);
                    if (below != null) return below;
                }
                return super.focusSearch(focused, direction);
            }
        };
        page.setOrientation(LinearLayout.VERTICAL);
        page.setClipChildren(false);
        page.setClipToPadding(false);
        root.addView(page, new FrameLayout.LayoutParams(MATCH, MATCH));

        board = new Board(this);
        board.setPadding(dp(40), dp(30), dp(40), dp(8));
        page.addView(board, new LinearLayout.LayoutParams(MATCH, 0, BOARD_SHARE));

        LinearLayout bottom = new LinearLayout(this);
        bottom.setOrientation(LinearLayout.VERTICAL);
        bottom.setClipChildren(false);
        bottom.setClipToPadding(false);
        bottom.setPadding(0, 0, 0, dp(10));
        page.addView(bottom, new LinearLayout.LayoutParams(MATCH, 0, 1f - BOARD_SHARE));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(40), 0, dp(40), 0);
        caption = Ui.text(this, 13, Ui.TEXT, Ui.MEDIUM);
        caption.setSingleLine();
        head.addView(caption, new LinearLayout.LayoutParams(0, WRAP, 1f));
        credit = Ui.text(this, 10, Ui.FAINT, Ui.MEDIUM);
        credit.setSingleLine();
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(WRAP, WRAP);
        cl.rightMargin = dp(6);
        head.addView(credit, cl);
        head.addView(chip("Options", v -> startActivity(new Intent(this, Options.class))));
        head.addView(chip("Settings", v -> openSettings()));
        bottom.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        int edge = dp(40), bleed = dp(10);
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(bleed, dp(8), bleed, dp(2));
        apps = new Strip(Catalog.APPS, strip());
        inputs = new Strip(Catalog.INPUTS, strip());
        divider = new View(this);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(dp(1), Math.round(cardSize()[1] * 0.6f));
        dl.leftMargin = dp(4);
        dl.rightMargin = dp(18);
        row.addView(apps.tiles);
        row.addView(divider, dl);
        row.addView(inputs.tiles);
        dock = new HorizontalScrollView(this) {
            /** Keeps the focused tile centered, except near either end of the row. */
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                if (getChildCount() == 0) return 0;
                int range = Math.max(0, getChildAt(0).getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
                int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                return target - getScrollX();
            }
        };
        dock.setFocusable(false);
        dock.setHorizontalScrollBarEnabled(false);
        dock.setOverScrollMode(View.OVER_SCROLL_NEVER);
        dock.setClipChildren(false);
        dock.setClipToPadding(false);
        dock.setPadding(edge - bleed, 0, edge - bleed, 0);
        dock.addView(row, new FrameLayout.LayoutParams(WRAP, MATCH));
        bottom.addView(dock, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return root;
    }

    private LinearLayout strip() {
        LinearLayout s = new LinearLayout(this);
        s.setGravity(Gravity.CENTER_VERTICAL);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        return s;
    }

    /** The dock tile horizontally closest to {@code from}. */
    private View nearest(View from) {
        int[] at = new int[2];
        from.getLocationOnScreen(at);
        int x = at[0] + from.getWidth() / 2;
        View best = null;
        int bestGap = Integer.MAX_VALUE;
        for (Strip s : new Strip[] {apps, inputs}) {
            for (int i = 0; i < s.tiles.getChildCount(); i++) {
                View c = s.tiles.getChildAt(i);
                c.getLocationOnScreen(at);
                int gap = Math.abs(at[0] + c.getWidth() / 2 - x);
                if (gap < bestGap) {
                    bestGap = gap;
                    best = c;
                }
            }
        }
        return best;
    }

    private boolean locked() {
        return "on".equals(cfg.get(Config.LOCK));
    }

    private TextView chip(String label, View.OnClickListener click) {
        TextView v = Ui.text(this, 12, Ui.TEXT, Ui.MEDIUM);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(14), dp(5), dp(14), dp(5));
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(click);
        v.setOnFocusChangeListener((view, has) -> {
            view.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(110).start();
            if (has) caption.setText("");
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(8);
        v.setLayoutParams(lp);
        chips.add(v);
        return v;
    }

    private void apply() {
        applied = cfg.version();
        theme = Theme.of(this, cfg.get(Config.THEME));
        int accent = cfg.accent(theme);
        root.setBackgroundColor(cfg.cover());
        for (TextView c : chips) {
            int idle = theme.rain ? (theme.glow & 0x00FFFFFF) | 0x26000000 : 0x26FFFFFF;
            Ui.focusStyle(c, accent, idle, dp(theme.radius == 0 ? 0 : 14));
            c.setTypeface(theme.label);
            c.setLetterSpacing(theme.tracking * 0.6f);
            c.setVisibility(locked() ? View.GONE : View.VISIBLE);
        }
        caption.setTypeface(theme.label);
        caption.setTextColor(theme.text);
        caption.setLetterSpacing(theme.tracking);
        if (theme.shadow) caption.setShadowLayer(0.01f, dp(1.5f), dp(1.5f), 0xFF3F3F3F);
        else caption.setShadowLayer(0, 0, 0, 0);
        credit.setTextColor(theme.faint);
        divider.setBackgroundColor(theme.line);
        board.setup(cfg, theme);
        render(apps);
        render(inputs);
        background();
        tick();
    }

    /** Rebuilds the app list, keeping loaded art for every package except {@code changed}. */
    private void loadApps(String changed) {
        int generation = ++appsGeneration;
        io.execute(() -> {
            List<Item> list = Catalog.apps(this);
            ui.post(() -> {
                if (generation != appsGeneration || isDestroyed()) return;
                for (Item fresh : list) {
                    for (Item old : apps.all) {
                        if (old.id.equals(fresh.id) && !old.id.equals(changed) && old.artRequested) {
                            fresh.banner = old.banner;
                            fresh.icon = old.icon;
                            fresh.artRequested = true;
                        }
                    }
                }
                apps.all = list;
                render(apps);
            });
        });
    }

    private void loadInputs() {
        inputs.all = Catalog.inputs(this, tv);
        render(inputs);
    }

    /** The last-used times to sort {@code kind} by, or null when it keeps the user's order. */
    private Map<String, Long> recentFor(String kind) {
        return Catalog.APPS.equals(kind) && "recent".equals(cfg.get(Config.ORDER)) ? recent : null;
    }

    /** With App order › Recently used, re-sorts the apps on the way back home. */
    private void refreshRecent() {
        if (recentFor(Catalog.APPS) == null) return;
        io.execute(() -> {
            Map<String, Long> times = Recent.times(this);
            ui.post(() -> {
                if (isDestroyed() || recentFor(Catalog.APPS) == null) return;
                List<Item> before = Catalog.visible(cfg, Catalog.APPS, apps.all, recent);
                recent = times;
                if (!before.equals(Catalog.visible(cfg, Catalog.APPS, apps.all, recent))) render(apps);
            });
        });
    }

    private void render(Strip strip) {
        if (moving != null && moving.kind.equals(strip.kind)) stopMoving();
        View focused = getCurrentFocus();
        String keep = focused instanceof Tile && ((Tile) focused).kind.equals(strip.kind)
                ? ((Tile) focused).item.id : null;
        strip.tiles.removeAllViews();

        boolean enabled = !Catalog.INPUTS.equals(strip.kind) || "on".equals(cfg.get(Config.INPUTS));
        List<Item> shown = enabled ? Catalog.visible(cfg, strip.kind, strip.all, recentFor(strip.kind))
                : new ArrayList<>();
        int[] size = cardSize();
        int accent = cfg.accent(theme);
        int index = 0, restore = -1;
        for (Item it : shown) {
            int w = Catalog.APPS.equals(strip.kind) ? size[0] : Math.round(size[0] * 0.8f);
            Tile t = new Tile(this, it, strip.kind, Catalog.label(cfg, it), w, size[1], accent, theme);
            t.setOnClickListener(v -> open((Tile) v));
            t.setOnFocusChangeListener((v, has) -> {
                if (has) caption.setText(Catalog.label(cfg, ((Tile) v).item).toString().toUpperCase(Locale.getDefault()));
            });
            t.setOnLongClickListener(v -> {
                if (!locked()) menu((Tile) v);
                return true;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
            lp.rightMargin = dp(14);
            strip.tiles.addView(t, lp);
            if (it.id.equals(keep)) restore = index;
            index++;
            if (Catalog.APPS.equals(strip.kind) && !it.artRequested) loadArt(it);
        }
        boolean both = apps.tiles.getChildCount() > 0 && inputs.tiles.getChildCount() > 0;
        divider.setVisibility(both ? View.VISIBLE : View.GONE);
        if (restore >= 0) {
            strip.tiles.getChildAt(restore).requestFocus();
        } else if (keep != null && strip.tiles.getChildCount() > 0) {
            strip.tiles.getChildAt(0).requestFocus();
        } else if (getCurrentFocus() == null || !landed) {
            focusFirst();
        }
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(getPackageManager(), it);
            ui.post(() -> {
                for (int i = 0; i < apps.tiles.getChildCount(); i++) apps.tiles.getChildAt(i).invalidate();
            });
        });
    }

    /** 16:9 tiles as tall as the dock allows, after its caption line and padding. */
    private int[] cardSize() {
        int screenH = getResources().getDisplayMetrics().heightPixels;
        int h = Math.round(screenH * (1f - BOARD_SHARE)) - dp(44);
        return new int[] {Math.round(h * 16f / 9f), h};
    }

    private void focusFirst() {
        dock.scrollTo(0, 0);
        View first = apps.tiles.getChildAt(0);
        if (first == null) first = inputs.tiles.getChildAt(0);
        landed = apps.tiles.getChildCount() > 0;
        if (first == null) first = chips.get(0);
        first.requestFocus();
    }

    private void tick() {
        board.minute();
        // hourly and time-of-day rotations change while the screen stays open; a no-op otherwise
        background();
    }

    private void background() {
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        // A theme that animates its own background shows that instead of pictures.
        boolean live = theme.rain && "theme".equals(cfg.get(Config.BACKGROUND));
        rain.setVisibility(live ? View.VISIBLE : View.GONE);
        if (live && resumed) rain.start();
        else rain.stop();
        if (live) {
            // Picture dim is for pictures; the rain is drawn as dark as it should be.
            shade.setBackgroundColor(Color.TRANSPARENT);
            if (wall.getDrawable() != null) wall.setImageDrawable(null);
            if (board.backdrop != null) board.setBackdrop(null);
            credit.setText("");
            wallKey = null;
            return;
        }
        File file = cfg.wallpaper();
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            board.setBackdrop(null);
            credit.setText("");
            return;
        }
        int w = getResources().getDisplayMetrics().widthPixels, h = getResources().getDisplayMetrics().heightPixels;
        io.execute(() -> {
            Backdrop b = Backdrop.load(file, w, h, Config.credit(file));
            ui.post(() -> {
                if (!key.equals(wallKey) || isDestroyed()) return;
                wall.setImageBitmap(b == null ? null : b.full);
                board.setBackdrop(b);
                credit.setText(b == null ? "" : b.credit);
            });
        });
    }

    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void open(Tile t) {
        if (Guard.APPSTORE.equals(t.item.id)) {
            // Its launcher entry forwards to a tab of Amazon's home; its own home does not.
            Intent store = new Intent(Intent.ACTION_MAIN).setClassName(Guard.APPSTORE, Guard.APPSTORE_HOME);
            if (store.resolveActivity(getPackageManager()) != null) {
                launch(store, null);
                Recent.opened(this, t.item.id);
                return;
            }
        }
        if (Catalog.APPS.equals(t.kind)) {
            launch(new Intent(t.item.intent), t.item.id);
            return;
        }
        Intent own = Input.intent(this, cfg, t.item, Catalog.label(cfg, t.item));
        // The built-in screen goes on top of this one in the same task, so BACK returns here and
        // the HOME takeover's singleTask relaunch clears it. NEW_TASK | RESET_TASK_IF_NEEDED on an
        // activity of the task that is already in front only brings that task forward and starts
        // nothing (ActivityStarter.complyActivityFlags), so it is started plainly.
        if (own != null) startActivity(own);
        else launch(new Intent(t.item.intent), null);
    }

    private void launch(Intent i, String pkg) {
        // Amazon's home, and the Appstore's launcher entry, which forwards to a tab of Amazon's home.
        if (Guard.AMAZON.equals(pkg) || Guard.APPSTORE.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
            if (pkg != null) Recent.opened(this, pkg);
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(Tile t) {
        boolean app = Catalog.APPS.equals(t.kind);
        String[] actions = app
                ? new String[] {"Move", "Rename", "Hide", "App info"}
                : new String[] {"Move", "Rename", "Hide"};
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.label(cfg, t.item))
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0:
                            if (t.getParent() == null) break;
                            if (app && recentFor(t.kind) != null) {
                                Toast.makeText(this, "App order is Recently used. Pick Your order "
                                        + "in Options to move tiles.", Toast.LENGTH_LONG).show();
                                break;
                            }
                            moving = t;
                            t.setMoving(true);
                            break;
                        case 1:
                            rename(t);
                            break;
                        case 2:
                            Catalog.setShown(cfg, t.kind, t.item, false);
                            render(app ? apps : inputs);
                            Toast.makeText(this, "Hidden. Bring it back from Options.", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", t.item.id, null)), null);
                    }
                })
                .show();
    }

    private void rename(Tile t) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setText(Catalog.label(cfg, t.item));
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(24), dp(8), dp(24), 0);
        box.addView(field);
        String key = "name." + t.item.id;
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Rename")
                .setView(box)
                .setPositiveButton("Save", (dialog, w) -> {
                    String name = field.getText().toString().trim();
                    cfg.set(key, name.equals(t.item.label.toString()) ? null : name);
                    apply();
                })
                .setNeutralButton("Original", (dialog, w) -> {
                    cfg.set(key, null);
                    apply();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void shift(int by) {
        ViewGroup strip = (ViewGroup) moving.getParent();
        int from = strip.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= strip.getChildCount()) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        strip.removeViewAt(from);
        strip.addView(moving, to, lp);
        moving.requestFocus();
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        ViewGroup strip = (ViewGroup) t.getParent();
        if (strip == null) return;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < strip.getChildCount(); i++) ids.add(((Tile) strip.getChildAt(i)).item.id);
        Catalog.saveOrder(cfg, t.kind, ids);
        applied = cfg.version();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
