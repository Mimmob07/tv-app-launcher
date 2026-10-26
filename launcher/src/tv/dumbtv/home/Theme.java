package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Typeface;

import java.util.HashMap;
import java.util.Map;

/**
 * The look of the home screen: card glass, text colors, fonts, ornaments and what sits behind
 * it. Widgets draw only through a Theme, so Options › Theme restyles all of them.
 *
 * A theme's pictures are not in the APK. They live in the app's media folder,
 * /sdcard/Android/media/tv.dumbtv.home/themes/&lt;id&gt;/, which adb can write on every Fire OS;
 * install.sh pushes launcher/themes/&lt;id&gt;/ there. An optional credits.txt in the same folder
 * holds one "file|credit" line per picture, shown small on the home screen. A theme with
 * {@link #rain} draws its own animated background instead.
 *
 * To add a theme: its id and name in {@link #IDS} and {@link #NAMES}, a factory below and a
 * case in {@link #of}, its cover color in {@link #cover}, and a launcher/themes/&lt;id&gt;/ folder
 * of 1920x1080 pictures. launcher/WIDGETS.md has the details.
 */
final class Theme {
    static final String[] IDS = {"hyrule", "landscape", "minecraft", "matrix"};
    static final String[] NAMES = {"Hyrule", "Landscape", "Minecraft", "Matrix"};

    String id;
    /** Primary, secondary and tertiary text. */
    int text, muted, faint;
    /** The focus ring on tiles and buttons. */
    int accent;
    /** Card glass (drawn over the blurred wallpaper), its edge, and divider lines. */
    int glass, edge, line;
    /** Ornament glow, and the warm and cold colors weather glyphs and temperatures use. */
    int glow, warm, cold;
    /** Shown while the wallpaper loads, by the HOME cover, and when there are no pictures. */
    int cover;
    /** Card and tile corner radius in dp. */
    float radius, tileRadius;
    /** Letter spacing for small capital labels, in ems. */
    float tracking;
    /** Sheikah-style corner brackets and diamonds on cards, brackets around the focused tile. */
    boolean ornaments;
    /** Minecraft-style cards: a black outline with a light top-left and dark bottom-right bevel. */
    boolean bevel;
    /** A hard drop shadow under text, as Minecraft draws it. */
    boolean shadow;
    /** Blocky weather glyphs: a square sun and moon, flat rectangular clouds. */
    boolean pixel;
    /** Animated digital rain behind the cards instead of pictures. */
    boolean rain;
    /** Headings and big numbers; small labels; body text; light body text. */
    Typeface display, label, body, light;

    private static final Map<String, Typeface> FONTS = new HashMap<>();

    private Theme() {}

    static String name(String id) {
        for (int i = 0; i < IDS.length; i++) {
            if (IDS[i].equals(id)) return NAMES[i];
        }
        return id;
    }

    /** The cover color alone, for the HOME guard, which has no fonts to load. */
    static int cover(String id) {
        switch (id) {
            case "landscape": return 0xFF0E1216;
            case "minecraft": return 0xFF1A1F14;
            case "matrix": return 0xFF000000;
            default: return 0xFF0A1820;
        }
    }

    static Theme of(Context c, String id) {
        switch (id) {
            case "landscape": return landscape();
            case "minecraft": return minecraft(c);
            case "matrix": return matrix(c);
            default: return hyrule(c);
        }
    }

    /** A Sheikah Slate look: deep teal glass, cyan edges and brackets, Cinzel, gold focus. */
    private static Theme hyrule(Context c) {
        Theme t = new Theme();
        t.id = "hyrule";
        t.text = 0xFFF4EEDD;
        t.muted = 0xD9C9D6D4;
        t.faint = 0x99B5C8C8;
        t.accent = 0xFFF2D27A;
        t.glass = 0x6606161E;
        t.edge = 0x5C6FE3FF;
        t.line = 0x406FE3FF;
        t.glow = 0xFF6FE3FF;
        t.warm = 0xFFFFC861;
        t.cold = 0xFF8FD3FF;
        t.radius = 12;
        t.tileRadius = 6;
        t.tracking = 0.16f;
        t.ornaments = true;
        t.display = font(c, "fonts/cinzel-400.ttf", Typeface.SERIF);
        t.label = font(c, "fonts/cinzel-600.ttf", Typeface.create(Typeface.SERIF, Typeface.BOLD));
        t.body = Typeface.create("sans-serif", Typeface.NORMAL);
        t.light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        t.cover = cover(t.id);
        return t;
    }

