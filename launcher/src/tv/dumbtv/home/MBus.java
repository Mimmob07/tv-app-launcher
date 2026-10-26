package tv.dumbtv.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Next arrivals at one University of Michigan bus stop, from the Magic Bus real-time system
 * (Clever Devices BusTime, API v3, mbus.ltp.umich.edu). Set mbus.key in home.conf.
 * The public website's client key also needs mbus.signing-key for its request headers;
 * personal developer keys can leave that setting empty.
 *
 * home.conf: mbus.key; mbus.stop, the stop's name or names to look for, "|"-separated
 * (default: Central Campus Transit Center|CCTC); mbus.stops, stop ids to use instead of
 * looking them up; mbus.demo=on, made-up arrivals to place the widget before there is a key.
 * The lookup walks every route's stops once and keeps what it found in the
 * app's files, so it does not run again until mbus.stop changes. Arrivals refresh every
 * {@link #FRESH_MS} while the home screen or screensaver shows, never in the background.
 */
final class MBus {
    static final long FRESH_MS = 30_000L, RETRY_MS = 120_000L, STALE_MS = 5 * 60_000L;
    static final String DEFAULT_STOP = "Central Campus Transit Center|CCTC";
    private static final String API = "https://mbus.ltp.umich.edu/bustime/api/v3/";
    private static final String CACHE = "mbus-stops.json";

    static final class Arrival {
        String route, direction, destination, stop;
        /** "DUE", "DLY" or minutes, as BusTime sends it. */
        String countdown;
        int color;
    }

    static final class Report {
        long fetched;
        final List<Arrival> arrivals = new ArrayList<>();
    }

    private static MBus instance;

    private final File cache;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Report report;
    private volatile String problem = "";
    private volatile boolean busy;
    private String key = "";
    private long lastTry;
    private boolean lastFailed;
    /** Route to color, from getroutes; filled with the stop lookup. */
    private final Map<String, Integer> colors = new HashMap<>();

    static MBus get(Context c) {
        if (instance == null) instance = new MBus(c.getApplicationContext());
        return instance;
    }

    private MBus(Context c) {
        cache = new File(c.getFilesDir(), CACHE);
    }

    Report report() {
        return report;
    }

    /** What went wrong last, for the widget to show; empty when nothing did. */
    String problem() {
        return problem;
    }

    boolean stale() {
        Report r = report;
        return r == null || System.currentTimeMillis() - r.fetched > STALE_MS;
    }

    static boolean unset(Config cfg) {
        return cfg.get("mbus.key", "").isEmpty() && !demo(cfg);
    }

    static boolean demo(Config cfg) {
        return "on".equals(cfg.get("mbus.demo", ""));
    }

    /** Made-up arrivals in both directions, for placing the widget. */
    static Report sample() {
        Report r = new Report();
        r.fetched = System.currentTimeMillis();
        String[][] rows = {
            {"BB", "Northbound", "Bursley Hall", "DUE", "#00274C"},
            {"NW", "Southbound", "Central Campus Transit Center", "3", "#FFCB05"},
            {"CN", "Northbound", "North Campus", "6", "#2F65A7"},
            {"DD", "Southbound", "Diag to Diag", "9", "#9A3324"},
            {"BB", "Northbound", "Baits", "14", "#00274C"},
            {"CS", "Southbound", "Crisler Center", "17", "#575294"},
        };
        for (String[] x : rows) {
            Arrival a = new Arrival();
            a.route = x[0];
            a.direction = x[1];
            a.destination = x[2];
            a.countdown = x[3];
            a.color = color(x[4]);
            a.stop = "CCTC";
            r.arrivals.add(a);
        }
        return r;
    }

    void listen(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    void unlisten(Runnable r) {
        listeners.remove(r);
    }

    void refresh(Config cfg, boolean force) {
        if (unset(cfg)) return;
        if (demo(cfg) && cfg.get("mbus.key", "").isEmpty()) {
            report = sample();
            return;
        }
        String apiKey = cfg.get("mbus.key", "");
        String signingKey = cfg.get("mbus.signing-key", "");
        String names = cfg.get("mbus.stop", DEFAULT_STOP);
        List<String> pinned = cfg.list("mbus.stops");
        String want = apiKey + "|" + signingKey + "|" + names + "|" + pinned;
        long now = System.currentTimeMillis();
        if (!want.equals(key)) {
            key = want;
            report = null;
            force = true;
        }
        if (busy) return;
        Report r = report;
        boolean old = r == null || now - r.fetched > FRESH_MS;
        boolean waited = !lastFailed || now - lastTry > RETRY_MS;
        if (!force && !(old && waited)) return;
        busy = true;
        lastTry = now;
        net.execute(() -> {
            Report got = null;
            String trouble = "";
            try {
                Client client = new Client(apiKey, signingKey);
                List<String> stops = pinned.isEmpty() ? stops(client, names) : pinned;
                if (stops.isEmpty()) {
                    trouble = "No stop named " + names.replace("|", " or ");
                } else {
                    got = parse(client.call("getpredictions", "stpid", join(stops, 10), "top", "30"), colors);
                    got.fetched = System.currentTimeMillis();
                }
            } catch (IOException | JSONException | RuntimeException e) {
                Log.w("dumbtv", "mbus: " + e);
                trouble = e.getMessage() == null ? e.toString() : e.getMessage();
            }
            Report done = got;
            String said = trouble;
            ui.post(() -> {
                busy = false;
                lastFailed = done == null;
                problem = said;
                if (done != null && want.equals(key)) report = done;
                for (Runnable l : listeners) l.run();
            });
        });
    }

    /** The stop ids whose names match {@code names}, from the cache or by asking every route. */
    private List<String> stops(Client client, String names) throws IOException, JSONException {
        List<String> cached = loadCache(names);
        if (cached != null) return cached;
        Set<String> found = new LinkedHashSet<>();
        String[] wanted = names.toLowerCase(Locale.ROOT).split("\\|");
        JSONArray routes = client.call("getroutes").optJSONArray("routes");
        for (int i = 0; routes != null && i < routes.length(); i++) {
            JSONObject rt = routes.getJSONObject(i);
            String route = rt.getString("rt");
            colors.put(route, color(rt.optString("rtclr")));
            JSONArray dirs = client.call("getdirections", "rt", route).optJSONArray("directions");
            for (int d = 0; dirs != null && d < dirs.length(); d++) {
                JSONObject dir = dirs.getJSONObject(d);
                String id = dir.has("id") ? dir.getString("id") : dir.optString("dir");
                JSONArray stops = client.call("getstops", "rt", route, "dir", id).optJSONArray("stops");
                for (int s = 0; stops != null && s < stops.length(); s++) {
                    JSONObject stop = stops.getJSONObject(s);
                    String name = stop.optString("stpnm").toLowerCase(Locale.ROOT);
                    for (String w : wanted) {
                        if (!w.trim().isEmpty() && name.contains(w.trim())) found.add(stop.getString("stpid"));
                    }
                }
            }
        }
        List<String> out = new ArrayList<>(found);
        saveCache(names, out);
        return out;
    }

    /** One refresh uses server time plus monotonic elapsed time, independent of the TV clock. */
    private static final class Client {
        private final String apiKey, signingKey;
        private long serverTime, started;

        Client(String apiKey, String signingKey) throws IOException, JSONException {
            this.apiKey = apiKey;
            this.signingKey = signingKey;
            if (!signingKey.isEmpty()) {
                serverTime = call("gettime", "unixTime", "true").getLong("tm");
                started = System.nanoTime();
            }
        }

        JSONObject call(String request, String... params) throws IOException, JSONException {
            long now = serverTime + (System.nanoTime() - started) / 1_000_000L;
            return MBus.call(apiKey, "gettime".equals(request) ? "" : signingKey, now, request, params);
        }
    }

    /** The public website signs the API path, query and UTC date with HMAC-SHA256. */
    static String requestDate(long millis) {
        SimpleDateFormat date = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", Locale.US);
        date.setTimeZone(TimeZone.getTimeZone("GMT"));
        return date.format(new Date(millis));
    }

    static String requestId(URL url, String date, String signingKey) throws IOException {
        String path = url.getFile();
        int api = path.indexOf("/api/");
        if (api < 0) throw new IOException("Missing BusTime API path");
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal((path.substring(api) + date).getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format(Locale.ROOT, "%02x", b & 0xff));
            return hex.toString();
        } catch (GeneralSecurityException e) {
            throw new IOException("Could not sign BusTime request", e);
        }
    }

    /** One API call; BusTime's own error answers become exceptions, except "no arrivals". */
    private static JSONObject call(String apiKey, String signingKey, long now, String request, String... params)
            throws IOException, JSONException {
        StringBuilder url = new StringBuilder(API).append(request).append("?format=json&key=")
                .append(URLEncoder.encode(apiKey, "UTF-8"));
        for (int i = 0; i + 1 < params.length; i += 2) {
            url.append('&').append(params[i]).append('=').append(URLEncoder.encode(params[i + 1], "UTF-8"));
        }
        HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "dumbtv-home");
        conn.setRequestProperty("Accept", "application/json");
        if (!signingKey.isEmpty()) {
            String date = requestDate(now);
            conn.setRequestProperty("X-Date", date);
            conn.setRequestProperty("X-Request-ID", requestId(conn.getURL(), date, signingKey));
        }
        String body;
        try {
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream()) {
                body = read(in);
            }
        } finally {
            conn.disconnect();
        }
        JSONObject res = new JSONObject(body).getJSONObject("bustime-response");
        JSONArray errors = res.optJSONArray("error");
        if (errors != null && errors.length() > 0 && !res.has("prd") && !res.has("routes")
                && !res.has("directions") && !res.has("stops")) {
            String msg = errors.getJSONObject(0).optString("msg", "error");
            if (!quiet(msg)) throw new IOException(msg);
        }
        return res;
    }

    /** BusTime says these when there is simply nothing coming, which is not a failure. */
    static boolean quiet(String msg) {
        String m = msg.toLowerCase(Locale.ROOT);
        return m.contains("no arrival") || m.contains("no service") || m.contains("no data found");
    }

    /** The predictions in a getpredictions answer, soonest first as BusTime sends them. */
    static Report parse(JSONObject res, Map<String, Integer> colors) throws JSONException {
        Report r = new Report();
        JSONArray prd = res.optJSONArray("prd");
        for (int i = 0; prd != null && i < prd.length(); i++) {
            JSONObject p = prd.getJSONObject(i);
            Arrival a = new Arrival();
            a.route = p.optString("rt");
            a.direction = p.optString("rtdir");
            a.destination = p.optString("des");
            a.stop = p.optString("stpnm");
            a.countdown = p.optString("prdctdn");
            Integer c = colors.get(a.route);
            a.color = c == null ? 0 : c;
            r.arrivals.add(a);
        }
        return r;
    }

    /** An optional direction filter, matched without regard to the API's capitalization. */
    static List<Arrival> inDirection(List<Arrival> arrivals, String direction) {
        String wanted = direction.trim();
        if (wanted.isEmpty()) return arrivals;
        List<Arrival> out = new ArrayList<>();
        for (Arrival a : arrivals) {
            if (wanted.equalsIgnoreCase(a.direction)) out.add(a);
        }
        return out;
    }

    /** The arrivals grouped by direction, in order of each direction's first bus. */
    static Map<String, List<Arrival>> byDirection(List<Arrival> arrivals) {
        Map<String, List<Arrival>> out = new LinkedHashMap<>();
        for (Arrival a : arrivals) {
            String d = a.direction.isEmpty() ? a.stop : a.direction;
            List<Arrival> list = out.get(d);
            if (list == null) out.put(d, list = new ArrayList<>());
            list.add(a);
        }
        return out;
    }

    /** "Due", "Delayed" or "5 min". */
    static String when(String countdown) {
        if ("DUE".equalsIgnoreCase(countdown)) return "Due";
        if ("DLY".equalsIgnoreCase(countdown)) return "Delayed";
        return countdown + " min";
    }

    private static int color(String hex) {
        try {
            return android.graphics.Color.parseColor(hex);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static String join(List<String> ids, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < ids.size() && i < max; i++) {
            if (i > 0) b.append(',');
            b.append(ids.get(i));
        }
        return b.toString();
    }

    private List<String> loadCache(String names) {
        try {
            if (!cache.isFile()) return null;
            String text;
            try (InputStream in = new FileInputStream(cache)) {
                text = read(in);
            }
            JSONObject o = new JSONObject(text);
            if (!names.equals(o.optString("names"))) return null;
            JSONObject c = o.optJSONObject("colors");
            if (c != null) {
                for (Iterator<String> it = c.keys(); it.hasNext(); ) {
                    String k = it.next();
                    colors.put(k, c.getInt(k));
                }
            }
            JSONArray ids = o.getJSONArray("stops");
            List<String> out = new ArrayList<>();
            for (int i = 0; i < ids.length(); i++) out.add(ids.getString(i));
            return out.isEmpty() ? null : out;
        } catch (IOException | JSONException e) {
            Log.w("dumbtv", "mbus cache: " + e);
            return null;
        }
    }

    private void saveCache(String names, List<String> stops) {
        try {
            JSONObject o = new JSONObject();
            o.put("names", names);
            o.put("stops", new JSONArray(stops));
            JSONObject c = new JSONObject();
            for (Map.Entry<String, Integer> e : colors.entrySet()) c.put(e.getKey(), e.getValue());
            o.put("colors", c);
            try (OutputStream out = new FileOutputStream(cache)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
        } catch (IOException | JSONException e) {
            Log.w("dumbtv", "mbus cache: " + e);
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }
}
