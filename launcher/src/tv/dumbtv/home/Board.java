package tv.dumbtv.home;

import android.content.Context;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The widget area: the top of the home screen, a grid of {@link #COLS} x {@link #ROWS} cells.
 * home.conf lists the widgets in order, each with an optional size and position:
 *
 * <pre>widgets=time:5x4,weather:7x4,forecast:12x2</pre>
 *
 * "time:5x4" is five columns by four rows; "memory:4x2+8+4" also pins it to column 8, row 4
 * (X11 geometry: width x height + column + row).
 * Widgets without a position are packed in order, left to right and top to bottom, into the
 * first free spot that fits. One that fits nowhere is left out and logged.
 */
final class Board extends ViewGroup {
    static final int COLS = 12, ROWS = 6;
    static final String KEY = "widgets";
    static final String DEFAULT = "time:5x4,weather:7x4,forecast:12x2";

    /** One entry of the widgets= list. Position -1 means "pack it". */
    static final class Spec {
        final String id;
        final int cols, rows, col, row;

        Spec(String id, int cols, int rows, int col, int row) {
            this.id = id;
            this.cols = cols;
            this.rows = rows;
            this.col = col;
            this.row = row;
        }
    }

    Theme theme;
    Config cfg;
    Backdrop backdrop;
    private final List<Widget> widgets = new ArrayList<>();
    private final List<int[]> cells = new ArrayList<>();
    private String built;
    private boolean shown;

    Board(Context c) {
        super(c);
        setClipChildren(false);
        setClipToPadding(false);
    }

    /** Rebuilds the widgets when the list changed, otherwise restyles the ones there are. */
    void setup(Config cfg, Theme theme) {
        this.cfg = cfg;
        boolean themeChanged = this.theme == null || !this.theme.id.equals(theme.id);
        this.theme = theme;
        String spec = cfg.get(KEY, DEFAULT);
        if (spec.equals(built) && !themeChanged) {
            for (Widget w : widgets) {
                w.onConfig();
                w.invalidate();
            }
            return;
        }
        if (shown) for (Widget w : widgets) w.onHide();
        removeAllViews();
        widgets.clear();
        cells.clear();
        built = spec;
        List<Spec> specs = new ArrayList<>();
        for (String token : spec.split(",")) {
            Spec s = parse(token);
            if (s != null) specs.add(s);
        }
        List<int[]> placed = pack(specs, COLS, ROWS);
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            int[] at = placed.get(i);
            Widget w = at == null ? null : Widgets.create(s.id, this);
            if (w == null) {
                Log.w("dumbtv", "widget left out: " + s.id + (at == null ? " (no room)" : " (unknown id)"));
                continue;
            }
            widgets.add(w);
            cells.add(at);
            addView(w);
            w.onConfig();
            if (shown) w.onShow();
        }
    }

    void setBackdrop(Backdrop b) {
        backdrop = b;
        for (Widget w : widgets) w.invalidate();
    }

    void show() {
        if (shown) return;
        shown = true;
        for (Widget w : widgets) w.onShow();
    }

    void hide() {
        if (!shown) return;
        shown = false;
        for (Widget w : widgets) w.onHide();
    }

    void minute() {
        for (Widget w : widgets) w.onMinute();
    }

    /**
     * "id", "id:CxR" or "id:CxR+col+row". A size left out comes from {@link Widgets}; sizes are
     * clamped to the grid. Unknown or malformed entries give null.
     */
    static Spec parse(String token) {
        String t = token.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return null;
        String id = t, size = null, pos = null;
        int colon = t.indexOf(':');
        if (colon >= 0) {
            id = t.substring(0, colon).trim();
            size = t.substring(colon + 1).trim();
            int plus = size.indexOf('+');
            if (plus >= 0) {
                pos = size.substring(plus + 1).trim();
                size = size.substring(0, plus).trim();
            }
        }
        if (id.isEmpty()) return null;
        int[] def = Widgets.size(id);
        int cols = def[0], rows = def[1], col = -1, row = -1;
        try {
            if (size != null && !size.isEmpty()) {
                String[] wh = size.split("x");
                cols = Integer.parseInt(wh[0].trim());
                rows = Integer.parseInt(wh[1].trim());
            }
            if (pos != null && !pos.isEmpty()) {
                String[] cr = pos.split("\\+");
                col = Integer.parseInt(cr[0].trim());
                row = Integer.parseInt(cr[1].trim());
            }
        } catch (RuntimeException e) {
            return null;
        }
        cols = Math.max(1, Math.min(COLS, cols));
        rows = Math.max(1, Math.min(ROWS, rows));
        return new Spec(id, cols, rows, col, row);
    }

    /**
     * Where each spec goes, as {col, row, cols, rows}, or null when it does not fit. Pinned
     * specs claim their cells first; the rest fill the first free spot, scanning rows top to
     * bottom and columns left to right.
     */
    static List<int[]> pack(List<Spec> specs, int cols, int rows) {
        boolean[][] used = new boolean[rows][cols];
        List<int[]> out = new ArrayList<>();
        for (int i = 0; i < specs.size(); i++) out.add(null);
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            if (s.col >= 0 && s.row >= 0 && free(used, s.col, s.row, s.cols, s.rows)) {
                take(used, s.col, s.row, s.cols, s.rows);
                out.set(i, new int[] {s.col, s.row, s.cols, s.rows});
            }
        }
        for (int i = 0; i < specs.size(); i++) {
            Spec s = specs.get(i);
            if (s.col >= 0 && s.row >= 0) continue;
            search:
            for (int r = 0; r + s.rows <= rows; r++) {
                for (int c = 0; c + s.cols <= cols; c++) {
                    if (free(used, c, r, s.cols, s.rows)) {
                        take(used, c, r, s.cols, s.rows);
                        out.set(i, new int[] {c, r, s.cols, s.rows});
                        break search;
                    }
                }
            }
        }
        return out;
    }

    private static boolean free(boolean[][] used, int c, int r, int w, int h) {
        if (r + h > used.length || c + w > used[0].length) return false;
        for (int y = r; y < r + h; y++) {
            for (int x = c; x < c + w; x++) {
                if (used[y][x]) return false;
            }
        }
        return true;
    }

    private static void take(boolean[][] used, int c, int r, int w, int h) {
        for (int y = r; y < r + h; y++) {
            for (int x = c; x < c + w; x++) used[y][x] = true;
        }
    }

    private float gap() {
        return 14 * getResources().getDisplayMetrics().density;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec), h = MeasureSpec.getSize(heightSpec);
        setMeasuredDimension(w, h);
        float g = gap();
        float cw = (w - getPaddingLeft() - getPaddingRight() - g * (COLS - 1)) / COLS;
        float ch = (h - getPaddingTop() - getPaddingBottom() - g * (ROWS - 1)) / ROWS;
        for (int i = 0; i < widgets.size(); i++) {
            int[] at = cells.get(i);
            widgets.get(i).measure(
                    MeasureSpec.makeMeasureSpec(Math.round(cw * at[2] + g * (at[2] - 1)), MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(Math.round(ch * at[3] + g * (at[3] - 1)), MeasureSpec.EXACTLY));
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        float g = gap();
        float cw = (getWidth() - getPaddingLeft() - getPaddingRight() - g * (COLS - 1)) / COLS;
        float ch = (getHeight() - getPaddingTop() - getPaddingBottom() - g * (ROWS - 1)) / ROWS;
        for (int i = 0; i < widgets.size(); i++) {
            int[] at = cells.get(i);
            View v = widgets.get(i);
            int x = Math.round(getPaddingLeft() + at[0] * (cw + g));
            int y = Math.round(getPaddingTop() + at[1] * (ch + g));
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
        }
    }
}
