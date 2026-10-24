package tv.dumbtv.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONObject;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Last success survives process restarts; failures never replace it with invented readings. */
final class WeatherRepository {
    interface Listener { void update(Weather.Current data, String message); }
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final File cache;
    private final Listener listener;
    private String key = "";
    private String lastError = "";
    private Weather.Current current;
    private Weather.Cancellation cancellation;
    private long nextAttempt;
    private int failures, generation;
    private boolean loading, active;

    WeatherRepository(Context context, Listener listener) {
        cache = new File(context.getFilesDir(), "weather-cache.properties"); this.listener = listener;
    }
    void resume(Config config) { active = true; refresh(config); }
    void pause() {
        active = false; generation++; loading = false;
        if (cancellation != null) cancellation.cancel();
    }
    void close() { pause(); io.shutdownNow(); }

    void refresh(Config config) {
        if (!active) return;
        String query = config.weatherLocation(), units = config.get(Config.UNITS);
        String requested = query + "|" + units;
        if (!requested.equals(key)) {
            generation++; if (cancellation != null) cancellation.cancel();
            key = requested; current = null; nextAttempt = 0; failures = 0; loading = false; lastError = "";
            if (!query.isEmpty()) current = readCache(requested);
        }
        if (query.isEmpty()) { listener.update(null, "Set weather location"); return; }
        long now = System.currentTimeMillis();
        listener.update(current, !lastError.isEmpty() ? current == null ? lastError : "Offline \u00B7 updated " + updatedTime(current)
                : current == null ? "Getting weather\u2026"
                : DashboardLogic.stale(current.fetchedAt, now) ? "Updated " + updatedTime(current) + " \u00B7 stale" : "");
        if (loading || now < nextAttempt || current != null && !DashboardLogic.stale(current.fetchedAt, now)) return;
        loading = true;
        int request = ++generation;
        cancellation = new Weather.Cancellation();
        Weather.Cancellation token = cancellation;
        Weather.Location saved = savedLocation(config, query);
        io.execute(() -> {
            try {
                Weather.Location location = saved == null ? Weather.resolve(query, token) : saved;
                JSONObject body = Weather.fetch(location, units, token);
                long fetchedAt = System.currentTimeMillis();
                Weather.Current data = Weather.parse(body, location, fetchedAt);
                token.check();
                writeCache(requested, data.location, body, fetchedAt);
                ui.post(() -> {
                    if (!active || request != generation) return;
                    current = data; loading = false; failures = 0; nextAttempt = 0; lastError = "";
                    config.resolvedWeather(query, data.location);
                    listener.update(data, "");
                });
            } catch (Exception error) {
                ui.post(() -> {
                    if (!active || request != generation) return;
                    loading = false;
                    nextAttempt = System.currentTimeMillis() + DashboardLogic.retryDelay(++failures);
                    String message = error.getMessage();
                    lastError = "Town or city not found".equals(message) ? message : "Weather unavailable \u00B7 retrying";
                    listener.update(current, current == null ? lastError : "Offline \u00B7 updated " + updatedTime(current));
                });
            }
        });
    }

    private static String updatedTime(Weather.Current data) {
        java.text.SimpleDateFormat f = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
        return f.format(new java.util.Date(data.fetchedAt));
    }
    private static Weather.Location savedLocation(Config c, String query) {
        if (!query.equals(c.get("weather.resolved", ""))) return null;
        try {
            return new Weather.Location(c.get("weather.city", query),
                    Double.parseDouble(c.get("weather.latitude", "")),
                    Double.parseDouble(c.get("weather.longitude", "")), c.get("weather.timezone", "UTC"));
        } catch (NumberFormatException ignored) { return null; }
    }
    private Weather.Current readCache(String requested) {
        try (FileInputStream in = new FileInputStream(cache)) {
            Properties p = new Properties(); p.load(in);
            if (!requested.equals(p.getProperty("key"))) return null;
            Weather.Location location = new Weather.Location(p.getProperty("city"),
                    Double.parseDouble(p.getProperty("latitude")), Double.parseDouble(p.getProperty("longitude")),
                    p.getProperty("timezone", "UTC"));
            return Weather.parse(new JSONObject(p.getProperty("body")), location, Long.parseLong(p.getProperty("time")));
        } catch (Exception ignored) { return null; }
    }
    private void writeCache(String requested, Weather.Location location, JSONObject body, long timestamp) {
        Properties p = new Properties(); p.setProperty("key", requested); p.setProperty("body", body.toString());
        p.setProperty("city", location.name); p.setProperty("latitude", "" + location.latitude);
        p.setProperty("longitude", "" + location.longitude); p.setProperty("timezone", location.zone);
        p.setProperty("time", "" + timestamp);
        File tmp = new File(cache.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(tmp)) {
            p.store(out, "Last successful Open-Meteo response");
        } catch (Exception ignored) { return; }
        if (!tmp.renameTo(cache)) android.util.Log.w("HomeTogether", "Could not save weather cache");
    }
}
