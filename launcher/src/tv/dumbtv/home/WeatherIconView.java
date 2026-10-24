package tv.dumbtv.home;

/** Google's Material Symbols Outlined vectors, kept at their original square aspect ratio. */
final class WeatherIconView {
    private WeatherIconView() {}
    static int resource(int code, boolean day) {
        if (code == 0) return day ? R.drawable.material_sunny : R.drawable.material_clear_night;
        if (code == 1 || code == 2) return day ? R.drawable.material_partly_cloudy_day : R.drawable.material_partly_cloudy_night;
        if (code == 3) return R.drawable.material_cloud;
        if (code == 45 || code == 48) return R.drawable.material_foggy;
        if (code >= 51 && code <= 67 || code >= 80 && code <= 82) return R.drawable.material_rainy;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return R.drawable.material_weather_snowy;
        if (code >= 95 && code <= 99) return R.drawable.material_thunderstorm;
        return 0;
    }
}
