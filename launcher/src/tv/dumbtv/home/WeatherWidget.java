package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.format.DateFormat;

import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Conditions now (icon, temperature, what it feels like, high and low) over a strip of the
 * next hours, three hours apart, like the forecast bar in Breath of the Wild's HUD.
 */
final class WeatherWidget extends Widget {
    private final Runnable changed = this::invalidate;
    private Weather weather;

    WeatherWidget(Board board) {
        super(board);
    }

    @Override
    void onShow() {
        weather = Weather.get(getContext());
        weather.listen(changed);
        weather.refresh(cfg(), false);
        invalidate();
    }

    @Override
    void onHide() {
        if (weather != null) weather.unlisten(changed);
    }

    @Override
    void onMinute() {
        if (weather != null) weather.refresh(cfg(), false);
        invalidate();
    }

    @Override
    void onConfig() {
        if (weather != null) weather.refresh(cfg(), false);
    }

    @Override
    protected void content(Canvas c, RectF a) {
        Theme t = theme();
        Weather.Report r = weather == null ? null : weather.report();
        String place = cfg().get("weather.place", "Weather").toUpperCase(Locale.getDefault());
        TextPaint head = label(14, t.muted);
        float y = a.top - head.ascent();
        fit(c, place, a.left, y, a.width() * 0.7f, head);
        if (r == null) {
            TextPaint msg = paint(t.light, 20, t.muted);
            String text = Weather.unset(cfg()) ? "Set weather.lat and weather.lon in home.conf" : "Looking at the sky…";
            fit(c, text, a.left, a.centerY(), a.width(), msg);
            return;
        }
        if (weather.stale()) {
            TextPaint off = label(12, t.faint);
            off.setTextAlign(Paint.Align.RIGHT);
            c.drawText("OFFLINE", a.right, y, off);
        }

        // Main row: icon, the temperature, and the details beside it. A short card has no room for
        // the strip of hours, so the main row takes all of it.
        boolean strip = a.height() >= dp(170);
        float rowTop = y + dp(12), rowH = strip ? a.height() * 0.46f : a.bottom - rowTop;
        float icon = Math.min(rowH, dp(120));
        Glyphs.draw(c, r.code, r.day, a.left + icon / 2, rowTop + rowH / 2, icon, t);
        TextPaint big = paint(t.display, 84, t.text);
        String temp = r.deg(r.temp);
        float x = a.left + icon + dp(16);
        float base = rowTop + rowH / 2 - (big.ascent() + big.descent()) / 2 - dp(4);
        c.drawText(temp, x, base, big);
        x += big.measureText(temp) + dp(22);

        TextPaint cond = paint(t.body, 22, t.text);
        TextPaint small = paint(t.light, 17, t.muted);
        float room = a.right - x;
        float line = dp(26);
        float top = rowTop + rowH / 2 - line * 1.1f;
        String feels = "Feels like " + r.deg(r.feels);
        String range = r.days.isEmpty() ? ""
                : "H " + r.deg(r.days.get(0).high) + "   L " + r.deg(r.days.get(0).low);
        // Wide faces (Monocraft) shrink to fit rather than lose the numbers to an ellipsis.
        shrink(cond, Weather.describe(r.code), room, 14);
        shrink(small, feels.length() > range.length() ? feels : range, room, 12);
        fit(c, Weather.describe(r.code), x, top, room, cond);
        fit(c, feels, x, top + line, room, small);
        if (!range.isEmpty()) fit(c, range, x, top + line * 2, room, small);

        // The strip: the next six slots, three hours apart.
        if (!strip) return;
        List<Weather.Hour> next = Weather.upcoming(r, 6, 3, System.currentTimeMillis());
        if (next.isEmpty()) return;
        float stripTop = rowTop + rowH + dp(14);
        rule(c, a.left, a.right, stripTop);
        float slot = a.width() / next.size();
        TextPaint when = label(12, t.muted);
        when.setTextAlign(Paint.Align.CENTER);
        TextPaint deg = paint(t.body, 17, t.text);
        deg.setTextAlign(Paint.Align.CENTER);
        boolean h24 = "24h".equals(cfg().get(Config.CLOCK))
                || "auto".equals(cfg().get(Config.CLOCK)) && DateFormat.is24HourFormat(getContext());
        float left = a.bottom - stripTop - dp(10);
        float glyph = Math.min(slot * 0.5f, left * 0.42f);
        for (int i = 0; i < next.size(); i++) {
            Weather.Hour h = next.get(i);
            float cx = a.left + slot * (i + 0.5f);
            String label = DateFormat.format(h24 ? "HH:mm" : "h a", new Date(h.time)).toString()
                    .toUpperCase(Locale.getDefault());
            float ty = stripTop + dp(10) - when.ascent();
            c.drawText(label, cx, ty, when);
            Glyphs.draw(c, h.code, h.day, cx, ty + dp(8) + glyph / 2, glyph, t);
            c.drawText(r.deg(h.temp), cx, a.bottom - deg.descent(), deg);
        }
    }
}
