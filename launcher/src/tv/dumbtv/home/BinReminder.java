package tv.dumbtv.home;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.TimeZone;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Plymouth household collection lookup, cached only in this app's private files directory. */
final class BinReminder {
    static final String CHECKER = "https://www.plymouth.gov.uk/check-your-bin-day";
    static final String SERVICE = "https://whenisbins.com/v1";
    private static final long REFRESH_MS = 12L * 60L * 60L * 1000L;

    interface Listener { void update(String title, String detail, List<Collection> upcoming); }
    interface AddressListener { void found(JSONObject response, Exception error); }
    interface LookupListener { void finished(Schedule schedule, Exception error); }

    static final class Schedule {
        final List<Collection> upcoming;
        final String source, qualification;
        Schedule(List<Collection> upcoming, String source, String qualification) {
            this.upcoming = upcoming; this.source = source; this.qualification = qualification;
        }
        String title() { return "Next bin collection"; }
        String detail() { return upcoming.isEmpty() ? "No upcoming dates" : upcoming.get(0).detail()
                + (qualification.isEmpty() ? "" : " \u00B7 " + qualification); }
    }

    static final class Collection {
        final String type, date, weekday;
        final boolean subscriptionRequired;
        Collection(String type, String date, String weekday, boolean subscriptionRequired) {
            this.type = type; this.date = date; this.weekday = weekday;
            this.subscriptionRequired = subscriptionRequired;
        }
        String detail() { return type + " | " + weekday + ", " + pretty(date)
                + (subscriptionRequired ? " \u00B7 subscription required" : ""); }
    }

    private final Context context;
    private final File stateFile;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private String propertyId;
    private Schedule current;
    private long fetchedAt;
    private boolean active;

    BinReminder(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        stateFile = new File(this.context.getFilesDir(), "bin-reminder.properties");
        this.listener = listener;
        read();
    }

    void resume() {
        active = true;
        read();
        if (current != null) listener.update(current.title(), current.detail(), current.upcoming);
        else if (propertyId.isEmpty()) listener.update("Set up bin reminders", "Choose your address to see upcoming collections", Collections.emptyList());
        else listener.update("Checking collection dates", "Getting the latest schedule", Collections.emptyList());
        refresh(false);
    }

    void pause() { active = false; }
    void close() { active = false; io.shutdownNow(); }
    boolean isConfigured() { return !propertyId.isEmpty(); }
    Schedule schedule() { return current; }

    static boolean hasSavedAddress(Context context) {
        Properties p = new Properties();
        File file = new File(context.getApplicationContext().getFilesDir(), "bin-reminder.properties");
        try (FileInputStream in = new FileInputStream(file)) { p.load(in); return !p.getProperty("property_id", "").isEmpty(); }
        catch (Exception ignored) { return false; }
    }

    void clear() {
        propertyId = ""; current = null; fetchedAt = 0;
        if (!stateFile.delete() && stateFile.exists()) android.util.Log.w("HomeTogether", "Could not clear bin reminder cache");
    }

    void discover(String postcode, AddressListener callback) {
        io.execute(() -> {
            try {
                String url = SERVICE + "/addresses?postcode=" + URLEncoder.encode(postcode.trim(), "UTF-8");
                JSONObject result = get(url);
                String council = result.optJSONObject("council") == null ? "" : result.getJSONObject("council").optString("name", "");
                if (!council.toLowerCase(Locale.ROOT).contains("plymouth")) {
                    throw new java.io.IOException("That postcode did not resolve to Plymouth City Council.");
                }
                ui.post(() -> callback.found(result, null));
            } catch (Exception error) { ui.post(() -> callback.found(null, error)); }
        });
    }

