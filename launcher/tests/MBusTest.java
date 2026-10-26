package tv.dumbtv.home;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Host-side checks for the M-Bus widget's grouping and wording, without a device. */
public final class MBusTest {
    private static void check(boolean ok, String name) {
        if (!ok) throw new AssertionError(name);
    }

    private static MBus.Arrival bus(String route, String dir, String stop) {
        MBus.Arrival a = new MBus.Arrival();
        a.route = route;
        a.direction = dir;
        a.stop = stop;
        a.destination = "";
        a.countdown = "5";
        return a;
    }

    public static void main(String[] args) {
        check("Due".equals(MBus.when("DUE")), "due");
        check("Delayed".equals(MBus.when("DLY")), "delayed");
        check("7 min".equals(MBus.when("7")), "minutes");

        check(MBus.quiet("No arrival times"), "no arrivals is not an error");
        check(MBus.quiet("No service scheduled"), "no service is not an error");
        check(!MBus.quiet("Invalid API access key supplied"), "a bad key is an error");

        List<MBus.Arrival> list = new ArrayList<>(Arrays.asList(
                bus("BB", "Northbound", "CCTC"), bus("NW", "Southbound", "CCTC"),
                bus("CN", "Northbound", "CCTC"), bus("DD", "", "CCTC - South")));
        Map<String, List<MBus.Arrival>> g = MBus.byDirection(list);
        check(new ArrayList<>(g.keySet()).equals(Arrays.asList("Northbound", "Southbound", "CCTC - South")),
                "directions in order of their first bus, the stop name standing in for a missing one: " + g.keySet());
        check(g.get("Northbound").size() == 2, "both northbound buses together");
        System.out.println("MBusTest passed");
    }
}
