package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * The days ahead, one column each: the day, an icon, the high and low, and the chance of rain
 * when it is worth mentioning. As many days as fit, up to six.
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
        int n = Math.min(r.days.size(), Math.max(1, Math.min(6, (int) (a.width() / dp(104)))));
        float col = a.width() / n;
        TextPaint day = label(13, t.muted);
        day.setTextAlign(Paint.Align.CENTER);
        TextPaint hi = paint(t.body, 19, t.text);
        TextPaint lo = paint(t.light, 19, t.muted);
        TextPaint wet = paint(t.body, 13, t.cold);
        Paint line = new Paint();
        line.setColor(t.line);
        line.setStrokeWidth(dp(1));

        // The day names belong to the forecast's own time zone, not necessarily the TV's.
        java.text.DateFormat names = new java.text.SimpleDateFormat("EEE", Locale.getDefault());
        names.setTimeZone(r.zone);
        Calendar today = Calendar.getInstance(r.zone);
        float glyph = Math.min(col * 0.34f, a.height() * 0.40f);
        for (int i = 0; i < n; i++) {
            Weather.Day d = r.days.get(i);
            float cx = a.left + col * (i + 0.5f);
            if (i > 0) c.drawLine(a.left + col * i, a.top + dp(6), a.left + col * i, a.bottom - dp(6), line);
            Calendar when = Calendar.getInstance(r.zone);
            when.setTime(new Date(d.date));
            boolean isToday = when.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && when.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            String name = isToday ? "TODAY" : names.format(new Date(d.date)).toUpperCase(Locale.getDefault());
            c.drawText(name, cx, a.top - day.ascent(), day);

            // icon on the left half of the column, temperatures on the right
            float midY = a.top + (a.height() - day.descent() + day.ascent()) * 0.5f - day.ascent();
            float gx = cx - col * 0.18f;
            Glyphs.draw(c, d.code, true, gx, midY + dp(4), glyph, t);
            float tx = cx + col * 0.02f;
            String h = Math.round(d.high) + "°", l = Math.round(d.low) + "°";
            c.drawText(h, tx, midY + dp(2), hi);
            c.drawText(l, tx, midY + dp(2) - hi.ascent() + dp(2), lo);
            if (d.precip >= RAIN_WORTH) {
                wet.setTextAlign(Paint.Align.CENTER);
                c.drawText(d.precip + "%", cx, a.bottom - wet.descent(), wet);
            }
        }
    }
}
