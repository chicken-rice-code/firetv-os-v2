package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Home Together's activity: lifecycle, repositories and remote actions; layout lives in HomeScreen. */
public final class Home extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private Config cfg;
    private HomeScreen screen;
    private WeatherRepository weather;
    private BinReminder bins;
    private List<Item> all = new ArrayList<>();
    private String lastFocus, lastApp;
    private int generation;
    private boolean ready, resumed, initialFocusDone;
    private Tile moving;
    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { loadApps(); }
    };
    private final BroadcastReceiver time = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent intent) { tick(); }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);
        if (state != null) { lastFocus = state.getString("focus"); lastApp = state.getString("app"); initialFocusDone = true; }
        screen = new HomeScreen(this, () -> openOptions(false), () -> openOptions(true));
        screen.onResize(this::renderApps);
        screen.reminders.onUp(this::focusApp);
        screen.reminders.onRowFocusChange((v, active) -> {
            if (active) { lastFocus = "reminders"; screen.ensureVisible(v); }
            screen.footer.setText(active ? "Bin collection dates \u00B7 \u2191 Apps"
                    : "\u25C0 \u25B6 Browse apps \u00B7 Select to open \u00B7 \u2193 Weather");
        });
        screen.reminders.onTileHover((v, event) -> {
            if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER) screen.ensureVisible(v);
            return false;
        });
        screen.settings.setOnFocusChangeListener((v, has) -> {
            Ui.animateFocus(v, has); if (has && initialFocusDone) lastFocus = "settings";
        });
        screen.settings.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN) {
                if (screen.weatherAction.isFocusable()) { screen.weatherAction.requestFocus(); return true; }
                focusApp(); return true;
            }
            return false;
        });
        screen.weatherAction.setOnKeyListener((v, code, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                if (code == KeyEvent.KEYCODE_DPAD_UP) { screen.settings.requestFocus(); return true; }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) {
                    focusApp(); return true;
                }
            }
            return false;
        });
        weather = new WeatherRepository(this, (data, message) -> {
            if (isDestroyed()) return;
            screen.weather.update(data, message, is24Hour(), System.currentTimeMillis());
            boolean configure = cfg.weatherLocation().isEmpty() || data == null && !message.startsWith("Getting");
            screen.weatherAction.setText(configure ? "Set weather location" : "");
            screen.weatherAction.setFocusable(configure);
            screen.weatherAction.setClickable(configure);
            screen.weatherStatus.setText(data == null ? "" : message.isEmpty()
                    ? data.location.name + " \u00B7 " + ("fahrenheit".equals(cfg.get(Config.UNITS)) ? "\u00B0F" : "\u00B0C")
                    : message);
        });
        bins = new BinReminder(this, (title, detail, upcoming) -> {
            if (!isDestroyed()) screen.reminders.update(title, detail, upcoming);
        });
        setContentView(screen);
        IntentFilter changes = new IntentFilter();
        changes.addAction(Intent.ACTION_PACKAGE_ADDED); changes.addAction(Intent.ACTION_PACKAGE_REMOVED);
        changes.addAction(Intent.ACTION_PACKAGE_CHANGED); changes.addDataScheme("package");
        registerReceiver(packages, changes);
        Keeper.start(this);
        loadApps();
    }

    @Override protected void onResume() {
        super.onResume(); resumed = true; Guard.arrived(this); cfg.refresh();
        IntentFilter changes = new IntentFilter(Intent.ACTION_TIME_TICK);
        changes.addAction(Intent.ACTION_TIME_CHANGED); changes.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        changes.addAction(Intent.ACTION_DATE_CHANGED); registerReceiver(time, changes);
        weather.resume(cfg); bins.resume(); tick(); renderApps();
    }
    @Override protected void onPause() {
        resumed = false; unregisterReceiver(time); weather.pause(); bins.pause();
        if (moving != null) stopMoving();
        super.onPause();
    }
    @Override protected void onDestroy() {
        unregisterReceiver(packages); weather.close(); bins.close(); io.shutdownNow(); super.onDestroy();
    }
    @Override protected void onSaveInstanceState(Bundle state) {
        state.putString("focus", lastFocus); state.putString("app", lastApp); super.onSaveInstanceState(state);
    }
    @Override public void onEnterAnimationComplete() { Guard.shown(); }
    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent); if (resumed) restoreFocus();
    }
    @Override public void onBackPressed() {
        if (moving != null) stopMoving();
        else focusApp();
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_MENU) {
            View focused = getCurrentFocus();
            if (focused instanceof Tile) menu((Tile) focused); else openOptions(false);
            return true;
        }
        return super.onKeyDown(code, event);
    }
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (moving == null) return super.dispatchKeyEvent(event);
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) shift(-1);
            else if (event.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) shift(1);
            else stopMoving();
        }
        return true;
    }

    private boolean is24Hour() {
        String mode = cfg.get(Config.CLOCK);
        return "24h".equals(mode) || "auto".equals(mode) && DateFormat.is24HourFormat(this);
    }
    private void tick() { screen.header.update(true, cfg.get("greeting.name", "")); weather.refresh(cfg); }
    private void openOptions(boolean location) {
        initialFocusDone = true;
        lastFocus = location ? "weather" : "settings";
        startActivity(new Intent(this, Options.class).putExtra("weather", location));
    }
    private void openBinOptions() {
        initialFocusDone = true; lastFocus = "reminders";
        startActivity(new Intent(this, Options.class).putExtra("bin_reminders", true));
    }

    private void loadApps() {
        int request = ++generation;
        io.execute(() -> {
            List<Item> items = Catalog.apps(this);
            ui.post(() -> {
                if (isDestroyed() || request != generation) return;
                for (Item fresh : items) for (Item old : all) {
                    if (old.id.equals(fresh.id)) { fresh.icon = old.icon; fresh.banner = old.banner; fresh.artRequested = old.artRequested; }
                }
                all = items; ready = true; renderApps();
            });
        });
    }

    private void renderApps() {
        if (!ready || screen.designScale() <= 0 || isDestroyed()) return;
        if (moving != null) stopMoving();
        List<Item> items = Catalog.visible(cfg, Catalog.APPS, all);
        String restoreControl = lastFocus, restoreApp = lastApp;
        screen.apps.strip.removeAllViews();
        float scale = screen.designScale();
        int width = Math.round(230 * scale), height = Math.round(188 * scale);
        for (Item item : items) {
            Tile tile = new Tile(this, item, Catalog.APPS, Catalog.label(cfg, item), width, height, "always", Ui.TEXT);
            tile.setOnClickListener(v -> launch(tile));
            tile.setOnLongClickListener(v -> { menu(tile); return true; });
            tile.setOnFocusChangeListener((v, has) -> {
                if (has) { lastFocus = item.id; lastApp = item.id; screen.ensureVisible(v); }
            });
            tile.setOnHoverListener((v, event) -> {
                if (event.getActionMasked() == android.view.MotionEvent.ACTION_HOVER_ENTER) screen.ensureVisible(v);
                return false;
            });
            tile.setOnKeyListener((v, code, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (code == KeyEvent.KEYCODE_DPAD_UP) {
                    if (screen.weatherAction.isFocusable()) { screen.weatherAction.requestFocus(); return true; }
                    screen.settings.requestFocus(); return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN) { screen.reminders.focusFirst(); return true; }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(width, height);
            lp.rightMargin = Math.round(18 * scale);
            screen.apps.strip.addView(tile, lp);
            if (!item.artRequested) {
                item.artRequested = true;
                io.execute(() -> {
                    try { Catalog.art(getPackageManager(), item); }
                    catch (RuntimeException ignored) { /* App was removed during lookup. */ }
                    ui.post(() -> { if (!isDestroyed()) tile.invalidate(); });
                });
            }
        }
        screen.appCount.setText(items.size() + (items.size() == 1 ? " favourite" : " favourites"));
        if (items.isEmpty()) {
            TextView add = Ui.text(this, 18, Ui.TEXT, Ui.REGULAR);
            add.setText("+ Add apps"); add.setGravity(Gravity.CENTER); add.setFocusable(true); add.setClickable(true);
            add.setBackground(Ui.cardStates(this, width * 17f / 264, Ui.TEXT));
            add.setOnClickListener(v -> startActivity(new Intent(this, Favorites.class)));
            add.setOnKeyListener((v, code, event) -> {
                if (code == KeyEvent.KEYCODE_DPAD_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
                    if (screen.weatherAction.isFocusable()) { screen.weatherAction.requestFocus(); return true; }
                    screen.settings.requestFocus(); return true;
                }
                if (code == KeyEvent.KEYCODE_DPAD_DOWN && event.getAction() == KeyEvent.ACTION_DOWN) {
                    screen.reminders.focusFirst(); return true;
                }
                return false;
            });
            screen.apps.strip.addView(add, new LinearLayout.LayoutParams(width, Math.round(154 * scale)));
        }
        lastFocus = restoreControl; lastApp = restoreApp;
        restoreFocus();
    }

    private void restoreFocus() {
        if (!resumed || !ready) return;
        if (!initialFocusDone) {
            initialFocusDone = true; lastFocus = null; lastApp = null; focusApp(); return;
        }
        if ("settings".equals(lastFocus)) screen.settings.requestFocus();
        else if ("weather".equals(lastFocus) && screen.weatherAction.isFocusable()) screen.weatherAction.requestFocus();
        else if ("reminders".equals(lastFocus)) screen.reminders.focusFirst();
        else focusApp();
    }
    private void focusApp() {
        ViewGroup strip = screen.apps.strip;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < strip.getChildCount(); i++) {
            if (strip.getChildAt(i) instanceof Tile) ids.add(((Tile) strip.getChildAt(i)).item.id);
        }
        String id = DashboardLogic.focusId(ids, lastApp);
        for (int i = 0; i < strip.getChildCount(); i++) {
            View view = strip.getChildAt(i);
            if (view instanceof Tile && ((Tile) view).item.id.equals(id)) { view.requestFocus(); return; }
        }
        if (strip.getChildCount() > 0) strip.getChildAt(0).requestFocus();
        else screen.settings.requestFocus();
    }
    private void launch(Tile tile) {
        lastApp = tile.item.id; lastFocus = lastApp;
        try {
            if (Guard.AMAZON.equals(lastApp)) Guard.pause();
            startActivity(new Intent(tile.item.intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));
        } catch (ActivityNotFoundException | SecurityException error) {
            Toast.makeText(this, "App is no longer available. Update your favourites in Settings.", Toast.LENGTH_LONG).show();
            loadApps();
        }
    }
    private void menu(Tile tile) {
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle(Catalog.label(cfg, tile.item))
                .setItems(new String[] {"Move", "Rename", "Hide", "App info"}, (dialog, which) -> {
                    if (which == 0) { moving = tile; tile.setMoving(true); }
                    else if (which == 1) rename(tile);
                    else if (which == 2) { Catalog.setShown(cfg, Catalog.APPS, tile.item, false); renderApps(); }
                    else startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", tile.item.id, null)));
                }).setOnDismissListener(dialog -> { if (tile.getParent() != null) tile.requestFocus(); }).show();
    }
    private void rename(Tile tile) {
        EditText input = new EditText(this); input.setSingleLine(); input.setText(Catalog.label(cfg, tile.item));
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("App label").setView(input)
                .setPositiveButton("Save", (d, w) -> { cfg.set("name." + tile.item.id, input.getText().toString().trim()); renderApps(); })
                .setNegativeButton("Cancel", null).show();
    }
    private void shift(int by) {
        LinearLayout strip = screen.apps.strip;
        int from = strip.indexOfChild(moving), to = from + by;
        if (to < 0 || to >= strip.getChildCount()) return;
        ViewGroup.LayoutParams lp = moving.getLayoutParams();
        strip.removeView(moving); strip.addView(moving, to, lp); moving.requestFocus();
    }
    private void stopMoving() {
        moving.setMoving(false); moving = null;
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < screen.apps.strip.getChildCount(); i++) ids.add(((Tile) screen.apps.strip.getChildAt(i)).item.id);
        Catalog.saveOrder(cfg, Catalog.APPS, ids);
    }
}
