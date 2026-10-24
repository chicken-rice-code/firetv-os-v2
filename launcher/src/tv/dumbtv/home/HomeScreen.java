package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;

/** Responsive 16:9 dashboard; all component bounds are proportions of the available viewport. */
final class HomeScreen extends FrameLayout {
    final ClockGreeting header;
    final AppsRow apps;
    final WeatherRow weather;
    final ImageButton settings;
    final TextView appsTitle, appCount, weatherTitle, weatherAction, footer, weatherStatus;
    private final Paint ambient = new Paint();
    private Runnable resized;
    private float scale;

    HomeScreen(Context c, Runnable openSettings, Runnable chooseWeather) {
        super(c);
        setWillNotDraw(false);
        setClipChildren(true);
        header = new ClockGreeting(c, ClockGreeting.SYSTEM);
        apps = new AppsRow(c);
        weather = new WeatherRow(c);
        settings = new ImageButton(c);
        settings.setId(View.generateViewId());
        settings.setImageResource(R.drawable.material_settings);
        settings.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        settings.setContentDescription("Settings");
        settings.setFocusable(true);
        settings.setOnClickListener(v -> openSettings.run());
        settings.setOnFocusChangeListener((v, active) -> Ui.animateFocus(v, active));
        appsTitle = label(c, "Your apps", Ui.TEXT, Ui.MEDIUM);
        appCount = label(c, "", Ui.FAINT, Ui.REGULAR);
        appCount.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        weatherTitle = label(c, "Weather", Ui.TEXT, Ui.MEDIUM);
        weatherAction = label(c, "Set weather location", Ui.MUTED, Ui.REGULAR);
        weatherAction.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        weatherAction.setOnClickListener(v -> chooseWeather.run());
        weatherAction.setOnFocusChangeListener((v, active) -> {
            v.setBackground(active ? Ui.glassOutline(c, Ui.dp(c, 6), Ui.TEXT) : null);
        });
        footer = label(c, "\u25C0 \u25B6 Browse apps \u00B7 Select to open \u00B7 \u2193 Weather", Ui.FAINT, Ui.REGULAR);
        weatherStatus = label(c, "", Ui.FAINT, Ui.REGULAR);
        weatherStatus.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        for (View view : new View[] {header, appsTitle, appCount, apps, weatherTitle, weatherAction,
                weather, footer, weatherStatus, settings}) addView(view);
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

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        scale = Math.min(w / 1920f, h / 1080f);
        ambient.setShader(new RadialGradient(w * .20f, -h * .15f, w * .65f,
                new int[] {0xFF252A2B, 0xFF080A0C}, null, Shader.TileMode.CLAMP));
        settings.setPadding(px(18), px(18), px(18), px(18));
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
        int ox = (getWidth() - px(1920)) / 2, oy = (getHeight() - px(1080)) / 2;
        int left = ox + px(x), top = oy + px(y), width = px(w), height = px(h);
        v.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
        v.layout(left, top, left + width, top + height);
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        if (scale == 0) scale = Math.min(getWidth() / 1920f, getHeight() / 1080f);
        place(header, 0, 0, 1920, 260);
        place(settings, 1740, 112, 72, 72);
        setType(appsTitle, 28); setType(weatherTitle, 28);
        setType(appCount, 20); setType(weatherAction, 20);
        setType(footer, 18); setType(weatherStatus, 18);
        place(appsTitle, 108, 330, 900, 48);
        place(appCount, 1200, 334, 612, 40);
        place(apps, 96, 392, 1728, 238);
        place(weatherTitle, 108, 670, 900, 48);
        place(weatherAction, 1200, 674, 612, 40);
        place(weather, 108, 744, 1704, 209);
        place(footer, 108, 985, 1100, 42);
        place(weatherStatus, 1200, 985, 612, 42);
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
