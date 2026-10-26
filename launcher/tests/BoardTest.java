package tv.dumbtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Host-side checks for the widget board's widgets= parsing and packing, without a device. */
public final class BoardTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    private static List<Board.Spec> specs(String list) {
        List<Board.Spec> out = new ArrayList<>();
        for (String t : list.split(",")) {
            Board.Spec s = Board.parse(t);
            if (s != null) out.add(s);
        }
        return out;
    }

    public static void main(String[] args) {
        Board.Spec s = Board.parse(" Weather:7x4 ");
        check(s != null && "weather".equals(s.id) && s.cols == 7 && s.rows == 4 && s.col < 0,
                "id and size, trimmed and lowercased");
        s = Board.parse("memory:4x2+8+4");
        check(s != null && s.col == 8 && s.row == 4, "a pinned position");
        s = Board.parse("time");
        check(s != null && s.cols == 5 && s.rows == 4, "no size takes the registered default");
        s = Board.parse("time:40x9");
        check(s != null && s.cols == Board.COLS && s.rows == Board.ROWS, "sizes clamp to the grid");
        check(Board.parse("time:wide") == null, "a malformed size is dropped");
        check(Board.parse("  ") == null, "an empty entry is dropped");

        List<int[]> at = Board.pack(specs(Board.DEFAULT), Board.COLS, Board.ROWS);
        check(Arrays.equals(at.get(0), new int[] {0, 0, 5, 4}), "time top left");
        check(Arrays.equals(at.get(1), new int[] {5, 0, 7, 4}), "weather beside it");
        check(Arrays.equals(at.get(2), new int[] {0, 4, 12, 2}), "forecast across the bottom");

        at = Board.pack(specs("time:12x6,memory"), Board.COLS, Board.ROWS);
        check(at.get(1) == null, "no room left means left out");

        at = Board.pack(specs("time:4x2,memory:4x2+0+0"), Board.COLS, Board.ROWS);
        check(Arrays.equals(at.get(1), new int[] {0, 0, 4, 2}), "pinned claims its cells first");
        check(Arrays.equals(at.get(0), new int[] {4, 0, 4, 2}), "the rest pack around it");
        System.out.println("BoardTest passed");
    }
}
