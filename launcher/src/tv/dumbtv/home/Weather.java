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
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Current conditions and the forecast for one place, from Open-Meteo (open-meteo.com: free,
 * no account, no key). Shared by every weather widget, so the board makes one request however
 * many of them it shows. The last answer is kept in the app's files, so the widgets have
 * something to draw right after a reboot.
 *
 * home.conf: weather.place (the name shown), weather.lat and weather.lon (decimal degrees),
 * weather.units (f or c). Refreshes every {@link #FRESH_MS} while the home screen is showing,
 * and never in the background.
 */
final class Weather {
    static final long FRESH_MS = 15 * 60_000L, RETRY_MS = 5 * 60_000L, STALE_MS = 3 * 3_600_000L;
    private static final String CACHE = "weather.json";

    static final class Hour {
        long time;
        float temp;
        int code, precip;
        boolean day;
    }

    static final class Day {
        long date;
        float high, low;
        int code, precip;
    }

    static final class Report {
        String place;
        boolean fahrenheit;
        long fetched;
        float temp, feels, wind;
        int code, humidity;
        boolean day;
        long sunrise, sunset;
        TimeZone zone;
        final List<Hour> hours = new ArrayList<>();
        final List<Day> days = new ArrayList<>();

        String deg(float v) {
            return Math.round(v) + "°";
        }

        String windText() {
            return Math.round(wind) + (fahrenheit ? " mph" : " km/h");
        }
    }

    private static Weather instance;

    private final File cache;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService net = Executors.newSingleThreadExecutor();
    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private volatile Report report;
    private volatile boolean busy;
    private String key = "";
    private long lastTry;
    private boolean lastFailed;

    static Weather get(Context c) {
        if (instance == null) instance = new Weather(c.getApplicationContext());
        return instance;
    }

    private Weather(Context c) {
        cache = new File(c.getFilesDir(), CACHE);
    }

    Report report() {
        return report;
    }

    /** True when the last good answer is older than {@link #STALE_MS}. */
    boolean stale() {
        Report r = report;
        return r == null || System.currentTimeMillis() - r.fetched > STALE_MS;
    }

    /** True when there is no place set, so there is nothing to fetch. */
    static boolean unset(Config cfg) {
        return cfg.get("weather.lat", "").isEmpty() || cfg.get("weather.lon", "").isEmpty();
    }

    void listen(Runnable r) {
        if (!listeners.contains(r)) listeners.add(r);
    }

    void unlisten(Runnable r) {
        listeners.remove(r);
    }

    /** Fetches when the place changed, the data is old, or {@code force}. Cheap to call often. */
    void refresh(Config cfg, boolean force) {
        if (unset(cfg)) return;
        String lat = cfg.get("weather.lat", ""), lon = cfg.get("weather.lon", "");
        boolean f = !"c".equals(cfg.get(Config.UNITS));
        String place = cfg.get("weather.place", "");
        String want = lat + "," + lon + "," + (f ? "f" : "c");
        long now = System.currentTimeMillis();
        if (!want.equals(key)) {
            key = want;
            report = null;
            loadCache(want, place);
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
            try {
                String body = download(lat, lon, f);
                got = parse(body, place, f, System.currentTimeMillis());
                saveCache(want, got.fetched, body);
            } catch (IOException | JSONException | RuntimeException e) {
                Log.w("dumbtv", "weather: " + e);
            }
            Report done = got;
            ui.post(() -> {
                busy = false;
                lastFailed = done == null;
                if (done != null && want.equals(key)) report = done;
                for (Runnable l : listeners) l.run();
            });
        });
    }

    private static String download(String lat, String lon, boolean f) throws IOException {
        String url = "https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,"
                + "wind_speed_10m,is_day"
                + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min,"
                + "precipitation_probability_max,sunrise,sunset"
                + "&temperature_unit=" + (f ? "fahrenheit" : "celsius")
                + "&wind_speed_unit=" + (f ? "mph" : "kmh")
                + "&timezone=auto&timeformat=unixtime&forecast_days=7";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(10_000);
        conn.setReadTimeout(15_000);
        conn.setRequestProperty("User-Agent", "dumbtv-home");
        try {
            if (conn.getResponseCode() != 200) throw new IOException("HTTP " + conn.getResponseCode());
            try (InputStream in = conn.getInputStream()) {
                return read(in);
            }
        } finally {
            conn.disconnect();
        }
    }

    static Report parse(String body, String place, boolean f, long fetched) throws JSONException {
        JSONObject root = new JSONObject(body);
        Report r = new Report();
        r.place = place;
        r.fahrenheit = f;
        r.fetched = fetched;
        r.zone = TimeZone.getTimeZone(root.optString("timezone", "GMT"));
        JSONObject cur = root.getJSONObject("current");
        r.temp = (float) cur.getDouble("temperature_2m");
        r.feels = (float) cur.optDouble("apparent_temperature", r.temp);
        r.humidity = cur.optInt("relative_humidity_2m", -1);
        r.wind = (float) cur.optDouble("wind_speed_10m", 0);
        r.code = cur.optInt("weather_code", 0);
        r.day = cur.optInt("is_day", 1) == 1;

        JSONObject h = root.getJSONObject("hourly");
        JSONArray ht = h.getJSONArray("time");
        for (int i = 0; i < ht.length(); i++) {
            Hour x = new Hour();
            x.time = ht.getLong(i) * 1000L;
            x.temp = (float) h.getJSONArray("temperature_2m").optDouble(i, 0);
            x.code = h.getJSONArray("weather_code").optInt(i, 0);
            x.day = h.getJSONArray("is_day").optInt(i, 1) == 1;
            x.precip = h.getJSONArray("precipitation_probability").optInt(i, 0);
            r.hours.add(x);
        }

        JSONObject d = root.getJSONObject("daily");
        JSONArray dt = d.getJSONArray("time");
        for (int i = 0; i < dt.length(); i++) {
            Day x = new Day();
            x.date = dt.getLong(i) * 1000L;
            x.code = d.getJSONArray("weather_code").optInt(i, 0);
            x.high = (float) d.getJSONArray("temperature_2m_max").optDouble(i, 0);
            x.low = (float) d.getJSONArray("temperature_2m_min").optDouble(i, 0);
            x.precip = d.getJSONArray("precipitation_probability_max").optInt(i, 0);
            r.days.add(x);
        }
        if (dt.length() > 0) {
            r.sunrise = d.getJSONArray("sunrise").optLong(0, 0) * 1000L;
            r.sunset = d.getJSONArray("sunset").optLong(0, 0) * 1000L;
        }
        return r;
    }

    /** The next {@code n} hours after now, {@code step} hours apart. */
    static List<Hour> upcoming(Report r, int n, int step, long now) {
        List<Hour> out = new ArrayList<>();
        int i = 0;
        while (i < r.hours.size() && r.hours.get(i).time <= now) i++;
        for (; i < r.hours.size() && out.size() < n; i += step) out.add(r.hours.get(i));
        return out;
    }

    private void loadCache(String want, String place) {
        try {
            if (!cache.isFile()) return;
            String text;
            try (InputStream in = new FileInputStream(cache)) {
                text = read(in);
            }
            JSONObject o = new JSONObject(text);
            if (!want.equals(o.optString("key"))) return;
            report = parse(o.getString("body"), place, want.endsWith(",f"), o.getLong("fetched"));
        } catch (IOException | JSONException e) {
            Log.w("dumbtv", "weather cache: " + e);
        }
    }

    private void saveCache(String want, long fetched, String body) {
        try {
            JSONObject o = new JSONObject();
            o.put("key", want);
            o.put("fetched", fetched);
            o.put("body", body);
            File tmp = new File(cache.getPath() + ".tmp");
            try (OutputStream out = new FileOutputStream(tmp)) {
                out.write(o.toString().getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(cache)) Log.w("dumbtv", "weather cache: rename failed");
        } catch (IOException | JSONException e) {
            Log.w("dumbtv", "weather cache: " + e);
        }
    }

    private static String read(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    /** "Clear", "Partly cloudy", ... for a WMO weather code, as Open-Meteo reports them. */
    static String describe(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Overcast";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: return "Snow";
            case 75: return "Heavy snow";
            case 77: return "Snow grains";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: return "Thunderstorm";
            case 96: case 99: return "Storm with hail";
            default: return String.format(Locale.ROOT, "Code %d", code);
        }
    }
}
