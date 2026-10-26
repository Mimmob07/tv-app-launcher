package tv.dumbtv.home;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every widget the board can show, by the id home.conf uses. Adding a widget is one line here
 * plus its class: the id, a name for Options, a default size in board cells, and a constructor.
 */
final class Widgets {
    interface Factory {
        Widget create(Board board);
    }

    static final class Entry {
        final String id, name;
        final int cols, rows;
        final Factory factory;

        Entry(String id, String name, int cols, int rows, Factory factory) {
            this.id = id;
            this.name = name;
            this.cols = cols;
            this.rows = rows;
            this.factory = factory;
        }
    }

    static final Map<String, Entry> ALL = new LinkedHashMap<>();

    static {
        add("time", "Time", 5, 4, TimeWidget::new);
        add("weather", "Weather", 7, 4, WeatherWidget::new);
        add("forecast", "Forecast", 12, 2, ForecastWidget::new);
        add("memory", "Memory", 4, 2, MemoryWidget::new);
        add("mbus", "M-Bus", 7, 3, MBusWidget::new);
    }

    private Widgets() {}

    private static void add(String id, String name, int cols, int rows, Factory f) {
        ALL.put(id, new Entry(id, name, cols, rows, f));
    }

    /** The widget for {@code id}, or null when there is none by that name. */
    static Widget create(String id, Board board) {
        Entry e = ALL.get(id);
        return e == null ? null : e.factory.create(board);
    }

    /** Default {cols, rows} for {@code id}; 4 x 2 for an id that is not registered. */
    static int[] size(String id) {
        Entry e = ALL.get(id);
        return e == null ? new int[] {4, 2} : new int[] {e.cols, e.rows};
    }
}
