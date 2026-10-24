package tv.dumbtv.home;

/** Google's Material Symbols Outlined vectors, kept at their original square aspect ratio. */
final class WeatherIconView {
    private WeatherIconView() {}
    static int resource(int code, boolean day) {
        if (code == 0) return day ? R.drawable.material_sunny : R.drawable.material_clear_night;
        if (code == 1 || code == 2) return day ? R.drawable.material_partly_cloudy_day : R.drawable.material_partly_cloudy_night;
        if (code == 3) return R.drawable.material_cloud;
        if (code == 45 || code == 48) return R.drawable.material_foggy;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return R.drawable.material_rainy_color;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.drawable.material_weather_snowy;
        if (code >= 95 && code <= 99) return R.drawable.material_thunderstorm;
        return 0;
    }

    /** Weather hues stay muted enough for the dashboard; the sunny vector keeps its built-in gold. */
    static int tint(int code, boolean day) {
        if (code == 0 && day) return 0;
        if (code >= 95 && code <= 99) return 0xFFF0C76A;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return 0xFF9DD9E5;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return 0;
        if (code == 45 || code == 48) return 0xFFB7AACF;
        if (code == 0 && !day) return 0xFFF5F4F0;
        return 0xFFBCC7CE;
    }
}
