package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.StateListDrawable;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** TV friendly, focusable setting tiles that save changes immediately. */
public final class Options extends Activity {
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private final List<Runnable> refreshers = new ArrayList<>();
    private Config cfg;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        cfg = Config.get(this);

        FrameLayout page = new FrameLayout(this);
        page.setBackground(Ui.gradient(cfg.gradientColors(), cfg.get(Config.GRADIENT_DIRECTION)));
        refreshers.add(() -> page.setBackground(Ui.gradient(cfg.gradientColors(), cfg.get(Config.GRADIENT_DIRECTION))));
        ScrollView scroll = new ScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0x00000000);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(Ui.PAGE_GUTTER), dp(Ui.PAGE_GUTTER), dp(Ui.PAGE_GUTTER), dp(Ui.PAGE_GUTTER));
        scroll.addView(list);

        TextView title = Ui.text(this, 34, Ui.TEXT, Ui.LIGHT);
        title.setText("Options");
        title.setPadding(dp(Ui.SPACE_MD), 0, 0, dp(Ui.SPACE_XS));
        list.addView(title);

        TextView subtitle = Ui.text(this, 15, Ui.MUTED, Ui.REGULAR);
        subtitle.setText("Select a tile to change a setting or open a screen.");
        subtitle.setPadding(dp(Ui.SPACE_MD), 0, 0, dp(Ui.SPACE_LG));
        list.addView(subtitle);
        list.addView(section("DISPLAY"));
        addTileGrid(list,
                choice(Config.ACCENT), choice(Config.TILES), choice(Config.LAYOUT), choice(Config.LABELS));

        list.addView(section("TV AND TIME"));
        addTileGrid(list, choice(Config.CLOCK), choice(Config.INPUTS), choice(Config.SWITCH));

        list.addView(section("BACKGROUND"));
        addTileGrid(list, choice(Config.BACKGROUND), choice(Config.GRADIENT),
                choice(Config.GRADIENT_DIRECTION), choice(Config.DIM), choice(Config.ROTATE),
                action("Next wallpaper", () -> {
                    cfg.nextWallpaper();
                    refresh();
                }));

        list.addView(section("WEATHER"));
        addTileGrid(list, weatherLocation());

        list.addView(section("HOME SCREEN"));
        addTileGrid(list,
                action("Apps on home screen", () -> pick(Catalog.APPS)),
                action("Inputs on home screen", () -> pick(Catalog.INPUTS)),
                action("Reset order, names and hidden tiles", this::reset),
                action("Open Amazon's home screen", () -> {
                    Guard.amazon(this);
                    finish();
                }));
        list.addView(section("ABOUT"));
        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.MEDIUM);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_XL), 0, 0);
        about.setText("Settings file: " + cfg.dir + "/" + Config.FILE
                + "\nOne wallpaper: name it exactly wallpaper.jpg (or .png) and put it in that folder"
                + "\nSeveral wallpapers: put them in " + Config.WALLPAPERS + "/ with any names, and pick a rotation"
                + "\nChoose Background > Gradient, then select a Gradient style. Image file and adaptive wallpaper tint are also available"
                + "\nAdaptive wallpaper tint shows graphite until you add a wallpaper"
                + "\nWeather via Open-Meteo. Your chosen city is sent to its API; device location is not read.");
        list.addView(about);

        page.addView(scroll, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        setContentView(page);
        refresh();
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg.refresh();
        refresh();
    }

    private void refresh() {
        for (Runnable r : refreshers) r.run();
    }

    private View choice(Config.Choice c) {
        LinearLayout tile = row();
        tile.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, 12, Ui.MUTED, Ui.MEDIUM);
        name.setText(c.title);
        TextView value = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        value.setMaxLines(2);
        value.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
        valueLp.topMargin = dp(Ui.SPACE_XS);
        tile.addView(name);
        tile.addView(value, valueLp);
        tile.setOnClickListener(v -> step(c, 1));
        Runnable show = () -> {
            boolean focused = tile.isFocused();
            String text = c.name(cfg.get(c));
            if (c == Config.ACCENT) {
                SpannableString swatch = new SpannableString("\u25CF  " + text);
                swatch.setSpan(new ForegroundColorSpan(cfg.accent()), 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                value.setText(focused ? "\u2039  " : "");
                value.append(swatch);
                if (focused) value.append("  \u203A");
            } else {
                value.setText(focused ? "\u2039  " + text + "  \u203A" : text);
            }
            value.setTextColor(focused ? Ui.TEXT : Ui.MUTED);
            String background = cfg.get(Config.BACKGROUND);
            boolean off = ((c == Config.DIM || c == Config.ROTATE)
                    && !"image".equals(background)
                    && !"adaptive-tint".equals(background))
                    || (c == Config.GRADIENT || c == Config.GRADIENT_DIRECTION) && !"gradient".equals(background)
                    || c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS));
            tile.setAlpha(off ? 0.4f : 1f);
        };
        tile.setOnFocusChangeListener((v, has) -> {
            show.run();
            style(tile);
        });
        refreshers.add(show);
        refreshers.add(() -> style(tile));
        return tile;
    }
    private View weatherLocation() {
        LinearLayout tile = row();
        tile.setOrientation(LinearLayout.VERTICAL);
        TextView name = Ui.text(this, 12, Ui.MUTED, Ui.MEDIUM);
        name.setText("Weather location");
        TextView value = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        value.setText(cfg.weatherLocation().isEmpty() ? "Off" : cfg.weatherLocation());
        tile.addView(name);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
        valueLp.topMargin = dp(Ui.SPACE_XS);
        tile.addView(value, valueLp);
        tile.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setText(cfg.weatherLocation());
            input.setSelection(input.length());
            new AlertDialog.Builder(this, Ui.DIALOG)
                    .setTitle("Weather location")
                    .setMessage("Enter a city or town; leave blank for Off")
                    .setView(input)
                    .setPositiveButton("Save", (d, which) -> {
                        String place = input.getText().toString().trim();
                        cfg.set("weather.location", place.isEmpty() ? "off" : place);
                        value.setText(cfg.weatherLocation().isEmpty() ? "Off" : cfg.weatherLocation());
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });
        refreshers.add(() -> style(tile));
        return tile;
    }
    private boolean step(Config.Choice c, int by) {
        int i = Arrays.asList(c.values).indexOf(cfg.get(c));
        int n = c.values.length;
        cfg.set(c.key, c.values[((i + by) % n + n) % n]);
        refresh();
        return true;
    }

    private View action(String text, Runnable run) {
        LinearLayout tile = row();
        tile.setOrientation(LinearLayout.HORIZONTAL);
        TextView title = Ui.text(this, 15, Ui.TEXT, Ui.MEDIUM);
        title.setText(text);
        title.setMaxLines(2);
        tile.addView(title, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView chevron = Ui.text(this, 20, Ui.MUTED, Ui.REGULAR);
        chevron.setText("\u203A");
        tile.addView(chevron);
        tile.setOnClickListener(v -> run.run());
        refreshers.add(() -> style(tile));
        return tile;
    }

    private LinearLayout row() {
        LinearLayout tile = new LinearLayout(this);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setOnFocusChangeListener((v, has) -> style(tile));
        tile.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_SM), dp(Ui.SPACE_MD), dp(Ui.SPACE_SM));
        return tile;
    }

    private void addTileGrid(LinearLayout parent, View... tiles) {
        for (int i = 0; i < tiles.length; i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setClipChildren(false);
            row.setClipToPadding(false);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
            rowLp.bottomMargin = dp(Ui.SPACE_XS);
            parent.addView(row, rowLp);
            for (int column = 0; column < 2 && i + column < tiles.length; column++) {
                LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(
                        0, dp(84), 1f);
                if (column == 0) tileLp.rightMargin = dp(Ui.SPACE_XS);
                else tileLp.leftMargin = dp(Ui.SPACE_XS);
                row.addView(tiles[i + column], tileLp);
            }
        }
    }
    private View section(String text) {
        TextView label = Ui.text(this, 11, Ui.MUTED, Ui.MEDIUM);
        label.setText(text);
        label.setLetterSpacing(0.18f);
        label.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_LG), 0, dp(Ui.SPACE_XS));
        return label;
    }

    private void style(View row) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, Ui.glassOutline(this, dp(14), cfg.accent()));
        bg.addState(new int[] {android.R.attr.state_hovered}, Ui.glassOutline(this, dp(14), cfg.accent()));
        bg.addState(new int[0], Ui.glass(this, dp(14)));
        row.setBackground(bg);
    }

    private void pick(String kind) {
        List<Item> items = Catalog.APPS.equals(kind)
                ? Catalog.apps(this)
                : Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        CharSequence[] names = new CharSequence[items.size()];
        boolean[] on = new boolean[items.size()];
        for (int i = 0; i < items.size(); i++) {
            names[i] = Catalog.label(cfg, items.get(i));
            on[i] = Catalog.shown(cfg, kind, items.get(i));
        }
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle(Catalog.APPS.equals(kind) ? "Apps on home screen" : "Inputs on home screen")
                .setMultiChoiceItems(names, on, (d, which, checked) ->
                        Catalog.setShown(cfg, kind, items.get(which), checked))
                .setPositiveButton("Done", null)
                .show();
    }

    private void reset() {
        new AlertDialog.Builder(this, Ui.DIALOG)
                .setTitle("Reset the home screen layout?")
                .setMessage("Order, names and hidden tiles go back to default. Colors and sizes stay.")
                .setPositiveButton("Reset", (d, w) -> cfg.removePrefix("name.", "apps.", "inputs."))
                .setNegativeButton("Cancel", null)
                .show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
