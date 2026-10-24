package tv.dumbtv.home;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.TimeZone;
import java.time.ZonedDateTime;

/** Host-side regression tests for time boundaries, DST, cache expiry and removed-app focus. */
public final class DashboardLogicTest {
    private static int checks;
    private static void check(boolean result, String name) {
        checks++; if (!result) throw new AssertionError(name);
    }
    private static long at(String iso) { return ZonedDateTime.parse(iso).toInstant().toEpochMilli(); }
    private static void greeting(String time, String period) {
        String value = DashboardLogic.greetingAt(at("2026-10-24T" + time + "+01:00[Europe/London]"), TimeZone.getTimeZone("Europe/London"));
        check(value.equals("Good " + period + ", Lizzie & Jimmi"), time + " " + period);
    }
    public static void main(String[] args) {
        greeting("04:59", "evening"); greeting("05:00", "morning");
        greeting("11:59", "morning"); greeting("12:00", "afternoon");
        greeting("17:59", "afternoon"); greeting("18:00", "evening");
        greeting("23:59", "evening"); greeting("00:00", "evening");
        long noon = at("2026-10-24T12:00Z[UTC]");
        check(DashboardLogic.greetingAt(noon, TimeZone.getTimeZone("America/Los_Angeles")).contains("morning"), "timezone change");
        long before = at("2026-10-25T01:59+01:00[Europe/London]");
        long after = at("2026-10-25T01:00Z[Europe/London]");
        check(after - before == 60000, "DST repeated hour chronological order");
        check(DashboardLogic.greetingAt(before, TimeZone.getTimeZone("Europe/London")).contains("evening"), "before DST");
        check(DashboardLogic.greetingAt(after, TimeZone.getTimeZone("Europe/London")).contains("evening"), "after DST");
        check(DashboardLogic.futureHour(after, before), "forecast selects repeated hour by epoch");
        check(!DashboardLogic.futureHour(before, after), "forecast excludes past hour");
        check(DashboardLogic.futureHour(noon, noon), "exact hour included");
        check(!DashboardLogic.stale(noon, noon + 1799999), "cached offline data still fresh");
        check(DashboardLogic.stale(noon, noon + 1800000), "stale at 30 minutes");
        check(DashboardLogic.stale(0, noon), "missing cache");
        check(DashboardLogic.stale(noon, noon - 1), "clock moved backwards");
        check(DashboardLogic.retryDelay(1) == 60000 && DashboardLogic.retryDelay(2) == 120000, "exponential retry");
        check(DashboardLogic.retryDelay(20) == 900000, "retry capped");
        check("b".equals(DashboardLogic.focusId(Arrays.asList("a", "b"), "b")), "restore previous app");
        check("a".equals(DashboardLogic.focusId(Arrays.asList("a", "b"), "removed")), "removed app fallback");
        check(DashboardLogic.focusId(Collections.emptyList(), "removed") == null, "empty favourites");
        check(DashboardLogic.scrollTarget(2304, 1728, 120) == 0, "scroll starts at first app");
        check(DashboardLogic.scrollTarget(2304, 1728, 1600) == 576, "extra favourites scroll to end");
        check(DashboardLogic.scrollTarget(864, 1728, 500) == 0, "short row does not scroll");
        List<Weather.Hour> hours = new ArrayList<>();
        long evening = at("2026-10-24T20:30Z[UTC]");
        long first = at("2026-10-24T21:00Z[UTC]");
        for (int i = -2; i < 18; i++) hours.add(new Weather.Hour(first + i * 3600000L, 14, 2, false));
        Weather.Current current = new Weather.Current(new Weather.Location("Test town", 0, 0, "UTC"),
                14, 12, 16, 9, 2, false, evening, hours);
        List<Weather.Hour> next = current.upcoming(evening);
        check(next.size() == 12, "twelve forecast hours are returned");
        check(next.get(0).time == first, "past hourly readings are excluded");
        check(next.get(11).time == first + 11 * 3600000L, "timeline continues across midnight");
        check(current.upcoming(first).get(0).time == first, "exact next hour is not skipped");
        check(current.upcoming(first + 17 * 3600000L).size() == 1, "short forecast is not fabricated");
        check(current.upcoming(first + 18 * 3600000L).isEmpty(), "expired forecast has no fake slots");
        check(DashboardLogic.forecastStart(0, 5, 12, 6) == 0, "first six hours remain visible");
        check(DashboardLogic.forecastStart(0, 6, 12, 6) == 1, "seventh hour scrolls one whole column");
        check(DashboardLogic.forecastStart(0, 11, 12, 6) == 6, "twelfth hour reaches final six slots");
        check(DashboardLogic.forecastStart(6, 5, 12, 6) == 5, "left scroll reveals preceding hour");
        check(DashboardLogic.forecastStart(6, 0, 12, 6) == 0, "first hour scrolls back to start");
        check(DashboardLogic.forecastStart(0, 2, 3, 6) == 0, "short forecast does not scroll");
        System.out.println("DashboardLogicTest: " + checks + " checks passed");
    }
}
