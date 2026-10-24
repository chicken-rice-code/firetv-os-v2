package tv.dumbtv.home;

import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** HTTPS client and model. Unix timestamps preserve location forecasts across DST changes. */
final class Weather {
    private Weather() {}
    static final class Location {
        final String name, zone;
        final double latitude, longitude;
        Location(String name, double lat, double lon, String zone) {
            this.name = name; latitude = lat; longitude = lon; this.zone = zone;
        }
    }
    static final class Hour {
        final long time;
        final int temperature, code;
        final boolean day;
        Hour(long time, int temperature, int code, boolean day) {
            this.time = time; this.temperature = temperature; this.code = code; this.day = day;
        }
    }
    static final class Current {
        final Location location;
        final int temperature, feelsLike, high, low, code;
        final boolean day;
        final long fetchedAt;
        final List<Hour> hours;
        Current(Location location, int temp, int feels, int high, int low, int code,
                boolean day, long fetchedAt, List<Hour> hours) {
            this.location = location; temperature = temp; feelsLike = feels; this.high = high;
            this.low = low; this.code = code; this.day = day; this.fetchedAt = fetchedAt; this.hours = hours;
        }
        List<Hour> upcoming(long now) {
            List<Hour> result = new ArrayList<>();
            for (Hour h : hours) {
                if (DashboardLogic.futureHour(h.time, now) && result.size() < 12) result.add(h);
            }
            return result;
        }
    }
    static final class Cancellation {
        private volatile HttpURLConnection connection;
        private volatile boolean cancelled;
        void cancel() {
            cancelled = true;
            HttpURLConnection c = connection;
            if (c != null) c.disconnect();
        }
        void check() throws IOException {
            if (cancelled || Thread.currentThread().isInterrupted()) throw new IOException("Cancelled");
        }
    }

    static Location resolve(String place, Cancellation cancellation) throws Exception {
        String cleaned = place.trim().replaceAll("(?i)[,\\s]+(united kingdom|uk|gb)$", "");
        boolean uk = !cleaned.equals(place.trim());
        JSONObject search = request("https://geocoding-api.open-meteo.com/v1/search?name="
                + URLEncoder.encode(cleaned, "UTF-8") + "&count=10&language=en&format=json", cancellation);
        JSONArray results = search.optJSONArray("results");
        if (results == null || results.length() == 0) throw new IOException("Town or city not found");
        JSONObject selected = results.getJSONObject(0);
        for (int i = 0; uk && i < results.length(); i++) {
            if ("GB".equals(results.getJSONObject(i).optString("country_code"))) {
                selected = results.getJSONObject(i); break;
            }
        }
        String name = selected.getString("name");
        String country = selected.optString("country_code", "");
        return new Location(name + (country.isEmpty() ? "" : ", " + country),
                selected.getDouble("latitude"), selected.getDouble("longitude"),
                selected.optString("timezone", "UTC"));
    }

    static JSONObject fetch(Location location, String units, Cancellation cancellation) throws Exception {
        return request("https://api.open-meteo.com/v1/forecast?latitude=" + location.latitude
                + "&longitude=" + location.longitude
                + "&current=temperature_2m,apparent_temperature,weather_code,is_day"
                + "&hourly=temperature_2m,weather_code,is_day"
                + "&daily=temperature_2m_max,temperature_2m_min"
                + "&temperature_unit=" + units + "&timezone=auto&timeformat=unixtime&forecast_days=3", cancellation);
    }

    static Current parse(JSONObject body, Location location, long timestamp) throws Exception {
        JSONObject current = body.getJSONObject("current"), hourly = body.getJSONObject("hourly");
        JSONObject daily = body.getJSONObject("daily");
        Location resolved = new Location(location.name, location.latitude, location.longitude,
                body.optString("timezone", location.zone));
        JSONArray times = hourly.getJSONArray("time"), temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code"), daylight = hourly.getJSONArray("is_day");
        List<Hour> hours = new ArrayList<>();
        for (int i = 0; i < times.length(); i++) {
            if (times.isNull(i) || temps.isNull(i) || codes.isNull(i)) continue;
            hours.add(new Hour(times.getLong(i) * 1000L, (int) Math.round(temps.getDouble(i)),
                    codes.getInt(i), daylight.optInt(i, 1) == 1));
        }
        return new Current(resolved, (int) Math.round(current.getDouble("temperature_2m")),
                (int) Math.round(current.getDouble("apparent_temperature")),
                (int) Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                (int) Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                current.getInt("weather_code"), current.optInt("is_day", 1) == 1, timestamp, hours);
    }

    private static JSONObject request(String address, Cancellation cancellation) throws Exception {
        cancellation.check();
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        cancellation.connection = c;
        c.setConnectTimeout(10000); c.setReadTimeout(10000);
        c.setRequestProperty("User-Agent", "HomeTogether/1.0 personal-dashboard");
        try {
            cancellation.check();
            if (c.getResponseCode() / 100 != 2) throw new IOException("Weather service unavailable");
            try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder out = new StringBuilder();
                for (String line; (line = r.readLine()) != null;) { cancellation.check(); out.append(line); }
                return new JSONObject(out.toString());
            }
        } finally { c.disconnect(); cancellation.connection = null; }
    }

    static String description(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code >= 95 && code <= 99) return "Thunderstorm";
        return "Unavailable";
    }
}
