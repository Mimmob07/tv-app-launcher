package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;

/**
 * Weather icons, drawn from simple shapes in the theme's colors so they stay sharp at any
 * size: sun or moon, clouds, rain, snow, a bolt, fog. One call draws the icon for a WMO code
 * centered on a point. Themes with {@link Theme#pixel} get blocky versions on a 16-unit grid:
 * Minecraft's square sun and moon and its flat clouds.
 */
final class Glyphs {
    private static final int CLEAR = 0, PARTLY = 1, CLOUDY = 2, FOG = 3, DRIZZLE = 4, RAIN = 5,
            SNOW = 6, STORM = 7, SLEET = 8;

    private Glyphs() {}

    static int kind(int code) {
        switch (code) {
            case 0: return CLEAR;
            case 1: case 2: return PARTLY;
            case 3: return CLOUDY;
            case 45: case 48: return FOG;
            case 51: case 53: case 55: return DRIZZLE;
            case 56: case 57: case 66: case 67: return SLEET;
            case 61: case 63: case 65: case 80: case 81: case 82: return RAIN;
            case 71: case 73: case 75: case 77: case 85: case 86: return SNOW;
            case 95: case 96: case 99: return STORM;
            default: return CLOUDY;
        }
    }

    /** Draws the icon for {@code code} in a {@code size} square centered on (cx, cy). */
    static void draw(Canvas c, int code, boolean day, float cx, float cy, float size, Theme t) {
        if (t.pixel) {
            blocks(c, code, day, cx, cy, size, t);
            return;
        }
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        float s = size;
        switch (kind(code)) {
            case CLEAR:
                if (day) sun(c, cx, cy, s * 0.24f, t, fill, stroke);
                else moon(c, cx, cy, s * 0.30f, t, fill);
                break;
            case PARTLY:
                if (day) sun(c, cx - s * 0.14f, cy - s * 0.12f, s * 0.17f, t, fill, stroke);
                else moon(c, cx - s * 0.12f, cy - s * 0.12f, s * 0.22f, t, fill);
                cloud(c, cx + s * 0.06f, cy + s * 0.10f, s * 0.74f, t.text, fill);
                break;
            case CLOUDY:
                cloud(c, cx + s * 0.12f, cy - s * 0.08f, s * 0.58f, t.muted, fill);
                cloud(c, cx - s * 0.04f, cy + s * 0.08f, s * 0.80f, t.text, fill);
                break;
            case FOG:
                cloud(c, cx, cy - s * 0.12f, s * 0.70f, t.muted, fill);
                stroke.setColor(t.text);
                stroke.setStrokeWidth(s * 0.06f);
                for (int i = 0; i < 3; i++) {
                    float y = cy + s * (0.14f + i * 0.11f), inset = s * (i == 1 ? 0.30f : 0.36f);
                    c.drawLine(cx - inset, y, cx + inset - s * 0.06f * i, y, stroke);
                }
                break;
            case DRIZZLE:
            case RAIN:
            case SLEET:
                cloud(c, cx, cy - s * 0.10f, s * 0.80f, t.text, fill);
                stroke.setColor(t.cold);
                stroke.setStrokeWidth(s * 0.06f);
                fill.setColor(t.text);
                for (int i = 0; i < 3; i++) {
                    float x = cx - s * 0.20f + i * s * 0.20f, y = cy + s * 0.22f;
                    if (kind(code) == SLEET && i == 1) {
                        c.drawCircle(x, y + s * 0.08f, s * 0.045f, fill);
                    } else {
                        float len = kind(code) == DRIZZLE ? s * 0.08f : s * 0.18f;
                        c.drawLine(x + s * 0.04f, y, x - s * 0.03f, y + len, stroke);
                    }
                }
                break;
            case SNOW:
                cloud(c, cx, cy - s * 0.10f, s * 0.80f, t.text, fill);
                fill.setColor(t.text);
                for (int i = 0; i < 3; i++) {
                    float x = cx - s * 0.20f + i * s * 0.20f;
                    c.drawCircle(x, cy + s * (i == 1 ? 0.33f : 0.27f), s * 0.045f, fill);
                }
                break;
            case STORM:
            default:
                cloud(c, cx, cy - s * 0.10f, s * 0.80f, t.text, fill);
                Path bolt = new Path();
                bolt.moveTo(cx + s * 0.04f, cy + s * 0.08f);
                bolt.lineTo(cx - s * 0.10f, cy + s * 0.28f);
                bolt.lineTo(cx, cy + s * 0.28f);
                bolt.lineTo(cx - s * 0.06f, cy + s * 0.44f);
                bolt.lineTo(cx + s * 0.12f, cy + s * 0.20f);
                bolt.lineTo(cx + s * 0.02f, cy + s * 0.20f);
                bolt.close();
                fill.setColor(t.warm);
                c.drawPath(bolt, fill);
        }
    }

