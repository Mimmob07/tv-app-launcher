package tv.dumbtv.home;

import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.TimeZone;

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

    public static void main(String[] args) throws Exception {
        Locale originalLocale = Locale.getDefault();
        TimeZone originalZone = TimeZone.getDefault();
        String date;
        try {
            Locale.setDefault(Locale.FRANCE);
            TimeZone.setDefault(TimeZone.getTimeZone("America/Chicago"));
            date = MBus.requestDate(1793030400000L);
            check("Mon, 26 Oct 2026 16:00:00 GMT".equals(date), "signature date is English UTC");
        } finally {
            Locale.setDefault(originalLocale);
            TimeZone.setDefault(originalZone);
        }
        URL url = new URL("https://mbus.ltp.umich.edu/bustime/api/v3/getpredictions"
                + "?format=json&key=test-key&stpid=C250%2CC251");
        check("793284925993163f5826ad87a8f6bd42be60914e4ab47a3e1289f4e61b4909ad"
                .equals(MBus.requestId(url, date, "test-signing-key")),
                "website signature includes the encoded query and omits the /bustime prefix");

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
        List<MBus.Arrival> north = MBus.inDirection(list, " NORTHBOUND ");
        check(north.size() == 2 && "BB".equals(north.get(0).route) && "CN".equals(north.get(1).route),
                "northbound filter ignores case and whitespace, keeps arrival order, excludes other directions");
        check(MBus.inDirection(list, "").size() == 4, "empty filter keeps all directions");
        check(MBus.inDirection(list, "Eastbound").isEmpty(), "no matching direction has no arrivals");
        System.out.println("MBusTest passed");
    }
}
