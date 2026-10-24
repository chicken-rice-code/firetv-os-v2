package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ScrollView;
import android.widget.TextView;

/** Responsive 16:9 dashboard; all component bounds are proportions of the available viewport. */
final class HomeScreen extends FrameLayout {
    final ClockGreeting header;
    final AppsRow apps;
    final WeatherRow weather;
    final RemindersRow reminders;
    final ImageButton settings;
    final TextView appsTitle, appCount, weatherTitle, weatherAction, remindersTitle, footer, weatherStatus;
    private final ScrollView viewport;
    private final FrameLayout content;
    private final Paint ambient = new Paint();
    private Runnable resized;
    private float scale;

    HomeScreen(Context c, Runnable openSettings, Runnable chooseWeather) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        viewport = new ScrollView(c);
        viewport.setFocusable(false);
        viewport.setVerticalScrollBarEnabled(false);
        viewport.setFillViewport(false);
        viewport.setOverScrollMode(OVER_SCROLL_NEVER);
        viewport.setClipChildren(false);
        viewport.setClipToPadding(false);
        content = new FrameLayout(c);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        viewport.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        addView(viewport, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
        header = new ClockGreeting(c, ClockGreeting.SYSTEM);
        apps = new AppsRow(c);
        weather = new WeatherRow(c);
        reminders = new RemindersRow(c);
        settings = new ImageButton(c);
        settings.setId(View.generateViewId());
        settings.setImageResource(R.drawable.material_settings);
        settings.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        settings.setContentDescription("Settings");
        settings.setFocusable(true);
        settings.setOnHoverListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER) ensureVisible(v);
            return false;
        });
        settings.setOnClickListener(v -> openSettings.run());
        settings.setOnFocusChangeListener((v, active) -> {
            Ui.animateFocus(v, active);
            if (active) ensureVisible(v);
        });
        appsTitle = label(c, "Your apps", Ui.TEXT, Ui.MEDIUM);
        appsTitle.setVisibility(GONE);
        appCount = label(c, "", Ui.FAINT, Ui.REGULAR);
        appCount.setVisibility(GONE);
        appCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        weatherTitle = label(c, "Weather", Ui.TEXT, Ui.MEDIUM);
        weatherTitle.setVisibility(GONE);
        remindersTitle = label(c, "Bin collection", Ui.TEXT, Ui.MEDIUM);
        remindersTitle.setVisibility(GONE);
        weatherAction = label(c, "Set weather location", Ui.MUTED, Ui.REGULAR);
        weatherAction.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        weatherAction.setOnClickListener(v -> chooseWeather.run());
        weatherAction.setOnFocusChangeListener((v, active) -> {
            v.setBackground(active ? Ui.glassOutline(c, Ui.dp(c, 6), Ui.TEXT) : null);
            if (active) ensureVisible(v);
        });
        weatherAction.setOnHoverListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER) ensureVisible(v);
            return false;
        });
        footer = label(c, "\u25C0 \u25B6 Browse apps \u00B7 Select to open \u00B7 \u2193 Weather", Ui.FAINT, Ui.REGULAR);
        footer.setVisibility(GONE);
        weatherStatus = label(c, "", Ui.FAINT, Ui.REGULAR);
        weatherStatus.setVisibility(GONE);
        weatherStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        for (View view : new View[] {header, appsTitle, appCount, apps, weatherTitle, weatherAction,
                remindersTitle, reminders, footer, weatherStatus}) autoScrollOnHover(view);
        for (View view : new View[] {header, appsTitle, appCount, apps, weatherTitle, weatherAction,
                weather, remindersTitle, reminders, footer, weatherStatus, settings}) {
            content.addView(view, new FrameLayout.LayoutParams(0, 0));
        }
    }

    private static TextView label(Context c, String s, int color, android.graphics.Typeface face) {
        TextView v = Ui.text(c, 12, color, face);
        v.setText(s);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setSingleLine(true);
        return v;
    }

    void onResize(Runnable run) { resized = run; }
    float designScale() { return scale; }

    void ensureVisible(View child) {
        if (child == null || child.getWidth() <= 0 || child.getHeight() <= 0 || viewport.getHeight() <= 0) return;
        int[] itemPos = new int[2], viewportPos = new int[2];
        child.getLocationOnScreen(itemPos);
        viewport.getLocationOnScreen(viewportPos);
        int scrollY = viewport.getScrollY();
        int childTop = itemPos[1] - viewportPos[1] + scrollY;
        int childBottom = childTop + child.getHeight();
        int padding = px(24), target = scrollY;
        if (childTop < scrollY + padding) target = childTop - padding;
        else if (childBottom > scrollY + viewport.getHeight() - padding) {
            target = childBottom - viewport.getHeight() + padding;
        }
        int max = Math.max(0, content.getHeight() - viewport.getHeight());
        target = Math.max(0, Math.min(max, target));
        if (Math.abs(target - scrollY) > px(2)) viewport.smoothScrollTo(0, target);
    }

    private void autoScrollOnHover(View view) {
        view.setOnHoverListener((child, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER) ensureVisible(child);
            return false;
        });
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        scale = Math.min(w / 1920f, h / 1080f);
        ambient.setShader(new RadialGradient(w * .20f, -h * .15f, w * .65f,
                new int[] {0xFF252A2B, 0xFF080A0C}, null, Shader.TileMode.CLAMP));
        settings.setPadding(px(18), px(18), px(18), px(18));
        ScrollView.LayoutParams contentLp = (ScrollView.LayoutParams) content.getLayoutParams();
        contentLp.height = px(1080);
        content.setLayoutParams(contentLp);
        StateListDrawable states = new StateListDrawable();
        GradientDrawable focused = Ui.round(Ui.SURFACE, px(36));
        focused.setStroke(Math.max(1, px(2)), Ui.TEXT);
        states.addState(new int[] {android.R.attr.state_focused}, focused);
        states.addState(new int[] {android.R.attr.state_hovered}, focused);
        GradientDrawable idle = Ui.round(0xFF1B2124, px(36));
        idle.setStroke(Math.max(1, px(1)), 0xFF354046);
        states.addState(new int[0], idle);
        settings.setBackground(states);
        apps.strip.setPadding(px(12), px(12), px(12), px(12));
        if (resized != null) post(resized);
    }

    private int px(float value) { return Math.round(value * scale); }
    private void place(View v, float x, float y, float w, float h) {
        int ox = (getWidth() - px(1920)) / 2;
        int left = ox + px(x), top = px(y), width = px(w), height = px(h);
        v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        v.layout(left, top, left + width, top + height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (scale == 0) scale = Math.min(getWidth() / 1920f, getHeight() / 1080f);
        viewport.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
        viewport.layout(0, 0, getWidth(), getHeight());
        content.measure(MeasureSpec.makeMeasureSpec(getWidth(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(px(1080), MeasureSpec.EXACTLY));
        content.layout(0, 0, getWidth(), px(1080));
        place(header, 0, 0, 1920, 260);
        place(settings, 1740, 112, 72, 72);
        setType(appsTitle, 28); setType(weatherTitle, 28); setType(remindersTitle, 28);
        setType(appCount, 20); setType(weatherAction, 20);
        setType(footer, 18); setType(weatherStatus, 18);
        place(appsTitle, 108, 252, 900, 42);
        place(appCount, 1200, 252, 612, 40);
        place(weatherTitle, 108, 263, 900, 32);
        place(weatherAction, 1200, 215, 612, 30);
        place(weather, 74, 253, 1778, 144);
        place(apps, 96, 445, 1728, 212);
        place(remindersTitle, 108, 703, 900, 32);
        place(reminders, 108, 705, 852, 106);
        place(footer, 108, 850, 1100, 42);
        place(weatherStatus, 1200, 850, 612, 42);
    }

    private void setType(TextView view, float designSize) {
        view.setTextSize(TypedValue.COMPLEX_UNIT_PX, px(designSize));
    }

    @Override protected void onDraw(Canvas canvas) {
        canvas.drawColor(0xFF080A0C);
        canvas.drawRect(0, 0, getWidth(), getHeight(), ambient);
        super.onDraw(canvas);
    }
}
