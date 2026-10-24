package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.text.TextUtils;
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
    private final TextPaint paint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Rect textBounds = new Rect();
    private final TimeSource time;
    private String clock = "", suffix = "", date = "", greeting = "";

    ClockGreeting(Context context, TimeSource time) {
        super(context);
        this.time = time;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void update(boolean h24, String name) {
        Date now = new Date(time.now());
        TimeZone zone = time.zone();
        clock = format(h24 ? "HH:mm" : "h:mm", now, zone);
        suffix = h24 ? "" : format("a", now, zone);
        date = format("EEE, MMMM d", now, zone);
        greeting = DashboardLogic.greetingAt(now.getTime(), zone, name);
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
        paint.getTextBounds(clock, 0, clock.length(), textBounds);
        float clockEnd = 108 + textBounds.right;
        paint.setTextSize(24);
        paint.setColor(Ui.MUTED);
        if (!suffix.isEmpty()) {
            canvas.drawText(suffix, clockEnd + 14, 170, paint);
            clockEnd += 14 + paint.measureText(suffix);
        }
        float dividerX = clockEnd + 48;
        float greetingX = dividerX + 48;
        float dateWidth = paint.measureText(date);
        float dateRoom = clockEnd - 108;
        if (dateWidth > dateRoom) paint.setTextSize(24 * dateRoom / dateWidth);
        canvas.drawText(date, 108, 215, paint);
        paint.setTextSize(24);
        canvas.drawText("(and Socks)  \uD83D\uDC15", greetingX, 215, paint);
        paint.setColor(Ui.DIVIDER);
        paint.setStrokeWidth(1.5f);
        canvas.drawLine(dividerX, 110, dividerX, 225, paint);
        paint.setColor(Ui.TEXT);
        paint.setTextSize(Ui.DASHBOARD_DISPLAY);
        CharSequence fitted = TextUtils.ellipsize(greeting, paint, Math.max(0, 1708 - greetingX), TextUtils.TruncateAt.END);
        String visible = fitted.toString();
        paint.getTextBounds(visible, 0, visible.length(), textBounds);
        canvas.drawText(visible, greetingX - textBounds.left, 170, paint);
        canvas.restore();
    }
}