    void lookup(String postcode, String propertyId, String property, LookupListener callback) {
        io.execute(() -> {
            try {
                JSONObject payload = new JSONObject();
                payload.put("postcode", postcode.trim());
                if (propertyId != null && !propertyId.isEmpty()) payload.put("property_id", propertyId);
                else payload.put("property", property.trim());
                HttpURLConnection connection = (HttpURLConnection) new URL(SERVICE + "/lookups").openConnection();
                connection.setConnectTimeout(15000); connection.setReadTimeout(15000);
                connection.setRequestMethod("POST"); connection.setDoOutput(true);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Idempotency-Key", UUID.randomUUID().toString());
                try (OutputStreamWriter out = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                    out.write(payload.toString());
                }
                JSONObject lookup = readResponse(connection);
                String status = lookup.optString("status", "");
                long until = System.currentTimeMillis() + 120000;
                while ("queued".equals(status) || "running".equals(status) || "partial".equals(status)) {
                    JSONObject provisional = lookup.optJSONObject("result");
                    if (provisional != null && provisional.optBoolean("provisional", false)) provisional = null;
                    if (provisional != null && "partial".equals(status)) break;
                    if (System.currentTimeMillis() >= until) throw new java.io.IOException("The council lookup is taking longer than expected. Try again later.");
                    Thread.sleep(5000);
                    lookup = get(SERVICE + "/lookups/" + lookup.getString("id"));
                    status = lookup.optString("status", "");
                }
                if ("failed".equals(status)) {
                    JSONObject problem = lookup.optJSONObject("problem");
                    throw new java.io.IOException(problem == null ? lookup.optString("detail", "No collection dates were returned")
                            : problem.optString("detail", lookup.optString("detail", "No collection dates were returned")));
                }
                JSONObject schedule = lookup.optJSONObject("result");
                if (schedule == null || schedule.optBoolean("provisional", false))
                    throw new java.io.IOException("No confirmed schedule was returned for this address.");
                Schedule parsed = parse(schedule);
                if (parsed == null) throw new java.io.IOException("No upcoming collection date was returned.");
                save(propertyIdFrom(schedule), parsed);
                ui.post(() -> callback.finished(parsed, null));
            } catch (Exception error) { ui.post(() -> callback.finished(null, error)); }
        });
    }

    private void refresh(boolean force) {
        if (!active || propertyId.isEmpty() || !force && System.currentTimeMillis() - fetchedAt < REFRESH_MS) return;
        io.execute(() -> {
            try {
                String token = propertyId.startsWith("p:") ? propertyId.substring(2) : propertyId;
                JSONObject body = get(SERVICE + "/schedules/" + URLEncoder.encode(token, "UTF-8"));
                Schedule parsed = parse(body);
                if (parsed == null) throw new java.io.IOException("No upcoming collection date was returned.");
                save(propertyId, parsed);
                ui.post(() -> { if (active) listener.update(parsed.title(), parsed.detail(), parsed.upcoming); });
            } catch (Exception error) {
                ui.post(() -> { if (active) listener.update(current == null ? "Bin dates unavailable" : current.title(),
                        current == null ? "Select to check Plymouth Council's collection checker" : current.detail() + " \u00B7 saved schedule",
                        current == null ? Collections.emptyList() : current.upcoming); });
            }
        });
    }

    private static String propertyIdFrom(JSONObject result) throws Exception {
        String id = result.optString("property_id", "");
        if (id.isEmpty()) throw new java.io.IOException("The council service did not return a property reference.");
        return id;
    }

    private static Schedule parse(JSONObject body) throws Exception {
        JSONArray collections = body.optJSONArray("collections");
        if (collections == null) return null;
        SimpleDateFormat localDate = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        localDate.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        String today = localDate.format(new Date());
        List<Collection> upcoming = new ArrayList<>();
        for (int i = 0; i < collections.length(); i++) {
            JSONObject collection = collections.optJSONObject(i);
            if (collection == null) continue;
            String name = collection.optString("name", "").toLowerCase(Locale.ROOT);
            String type = collection.optString("waste_type", "").toLowerCase(Locale.ROOT);
            String colour = collection.optString("bin_colour", collection.optString("lid_colour", "")).toLowerCase(Locale.ROOT);
            String label;
            if (name.contains("food") || type.startsWith("food")) label = "Food";
            else if (name.contains("brown") || colour.equals("brown") || "refuse".equals(type)) label = "General";
            else if (name.contains("green") || colour.equals("green") || "recycling".equals(type)) label = "Recycling";
            else continue;
            JSONArray dates = collection.optJSONArray("dates");
            if (dates == null) continue;
            for (int d = 0; d < dates.length(); d++) {
                String date = dates.optString(d, "");
                if (date.compareTo(today) >= 0) upcoming.add(new Collection(label, date, weekday(date),
                        collection.optBoolean("subscription_required", false)));
            }
        }
        if (upcoming.isEmpty()) return null;
        Collections.sort(upcoming, Comparator.comparing((Collection entry) -> entry.date).thenComparing(entry -> entry.type));
        String source = body.optString("source_url", CHECKER);
        String confidence = body.optString("date_confidence", "");
        String qualification = "published_calendar".equals(confidence) ? "" : "Council schedule";
        return new Schedule(upcoming, source, qualification);
    }

