package tv.dumbtv.home;

/**
 * The numbers behind the memory widget. They come from ActivityManager.getMemoryInfo, which
 * needs no permission. It only shows; nothing is closed or freed.
 */
final class Meter {
    /** Below this share of memory free, the widget turns amber even before Android says "low". */
    static final float LOW_SHARE = 0.15f;

    private Meter() {}

    /** "247 MB free of 903 MB". */
    static String label(long avail, long total) {
        return mb(avail) + " MB free of " + mb(total) + " MB";
    }

    static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }

    /** Share of memory in use, 0 to 1. */
    static float used(long avail, long total) {
        if (total <= 0) return 0f;
        return Math.max(0f, Math.min(1f, 1f - (float) avail / total));
    }

    static boolean low(boolean systemLow, long avail, long total) {
        return systemLow || total > 0 && avail < total * LOW_SHARE;
    }
}
