package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/** Two shared baselines, independent of the relative sizes of clock and greeting. */
final class ClockGreeting extends View {
    interface TimeSource {
        long now();
        TimeZone zone();
    }
    static final TimeSource SYSTEM = new TimeSource() {
        public long now() { return System.currentTimeMillis(); }
        public TimeZone zone() { return TimeZone.getDefault(); }
    };
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TimeSource time;
    private String clock = "", suffix = "", date = "", greeting = "";

    ClockGreeting(Context context, TimeSource time) {
        super(context);
        this.time = time;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    static String greetingAt(long millis, TimeZone zone) {
        return DashboardLogic.greetingAt(millis, zone);
    }

    void update(boolean h24) {
        Date now = new Date(time.now());
        TimeZone zone = time.zone();
        clock = format(h24 ? "HH:mm" : "h:mm", now, zone);
        suffix = h24 ? "" : format("a", now, zone);
        date = format("EEEE, MMMM d", now, zone);
        greeting = greetingAt(now.getTime(), zone);
        setContentDescription(clock + " " + suffix + ". " + date + ". " + greeting + ". And Socks.");
        invalidate();
    }

    private static String format(String pattern, Date now, TimeZone zone) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.getDefault());
        f.setTimeZone(zone);
        return f.format(now);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save();
        canvas.scale(getWidth() / 1920f, getHeight() / 260f);
        paint.setTypeface(Ui.REGULAR);
        paint.setColor(Ui.TEXT);
        paint.setTextSize(Ui.DASHBOARD_DISPLAY);
        canvas.drawText(clock, 108, 170, paint);
        float clockEnd = 108 + paint.measureText(clock);
        paint.setTextSize(24);
        paint.setColor(Ui.MUTED);
        canvas.drawText(suffix, Math.max(307, clockEnd + 14), 170, paint);
        float dateWidth = paint.measureText(date);
        if (dateWidth > 288) paint.setTextSize(24 * 288 / dateWidth);
        canvas.drawText(date, 108, 215, paint);
        paint.setTextSize(24);
        canvas.drawText("(and Socks)  \uD83D\uDC15", 468, 215, paint);
        paint.setColor(Ui.DIVIDER);
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(420, 110, 420, 225, paint);
        paint.setColor(Ui.TEXT);
        paint.setTextSize(Ui.DASHBOARD_DISPLAY);
        canvas.drawText(greeting, 468, 170, paint);
        canvas.restore();
    }
}
