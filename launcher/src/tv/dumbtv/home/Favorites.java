package tv.dumbtv.home;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;

/** Remote-friendly selection and ordering, using only the installed app catalogue. */
public final class Favorites extends Activity {
    private Config cfg;
    private LinearLayout list;
    private List<Item> all;
    private String selected;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state); cfg = Config.get(this);
        FocusScrollView scroll = new FocusScrollView(this); scroll.setBackgroundColor(0xFF080A0C);
        scroll.setVerticalScrollBarEnabled(false); scroll.setFocusable(false);
        list = new LinearLayout(this); list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false); list.setClipToPadding(false);
        list.setPadding(Ui.dp(this, 54), Ui.dp(this, 36), Ui.dp(this, 54), Ui.dp(this, 36));
        scroll.addView(list); setContentView(scroll); render();
    }
    private void render() {
        all = Catalog.apps(this); list.removeAllViews();
        TextView title = Ui.text(this, 28, Ui.TEXT, Ui.REGULAR); title.setText("Favourite apps"); list.addView(title);
        TextView hint = Ui.text(this, 13, Ui.MUTED, Ui.REGULAR);
        hint.setText("Select an app to move or remove it. Your order is saved automatically.");
        hint.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 20)); list.addView(hint);
        TextView add = action("+ Choose apps", this::choose);
        List<Item> visible = Catalog.visible(cfg, Catalog.APPS, all);
        TextView focus = add;
        for (int i = 0; i < visible.size(); i++) {
            Item item = visible.get(i);
            TextView row = action((i + 1) + ".  " + Catalog.label(cfg, item), () -> edit(item));
            if (item.id.equals(selected)) focus = row;
        }
        focus.requestFocus();
    }
    private TextView action(String title, Runnable run) {
        TextView row = Ui.text(this, 16, Ui.TEXT, Ui.REGULAR); row.setText(title);
        row.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        row.setFocusable(true); row.setClickable(true);
        row.setBackground(Ui.cardStates(this, Ui.dp(this, 8), Ui.TEXT));
        row.setOnFocusChangeListener((v, has) -> Ui.animateFocus(v, has || v.isHovered()));
        Ui.hoverScale(row);
        row.setOnClickListener(v -> run.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 12); list.addView(row, lp); return row;
    }
    private void choose() {
        CharSequence[] names = new CharSequence[all.size()]; boolean[] checked = new boolean[all.size()];
        for (int i = 0; i < all.size(); i++) { names[i] = Catalog.label(cfg, all.get(i)); checked[i] = Catalog.shown(cfg, Catalog.APPS, all.get(i)); }
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle("Apps on your home screen")
                .setMultiChoiceItems(names, checked, (d, which, on) -> Catalog.setShown(cfg, Catalog.APPS, all.get(which), on))
                .setPositiveButton("Done", (d, w) -> render()).setOnCancelListener(d -> render()).show();
    }
    private void edit(Item item) {
        selected = item.id;
        new AlertDialog.Builder(this, Ui.DIALOG).setTitle(Catalog.label(cfg, item))
                .setItems(new String[] {"Move earlier", "Move later", "Remove favourite"}, (dialog, which) -> {
                    List<Item> items = Catalog.visible(cfg, Catalog.APPS, all);
                    int from = items.indexOf(item);
                    if (which == 2) Catalog.setShown(cfg, Catalog.APPS, item, false);
                    else {
                        int to = from + (which == 0 ? -1 : 1);
                        if (from >= 0 && to >= 0 && to < items.size()) {
                            items.remove(from); items.add(to, item);
                            List<String> ids = new ArrayList<>(); for (Item it : items) ids.add(it.id);
                            Catalog.saveOrder(cfg, Catalog.APPS, ids);
                        }
                    }
                    render();
                }).show();
    }
}
