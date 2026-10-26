package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.View;

/**
 * One card on the {@link Board}. The card itself (frosted glass over the wallpaper, its edge,
 * the theme's ornaments) is drawn here; a widget only draws its content into {@link #content}.
 *
 * To add a widget: subclass this, draw in {@link #content}, override the lifecycle calls you
 * need, and add one line to {@link Widgets}. launcher/WIDGETS.md walks through it.
 */
abstract class Widget extends View {
    protected final Board board;
    private final RectF card = new RectF();
    private final RectF inner = new RectF();
    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ornament = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix shaderMatrix = new Matrix();
    private final Path path = new Path();
    private final int[] at = new int[2];
    private Bitmap shaderFor;

    Widget(Board board) {
        super(board.getContext());
        this.board = board;
        edge.setStyle(Paint.Style.STROKE);
        ornament.setStyle(Paint.Style.STROKE);
        ornament.setStrokeCap(Paint.Cap.SQUARE);
    }

    /** The home screen came to the front: start timers, subscribe to data. */
    void onShow() {}

    /** The home screen left the front: stop whatever {@link #onShow} started. */
    void onHide() {}

    /** Once a minute while shown, and when the clock or time zone changes. */
    void onMinute() {}

    /** The theme or home.conf changed. Read settings again here. */
    void onConfig() {}

    /** Draws the widget's content inside {@code area}: the card minus its padding. */
    protected abstract void content(Canvas c, RectF area);

    protected final Theme theme() {
        return board.theme;
    }

    protected final Config cfg() {
        return board.cfg;
    }

    protected final float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    /** Padding between the card edge and {@link #content}'s area, in dp. */
    protected float padding() {
        return 22;
    }

    /** A text paint in one of the theme's faces, {@code size} in dp. */
    protected final TextPaint paint(Typeface face, float size, int color) {
        TextPaint p = new TextPaint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
        p.setTypeface(face);
        p.setTextSize(dp(size));
        p.setColor(color);
        if (theme().shadow) {
            // Minecraft's hard shadow: the text again, a quarter as bright, two pixels down-right.
            float off = Math.max(dp(1.5f), dp(size) / 16);
            p.setShadowLayer(0.01f, off, off, shade(color));
        }
        return p;
    }

    private static int shade(int color) {
        int a = color >>> 24, r = (color >> 16 & 0xFF) / 4, g = (color >> 8 & 0xFF) / 4, b = (color & 0xFF) / 4;
        return a << 24 | r << 16 | g << 8 | b;
    }

    /** A small capital label in the theme's label face and letter spacing. */
    protected final TextPaint label(float size, int color) {
        TextPaint p = paint(theme().label, size, color);
        p.setLetterSpacing(theme().tracking);
        return p;
    }

    /** Draws {@code s} cut to {@code width} with an ellipsis. */
    protected static void fit(Canvas c, CharSequence s, float x, float baseline, float width, TextPaint p) {
        CharSequence cut = TextUtils.ellipsize(s, p, width, TextUtils.TruncateAt.END);
        c.drawText(cut, 0, cut.length(), x, baseline, p);
    }

    /** Shrinks {@code p} until {@code s} fits {@code width}, never below {@code min} dp. */
    protected final void shrink(TextPaint p, String s, float width, float min) {
        while (p.measureText(s) > width && p.getTextSize() > dp(min)) p.setTextSize(p.getTextSize() - dp(1));
    }

    /** A thin rule; with ornaments on, it carries a small diamond in the middle. */
    protected final void rule(Canvas c, float left, float right, float y) {
        Theme t = theme();
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(t.line);
        p.setStrokeWidth(dp(1));
        if (!t.ornaments) {
            c.drawLine(left, y, right, y, p);
            return;
        }
        float mid = (left + right) / 2, d = dp(4);
        c.drawLine(left, y, mid - d * 2.2f, y, p);
        c.drawLine(mid + d * 2.2f, y, right, y, p);
        diamond(c, mid, y, d, t.glow);
    }

