package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.RectF;
import android.text.TextPaint;
import android.text.format.DateFormat;

import java.util.Date;
import java.util.Locale;

/**
 * The time, large, with the weekday above and the date below. Follows Options › Clock
 * (system, 24-hour or 12-hour) and the TV's time zone.
 */
final class TimeWidget extends Widget {
    TimeWidget(Board board) {
        super(board);
    }

    @Override
    void onShow() {
        invalidate();
    }

    @Override
    void onMinute() {
        invalidate();
    }

    @Override
    protected void content(Canvas c, RectF a) {
        Theme t = theme();
        Date now = new Date();
        String mode = cfg().get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(getContext());
        String time = DateFormat.format(h24 ? "HH:mm" : "h:mm", now).toString();
        String half = h24 ? "" : DateFormat.format("a", now).toString().toUpperCase(Locale.ROOT);
        String day = DateFormat.format("EEEE", now).toString().toUpperCase(Locale.getDefault());
        String date = DateFormat.format(DateFormat.getBestDateTimePattern(Locale.getDefault(), "MMMMd"), now).toString();

        TextPaint dayP = label(14, t.muted);
        TextPaint big = paint(t.display, 112, t.text);
        TextPaint halfP = label(22, t.muted);
        TextPaint dateP = paint(t.light, 24, t.text);

        // The big numbers take what is left once the labels and gaps are counted.
        float halfW = half.isEmpty() ? 0 : halfP.measureText(half) + dp(10);
        shrink(big, time, a.width() - halfW, 40);
        float bigH = -big.ascent() * 0.86f;
        float gap = dp(14), ruleGap = t.ornaments ? dp(18) : dp(12);
        float dayH = -dayP.ascent(), dateH = -dateP.ascent();
        float total = dayH + gap + bigH + ruleGap + dateH;
        float top = a.top + Math.max(0, (a.height() - total) / 2);

        float y = top + dayH;
        c.drawText(day, a.left, y, dayP);
        y += gap + bigH;
        c.drawText(time, a.left - dp(3), y, big);
        if (!half.isEmpty()) {
            c.drawText(half, a.left + big.measureText(time) + dp(8), y, halfP);
        }
        if (t.ornaments) {
            float ry = y + ruleGap * 0.55f;
            rule(c, a.left, Math.min(a.right, a.left + big.measureText(time) + halfW), ry);
        }
        y += ruleGap + dateH;
        fit(c, date, a.left, y, a.width(), dateP);
    }
}
