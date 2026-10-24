package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Rect;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.provider.Settings;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.format.DateFormat;
import android.text.style.RelativeSizeSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class Home extends Activity {
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final ExecutorService colorIo = Executors.newSingleThreadExecutor();
    private final List<TextView> chips = new ArrayList<>();
    private Config cfg;
    private TvInputManager tv;
    private int applied = -1, appsGeneration;
    private boolean resumed, landed, catalogReady;
    private FrameLayout root;
    private ImageView wall;

    private View shade;
    private View tint;
    private TextView greeting, socks, clock, date, weatherIcon, weatherTemp, weatherCondition, weatherPlace;
    private TextView menuButton, loadingStatus;
    private PopupWindow homeMenu;
    private LinearLayout weather, forecastPanel, forecastRow;
    private TextView forecastHint, forecastTitle;
    private Weather.Current weatherData;
    private Row apps, inputs;
    private Tile moving;
    private String wallKey;
    private String backgroundMode;
    private int targetColor;
    private boolean tintWallpaper;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri data = i.getData();
            loadApps(data == null ? null : data.getSchemeSpecificPart());
        }
    };

    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            tick();
        }
    };

    private final TvInputManager.TvInputCallback inputWatch = new TvInputManager.TvInputCallback() {
        @Override
        public void onInputStateChanged(String id, int state) {
            loadInputs();
        }

        @Override
        public void onInputAdded(String id) {
            loadInputs();
        }

        @Override
        public void onInputRemoved(String id) {
            loadInputs();
        }

        @Override
        public void onInputUpdated(String id) {
            loadInputs();
        }
    };

    private final class Row {
        final String kind;
        final TextView title;
        final HorizontalScrollView scroll;
        final LinearLayout strip;
        final Grid grid;
        List<Item> all = new ArrayList<>();

        Row(String kind, String name, LinearLayout parent) {
            this.kind = kind;
            int edge = dp(Ui.PAGE_GUTTER), bleed = dp(Ui.SPACE_SM);
            title = Ui.text(Home.this, 12, Ui.MUTED, Ui.MEDIUM);
            title.setText(name.toUpperCase(Locale.ROOT));
            title.setLetterSpacing(0.14f);
            title.setPadding(edge, dp(Ui.SPACE_LG), edge, 0);
            strip = new LinearLayout(Home.this);
            strip.setClipChildren(false);
            strip.setClipToPadding(false);
            strip.setPadding(bleed, dp(Ui.SPACE_SM), bleed, dp(Ui.SPACE_XS));
            scroll = new HorizontalScrollView(Home.this) {
                /** Keeps the focused tile centered, except near either end of the row. */
                @Override
                protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                    if (getChildCount() == 0) return 0;
                    int range = Math.max(0, getChildAt(0).getWidth() - (getWidth() - getPaddingLeft() - getPaddingRight()));
                    int target = Math.max(0, Math.min(range, r.centerX() - getWidth() / 2));
                    return target - getScrollX();
                }
            };
            scroll.setFocusable(false);
            scroll.setHorizontalScrollBarEnabled(false);
            scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
            scroll.setClipChildren(false);
            scroll.setClipToPadding(false);
            scroll.setPadding(edge - bleed, 0, edge - bleed, 0);
            scroll.addView(strip, new FrameLayout.LayoutParams(WRAP, WRAP));
            grid = new Grid(Home.this);
            grid.setPadding(edge, dp(Ui.SPACE_SM), edge, dp(Ui.SPACE_XS));
            parent.addView(title, new LinearLayout.LayoutParams(MATCH, WRAP));
            parent.addView(scroll, new LinearLayout.LayoutParams(MATCH, WRAP));
            parent.addView(grid, new LinearLayout.LayoutParams(MATCH, WRAP));
        }

        /** Whichever container currently holds the tiles, following Options › Layout. */
        ViewGroup tiles() {
            return "grid".equals(cfg.get(Config.LAYOUT)) ? grid : strip;
        }

        void setVisible(boolean on) {
            boolean gridMode = "grid".equals(cfg.get(Config.LAYOUT));
            title.setVisibility(on ? View.VISIBLE : View.GONE);
            scroll.setVisibility(on && !gridMode ? View.VISIBLE : View.GONE);
            grid.setVisibility(on && gridMode ? View.VISIBLE : View.GONE);
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        Future<List<Item>> catalog = io.submit(() -> Catalog.apps(this));
        cfg = Config.get(this);
        tv = (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE);
        setContentView(build());

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(packages, f);
        Keeper.start(this);
        if (tv != null) tv.registerCallback(inputWatch, ui);
        firstApps(catalog);
        loadInputs();
    }

    /** Loads the catalogue and first-screen artwork off the UI thread before showing the tiles. */
    private void firstApps(Future<List<Item>> catalog) {
        io.execute(() -> {
            List<Item> initial;
            try {
                initial = catalog.get();
            } catch (ExecutionException e) {
                Log.w("dumbtv", "could not load the app list", e);
                initial = new ArrayList<>();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                initial = new ArrayList<>();
            }
            int fit = getResources().getDisplayMetrics().widthPixels / Ui.dp(this, 178) + 1;
            for (Item it : initial) {
                if (fit-- <= 0) break;
                Catalog.art(getPackageManager(), it);
                it.artRequested = true;
            }
            List<Item> ready = initial;
            ui.post(() -> {
                if (isDestroyed()) return;
                apps.all = ready;
                catalogReady = true;
                render(apps);
            });
        });
    }
    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        Guard.arrived(this);
        cfg.refresh();
        cfg.visited();
        if (cfg.version() != applied) apply();
        else background();
        IntentFilter f = new IntentFilter(Intent.ACTION_TIME_TICK);
        f.addAction(Intent.ACTION_TIME_CHANGED);
        f.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(time, f);
        tick();
    }

    @Override
    public void onEnterAnimationComplete() {
        Guard.shown();
    }

    @Override
    protected void onPause() {
        super.onPause();
        resumed = false;
        targetColor = 0;
        unregisterReceiver(time);
        if (moving != null) stopMoving();
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        if (tv != null) tv.unregisterCallback(inputWatch);
        io.shutdownNow();
        colorIo.shutdownNow();
        super.onDestroy();
    }
    /**
     * Android 9 keeps a hidden window's GPU context (about 35 MB on this TV) unless memory is
     * critical. Dropping it while an app plays costs one context rebuild on the way back.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        if (level < TRIM_MEMORY_UI_HIDDEN || level >= TRIM_MEMORY_COMPLETE) return;
        try {
            Class<?> wmg = Class.forName("android.view.WindowManagerGlobal");
            Object global = wmg.getMethod("getInstance").invoke(null);
            wmg.getMethod("trimMemory", int.class).invoke(global, TRIM_MEMORY_COMPLETE);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // not reachable on this build: the context simply stays
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (moving != null) stopMoving();
        if (resumed) focusFirst();
    }

    @Override
    public void onBackPressed() {
        focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (moving == null) return super.dispatchKeyEvent(e);
        if (e.getAction() == KeyEvent.ACTION_DOWN) {
            switch (e.getKeyCode()) {
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    shift(-1);
                    break;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    shift(1);
                    break;
                case KeyEvent.KEYCODE_DPAD_UP:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    // In a grid, up and down move the tile a whole line; a strip has no lines.
                    if (moving.getParent() instanceof Grid) {
                        int cols = ((Grid) moving.getParent()).columns();
                        shift(e.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP ? -cols : cols);
                    }
                    break;
                default:
                    stopMoving();
            }
        }
        return true;
    }

    @Override
    public boolean onKeyDown(int code, KeyEvent e) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View f = getCurrentFocus();
            if (f instanceof Tile) menu((Tile) f);
            else openHomeMenu();
            return true;
        }
        return super.onKeyDown(code, e);
    }

    /**
     * The scroll position for a focused rect {@code r}: the earliest of the top, the apps row or
     * the inputs row that still shows the row holding {@code r} whole.
     */
    private int rowTarget(ScrollView page, Rect r) {
        View content = page.getChildAt(0);
        List<int[]> rows = rowBlocks(content);
        int[] at = null;
        for (int[] b : rows) {
            if (r.centerY() >= b[0]) at = b;
        }
        if (at == null) return 0;
        int range = Math.max(0, content.getHeight() - page.getHeight());
        return Math.max(0, Math.min(range, stopFor(at, rows, page.getHeight())));
    }

    /**
     * Title top and strip bottom of each visible row, in {@code content}'s coordinates. In grid
     * mode every line of tiles is its own block (the first one starting at the row title), so
     * a grid taller than the screen still scrolls a whole line at a time.
     */
    private List<int[]> rowBlocks(View content) {
        List<int[]> rows = new ArrayList<>();
        for (Row row : new Row[] {apps, inputs}) {
            if (row.scroll.getVisibility() == View.VISIBLE) {
                rows.add(new int[] {topIn(row.title, content), topIn(row.scroll, content) + row.scroll.getHeight()});
            } else if (row.grid.getVisibility() == View.VISIBLE) {
                int gridTop = topIn(row.grid, content), lastTop = Integer.MIN_VALUE;
                for (int i = 0; i < row.grid.getChildCount(); i++) {
                    View t = row.grid.getChildAt(i);
                    if (t.getTop() == lastTop) continue;
                    int start = lastTop == Integer.MIN_VALUE ? topIn(row.title, content) : gridTop + t.getTop() - dp(12);
                    lastTop = t.getTop();
                    rows.add(new int[] {start, gridTop + t.getBottom() + dp(8)});
                }
            }
        }
        return rows;
    }

    /** The earliest of the top or a row title that shows the whole of {@code at}. */
    private static int stopFor(int[] at, List<int[]> rows, int height) {
        if (height >= at[1]) return 0;
        for (int[] b : rows) {
            if (b[0] <= at[0] && b[0] + height >= at[1]) return b[0];
        }
        return at[0];
    }

    /**
     * When the page is taller than the screen, pads its bottom just enough that every row's stop
     * is reachable. Otherwise the last stop is clamped and the clock above it shows half cut.
     */
    private void fitBottom(ScrollView page) {
        View content = page.getChildAt(0);
        if (content == null || page.getHeight() == 0) return;
        int base = dp(18), height = page.getHeight();

        int natural = content.getHeight() - content.getPaddingBottom() + base;
        int want = base;
        if (natural > height) {
            int last = 0;
            List<int[]> rows = rowBlocks(content);
            for (int[] b : rows) last = Math.max(last, stopFor(b, rows, height));
            want = base + Math.max(0, last + height - natural);
        }
        if (content.getPaddingBottom() != want) {
            content.setPadding(content.getPaddingLeft(), content.getPaddingTop(), content.getPaddingRight(), want);
        }
    }

    /** {@code v}'s top edge in {@code content}'s coordinates. */
    private static int topIn(View v, View content) {
        int y = 0;
        while (v != null && v != content) {
            y += v.getTop();
            v = (View) v.getParent();
        }
        return y;
    }

    /** Returns the focusable tile horizontally closest to a header control. */
    private static View nearest(ViewGroup tiles, View from) {
        if (tiles == null || from == null) return null;
        int[] source = new int[2];
        from.getLocationOnScreen(source);
        int center = source[0] + from.getWidth() / 2;
        View best = null;
        long bestDistance = Long.MAX_VALUE;
        int[] location = new int[2];
        for (int i = 0; i < tiles.getChildCount(); i++) {
            View child = tiles.getChildAt(i);
            if (!child.isFocusable() || !child.isShown()) continue;
            child.getLocationOnScreen(location);
            long distance = Math.abs((long) center - (location[0] + child.getWidth() / 2));
            if (distance < bestDistance) {
                best = child;
                bestDistance = distance;
            }
        }
        return best;
    }

    private View build() {
        root = new FrameLayout(this);
        wall = new ImageView(this);
        wall.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(wall, new FrameLayout.LayoutParams(MATCH, MATCH));


        shade = new View(this);
        root.addView(shade, new FrameLayout.LayoutParams(MATCH, MATCH));
        tint = new View(this);
        root.addView(tint, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout shell = new LinearLayout(this) {
            @Override
            public View focusSearch(View focused, int direction) {
                if (direction == View.FOCUS_DOWN && (chips.contains(focused) || focused == weather)) {
                    View below = nearest(apps.tiles(), focused);
                    if (below != null && below.isFocusable()) return below;
                }
                return super.focusSearch(focused, direction);
            }
        };
        shell.setOrientation(LinearLayout.VERTICAL);
        shell.setClipChildren(false);
        shell.setClipToPadding(false);
        root.addView(shell, new FrameLayout.LayoutParams(MATCH, MATCH));

        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(Ui.PAGE_GUTTER), dp(Ui.SPACE_LG), dp(Ui.PAGE_GUTTER), dp(Ui.SPACE_LG));
        LinearLayout welcomeBlock = new LinearLayout(this);
        welcomeBlock.setOrientation(LinearLayout.HORIZONTAL);
        welcomeBlock.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout dateTime = new LinearLayout(this);
        dateTime.setOrientation(LinearLayout.VERTICAL);
        dateTime.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        clock = Ui.text(this, 27, Ui.TEXT, Ui.MEDIUM);
        clock.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        date = Ui.text(this, 12, Ui.MUTED, Ui.REGULAR);
        date.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        dateTime.addView(clock);
        dateTime.addView(date);
        welcomeBlock.addView(dateTime, new LinearLayout.LayoutParams(WRAP, WRAP));

        View dateDivider = new View(this);
        dateDivider.setBackgroundColor(0x45FFFFFF);
        LinearLayout.LayoutParams dateDividerLp = new LinearLayout.LayoutParams(dp(1), dp(42));
        dateDividerLp.leftMargin = dp(Ui.SPACE_MD);
        dateDividerLp.rightMargin = dp(Ui.SPACE_MD);
        welcomeBlock.addView(dateDivider, dateDividerLp);

        LinearLayout when = new LinearLayout(this);
        when.setOrientation(LinearLayout.VERTICAL);
        greeting = Ui.text(this, 27, Ui.TEXT, Ui.MEDIUM);
        socks = Ui.text(this, 12, Ui.MUTED, Ui.REGULAR);
        when.addView(greeting);
        when.addView(socks);
        welcomeBlock.addView(when, new LinearLayout.LayoutParams(WRAP, WRAP));
        head.addView(welcomeBlock, new LinearLayout.LayoutParams(WRAP, WRAP));
        head.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));

        weather = new LinearLayout(this);
        weather.setOrientation(LinearLayout.HORIZONTAL);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_SM), dp(Ui.SPACE_MD), dp(Ui.SPACE_SM));
        weather.setBackground(Ui.glass(this, dp(15)));
        weather.setFocusable(true);
        weather.setClickable(true);
        weather.setContentDescription("Weather. Select for the seven-day forecast");
        weather.setOnClickListener(v -> toggleForecast());
        weather.setOnFocusChangeListener((v, focused) -> styleWeather(focused || weather.isHovered()));
        weather.setOnHoverListener((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER
                    || event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_EXIT) {
                styleWeather(weather.isFocused() || event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER);
            }
            return false;
        });
        weatherIcon = Ui.text(this, 27, Ui.TEXT, Ui.MEDIUM);
        weatherIcon.setGravity(Gravity.CENTER);
        weather.addView(weatherIcon, new LinearLayout.LayoutParams(dp(36), MATCH));
        LinearLayout wxCopy = new LinearLayout(this);
        wxCopy.setOrientation(LinearLayout.VERTICAL);
        wxCopy.setGravity(Gravity.CENTER_VERTICAL);
        wxCopy.setPadding(dp(Ui.SPACE_SM), 0, dp(Ui.SPACE_SM), 0);
        weatherPlace = Ui.text(this, 9, Ui.MUTED, Ui.MEDIUM);
        weatherPlace.setLetterSpacing(0.05f);
        weatherCondition = Ui.text(this, 12, Ui.TEXT, Ui.MEDIUM);
        wxCopy.addView(weatherPlace);
        wxCopy.addView(weatherCondition);
        weather.addView(wxCopy, new LinearLayout.LayoutParams(0, WRAP, 1f));
        weatherTemp = Ui.text(this, 21, Ui.TEXT, Ui.LIGHT);
        weatherTemp.setGravity(Gravity.CENTER_VERTICAL);
        weather.addView(weatherTemp);
        LinearLayout.LayoutParams weatherLp = new LinearLayout.LayoutParams(dp(220), dp(60));
        weatherLp.rightMargin = dp(Ui.SPACE_XS);
        head.addView(weather, weatherLp);
        menuButton = chip("\u2630  Menu", v -> openHomeMenu());
        menuButton.setContentDescription("Options and TV settings");
        menuButton.setOnKeyListener((v, code, event) -> {
            boolean select = code == KeyEvent.KEYCODE_DPAD_CENTER
                    || code == KeyEvent.KEYCODE_ENTER || code == KeyEvent.KEYCODE_NUMPAD_ENTER;
            if (!select) return false;
            if (event.getAction() == KeyEvent.ACTION_UP) openHomeMenu();
            return true;
        });
        head.addView(menuButton);
        shell.addView(head, new LinearLayout.LayoutParams(MATCH, WRAP));

        View divider = new View(this);
        divider.setBackgroundColor(0x20FFFFFF);
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(MATCH, dp(1));
        dividerLp.leftMargin = dp(Ui.PAGE_GUTTER);
        dividerLp.rightMargin = dp(Ui.PAGE_GUTTER);
        shell.addView(divider, dividerLp);

        ScrollView scroll = new ScrollView(this) {
            @Override
            protected int computeScrollDeltaToGetChildRectOnScreen(Rect r) {
                return rowTarget(this, r) - getScrollY();
            }

            @Override
            protected void onLayout(boolean changed, int l, int t, int r, int b) {
                super.onLayout(changed, l, t, r, b);
                fitBottom(this);
            }

            @Override
            public boolean executeKeyEvent(KeyEvent e) {
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_UP:
                    case KeyEvent.KEYCODE_DPAD_DOWN:
                    case KeyEvent.KEYCODE_PAGE_UP:
                    case KeyEvent.KEYCODE_PAGE_DOWN:
                    case KeyEvent.KEYCODE_SPACE:
                        return false;
                    default:
                        return super.executeKeyEvent(e);
                }
            }
        };
        scroll.setFocusable(false);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setClipChildren(false);
        shell.addView(scroll, new LinearLayout.LayoutParams(MATCH, 0, 1f));

        LinearLayout column = new LinearLayout(this);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setClipChildren(false);
        column.setClipToPadding(false);
        column.setPadding(0, dp(Ui.SPACE_XS), 0, dp(Ui.SPACE_XL));
        scroll.addView(column, new FrameLayout.LayoutParams(MATCH, WRAP));

        forecastPanel = new LinearLayout(this);
        forecastPanel.setOrientation(LinearLayout.VERTICAL);
        forecastPanel.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_SM), dp(Ui.SPACE_MD), dp(Ui.SPACE_SM));
        forecastPanel.setBackground(Ui.glass(this, dp(16)));
        forecastTitle = Ui.text(this, 11, Ui.MUTED, Ui.MEDIUM);
        forecastTitle.setLetterSpacing(0.12f);
        forecastTitle.setText("THE WEEK AHEAD");
        forecastTitle.setPadding(dp(Ui.SPACE_XS), 0, 0, dp(Ui.SPACE_XS));
        forecastPanel.addView(forecastTitle);
        forecastHint = Ui.text(this, 13, Ui.MUTED, Ui.REGULAR);
        forecastHint.setText("Select the weather card to load the week ahead");
        forecastPanel.addView(forecastHint);
        forecastRow = new LinearLayout(this);
        forecastRow.setOrientation(LinearLayout.HORIZONTAL);
        forecastPanel.addView(forecastRow, new LinearLayout.LayoutParams(MATCH, WRAP));
        forecastPanel.setVisibility(View.GONE);
        LinearLayout.LayoutParams forecastLp = new LinearLayout.LayoutParams(MATCH, WRAP);
        forecastLp.setMargins(dp(Ui.PAGE_GUTTER), dp(Ui.SPACE_XS), dp(Ui.PAGE_GUTTER), dp(Ui.SPACE_SM));
        column.addView(forecastPanel, forecastLp);

        loadingStatus = Ui.text(this, 14, Ui.MUTED, Ui.REGULAR);
        loadingStatus.setText("Loading apps\u2026");
        loadingStatus.setPadding(dp(Ui.PAGE_GUTTER), dp(Ui.SPACE_LG), dp(Ui.PAGE_GUTTER), dp(Ui.SPACE_XS));
        column.addView(loadingStatus, new LinearLayout.LayoutParams(MATCH, WRAP));

        apps = new Row(Catalog.APPS, "APPS", column);
        inputs = new Row(Catalog.INPUTS, "INPUTS", column);
        apps.title.setVisibility(View.VISIBLE);
        apps.scroll.setVisibility("grid".equals(cfg.get(Config.LAYOUT)) ? View.GONE : View.VISIBLE);
        apps.grid.setVisibility("grid".equals(cfg.get(Config.LAYOUT)) ? View.VISIBLE : View.GONE);
        inputs.setVisible(true);
        return root;
    }

    private void styleWeather(boolean active) {
        weather.setBackground(active
                ? Ui.glassOutline(this, dp(15), cfg.accent())
                : Ui.glass(this, dp(15)));
    }

    private TextView chip(String label, View.OnClickListener click) {
        TextView v = Ui.text(this, 13, Ui.TEXT, Ui.MEDIUM);
        v.setText(label);
        v.setGravity(Gravity.CENTER);
        v.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_SM), dp(Ui.SPACE_MD), dp(Ui.SPACE_SM));
        v.setFocusable(true);
        v.setClickable(true);
        v.setOnClickListener(click);
        v.setOnFocusChangeListener((view, has) -> {
            if (has) focusBackground(null);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(Ui.SPACE_SM);
        v.setLayoutParams(lp);
        chips.add(v);
        return v;
    }
    private void apply() {
        applied = cfg.version();
        int accent = cfg.accent();
        for (TextView c : chips) Ui.focusStyle(c, accent, 0x14FFFFFF, dp(18));
        render(apps);
        render(inputs);
        background();
        tick();
    }

    /** Rebuilds the app list, keeping loaded art for every package except {@code changed}. */
    private void loadApps(String changed) {
        int generation = ++appsGeneration;
        io.execute(() -> {
            List<Item> list = Catalog.apps(this);
            ui.post(() -> {
                if (generation != appsGeneration || isDestroyed()) return;
                for (Item fresh : list) {
                    for (Item old : apps.all) {
                        if (old.id.equals(fresh.id) && !old.id.equals(changed) && old.artRequested) {
                            fresh.banner = old.banner;
                            fresh.icon = old.icon;
                            fresh.artRequested = true;
                            fresh.backgroundColor = old.backgroundColor;
                        }
                    }
                }
                apps.all = list;
                render(apps);
            });
        });
    }

    private void loadInputs() {
        inputs.all = Catalog.inputs(this, tv);
        render(inputs);
    }

    private void render(Row row) {
        if (row == apps && !catalogReady) return;
        if (row == apps && loadingStatus != null) loadingStatus.setVisibility(View.GONE);
        if (moving != null && moving.kind.equals(row.kind)) stopMoving();
        View focused = getCurrentFocus();
        String keep = focused instanceof Tile && ((Tile) focused).kind.equals(row.kind)
                ? ((Tile) focused).item.id : null;
        boolean gridMode = "grid".equals(cfg.get(Config.LAYOUT));
        ViewGroup target = gridMode ? row.grid : row.strip;
        row.strip.removeAllViews();
        row.grid.removeAllViews();

        boolean enabled = !Catalog.INPUTS.equals(row.kind) || "on".equals(cfg.get(Config.INPUTS));
        List<Item> shown = enabled ? Catalog.visible(cfg, row.kind, row.all) : new ArrayList<>();
        row.title.setText(Catalog.APPS.equals(row.kind) ? "APPS" : "INPUTS");
        int[] size = cardSize();
        int w = size[0], h = Catalog.APPS.equals(row.kind) ? size[1] : Math.round(size[1] * 0.56f);
        String labels = cfg.get(Config.LABELS);
        int accent = cfg.accent();
        int index = 0, restore = -1;
        for (Item it : shown) {
            Tile t = new Tile(this, it, row.kind, Catalog.label(cfg, it), w, h, labels, accent);
            t.setOnClickListener(v -> open((Tile) v));
            t.setOnFocusChangeListener((v, has) -> {
                if (has) focusBackground(((Tile) v).item);
            });
            t.setOnLongClickListener(v -> {
                menu((Tile) v);
                return true;
            });
            if (gridMode) {
                target.addView(t, new ViewGroup.LayoutParams(WRAP, WRAP));
            } else {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(WRAP, WRAP);
                lp.rightMargin = index + 1 < shown.size() ? dp(Ui.SPACE_SM) : 0;
                target.addView(t, lp);
            }
            if (it.id.equals(keep)) restore = index;
            index++;
            if (Catalog.APPS.equals(row.kind) && !it.artRequested) loadArt(it);
        }
        row.setVisible(!shown.isEmpty());
        if (restore >= 0) {
            target.getChildAt(restore).requestFocus();
        } else if (keep != null && target.getChildCount() > 0) {
            target.getChildAt(0).requestFocus();
        } else if (getCurrentFocus() == null || !landed) {
            focusFirst();
        }
    }

    private void loadArt(Item it) {
        it.artRequested = true;
        io.execute(() -> {
            Catalog.art(getPackageManager(), it);
            ui.post(() -> {
                ViewGroup tiles = apps.tiles();
                for (int i = 0; i < tiles.getChildCount(); i++) tiles.getChildAt(i).invalidate();
                if (focusedItem() == it) focusBackground(it);
            });
        });
    }
    private Item focusedItem() {
        View focused = getCurrentFocus();
        return focused instanceof Tile ? ((Tile) focused).item : null;
    }

    private void focusBackground(Item it) {
        String mode = cfg.get(Config.BACKGROUND);
        if (!resumed || !("adaptive".equals(mode) || "adaptive-tint".equals(mode))) return;
        if ("adaptive-tint".equals(mode) && !tintWallpaper) return;
        if (it != null && it.info != null && it.backgroundColor == 0 && !it.colorRequested
                && (it.banner != null || it.icon != null)) {
            it.colorRequested = true;
            colorIo.execute(() -> {
                int color = FocusColor.from(it.banner != null ? it.banner : it.icon);
                ui.post(() -> {
                    it.backgroundColor = color;
                    if (focusedItem() == it) focusBackground(it);
                });
            });
        }
        int color = it == null || it.backgroundColor == 0 ? FocusColor.FALLBACK : it.backgroundColor;
        if ("adaptive-tint".equals(mode)) color = FocusColor.tint(color);
        if (color == targetColor) return;
        targetColor = color;
        if ("adaptive-tint".equals(backgroundMode)) tint.setBackgroundColor(color);
        else root.setBackgroundColor(color);
    }

    private int[] cardSize() {
        switch (cfg.get(Config.TILES)) {
            case "small":
                return new int[] {dp(128), dp(72)};
            case "large":
                return new int[] {dp(190), dp(107)};
            default:
                return new int[] {dp(158), dp(89)};
        }
    }

    private void focusFirst() {
        if (!catalogReady) return;
        apps.scroll.scrollTo(0, 0);
        inputs.scroll.scrollTo(0, 0);
        View first = apps.tiles().getChildAt(0);
        if (first == null) first = inputs.tiles().getChildAt(0);
        landed = apps.tiles().getChildCount() > 0;
        if (first == null && !chips.isEmpty()) first = chips.get(0);
        if (first != null) first.requestFocus();
    }

    private void tick() {
        String mode = cfg.get(Config.CLOCK);
        boolean h24 = "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
        Date now = new Date();
        if (h24) {
            clock.setText(DateFormat.format("HH:mm", now));
        } else {
            SpannableString half = new SpannableString(" " + DateFormat.format("a", now));
            half.setSpan(new RelativeSizeSpan(0.36f), 0, half.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            clock.setText(DateFormat.format("h:mm", now));
            clock.append(half);
        }
        Locale locale = Locale.getDefault();
        date.setText(DateFormat.format(DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM"), now));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String timeOfDay = hour >= 5 && hour < 12 ? "morning" : hour >= 12 && hour < 17 ? "afternoon" : "evening";
        greeting.setText("Good " + timeOfDay + ", Lizzie & Jimmi");
        socks.setText("(and Socks) \uD83D\uDC15");
        refreshWeather();
        // hourly and time-of-day rotations change while the screen stays open; a no-op otherwise
        background();
    }


    private String weatherLocation;
    private long weatherUpdated;
    private boolean weatherLoading;

    private void toggleForecast() {
        boolean show = forecastPanel.getVisibility() != View.VISIBLE;
        weather.setContentDescription(show
                ? "Weather. Select to hide the seven-day forecast"
                : "Weather. Select for the seven-day forecast");
        forecastPanel.animate().cancel();
        if (show) {
            forecastPanel.setVisibility(View.VISIBLE);
            forecastPanel.setAlpha(0f);
            forecastPanel.animate().alpha(1f).setDuration(120).start();
            if (weatherData != null) renderForecast(weatherData);
            else {
                forecastHint.setText("Loading the week ahead\u2026");
                refreshWeather();
            }
        } else {
            forecastPanel.animate().alpha(0f).setDuration(100)
                    .withEndAction(() -> {
                        forecastPanel.setVisibility(View.GONE);
                        forecastPanel.setAlpha(1f);
                    }).start();
        }
    }

    private void renderForecast(Weather.Current data) {
        forecastRow.removeAllViews();
        if (data.error != null || data.days.isEmpty()) {
            forecastTitle.setText("THE WEEK AHEAD");
            forecastHint.setVisibility(View.VISIBLE);
            forecastHint.setText(data.error == null ? "Forecast unavailable" : data.error);
            return;
        }
        forecastTitle.setText("THE WEEK AHEAD   \u00B7   " + data.place.toUpperCase(Locale.getDefault()));
        forecastHint.setVisibility(View.GONE);
        for (int i = 0; i < data.days.size(); i++) {
            Weather.Day day = data.days.get(i);
            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setGravity(Gravity.CENTER);
            card.setPadding(dp(Ui.SPACE_SM), dp(Ui.SPACE_XS), dp(Ui.SPACE_SM), dp(Ui.SPACE_XS));
            card.setBackground("Today".equals(day.name)
                    ? Ui.glassOutline(this, dp(11), cfg.accent()) : Ui.glass(this, dp(11)));
            TextView name = Ui.text(this, 11, Ui.TEXT, Ui.MEDIUM);
            name.setGravity(Gravity.CENTER);
            name.setText(day.name);
            TextView symbol = Ui.text(this, 20, Ui.TEXT, Ui.MEDIUM);
            symbol.setGravity(Gravity.CENTER);
            symbol.setText(day.symbol);
            symbol.setPadding(0, dp(Ui.SPACE_XS), 0, dp(Ui.SPACE_XS));
            LinearLayout temps = new LinearLayout(this);
            temps.setGravity(Gravity.CENTER);
            TextView high = Ui.text(this, 13, Ui.TEXT, Ui.MEDIUM);
            high.setText(day.high + "\u00B0");
            TextView low = Ui.text(this, 11, Ui.MUTED, Ui.MEDIUM);
            low.setPadding(dp(Ui.SPACE_XS), 0, 0, 0);
            low.setText(day.low + "\u00B0");
            temps.addView(high);
            temps.addView(low);
            card.addView(name);
            card.addView(symbol);
            card.addView(temps);
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(0, dp(84), 1f);
            cardLp.rightMargin = i + 1 < data.days.size() ? dp(Ui.SPACE_XS) : 0;
            forecastRow.addView(card, cardLp);
        }
    }

    private void refreshWeather() {
        String location = cfg.weatherLocation().trim();
        if (location.isEmpty()) {
            weather.setVisibility(View.GONE);
            forecastPanel.setVisibility(View.GONE);
            return;
        }
        weather.setVisibility(View.VISIBLE);
        if (!location.equals(weatherLocation)) {
            weatherLocation = location;
            weatherUpdated = 0;
            weatherData = null;
            weatherPlace.setText(location.toUpperCase(Locale.getDefault()));
            weatherCondition.setText("Getting forecast...");
                    weatherTemp.setText("--\u00B0");
            weatherIcon.setText("\u25CC");
        }
        long now = System.currentTimeMillis();
        if (weatherLoading || now - weatherUpdated < 30 * 60 * 1000L) return;
        weatherLoading = true;
        io.execute(() -> {
            Weather.Current result = Weather.fetch(location);
            ui.post(() -> {
                weatherLoading = false;
                if (!location.equals(cfg.weatherLocation().trim())) return;
                weatherUpdated = System.currentTimeMillis() - (result.error == null ? 0 : 25 * 60 * 1000L);
                weatherData = result;
                if (result.error == null) {
                    weatherPlace.setText(result.place.toUpperCase(Locale.getDefault()));
                    weatherCondition.setText(result.condition);
                    weatherTemp.setText(result.temperature);
                    weatherIcon.setText(result.symbol);
                } else {
                    weatherCondition.setText(result.error);
                    weatherTemp.setText("--\u00B0");
                    weatherIcon.setText(result.symbol);
                }

                if (forecastPanel.getVisibility() == View.VISIBLE) renderForecast(result);
            });
        });
    }

    private void background() {
        String mode = cfg.get(Config.BACKGROUND);
        boolean image = "image".equals(mode) || "adaptive-tint".equals(mode);
        File file = image ? cfg.wallpaper() : null;
        tintWallpaper = "adaptive-tint".equals(mode) && file != null;

        if ("adaptive".equals(mode)) {
            if (!mode.equals(backgroundMode)) {
                targetColor = 0;
                root.setBackgroundColor(FocusColor.FALLBACK);
                tint.setBackgroundColor(Color.TRANSPARENT);
            }
            backgroundMode = mode;
            focusBackground(focusedItem());
        } else if ("adaptive-tint".equals(mode)) {
            root.setBackgroundColor(file == null ? cfg.background() : Color.BLACK);
            if (!mode.equals(backgroundMode) || file == null) {
                targetColor = 0;
                tint.setBackgroundColor(Color.TRANSPARENT);
            }
            backgroundMode = mode;
            if (tintWallpaper) focusBackground(focusedItem());
        } else {
            targetColor = 0;
            backgroundMode = mode;
            tint.setBackgroundColor(Color.TRANSPARENT);
            if ("gradient".equals(mode)) {
                root.setBackground(Ui.gradient(cfg.gradientColors(), cfg.get(Config.GRADIENT_DIRECTION)));
            } else {
                root.setBackgroundColor(cfg.background());
            }
        }
        int dim = Integer.parseInt(cfg.get(Config.DIM));
        shade.setBackgroundColor(file == null ? Color.TRANSPARENT : Color.argb(dim * 255 / 100, 0, 0, 0));
        String key = file == null ? null : file.getPath() + ":" + file.lastModified();
        if (key == null ? wallKey == null : key.equals(wallKey)) return;
        wallKey = key;
        if (file == null) {
            wall.setImageDrawable(null);
            return;
        }
        io.execute(() -> {
            Bitmap b = decode(file, getResources().getDisplayMetrics().widthPixels,
                    getResources().getDisplayMetrics().heightPixels);
            ui.post(() -> {
                if (key.equals(wallKey)) wall.setImageBitmap(b);
            });
        });
    }

    private static Bitmap decode(File f, int w, int h) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        o.inSampleSize = 1;
        while (o.outWidth / (o.inSampleSize * 2) >= w && o.outHeight / (o.inSampleSize * 2) >= h) {
            o.inSampleSize *= 2;
        }
        o.inJustDecodeBounds = false;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private void openHomeMenu() {
        if (homeMenu != null && homeMenu.isShowing()) {
            homeMenu.dismiss();
            return;
        }
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(Ui.SPACE_SM), dp(Ui.SPACE_SM), dp(Ui.SPACE_SM), dp(Ui.SPACE_SM));
        panel.setBackground(Ui.menuSurface(this, dp(16)));

        PopupWindow popup = new PopupWindow(panel, dp(208), WRAP, true);
        popup.setBackgroundDrawable(Ui.menuSurface(this, dp(16)));
        popup.setElevation(dp(10));
        popup.setOutsideTouchable(true);
        popup.setInputMethodMode(PopupWindow.INPUT_METHOD_NOT_NEEDED);
        homeMenu = popup;
        TextView options = homeMenuItem(panel, "Options", popup,
                () -> startActivity(new Intent(this, Options.class)));
        homeMenuItem(panel, "TV Settings", popup, this::openSettings);
        popup.setOnDismissListener(() -> {
            if (homeMenu == popup) homeMenu = null;
            if (menuButton != null && !isFinishing()) menuButton.requestFocus();
        });
        popup.showAsDropDown(menuButton, menuButton.getWidth() - dp(208), dp(Ui.SPACE_XS));
        options.requestFocus();
    }

    private TextView homeMenuItem(LinearLayout panel, String title, PopupWindow popup, Runnable action) {
        TextView item = Ui.text(this, 14, Ui.TEXT, Ui.MEDIUM);
        item.setText(title);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_SM), dp(Ui.SPACE_MD), dp(Ui.SPACE_SM));
        item.setFocusable(true);
        item.setClickable(true);
        Ui.focusStyle(item, cfg.accent(), 0x14FFFFFF, dp(10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, dp(44));
        lp.bottomMargin = dp(Ui.SPACE_XS);
        panel.addView(item, lp);
        item.setOnClickListener(v -> {
            popup.dismiss();
            action.run();
        });
        return item;
    }
    /**
     * Fire OS answers the generic settings action with a stub that reopens Amazon's home, which
     * Guard then replaces with ours. The TV settings entry point opens the real settings screen.
     */
    private void openSettings() {
        Intent tvSettings = new Intent(Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        boolean found = tvSettings.resolveActivity(getPackageManager()) != null;
        launch(found ? tvSettings : new Intent(Settings.ACTION_SETTINGS), null);
    }

    private void open(Tile t) {
        if (Catalog.APPS.equals(t.kind)) {
            launch(new Intent(t.item.intent), t.item.id);
            return;
        }
        Intent own = Input.intent(this, cfg, t.item, Catalog.label(cfg, t.item));
        // The built-in screen goes on top of this one in the same task, so BACK returns here and
        // the HOME takeover's singleTask relaunch clears it. NEW_TASK | RESET_TASK_IF_NEEDED on an
        // activity of the task that is already in front only brings that task forward and starts
        // nothing (ActivityStarter.complyActivityFlags), so it is started plainly.
        if (own != null) startActivity(own);
        else launch(new Intent(t.item.intent), null);
    }

    private void launch(Intent i, String pkg) {
        if (Guard.AMAZON.equals(pkg)) Guard.pause();
        try {
            startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException e) {
            Toast.makeText(this, "Could not open that", Toast.LENGTH_SHORT).show();
        }
    }

    private void menu(Tile t) {
        boolean app = Catalog.APPS.equals(t.kind);
        String[] actions = app
                ? new String[] {"Move", "Rename", "Hide", "App info"}
                : new String[] {"Move", "Rename", "Hide"};
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.label(cfg, t.item))
                .setItems(actions, (d, which) -> {
                    switch (which) {
                        case 0:
                            if (t.getParent() == null) break;
                            moving = t;
                            t.setMoving(true);
                            break;
                        case 1:
                            rename(t);
                            break;
                        case 2:
                            Catalog.setShown(cfg, t.kind, t.item, false);
                            render(app ? apps : inputs);
                            Toast.makeText(this, "Hidden. Bring it back from Options.", Toast.LENGTH_SHORT).show();
                            break;
                        default:
                            launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.fromParts("package", t.item.id, null)), null);
                    }
                })
                .show();
    }

    private void rename(Tile t) {
        EditText field = new EditText(this);
        field.setSingleLine();
        field.setText(Catalog.label(cfg, t.item));
        field.setSelectAllOnFocus(true);
        FrameLayout box = new FrameLayout(this);
        box.setPadding(dp(Ui.SPACE_XL), dp(Ui.SPACE_SM), dp(Ui.SPACE_XL), 0);
        box.addView(field);
        String key = "name." + t.item.id;
        AlertDialog d = new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Rename")
                .setView(box)
                .setPositiveButton("Save", (dialog, w) -> {
                    String name = field.getText().toString().trim();
                    cfg.set(key, name.equals(t.item.label.toString()) ? null : name);
                    apply();
                })
                .setNeutralButton("Original", (dialog, w) -> {
                    cfg.set(key, null);
                    apply();
                })
                .setNegativeButton("Cancel", null)
                .create();
        d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        d.show();
    }

    private void shift(int by) {
        ViewGroup strip = (ViewGroup) moving.getParent();
        int from = strip.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= strip.getChildCount()) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        strip.removeViewAt(from);
        strip.addView(moving, to, lp);
        moving.requestFocus();
    }

    private void stopMoving() {
        Tile t = moving;
        moving = null;
        t.setMoving(false);
        ViewGroup strip = (ViewGroup) t.getParent();
        if (strip == null) return;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < strip.getChildCount(); i++) ids.add(((Tile) strip.getChildAt(i)).item.id);
        Catalog.saveOrder(cfg, t.kind, ids);
        applied = cfg.version();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
