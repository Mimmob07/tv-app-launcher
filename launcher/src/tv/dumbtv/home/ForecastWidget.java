package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Up to six days ahead: the day, an icon, the high and low, and the chance of rain when it
 * is worth mentioning. Narrow, tall cards arrange the days in two rows of three.
 */
final class ForecastWidget extends Widget {
    private static final int RAIN_WORTH = 20;
    private final Runnable changed = this::invalidate;
    private Weather weather;

    ForecastWidget(Board board) {
        super(board);
    }

    @Override
    protected float padding() {
        return 16;
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
        invalidate();
    }

    @Override
    protected void content(Canvas c, RectF a) {
        Theme t = theme();
        Weather.Report r = weather == null ? null : weather.report();
        if (r == null || r.days.isEmpty()) return;
        boolean stacked = a.width() < dp(6 * 104) && a.height() >= dp(132);
        int n = Math.min(r.days.size(), stacked ? 6 : Math.max(1, Math.min(6, (int) (a.width() / dp(104)))));
        int columns = stacked ? Math.min(3, n) : n;
        int rows = (n + columns - 1) / columns;
        float gap = rows > 1 ? dp(12) : 0;
        float rowHeight = (a.height() - gap * (rows - 1)) / rows;
        float col = a.width() / columns;
        TextPaint day = label(stacked ? 11 : 13, t.muted);
        day.setTextAlign(Paint.Align.CENTER);
        TextPaint hi = paint(t.body, stacked ? 15 : 19, t.text);
        TextPaint lo = paint(t.light, stacked ? 15 : 19, t.muted);
        TextPaint wet = paint(t.body, stacked ? 10 : 13, t.cold);
        Paint line = new Paint();
        line.setColor(t.line);
        line.setStrokeWidth(dp(1));

        // The day names belong to the forecast's own time zone, not necessarily the TV's.
        java.text.DateFormat names = new java.text.SimpleDateFormat("EEE", Locale.getDefault());
        names.setTimeZone(r.zone);
        Calendar today = Calendar.getInstance(r.zone);
        for (int i = 0; i < n; i++) {
            Weather.Day d = r.days.get(i);
            int column = i % columns, row = i / columns;
            float top = a.top + row * (rowHeight + gap), bottom = top + rowHeight;
            float cx = a.left + col * (column + 0.5f);
            if (column > 0) c.drawLine(a.left + col * column, top + dp(6),
                    a.left + col * column, bottom - dp(6), line);
            if (row > 0 && column == 0) c.drawLine(a.left + dp(6), top - gap / 2,
                    a.right - dp(6), top - gap / 2, line);
            Calendar when = Calendar.getInstance(r.zone);
            when.setTime(new Date(d.date));
            boolean isToday = when.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && when.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            String name = isToday ? "TODAY" : names.format(new Date(d.date)).toUpperCase(Locale.getDefault());
            c.drawText(name, cx, top - day.ascent(), day);

            // icon on the left half of the column, temperatures on the right
            float midY = top + (rowHeight - day.descent() + day.ascent()) * 0.5f - day.ascent();
            float glyph = Math.min(col * 0.34f, rowHeight * 0.40f);
            float highY = midY + dp(2), lowY = highY - hi.ascent() + dp(2);
            float iconY = midY + dp(4);
            if (stacked) {
                float bodyTop = top - day.ascent() + day.descent() + dp(4);
                float bodyBottom = bottom - (wet.descent() - wet.ascent()) - dp(3);
                midY = (bodyTop + bodyBottom) / 2;
                float textHeight = hi.descent() - hi.ascent() + dp(1) + lo.descent() - lo.ascent();
                highY = midY - textHeight / 2 - hi.ascent();
                lowY = highY + hi.descent() + dp(1) - lo.ascent();
                iconY = midY;
                glyph = Math.min(col * 0.34f, (bodyBottom - bodyTop) * 0.95f);
            }
            float gx = cx - col * 0.18f;
            Glyphs.draw(c, d.code, true, gx, iconY, glyph, t);
            float tx = cx + col * 0.02f;
            String h = Math.round(d.high) + "°", l = Math.round(d.low) + "°";
            c.drawText(h, tx, highY, hi);
            c.drawText(l, tx, lowY, lo);
            if (d.precip >= RAIN_WORTH) {
                wet.setTextAlign(Paint.Align.CENTER);
                c.drawText(d.precip + "%", cx, bottom - wet.descent(), wet);
            }
        }
    }
}