    protected final void diamond(Canvas c, float cx, float cy, float r, int color) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(color);
        Path d = new Path();
        d.moveTo(cx, cy - r);
        d.lineTo(cx + r, cy);
        d.lineTo(cx, cy + r);
        d.lineTo(cx - r, cy);
        d.close();
        c.drawPath(d, p);
    }

    @Override
    protected final void onDraw(Canvas c) {
        Theme t = theme();
        if (t == null) return;
        float r = dp(t.radius), half = dp(0.75f);
        card.set(half, half, getWidth() - half, getHeight() - half);

        Backdrop back = board.backdrop;
        if (back != null && back.blur != null) {
            if (shaderFor != back.blur) {
                glass.setShader(new BitmapShader(back.blur, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
                shaderFor = back.blur;
            }
            // The blur covers the whole screen at 1/scale; shift it so this card shows what is
            // behind it.
            getLocationOnScreen(at);
            float scale = (float) back.width / back.blur.getWidth();
            shaderMatrix.setScale(scale, scale);
            shaderMatrix.postTranslate(-at[0], -at[1]);
            glass.getShader().setLocalMatrix(shaderMatrix);
            c.drawRoundRect(card, r, r, glass);
            tint.setColor(t.glass);
        } else {
            // No picture to frost: opaque, except over an animated background meant to show through.
            tint.setColor(t.rain ? t.glass : t.glass | 0xFF000000);
        }
        c.drawRoundRect(card, r, r, tint);

        if (!t.bevel && !t.rain) {
            // A faint sheen down from the top edge, so the glass reads as glass.
            sheen.setShader(new LinearGradient(0, 0, 0, getHeight() * 0.6f,
                    t.ornaments ? 0x146FE3FF : 0x1AFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawRoundRect(card, r, r, sheen);
        }

        if (t.bevel) {
            bevel(c);
        } else {
            edge.setColor(t.edge);
            edge.setStrokeWidth(dp(1.25f));
            c.drawRoundRect(card, r, r, edge);
        }
        if (t.ornaments) brackets(c, t);

        float pad = dp(padding());
        inner.set(pad, pad, getWidth() - pad, getHeight() - pad);
        content(c, inner);
    }

    /** A Minecraft panel edge: a black outline, lit inside along the top and left, dark along the rest. */
    private void bevel(Canvas c) {
        float p = Math.round(dp(2)), w = getWidth(), h = getHeight();
        Paint f = tint;
        f.setColor(0xFF000000);
        c.drawRect(0, 0, w, p, f);
        c.drawRect(0, h - p, w, h, f);
        c.drawRect(0, 0, p, h, f);
        c.drawRect(w - p, 0, w, h, f);
        f.setColor(0x59FFFFFF);
        c.drawRect(p, p, w - p, p * 2, f);
        c.drawRect(p, p * 2, p * 2, h - p * 2, f);
        f.setColor(0x73000000);
        c.drawRect(p * 2, h - p * 2, w - p, h - p, f);
        c.drawRect(w - p * 2, p * 2, w - p, h - p * 2, f);
    }

    /** Sheikah-style corner brackets, with a soft glow under each. */
    private void brackets(Canvas c, Theme t) {
        float in = dp(7), len = dp(16), w = getWidth(), h = getHeight();
        path.reset();
        path.moveTo(in, in + len);
        path.lineTo(in, in);
        path.lineTo(in + len, in);
        path.moveTo(w - in - len, in);
        path.lineTo(w - in, in);
        path.lineTo(w - in, in + len);
        path.moveTo(w - in, h - in - len);
        path.lineTo(w - in, h - in);
        path.lineTo(w - in - len, h - in);
        path.moveTo(in + len, h - in);
        path.lineTo(in, h - in);
        path.lineTo(in, h - in - len);
        ornament.setColor((t.glow & 0x00FFFFFF) | 0x30000000);
        ornament.setStrokeWidth(dp(5));
        c.drawPath(path, ornament);
        ornament.setColor((t.glow & 0x00FFFFFF) | 0xD0000000);
        ornament.setStrokeWidth(dp(1.75f));
        c.drawPath(path, ornament);
        diamond(c, w / 2, dp(0.75f), dp(3.5f), t.glow);
    }
}
