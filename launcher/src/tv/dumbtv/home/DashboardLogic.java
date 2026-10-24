package tv.dumbtv.home;

import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

/** Device-independent clock, cache and focus rules. */
final class DashboardLogic {
    static final long WEATHER_INTERVAL = 30 * 60 * 1000L;
    private DashboardLogic() {}
    static String greetingAt(long now, TimeZone zone) {
        Calendar c = Calendar.getInstance(zone); c.setTimeInMillis(now);
        int h = c.get(Calendar.HOUR_OF_DAY);
        return "Good " + (h >= 5 && h < 12 ? "morning" : h >= 12 && h < 18 ? "afternoon" : "evening")
                + ", Lizzie & Jimmi";
    }
    static boolean stale(long timestamp, long now) {
        return timestamp <= 0 || now < timestamp || now - timestamp >= WEATHER_INTERVAL;
    }
    static long retryDelay(int failures) {
        return Math.min(15 * 60 * 1000L, 60000L << Math.min(10, Math.max(0, failures - 1)));
    }
    static boolean futureHour(long hourTime, long now) { return hourTime >= now; }
    static String focusId(List<String> ids, String previous) {
        return ids.contains(previous) ? previous : ids.isEmpty() ? null : ids.get(0);
    }
    static int scrollTarget(int contentWidth, int viewportWidth, int centre) {
        return Math.max(0, Math.min(Math.max(0, contentWidth - viewportWidth), centre - viewportWidth / 2));
    }
    static int forecastStart(int first, int selected, int count, int visible) {
        if (count <= visible || visible <= 0) return 0;
        int focus = Math.max(0, Math.min(count - 1, selected));
        int start = Math.max(0, Math.min(count - visible, first));
        if (focus < start) return focus;
        if (focus >= start + visible) return focus - visible + 1;
        return start;
    }
}