    private static String weekday(String date) throws Exception {
        SimpleDateFormat parse = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
        parse.setLenient(false); parse.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        Date day = parse.parse(date);
        SimpleDateFormat weekday = new SimpleDateFormat("EEEE", Locale.UK);
        weekday.setTimeZone(TimeZone.getTimeZone("Europe/London"));
        return weekday.format(day);
    }

    private JSONObject get(String address) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(address).openConnection();
        connection.setConnectTimeout(15000); connection.setReadTimeout(20000);
        connection.setRequestProperty("User-Agent", "HomeTogether/1.0 personal-dashboard");
        return readResponse(connection);
    }

    private static JSONObject readResponse(HttpURLConnection connection) throws Exception {
        try {
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new java.io.IOException("Collection service returned HTTP " + code);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) body.append(line);
                return new JSONObject(body.toString());
            }
        } finally { connection.disconnect(); }
    }

    private void save(String id, Schedule schedule) throws Exception {
        propertyId = id; current = schedule; fetchedAt = System.currentTimeMillis();
        Properties p = new Properties(); p.setProperty("property_id", id);
        JSONArray items = new JSONArray();
        for (Collection item : schedule.upcoming) {
            JSONObject entry = new JSONObject();
            entry.put("type", item.type); entry.put("date", item.date); entry.put("weekday", item.weekday);
            entry.put("subscription_required", item.subscriptionRequired);
            items.put(entry);
        }
        p.setProperty("upcoming", items.toString());
        p.setProperty("source", schedule.source); p.setProperty("qualification", schedule.qualification);
        p.setProperty("fetched", Long.toString(fetchedAt));
        File temp = new File(stateFile.getPath() + ".tmp");
        try (FileOutputStream out = new FileOutputStream(temp)) { p.store(out, "Private bin collection cache"); }
        catch (Exception ignored) { return; }
        if (!temp.renameTo(stateFile)) android.util.Log.w("HomeTogether", "Could not save bin reminder cache");
    }

    private void read() {
        try (FileInputStream in = new FileInputStream(stateFile)) {
            Properties p = new Properties(); p.load(in);
            propertyId = p.getProperty("property_id", ""); fetchedAt = Long.parseLong(p.getProperty("fetched", "0"));
            if (!propertyId.isEmpty()) {
                JSONArray items = new JSONArray(p.getProperty("upcoming", "[]"));
                List<Collection> entries = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject entry = items.getJSONObject(i);
                    entries.add(new Collection(entry.getString("type"), entry.getString("date"), entry.getString("weekday"),
                            entry.optBoolean("subscription_required", false)));
                }
                current = entries.isEmpty() ? null : new Schedule(entries, p.getProperty("source", CHECKER), p.getProperty("qualification", ""));
                if (current == null) fetchedAt = 0;
            }
        } catch (Exception ignored) { propertyId = ""; current = null; fetchedAt = 0; }
    }

    private static String pretty(String date) {
        try {
            SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.UK);
            input.setTimeZone(TimeZone.getTimeZone("Europe/London"));
            SimpleDateFormat output = new SimpleDateFormat("d MMM", Locale.UK);
            output.setTimeZone(TimeZone.getTimeZone("Europe/London"));
            return output.format(input.parse(date));
        } catch (Exception ignored) { return date; }
    }
}
