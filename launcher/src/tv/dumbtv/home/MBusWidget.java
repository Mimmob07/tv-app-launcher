package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The next University of Michigan buses at one stop (the Central Campus Transit Center unless
 * mbus.stop says otherwise), one column per direction: route, where it is headed, and how soon.
 * Side by side when the card is wide, one above the other when it is not. See {@link MBus}.
 */
final class MBusWidget extends Widget {
    private final Runnable changed = this::invalidate;
    private MBus bus;

    MBusWidget(Board board) {
        super(board);
    }

    @Override
    void onShow() {
        bus = MBus.get(getContext());
        bus.listen(changed);
        bus.refresh(cfg(), false);
        invalidate();
    }

    @Override
    void onHide() {
        if (bus != null) bus.unlisten(changed);
    }

    /** Arrivals move by the minute; {@link MBus} only asks again every 30 s while shown. */
    @Override
    void onMinute() {
        if (bus != null) bus.refresh(cfg(), false);
    }

    @Override
    void onConfig() {
        if (bus != null) bus.refresh(cfg(), false);
    }

    @Override
    protected void content(Canvas c, RectF a) {
        Theme t = theme();
        TextPaint head = label(14, t.muted);
        String title = "M-BUS · " + cfg().get("mbus.title", "CCTC").toUpperCase(Locale.getDefault())
                + (MBus.demo(cfg()) && cfg().get("mbus.key", "").isEmpty() ? " · SAMPLE" : "");
        float y = a.top - head.ascent();
        fit(c, title, a.left, y, a.width() * 0.7f, head);

        MBus.Report r = bus == null ? null : bus.report();
        TextPaint msg = paint(t.light, 18, t.muted);
        if (MBus.unset(cfg())) {
            fit(c, "Add your M-Bus API key as mbus.key in home.conf", a.left, a.centerY(), a.width(), msg);
            return;
        }
        if (r == null) {
            String p = bus == null ? "" : bus.problem();
            fit(c, p.isEmpty() ? "Finding the buses…" : p, a.left, a.centerY(), a.width(), msg);
            return;
        }
        if (bus.stale()) {
            TextPaint off = label(12, t.faint);
            off.setTextAlign(Paint.Align.RIGHT);
            c.drawText("OFFLINE", a.right, y, off);
        }
        Map<String, List<MBus.Arrival>> groups = MBus.byDirection(r.arrivals);
        if (groups.isEmpty()) {
            fit(c, "No buses scheduled right now", a.left, a.centerY(), a.width(), msg);
            return;
        }
        List<String> dirs = new ArrayList<>(groups.keySet());
        if (dirs.size() > 2) dirs = dirs.subList(0, 2);
        boolean wide = a.width() > dp(400);
        float top = y + dp(14);
        float gap = dp(28);
        for (int i = 0; i < dirs.size(); i++) {
            RectF col;
            if (wide) {
                float w = (a.width() - gap * (dirs.size() - 1)) / dirs.size();
                col = new RectF(a.left + i * (w + gap), top, a.left + i * (w + gap) + w, a.bottom);
            } else {
                float h = (a.bottom - top - gap * (dirs.size() - 1)) / dirs.size();
                col = new RectF(a.left, top + i * (h + gap), a.right, top + i * (h + gap) + h);
            }
            if (wide && i > 0) {
                Paint line = new Paint();
                line.setColor(t.line);
                line.setStrokeWidth(dp(1));
                c.drawLine(col.left - gap / 2, col.top, col.left - gap / 2, col.bottom, line);
            }
            column(c, col, dirs.get(i), groups.get(dirs.get(i)), t);
        }
    }

    /** One direction: its name, then as many arrivals as fit. */
    private void column(Canvas c, RectF col, String direction, List<MBus.Arrival> list, Theme t) {
        TextPaint dir = paint(t.body, 15, t.muted);
        float y = col.top - dir.ascent();
        fit(c, direction, col.left, y, col.width(), dir);
        y += dp(10);

        TextPaint route = paint(t.label, 14, 0xFFFFFFFF);
        route.setTextAlign(Paint.Align.CENTER);
        TextPaint dest = paint(t.body, 18, t.text);
        TextPaint mins = paint(t.display, 20, t.text);
        mins.setTextAlign(Paint.Align.RIGHT);
        Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
        float row = dp(34), chipW = dp(46), chipH = dp(24);
        for (MBus.Arrival a : list) {
            if (y + row > col.bottom + dp(4)) break;
            float mid = y + row / 2;
            chip.setColor(a.color != 0 ? a.color | 0xFF000000 : t.accent);
            float r = t.radius == 0 ? 0 : dp(6);
            c.drawRoundRect(col.left, mid - chipH / 2, col.left + chipW, mid + chipH / 2, r, r, chip);
            route.setColor(Ui.ink(chip.getColor()));
            shrink(route, a.route, chipW - dp(8), 9);
            c.drawText(a.route, col.left + chipW / 2, mid - (route.ascent() + route.descent()) / 2, route);
            String when = MBus.when(a.countdown);
            float whenW = mins.measureText(when);
            fit(c, a.destination, col.left + chipW + dp(12), mid - (dest.ascent() + dest.descent()) / 2,
                    col.width() - chipW - dp(24) - whenW, dest);
            mins.setColor("Due".equals(when) ? t.accent : t.text);
            c.drawText(when, col.right, mid - (mins.ascent() + mins.descent()) / 2, mins);
            y += row;
        }
    }
}
