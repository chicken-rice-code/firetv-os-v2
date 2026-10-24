package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.media.tv.TvInputManager;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
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
        page.setBackgroundColor(0xFF080A0C);
        FocusScrollView scroll = new FocusScrollView(this);
        scroll.setFocusable(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackgroundColor(0x00000000);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        list.setClipToPadding(false);
        list.setPadding(dp(Ui.PAGE_GUTTER), dp(Ui.PAGE_GUTTER), dp(Ui.PAGE_GUTTER), dp(Ui.PAGE_GUTTER));
        scroll.addView(list);

        TextView title = Ui.text(this, Ui.TYPE_DISPLAY, Ui.TEXT, Ui.LIGHT);
        title.setText("Settings");
        title.setPadding(dp(Ui.SPACE_MD), 0, 0, dp(Ui.SPACE_XS));
        list.addView(title);

        TextView subtitle = Ui.text(this, Ui.TYPE_BODY, Ui.MUTED, Ui.REGULAR);
        subtitle.setText("Select a tile to change a setting or open a screen.");
        subtitle.setPadding(dp(Ui.SPACE_MD), 0, 0, dp(Ui.SPACE_LG));
        list.addView(subtitle);
        list.addView(section("YOUR APPS"));
        addTileGrid(list, action("Choose and reorder favourites", () ->
                startActivity(new android.content.Intent(this, Favorites.class))));

        list.addView(section("TV AND TIME"));
        addTileGrid(list, choice(Config.SWITCH), action("TV settings", this::tvSettings),
                action("Open a TV input", this::openInput));

        list.addView(section("WEATHER"));
        View location = weatherLocation();
        addTileGrid(list, location, choice(Config.UNITS), choice(Config.CLOCK));
        if (getIntent().getBooleanExtra("weather", false)) location.post(() -> location.performClick());

        list.addView(section("HOME SCREEN"));
        addTileGrid(list,
                greetingName(),
                action("Reset order, names and hidden tiles", this::reset),
                action("Open Amazon's home screen", () -> {
                    Guard.amazon(this);
                    finish();
                }));
        list.addView(section("ABOUT"));
        addTileGrid(list, action("Open source licences", this::licences));
        TextView about = Ui.text(this, 13, Ui.FAINT, Ui.MEDIUM);
        about.setLineSpacing(0, 1.4f);
        about.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_XL), 0, 0);
        about.setText("Home Together\nWeather by Open-Meteo \u00B7 open-meteo.com \u00B7 CC BY 4.0"
                + "\nPersonal, non-commercial use. Your chosen city and network address are sent to Open-Meteo."
                + "\nDevice location is not read. Last successful weather is cached for offline use."
                + "\nIcons: Google Material Symbols \u00B7 Apache License 2.0");
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
            value.setText(focused ? "\u2039  " + text + "  \u203A" : text);
            value.setTextColor(focused ? Ui.TEXT : Ui.MUTED);
            boolean off = c == Config.SWITCH && "off".equals(cfg.get(Config.INPUTS));
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
    private View greetingName() {
        LinearLayout tile = row();
        tile.setOrientation(LinearLayout.VERTICAL);
        TextView label = Ui.text(this, 12, Ui.MUTED, Ui.MEDIUM);
        label.setText("Greeting name");
        TextView value = Ui.text(this, 17, Ui.TEXT, Ui.MEDIUM);
        value.setMaxLines(2);
        Runnable show = () -> {
            String name = DashboardLogic.greetingName(cfg.get("greeting.name", ""));
            value.setText(name.isEmpty() ? "Not set" : name);
        };
        tile.addView(label);
        LinearLayout.LayoutParams valueLp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, WRAP);
        valueLp.topMargin = dp(Ui.SPACE_XS);
        tile.addView(value, valueLp);
        tile.setOnClickListener(v -> {
            EditText input = new EditText(this);
            input.setSingleLine(true);
            input.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(80)});
            input.setText(cfg.get("greeting.name", ""));
            input.setSelection(input.length());
            new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Greeting name")
                    .setMessage("Enter the name to use on the home screen. Leave blank for a greeting without a name.")
                    .setView(input)
                    .setPositiveButton("Save", (d, which) -> {
                        cfg.set("greeting.name", DashboardLogic.greetingName(input.getText().toString()));
                        show.run();
                    }).setNegativeButton("Cancel", null).show();
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
        value.setText(cfg.weatherLocation().isEmpty() ? "Set weather location" : cfg.weatherLocation());
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
                    .setMessage("Enter a town or city, with country if needed. Leave blank to turn weather off.")
                    .setView(input)
                    .setPositiveButton("Save", (d, which) -> {
                        String place = input.getText().toString().trim();
                        cfg.set("weather.location", place.isEmpty() ? "off" : place);
                        value.setText(cfg.weatherLocation().isEmpty() ? "Set weather location" : cfg.weatherLocation());
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
        Ui.hoverScale(tile);
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
            rowLp.bottomMargin = dp(Ui.SPACE_MD);
            parent.addView(row, rowLp);
            for (int column = 0; column < 2 && i + column < tiles.length; column++) {
                LinearLayout.LayoutParams tileLp = new LinearLayout.LayoutParams(
                        0, dp(84), 1f);
                if (column == 0 && i + 1 < tiles.length) tileLp.rightMargin = dp(Ui.SPACE_SM);
                else if (column == 1) tileLp.leftMargin = dp(Ui.SPACE_SM);
                row.addView(tiles[i + column], tileLp);
            }
        }
    }
    private View section(String text) {
        TextView label = Ui.sectionLabel(this, text);
        label.setPadding(dp(Ui.SPACE_MD), dp(Ui.SPACE_LG), 0, dp(Ui.SPACE_XS));
        return label;
    }

    private void style(View row) {
        row.setBackground(Ui.cardStates(this, dp(Ui.CARD_RADIUS), cfg.accent()));
        Ui.animateFocus(row, row.isFocused() || row.isHovered());
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

    private void tvSettings() {
        android.content.Intent tv = new android.content.Intent(android.content.Intent.ACTION_MAIN)
                .addCategory("android.intent.category.LEANBACK_SETTINGS");
        try { startActivity(tv.resolveActivity(getPackageManager()) == null
                ? new android.content.Intent(android.provider.Settings.ACTION_SETTINGS) : tv); }
        catch (android.content.ActivityNotFoundException | SecurityException error) {
            android.widget.Toast.makeText(this, "TV settings are unavailable", android.widget.Toast.LENGTH_SHORT).show();
        }
    }

    private void licences() {
        StringBuilder text = new StringBuilder("Google Material Symbols\nCopyright Google LLC\n\n");
        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(
                getResources().openRawResource(R.raw.material_icons_license), java.nio.charset.StandardCharsets.UTF_8))) {
            for (String line; (line = reader.readLine()) != null;) text.append(line).append('\n');
        } catch (java.io.IOException error) { text.append("Apache License 2.0: https://www.apache.org/licenses/LICENSE-2.0"); }
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Open source licences")
                .setMessage(text.toString()).setPositiveButton("Close", null).show();
    }

    private void openInput() {
        List<Item> inputs = Catalog.inputs(this, (TvInputManager) getSystemService(Context.TV_INPUT_SERVICE));
        if (inputs.isEmpty()) {
            android.widget.Toast.makeText(this, "No TV inputs are available on this device", android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        CharSequence[] labels = new CharSequence[inputs.size()];
        for (int i = 0; i < inputs.size(); i++) labels[i] = Catalog.label(cfg, inputs.get(i));
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("TV inputs").setItems(labels, (d, which) -> {
            Item item = inputs.get(which);
            android.content.Intent intent = Input.intent(this, cfg, item, Catalog.label(cfg, item));
            try { startActivity(intent == null ? item.intent : intent); }
            catch (android.content.ActivityNotFoundException | SecurityException error) {
                android.widget.Toast.makeText(this, "That input is unavailable", android.widget.Toast.LENGTH_SHORT).show();
            }
        }).show();
    }

    private int dp(float v) {
        return Ui.dp(this, v);
    }
}