    /** The blocky set: every shape is whole units of a 16-unit grid over the icon square. */
    private static void blocks(Canvas c, int code, boolean day, float cx, float cy, float size, Theme t) {
        Paint p = new Paint();
        float u = Math.max(1, Math.round(size / 16f));
        float x0 = Math.round(cx - u * 8), y0 = Math.round(cy - u * 8);
        int kind = kind(code);
        boolean cloud = kind != CLEAR;
        boolean low = kind != CLEAR && kind != PARTLY && kind != CLOUDY;
        if (kind == CLEAR || kind == PARTLY) {
            float ox = kind == CLEAR ? 4 : 1, oy = kind == CLEAR ? 4 : 1, s = kind == CLEAR ? 8 : 7;
            if (day) {
                p.setColor((t.warm & 0x00FFFFFF) | 0x40000000);
                cell(c, p, x0, y0, u, ox - 1, oy - 1, s + 2, s + 2);
                p.setColor(t.warm);
                cell(c, p, x0, y0, u, ox, oy, s, s);
                p.setColor(0xFFFFF6C8);
                cell(c, p, x0, y0, u, ox + 2, oy + 2, s - 4, s - 4);
            } else {
                p.setColor(0xFFE6E6EE);
                cell(c, p, x0, y0, u, ox, oy, s, s);
                p.setColor(0xFFB4B4C4);
                cell(c, p, x0, y0, u, ox + 1, oy + 1, 2, 2);
                cell(c, p, x0, y0, u, ox + s - 3, oy + s - 4, 2, 2);
            }
        }
        if (kind == CLOUDY) {
            p.setColor(t.muted);
            cell(c, p, x0, y0, u, 6, 2, 8, 3);
            cell(c, p, x0, y0, u, 4, 5, 12, 3);
        }
        if (cloud) {
            float top = low ? 2 : 6;
            p.setColor(kind == STORM ? 0xFF8C8C8C : t.text);
            cell(c, p, x0, y0, u, 4, top, 7, 3);
            cell(c, p, x0, y0, u, 1, top + 3, 14, 4);
            p.setColor(kind == STORM ? 0xFF6E6E6E : 0xFFD6D6D6);
            cell(c, p, x0, y0, u, 1, top + 6, 14, 1);
        }
        switch (kind) {
            case DRIZZLE:
            case RAIN:
            case SLEET:
                p.setColor(t.cold);
                int len = kind == DRIZZLE ? 1 : 3;
                for (int i = 0; i < 3; i++) {
                    if (kind == SLEET && i == 1) {
                        p.setColor(t.text);
                        cell(c, p, x0, y0, u, 3 + i * 4, 11, 2, 2);
                        p.setColor(t.cold);
                    } else {
                        cell(c, p, x0, y0, u, 3 + i * 4, 10 + (i % 2), 1, len);
                    }
                }
                break;
            case SNOW:
                p.setColor(t.text);
                for (int i = 0; i < 3; i++) cell(c, p, x0, y0, u, 3 + i * 4, 10 + (i % 2) * 2, 2, 2);
                break;
            case STORM:
                p.setColor(t.warm);
                cell(c, p, x0, y0, u, 8, 9, 2, 2);
                cell(c, p, x0, y0, u, 7, 11, 2, 2);
                cell(c, p, x0, y0, u, 6, 13, 2, 2);
                break;
            case FOG:
                p.setColor(t.muted);
                cell(c, p, x0, y0, u, 2, 10, 12, 1);
                cell(c, p, x0, y0, u, 4, 12, 10, 1);
                cell(c, p, x0, y0, u, 2, 14, 9, 1);
                break;
            default:
        }
    }

    private static void cell(Canvas c, Paint p, float x0, float y0, float u, float x, float y, float w, float h) {
        c.drawRect(x0 + x * u, y0 + y * u, x0 + (x + w) * u, y0 + (y + h) * u, p);
    }

    private static void sun(Canvas c, float cx, float cy, float r, Theme t, Paint fill, Paint stroke) {
        if (t.ornaments) {
            fill.setColor((t.warm & 0x00FFFFFF) | 0x2E000000);
            c.drawCircle(cx, cy, r * 1.95f, fill);
        }
        stroke.setColor(t.warm);
        stroke.setStrokeWidth(r * 0.22f);
        for (int i = 0; i < 8; i++) {
            double a = Math.PI * i / 4;
            float dx = (float) Math.cos(a), dy = (float) Math.sin(a);
            c.drawLine(cx + dx * r * 1.38f, cy + dy * r * 1.38f, cx + dx * r * 1.72f, cy + dy * r * 1.72f, stroke);
        }
        fill.setColor(t.warm);
        c.drawCircle(cx, cy, r, fill);
    }

    private static void moon(Canvas c, float cx, float cy, float r, Theme t, Paint fill) {
        Path disc = new Path(), bite = new Path();
        disc.addCircle(cx, cy, r, Path.Direction.CW);
        bite.addCircle(cx + r * 0.52f, cy - r * 0.34f, r * 0.86f, Path.Direction.CW);
        disc.op(bite, Path.Op.DIFFERENCE);
        fill.setColor(t.rain ? t.text : 0xFFF1EBD2);
        c.drawPath(disc, fill);
    }

    /** A cloud {@code w} wide centered on (cx, cy): three puffs on a rounded base. */
    private static void cloud(Canvas c, float cx, float cy, float w, int color, Paint fill) {
        Path p = new Path(), q = new Path();
        p.addRoundRect(cx - w * 0.42f, cy, cx + w * 0.44f, cy + w * 0.26f, w * 0.13f, w * 0.13f, Path.Direction.CW);
        q.addCircle(cx - w * 0.20f, cy + w * 0.05f, w * 0.19f, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        q.reset();
        q.addCircle(cx + w * 0.02f, cy - w * 0.06f, w * 0.25f, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        q.reset();
        q.addCircle(cx + w * 0.25f, cy + w * 0.06f, w * 0.18f, Path.Direction.CW);
        p.op(q, Path.Op.UNION);
        fill.setColor(color);
        c.drawPath(p, fill);
    }
}
