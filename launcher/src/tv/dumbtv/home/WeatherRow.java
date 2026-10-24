package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/** Fixed current conditions and twelve noninteractive hourly forecast cells. */
final class WeatherRow extends FrameLayout {
    private static final float DESIGN_HEIGHT = 144f;
    private static final float HOURLY_INSET = 14f;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final LinearLayout strip;
    private Weather.Current data;
    private List<Weather.Hour> hours = new ArrayList<>();
    private String message = "Set weather location";
    private boolean h24;

    WeatherRow(Context c) {
        super(c);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        strip.setFocusable(false);
        strip.setClipChildren(false);
        addView(strip, new FrameLayout.LayoutParams(0, 0));
    }

    void update(Weather.Current value, String status, boolean format24h, long now) {
        Weather.Current previous = data;
        data = value; message = status; h24 = format24h;
        List<Weather.Hour> next = value == null ? new ArrayList<>() : upcomingUnique(value, now);
        boolean rebuild = previous != value || next.size() != hours.size()
                || !next.isEmpty() && next.get(0).time != hours.get(0).time;
        hours = next;
        if (rebuild) {
            strip.removeAllViews();
            for (int i = 0; i < hours.size(); i++) {
                strip.addView(new HourSlot(getContext(), i),
                        new LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f));
            }
        }
        strip.setVisibility(hours.isEmpty() ? GONE : VISIBLE);
        if (data == null) setContentDescription(message);
        else setContentDescription(data.location.name + ", " + data.temperature + " degrees, "
                + Weather.description(data.code) + ", feels like " + data.feelsLike + ", high " + data.high
                + ", low " + data.low + ". " + message + ". Twelve-hour forecast.");
        for (int i = 0; i < strip.getChildCount(); i++) {
            Weather.Hour h = hours.get(i);
            strip.getChildAt(i).setContentDescription(time(h.time, data.location.zone) + ", "
                    + h.temperature + " degrees, " + Weather.description(h.code)
                    + ". Hour " + (i + 1) + " of " + hours.size());
            strip.getChildAt(i).invalidate();
        }
        requestLayout(); invalidate();
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int left = dividerPosition() + Math.round(HOURLY_INSET * getWidth() / 1704f);
        int width = getWidth() - left;
        strip.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        strip.layout(left, 0, getWidth(), getHeight());
    }

    /** Places the divider halfway between the current-conditions text and the first hour label. */
    private int dividerPosition() {
        if (data == null || hours.isEmpty()) return Math.round(getWidth() * 400f / 1704);

        paint.setTypeface(Ui.REGULAR);
        paint.setTextAlign(Paint.Align.LEFT);
        paint.setTextSize(24);
        float contentRight = 252 + paint.measureText(Weather.description(data.code));
        paint.setTextSize(18);
        contentRight = Math.max(contentRight, 252 + paint.measureText("Feels like " + data.feelsLike + "\u00B0"));
        paint.setTextSize(16);
        contentRight = Math.max(contentRight, 252 + paint.measureText("High " + data.high + "\u00B0 \u00B7 Low " + data.low + "\u00B0"));

        paint.setTextSize(16);
        float firstLabelWidth = paint.measureText(time(hours.get(0).time, data.location.zone));
        // Twelve equal cells: the first label is centered in the first cell.
        float divider = (1704f + 24f * contentRight + 23f * HOURLY_INSET
                - 12f * firstLabelWidth) / 25f - 15f;
        return Math.round(divider);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scaleX = getWidth() / 1704f;
        float scaleY = getHeight() / DESIGN_HEIGHT;
        canvas.save(); canvas.scale(scaleX, scaleY);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setColor(Ui.DIVIDER);
        canvas.drawLine(32, 1, 1672, 1, paint);
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(Ui.REGULAR);
        if (data == null) {
            text(canvas, message, 32, 62, 20, Ui.TEXT, false);
            text(canvas, "Choose a town or city in Settings. No device location is used.", 32, 88, 15, Ui.MUTED, false);
        } else {
            text(canvas, data.temperature + "\u00B0", 32, 95, 64, Ui.TEXT, false);
            icon(canvas, data.code, data.day, 156, 40, 64);
            text(canvas, Weather.description(data.code), 252, 55, 24, Ui.TEXT, false);
            text(canvas, "Feels like " + data.feelsLike + "\u00B0", 252, 83, 18, Ui.MUTED, false);
            text(canvas, "High " + data.high + "\u00B0 \u00B7 Low " + data.low + "\u00B0", 252, 109, 16, Ui.FAINT, false);
            paint.setColor(Ui.DIVIDER); paint.setStrokeWidth(1.5f);
            float divider = dividerPosition();
            canvas.drawLine(divider, 32, divider, DESIGN_HEIGHT - 32, paint);
        }
        canvas.restore();
    }

    private final class HourSlot extends View {
        final int index;
        HourSlot(Context c, int index) {
            super(c); this.index = index;
            setFocusable(false); setClickable(false); setLongClickable(false);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (data == null || index >= hours.size()) return;
            float scale = getWidth() / 100f;
            canvas.save(); canvas.scale(scale, 1f);
            paint.setTypeface(Ui.REGULAR); paint.setStyle(Paint.Style.FILL);
            Weather.Hour hour = hours.get(index);
            float heightScale = getHeight() / DESIGN_HEIGHT;
            canvas.scale(1f, heightScale);
            text(canvas, time(hour.time, data.location.zone), 50, 44, 16, Ui.MUTED, true);
            WeatherRow.this.icon(canvas, hour.code, hour.day, 35, 57, 30);
            text(canvas, hour.temperature + "\u00B0", 50, 113, 16, Ui.TEXT, true);
            canvas.restore();
        }
    }

    private String time(long millis, String zone) {
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "h a", Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone(zone)); return f.format(new Date(millis));
    }

    private List<Weather.Hour> upcomingUnique(Weather.Current current, long now) {
        List<Weather.Hour> result = new ArrayList<>();
        Set<String> labels = new HashSet<>();
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "h a", Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone(current.location.zone));
        for (Weather.Hour hour : current.hours) {
            if (!DashboardLogic.futureHour(hour.time, now)) continue;
            if (!labels.add(f.format(new Date(hour.time)))) continue;
            result.add(hour);
            if (result.size() == 12) break;
        }
        return result;
    }

    private void text(Canvas c, String s, float x, float baseline, float size, int color, boolean centred) {
        paint.setTextSize(size); paint.setColor(color);
        paint.setTextAlign(centred ? Paint.Align.CENTER : Paint.Align.LEFT);
        c.drawText(s, x, baseline, paint);
    }
    private void icon(Canvas c, int code, boolean day, float x, float y, int size) {
        int resource = WeatherIconView.resource(code, day);
        if (resource == 0) return;
        Drawable d = getContext().getDrawable(resource);
        if (d == null) return;
        d = d.mutate();
        int tint = WeatherIconView.tint(code, day);
        if (tint != 0) d.setTint(tint);
        d.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size)); d.draw(c);
    }

}
