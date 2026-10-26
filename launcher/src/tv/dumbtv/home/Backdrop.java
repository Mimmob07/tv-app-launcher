package tv.dumbtv.home;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;

import java.io.File;

/**
 * The wallpaper behind the home screen, cropped to the screen, plus a small blurred copy that
 * widget cards draw through as frosted glass. Built off the UI thread: decoding a 1080p
 * picture and blurring takes ~100 ms on a TV.
 */
final class Backdrop {
    /**
     * The blur is this many times smaller than the screen; bilinear upscaling smooths it. Three
     * box passes of radius 3 at 1/4 scale come to a Gaussian of about 14 px at 1080p: frosted,
     * with the picture behind still recognizable.
     */
    private static final int SHRINK = 4;
    private static final int BLUR_RADIUS = 3, BLUR_PASSES = 3;

    final Bitmap full, blur;
    final int width, height;
    /** Who made the picture, from the theme folder's credits.txt; empty when not known. */
    final String credit;

    private Backdrop(Bitmap full, Bitmap blur, int width, int height, String credit) {
        this.full = full;
        this.blur = blur;
        this.width = width;
        this.height = height;
        this.credit = credit;
    }

    static Backdrop load(File file, int width, int height, String credit) {
        Bitmap src = decode(file, width, height);
        if (src == null) return null;
        Bitmap full = crop(src, width, height);
        if (full != src) src.recycle();
        Bitmap small = Bitmap.createScaledBitmap(full, Math.max(1, width / SHRINK), Math.max(1, height / SHRINK), true);
        return new Backdrop(full, blur(small), width, height, credit == null ? "" : credit);
    }

    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return null;
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** Center-crops {@code src} to exactly {@code w} x {@code h}. */
    private static Bitmap crop(Bitmap src, int w, int h) {
        if (src.getWidth() == w && src.getHeight() == h) return src;
        float scale = Math.max((float) w / src.getWidth(), (float) h / src.getHeight());
        Matrix m = new Matrix();
        m.setScale(scale, scale);
        m.postTranslate((w - src.getWidth() * scale) / 2f, (h - src.getHeight() * scale) / 2f);
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        new Canvas(out).drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        return out;
    }

    /** A few passes of a separable box blur, which together approach a Gaussian. */
    static Bitmap blur(Bitmap small) {
        int w = small.getWidth(), h = small.getHeight();
        int[] px = new int[w * h], tmp = new int[w * h];
        small.getPixels(px, 0, w, 0, 0, w, h);
        for (int i = 0; i < BLUR_PASSES; i++) {
            box(px, tmp, w, h, BLUR_RADIUS, true);
            box(tmp, px, w, h, BLUR_RADIUS, false);
        }
        Bitmap out = Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888);
        small.recycle();
        return out;
    }

    /** One box-blur pass along rows ({@code horizontal}) or columns, edges clamped. */
    static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int lines = horizontal ? h : w, len = horizontal ? w : h, span = r * 2 + 1;
        for (int line = 0; line < lines; line++) {
            int rs = 0, gs = 0, bs = 0;
            for (int k = -r; k <= r; k++) {
                int p = in[index(line, Math.max(0, Math.min(len - 1, k)), w, horizontal)];
                rs += (p >> 16) & 0xFF;
                gs += (p >> 8) & 0xFF;
                bs += p & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[index(line, i, w, horizontal)] = 0xFF000000 | (rs / span) << 16 | (gs / span) << 8 | (bs / span);
                int add = in[index(line, Math.min(len - 1, i + r + 1), w, horizontal)];
                int drop = in[index(line, Math.max(0, i - r), w, horizontal)];
                rs += ((add >> 16) & 0xFF) - ((drop >> 16) & 0xFF);
                gs += ((add >> 8) & 0xFF) - ((drop >> 8) & 0xFF);
                bs += (add & 0xFF) - (drop & 0xFF);
            }
        }
    }

    private static int index(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }
}
