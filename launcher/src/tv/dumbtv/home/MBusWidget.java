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
 * mbus.stop says otherwise): route, where it is headed, and how soon. A direction filter
 * uses two columns of up to three arrivals each on a wide card. See {@link MBus}.
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
        String direction = cfg().get("mbus.direction", "").trim();
        if (bus.stale() && direction.isEmpty()) {
            TextPaint off = label(12, t.faint);
            off.setTextAlign(Paint.Align.RIGHT);
            c.drawText("OFFLINE", a.right, y, off);
        }
        List<MBus.Arrival> selected = MBus.inDirection(r.arrivals, direction);
        Map<String, List<MBus.Arrival>> groups = MBus.byDirection(selected);
        if (groups.isEmpty()) {
            String message = direction.isEmpty() ? "No buses scheduled right now"
                    : "No " + direction.toLowerCase(Locale.getDefault()) + " buses scheduled right now";
            fit(c, message, a.left, a.centerY(), a.width(), msg);
            return;
        }
        List<String> dirs = new ArrayList<>(groups.keySet());
        if (dirs.size() > 2) dirs = dirs.subList(0, 2);
        boolean wide = a.width() > dp(400);
        float top = y + dp(14);
        if (!direction.isEmpty() && wide) {
            TextPaint heading = label(12, t.muted);
            heading.setTextAlign(Paint.Align.RIGHT);
            String name = direction.toUpperCase(Locale.getDefault()) + (bus.stale() ? " · OFFLINE" : "");
            c.drawText(name, a.right, y, heading);
            List<MBus.Arrival> arrivals = selected;
            int count = Math.min(6, arrivals.size()), split = (count + 1) / 2;
            float gap = dp(20), width = (a.width() - gap) / 2;
            RectF left = new RectF(a.left, top, a.left + width, a.bottom);
            RectF right = new RectF(a.right - width, top, a.right, a.bottom);
            Paint line = new Paint();
            line.setColor(t.line);
            line.setStrokeWidth(dp(1));
            c.drawLine(a.centerX(), top, a.centerX(), a.bottom, line);
            arrivals(c, left, arrivals.subList(0, split), t, true);
            arrivals(c, right, arrivals.subList(split, count), t, true);
            return;
        }
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
        arrivals(c, new RectF(col.left, y, col.right, col.bottom), list, t, false);
    }

    private void arrivals(Canvas c, RectF col, List<MBus.Arrival> list, Theme t, boolean compact) {
        float y = col.top;
        TextPaint route = paint(t.label, 14, 0xFFFFFFFF);
        route.setTextAlign(Paint.Align.CENTER);
        TextPaint dest = paint(t.body, compact ? 16 : 18, t.text);
        TextPaint mins = paint(t.display, compact ? 18 : 20, t.text);
        mins.setTextAlign(Paint.Align.RIGHT);
        Paint chip = new Paint(Paint.ANTI_ALIAS_FLAG);
        float row = compact ? Math.min(dp(40), col.height() / 3) : dp(34);
        float chipW = dp(compact ? 34 : 46), chipH = dp(compact ? 22 : 24);
        float textGap = dp(compact ? 8 : 12);
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
            fit(c, a.destination, col.left + chipW + textGap, mid - (dest.ascent() + dest.descent()) / 2,
                    col.width() - chipW - 2 * textGap - whenW, dest);
            mins.setColor("Due".equals(when) ? t.accent : t.text);
            c.drawText(when, col.right, mid - (mins.ascent() + mins.descent()) / 2, mins);
            y += row;
        }
    }
}
