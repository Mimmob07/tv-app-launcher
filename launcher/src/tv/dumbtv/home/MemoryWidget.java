package tv.dumbtv.home;

import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextPaint;

/**
 * Free memory, as a line of text over a bar of what is in use; amber when Android reports low
 * memory or under {@link Meter#LOW_SHARE} is free. It only shows the numbers and frees nothing.
 */
final class MemoryWidget extends Widget {
    private final ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();

    MemoryWidget(Board board) {
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
        ActivityManager am = (ActivityManager) getContext().getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) return;
        am.getMemoryInfo(info);
        boolean low = Meter.low(info.lowMemory, info.availMem, info.totalMem);
        float used = Meter.used(info.availMem, info.totalMem);

        TextPaint head = label(14, t.muted);
        float y = a.top - head.ascent();
        c.drawText("MEMORY", a.left, y, head);
        TextPaint value = paint(t.display, 30, low ? 0xFFFFB340 : t.text);
        y += dp(14) - value.ascent();
        fit(c, Meter.mb(info.availMem) + " MB free", a.left, y, a.width(), value);
        TextPaint of = paint(t.light, 16, t.muted);
        y += dp(8) - of.ascent();
        fit(c, "of " + Meter.mb(info.totalMem) + " MB" + (low ? " · running low" : ""), a.left, y, a.width(), of);

        float barH = dp(5), top = a.bottom - barH, r = barH / 2;
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        track.setColor(t.line);
        c.drawRoundRect(a.left, top, a.right, a.bottom, r, r, track);
        Paint bar = new Paint(Paint.ANTI_ALIAS_FLAG);
        bar.setColor(low ? 0xFFFFB340 : t.glow);
        c.drawRoundRect(a.left, top, Math.max(a.left + barH, a.left + a.width() * used), a.bottom, r, r, bar);
    }
}
