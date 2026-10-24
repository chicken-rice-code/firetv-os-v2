package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/** Current conditions stay fixed while twelve hourly slots scroll through a six-slot viewport. */
final class WeatherRow extends FrameLayout {
    private static final int VISIBLE_HOURS = 6;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final SparseArray<Drawable> icons = new SparseArray<>();
    private final HorizontalScrollView timeline;
    private final LinearLayout strip;
    private Weather.Current data;
    private List<Weather.Hour> hours = new ArrayList<>();
    private String message = "Set weather location";
    private boolean h24;
    private int slotWidth, selectedHour;
    private Runnable returnToApps;
    private View.OnFocusChangeListener forecastFocus;

    WeatherRow(Context c) {
        super(c);
        setWillNotDraw(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        timeline = new HorizontalScrollView(c) {
            @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
                View focus = findFocus();
                if (slotWidth <= 0 || !(focus instanceof HourSlot)) return 0;
                int first = Math.round(getScrollX() / (float) slotWidth);
                int target = DashboardLogic.forecastStart(first, ((HourSlot) focus).index,
                        hours.size(), VISIBLE_HOURS);
                return target * slotWidth - getScrollX();
            }
        };
        timeline.setFocusable(false);
        timeline.setHorizontalScrollBarEnabled(false);
        timeline.setOverScrollMode(OVER_SCROLL_NEVER);
        strip = new LinearLayout(c);
        strip.setOrientation(LinearLayout.HORIZONTAL);
        timeline.addView(strip, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
        addView(timeline);
    }

    void onReturnToApps(Runnable run) { returnToApps = run; }
    void onForecastFocus(View.OnFocusChangeListener listener) { forecastFocus = listener; }
    boolean hasForecast() { return !hours.isEmpty(); }
    boolean focusForecast() {
        if (strip.getChildCount() == 0) return false;
        return strip.getChildAt(Math.min(selectedHour, strip.getChildCount() - 1)).requestFocus();
    }

    void update(Weather.Current value, String status, boolean format24h, long now) {
        Weather.Current previous = data;
        data = value; message = status; h24 = format24h;
        List<Weather.Hour> next = value == null ? new ArrayList<>() : value.upcoming(now);
        boolean rebuild = previous != value || next.size() != hours.size()
                || !next.isEmpty() && next.get(0).time != hours.get(0).time;
        long keepTime = hours.isEmpty() ? -1 : hours.get(Math.min(selectedHour, hours.size() - 1)).time;
        boolean restore = timeline.hasFocus();
        hours = next;
        if (rebuild) {
            strip.removeAllViews();
            selectedHour = 0;
            for (int i = 0; i < hours.size(); i++) {
                if (hours.get(i).time == keepTime) selectedHour = i;
                strip.addView(new HourSlot(getContext(), i),
                        new LinearLayout.LayoutParams(Math.max(1, slotWidth), LayoutParams.MATCH_PARENT));
            }
            if (restore && !hours.isEmpty()) focusForecast();
            else timeline.scrollTo(Math.max(0, selectedHour - VISIBLE_HOURS + 1) * slotWidth, 0);
        }
        timeline.setVisibility(hours.isEmpty() ? GONE : VISIBLE);
        if (data == null) setContentDescription(message);
        else setContentDescription(data.location.name + ", " + data.temperature + " degrees, "
                + Weather.description(data.code) + ", feels like " + data.feelsLike + ", high " + data.high
                + ", low " + data.low + ". " + message + ". Next twelve hours. Down to browse; left and right to scroll.");
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
        int left = Math.round(getWidth() * 540f / 1704);
        int width = (getWidth() - left) / VISIBLE_HOURS * VISIBLE_HOURS;
        int cell = width / VISIBLE_HOURS;
        if (cell != slotWidth) {
            slotWidth = cell;
            for (int i = 0; i < strip.getChildCount(); i++) {
                strip.getChildAt(i).setLayoutParams(new LinearLayout.LayoutParams(cell, LayoutParams.MATCH_PARENT));
            }
        }
        timeline.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        timeline.layout(left, 0, left + width, getHeight());
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.save(); canvas.scale(getWidth() / 1704f, getHeight() / 209f);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setColor(Ui.DIVIDER);
        canvas.drawLine(0, 1, 1704, 1, paint); canvas.drawLine(0, 208, 1704, 208, paint);
        paint.setStyle(Paint.Style.FILL); paint.setTypeface(Ui.REGULAR);
        if (data == null) {
            text(canvas, message, 0, 87, 28, Ui.TEXT, false);
            text(canvas, "Choose a town or city in Settings. No device location is used.", 0, 131, 22, Ui.MUTED, false);
        } else {
            text(canvas, "NOW", 0, 49, 18, Ui.MUTED, false);
            text(canvas, data.temperature + "\u00B0", 0, 156, Ui.DASHBOARD_DISPLAY, Ui.TEXT, false);
            icon(canvas, data.code, data.day, 156, 93, 60);
            text(canvas, Weather.description(data.code), 242, 67, 28, Ui.TEXT, false);
            text(canvas, "Feels like " + data.feelsLike + "\u00B0", 242, 111, 23, Ui.MUTED, false);
            text(canvas, "High " + data.high + "\u00B0 \u00B7 Low " + data.low + "\u00B0", 242, 151, 20, Ui.FAINT, false);
            paint.setColor(Ui.DIVIDER); paint.setStrokeWidth(1.5f);
            canvas.drawLine(540, 36, 540, 174, paint);
        }
        canvas.restore();
    }

    private final class HourSlot extends View {
        final int index;
        HourSlot(Context c, int index) {
            super(c); this.index = index; setFocusable(true);
        }
        @Override public boolean onHoverEvent(android.view.MotionEvent event) {
            boolean handled = super.onHoverEvent(event); invalidate(); return handled;
        }
        @Override protected void onFocusChanged(boolean active, int direction, Rect previous) {
            super.onFocusChanged(active, direction, previous);
            if (active) selectedHour = index;
            if (forecastFocus != null) forecastFocus.onFocusChange(this, active);
            invalidate();
        }
        @Override public boolean onKeyDown(int code, KeyEvent event) {
            if (code == KeyEvent.KEYCODE_DPAD_UP) {
                if (returnToApps != null) returnToApps.run(); return true;
            }
            if (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_LEFT && index == 0
                    || code == KeyEvent.KEYCODE_DPAD_RIGHT && index == hours.size() - 1) return true;
            return super.onKeyDown(code, event);
        }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (data == null || index >= hours.size()) return;
            canvas.save(); canvas.scale(getWidth() / 194f, getHeight() / 209f);
            paint.setTypeface(Ui.REGULAR); paint.setStyle(Paint.Style.FILL);
            if (isFocused() || isHovered()) {
                paint.setColor(0x15FFFFFF); canvas.drawRoundRect(4, 8, 190, 201, 8, 8, paint);
                paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1.5f); paint.setColor(Ui.TEXT);
                canvas.drawRoundRect(4, 8, 190, 201, 8, 8, paint); paint.setStyle(Paint.Style.FILL);
            }
            Weather.Hour hour = hours.get(index);
            text(canvas, time(hour.time, data.location.zone), 97, 48, 22, Ui.MUTED, true);
            icon(canvas, hour.code, hour.day, 74, 74, 46);
            text(canvas, hour.temperature + "\u00B0", 97, 172, 36, Ui.TEXT, true);
            canvas.restore();
        }
    }

    private String time(long millis, String zone) {
        SimpleDateFormat f = new SimpleDateFormat(h24 ? "HH:mm" : "h a", Locale.getDefault());
        f.setTimeZone(TimeZone.getTimeZone(zone)); return f.format(new Date(millis));
    }
    private void text(Canvas c, String s, float x, float baseline, float size, int color, boolean centred) {
        paint.setTextSize(size); paint.setColor(color);
        paint.setTextAlign(centred ? Paint.Align.CENTER : Paint.Align.LEFT);
        c.drawText(s, x, baseline, paint);
    }
    private void icon(Canvas c, int code, boolean day, float x, float y, int size) {
        int resource = WeatherIconView.resource(code, day);
        if (resource == 0) return;
        Drawable d = icons.get(resource);
        if (d == null) { d = getContext().getDrawable(resource); icons.put(resource, d); }
        if (d != null) { d.setBounds(Math.round(x), Math.round(y), Math.round(x + size), Math.round(y + size)); d.draw(c); }
    }
}
