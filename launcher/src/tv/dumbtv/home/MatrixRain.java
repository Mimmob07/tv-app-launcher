package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.view.View;

import java.util.Random;

/**
 * The digital rain from The Matrix: columns of mirrored half-width katakana and digits falling
 * down the screen, each led by a bright glyph that turns green and fades behind it.
 *
 * Every frame dims a half-resolution bitmap a little and draws only the glyphs that moved, so
 * the trails come from the fading and a frame costs one fill and a few dozen glyphs. It runs
 * at {@link #FPS} frames a second, and only between {@link #start} and {@link #stop}: the home
 * screen stops it whenever it leaves the front, so it costs nothing while an app plays.
 */
final class MatrixRain extends View {
    private static final int FPS = 20;
    /** Glyph cell in dp at full resolution. */
    private static final float CELL_DP = 15;
    /** How much each frame darkens what is there: trails last about two and a half seconds. */
    private static final int FADE_ALPHA = 14;
    private static final String GLYPHS =
            "ｦｧｨｩｪｫｬｭｮｯｱｲｳｴｵｶｷｸｹｺｻｼｽｾｿﾀﾁﾂﾃﾄﾅﾆﾇﾈﾉﾊﾋﾌﾍﾎﾏﾐﾑﾒﾓﾔﾕﾖﾗﾘﾙﾚﾛﾜﾝ0123456789Z:.=*+-<>¦|\"";
    private static final int HEAD = 0xFFE4FFE8, TRAIL = 0xFF00FF41;

    private final Random random = new Random();
    private final Paint fade = new Paint();
    private final Paint clear = new Paint();
    private final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blit = new Paint(Paint.FILTER_BITMAP_FLAG);
    private final Rect screen = new Rect();
    private final Runnable frame = this::step;
    private final char[] one = new char[1];
    private Bitmap buffer;
    private Canvas canvas;
    private float cell;
    private int cols, rows;
    /** Per column: head position in cells, speed in cells per frame, last row drawn. */
    private float[] head, speed;
    private int[] last;
    private boolean running;

    MatrixRain(Context c) {
        super(c);
        fade.setColor(Color.argb(FADE_ALPHA, 0, 0, 0));
        clear.setColor(Color.BLACK);
        glyph.setTypeface(Theme.rainFont(c));
        glyph.setTextAlign(Paint.Align.CENTER);
        setBackgroundColor(Color.BLACK);
    }

    void start() {
        if (running) return;
        running = true;
        removeCallbacks(frame);
        postOnAnimation(frame);
    }

    void stop() {
        running = false;
        removeCallbacks(frame);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldW, int oldH) {
        if (w <= 0 || h <= 0) return;
        int bw = Math.max(1, w / 2), bh = Math.max(1, h / 2);
        buffer = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888);
        canvas = new Canvas(buffer);
        canvas.drawColor(Color.BLACK);
        cell = Ui.dp(getContext(), CELL_DP) / 2f;
        glyph.setTextSize(cell * 1.0f);
        cols = (int) Math.ceil(bw / cell);
        rows = (int) Math.ceil(bh / cell);
        head = new float[cols];
        speed = new float[cols];
        last = new int[cols];
        for (int i = 0; i < cols; i++) reset(i, true);
        screen.set(0, 0, w, h);
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    /** A column starts again above the top, at its own speed; at first, anywhere on screen. */
    private void reset(int i, boolean anywhere) {
        head[i] = anywhere ? random.nextFloat() * rows * 1.5f - rows * 0.5f : -random.nextFloat() * rows * 0.6f;
        speed[i] = 0.25f + random.nextFloat() * 0.6f;
        last[i] = Integer.MIN_VALUE;
    }

    private void step() {
        if (!running) return;
        if (canvas == null) {
            // Not laid out yet: it was just made visible. Try again next frame.
            postDelayed(frame, 1000L / FPS);
            return;
        }
        canvas.drawRect(0, 0, buffer.getWidth(), buffer.getHeight(), fade);
        for (int i = 0; i < cols; i++) {
            head[i] += speed[i];
            int row = (int) head[i];
            if (row != last[i]) {
                // The old head turns green; the new one is drawn bright.
                if (last[i] >= 0 && last[i] < rows) draw(i, last[i], TRAIL);
                last[i] = row;
            }
            if (row >= 0 && row < rows) draw(i, row, HEAD);
            if (row - 30 > rows && random.nextInt(40) == 0) reset(i, false);
        }
        // A few trail glyphs change in place, as in the film.
        for (int k = 0; k < cols / 6; k++) {
            int i = random.nextInt(cols), row = last[i] - 1 - random.nextInt(12);
            if (row >= 0 && row < rows) draw(i, row, TRAIL & 0x00FFFFFF | 0x70000000);
        }
        invalidate();
        postDelayed(frame, 1000L / FPS);
    }

    /** One glyph, mirrored as the film's are, on a cleared cell. */
    private void draw(int col, int row, int color) {
        float x = col * cell, y = row * cell;
        canvas.drawRect(x, y, x + cell, y + cell, clear);
        glyph.setColor(color);
        one[0] = GLYPHS.charAt(random.nextInt(GLYPHS.length()));
        float cx = x + cell / 2;
        canvas.save();
        canvas.scale(-1, 1, cx, 0);
        canvas.drawText(one, 0, 1, cx, y + cell * 0.84f, glyph);
        canvas.restore();
    }

    @Override
    protected void onDraw(Canvas c) {
        if (buffer != null) c.drawBitmap(buffer, null, screen, blit);
    }
}
