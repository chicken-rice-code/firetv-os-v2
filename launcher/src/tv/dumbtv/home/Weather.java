package tv.dumbtv.home;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Keyless Open-Meteo lookup for the home-screen weather card and seven-day forecast. */
final class Weather {
    private static final String TAG = "DumbTVWeather";

    private Weather() {}

    static final class Day {
        final String name;
        final String symbol;
        final int high;
        final int low;

        Day(String name, String symbol, int high, int low) {
            this.name = name;
            this.symbol = symbol;
            this.high = high;
            this.low = low;
        }
    }

    static final class Current {
        final String place;
        final String temperature;
        final String condition;
        final String symbol;
        final String error;
        final List<Day> days;

        private Current(String place, String temperature, String condition, String symbol,
                        String error, List<Day> days) {
            this.place = place;
            this.temperature = temperature;
            this.condition = condition;
            this.symbol = symbol;
            this.error = error;
            this.days = days;
        }

        static Current failure(String reason) {
            return new Current("", "--\u00B0", reason, "!", reason, new ArrayList<>());
        }
    }

    static Current fetch(String place) {
        try {
            JSONObject location = findLocation(place);
            if (location == null) return Current.failure("Location not found");
            double latitude = location.getDouble("latitude");
            double longitude = location.getDouble("longitude");
            String city = location.optString("name", place);
            String country = location.optString("country_code", "");
            String forecastUrl = "https://api.open-meteo.com/v1/forecast?latitude=" + latitude
                    + "&longitude=" + longitude
                    + "&current=temperature_2m,weather_code"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                    + "&temperature_unit=celsius&forecast_days=7&timezone=auto";
            JSONObject response = request(forecastUrl);
            JSONObject current = response.getJSONObject("current");
            int degrees = (int) Math.round(current.getDouble("temperature_2m"));
            int code = current.getInt("weather_code");
            String suffix = country.isEmpty() ? "" : ", " + country;
            List<Day> days = parseDays(response.optJSONObject("daily"));
            return new Current(city + suffix, degrees + "\u00B0", description(code), symbol(code), null, days);
        } catch (Exception e) {
            Log.w(TAG, "Weather lookup failed for " + place, e);
            return Current.failure("Check TV internet");
        }
    }

    private static List<Day> parseDays(JSONObject daily) throws Exception {
        List<Day> days = new ArrayList<>();
        if (daily == null) return days;
        JSONArray dates = daily.getJSONArray("time");
        JSONArray highs = daily.getJSONArray("temperature_2m_max");
        JSONArray lows = daily.getJSONArray("temperature_2m_min");
        JSONArray codes = daily.getJSONArray("weather_code");
        SimpleDateFormat input = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat output = new SimpleDateFormat("EEE", Locale.getDefault());
        for (int i = 0; i < dates.length(); i++) {
            Date date = input.parse(dates.getString(i));
            String day = i == 0 ? "Today" : output.format(date);
            int code = codes.getInt(i);
            days.add(new Day(day, symbol(code),
                    (int) Math.round(highs.getDouble(i)), (int) Math.round(lows.getDouble(i))));
        }
        return days;
    }

    private static JSONObject findLocation(String place) throws Exception {
        String cleaned = place.trim().replaceAll("(?i)[,\\s]+(united kingdom|uk|gb)$", "")
                .replaceAll("[,\\s]+$", "").trim();
        boolean preferUk = place.trim().matches("(?is).*(,|\\s)(united kingdom|uk|gb)$");
        String[] words = cleaned.split("[,\\s]+");
        // Drop trailing district words one at a time to fall back to the nearest named city.
        for (int length = words.length; length > 0; length--) {
            StringBuilder candidate = new StringBuilder();
            for (int i = 0; i < length; i++) {
                if (candidate.length() > 0) candidate.append(' ');
                candidate.append(words[i]);
            }
            String query = URLEncoder.encode(candidate.toString(), "UTF-8");
            JSONObject search = request("https://geocoding-api.open-meteo.com/v1/search?name="
                    + query + "&count=10&language=en&format=json");
            JSONArray results = search.optJSONArray("results");
            if (results == null || results.length() == 0) continue;
            JSONObject first = results.getJSONObject(0);
            if (preferUk) {
                for (int i = 0; i < results.length(); i++) {
                    JSONObject result = results.getJSONObject(i);
                    if ("GB".equalsIgnoreCase(result.optString("country_code", ""))) return result;
                }
            }
            return first;
        }
        return null;
    }

    private static JSONObject request(String address) throws IOException, org.json.JSONException {
        HttpURLConnection c = (HttpURLConnection) new URL(address).openConnection();
        c.setConnectTimeout(12000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", "dumbtv-home");
        try {
            int status = c.getResponseCode();
            if (status < 200 || status >= 300) throw new IOException("HTTP " + status);
            try (InputStream in = c.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                StringBuilder body = new StringBuilder();
                for (String line; (line = reader.readLine()) != null;) body.append(line);
                return new JSONObject(body.toString());
            }
        } finally {
            c.disconnect();
        }
    }

    private static String symbol(int code) {
        if (code == 0) return "\u2600";
        if (code <= 3) return "\u2601";
        if (code == 45 || code == 48) return "\u224B";
        if (code >= 51 && code <= 67) return "\u2602";
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return "\u2744";
        if (code >= 80 && code <= 82) return "\u2602";
        if (code >= 95) return "\u26A1";
        return "\u25CC";
    }

    private static String description(int code) {
        if (code == 0) return "Clear skies";
        if (code == 1) return "Mostly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Fog";
        if (code == 51 || code == 53 || code == 55 || code == 56 || code == 57) return "Drizzle";
        if (code == 61 || code == 63 || code == 65 || code == 66 || code == 67) return "Rain";
        if (code == 71 || code == 73 || code == 75 || code == 77) return "Snow";
        if (code == 80 || code == 81 || code == 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code == 95 || code == 96 || code == 99) return "Thunderstorms";
        return "Current conditions";
    }
}