    /** Clean frosted glass over photographs: white type, no ornaments, a yellow focus ring. */
    private static Theme landscape() {
        Theme t = new Theme();
        t.id = "landscape";
        t.text = 0xFFFFFFFF;
        t.muted = 0xD0FFFFFF;
        t.faint = 0x8CFFFFFF;
        t.accent = 0xFFFFD54A;
        t.glass = 0x400C141C;
        t.edge = 0x40FFFFFF;
        t.line = 0x2EFFFFFF;
        t.glow = 0xFFFFFFFF;
        t.warm = 0xFFFFD36B;
        t.cold = 0xFF9AD0FF;
        t.radius = 22;
        t.tileRadius = 10;
        t.tracking = 0.10f;
        t.display = Typeface.create("sans-serif-light", Typeface.NORMAL);
        t.label = Ui.MEDIUM;
        t.body = Typeface.create("sans-serif", Typeface.NORMAL);
        t.light = Typeface.create("sans-serif-light", Typeface.NORMAL);
        t.cover = cover(t.id);
        return t;
    }

    /**
     * Minecraft's menus over the world: dim square panels with a pixel bevel, Monocraft type with
     * a hard shadow, a white hotbar-style focus frame, and blocky weather.
     */
    private static Theme minecraft(Context c) {
        Theme t = new Theme();
        t.id = "minecraft";
        t.text = 0xFFFFFFFF;
        t.muted = 0xFFD0D0D0;
        t.faint = 0xFFA0A0A0;
        t.accent = 0xFFFFFFFF;
        t.glass = 0x73101010;
        t.edge = 0xFF000000;
        t.line = 0x55FFFFFF;
        t.glow = 0xFF55FF55;
        t.warm = 0xFFFFE15A;
        t.cold = 0xFF5FA8FF;
        t.radius = 0;
        t.tileRadius = 0;
        t.tracking = 0.04f;
        t.bevel = true;
        t.shadow = true;
        t.pixel = true;
        Typeface mono = font(c, "fonts/monocraft.ttf", Typeface.MONOSPACE);
        t.display = mono;
        t.label = font(c, "fonts/monocraft-bold.ttf", Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        t.body = mono;
        t.light = mono;
        t.cover = cover(t.id);
        return t;
    }

    /** Green phosphor on black glass over falling code. */
    private static Theme matrix(Context c) {
        Theme t = new Theme();
        t.id = "matrix";
        t.text = 0xFFCCFFD6;
        t.muted = 0xFF6FE98A;
        t.faint = 0xFF3A9E52;
        t.accent = 0xFF00FF41;
        t.glass = 0xB8000803;
        t.edge = 0x8000FF41;
        t.line = 0x4000FF41;
        t.glow = 0xFF00FF41;
        t.warm = 0xFFB8FF6A;
        t.cold = 0xFF00FF41;
        t.radius = 4;
        t.tileRadius = 3;
        t.tracking = 0.12f;
        t.rain = true;
        Typeface mono = font(c, "fonts/sharetechmono.ttf", Typeface.MONOSPACE);
        t.display = mono;
        t.label = mono;
        t.body = mono;
        t.light = mono;
        t.cover = cover(t.id);
        return t;
    }

    /** The katakana, digits and symbols of the digital rain (a subset of Noto Sans JP). */
    static Typeface rainFont(Context c) {
        return font(c, "fonts/rain.ttf", Typeface.MONOSPACE);
    }

    private static Typeface font(Context c, String asset, Typeface fallback) {
        Typeface t = FONTS.get(asset);
        if (t != null) return t;
        try {
            t = Typeface.createFromAsset(c.getAssets(), asset);
        } catch (RuntimeException e) {
            t = fallback;
        }
        FONTS.put(asset, t);
        return t;
    }
}
