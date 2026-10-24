package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.KeyEvent;
import android.view.View;
import android.widget.FrameLayout;
import java.util.List;

/** Four focusable collection tiles arranged in one row. */
final class RemindersRow extends FrameLayout {
    private static final String[] TYPES = {"General", "Recycling", "Food"};
    private final BinTile[] tiles = new BinTile[TYPES.length];
    private final int[] displayOrder = {0, 1, 2};
    private final Paint dividers = new Paint(Paint.ANTI_ALIAS_FLAG);
    private Runnable open;
    private Runnable up;
    private View.OnFocusChangeListener rowFocus;
    private View.OnFocusChangeListener tileFocus;
    private View.OnHoverListener tileHover;

    RemindersRow(Context context) {
        super(context);
        setFocusable(true);
        setClickable(false);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
        setContentDescription("Bin collection");
        for (int i = 0; i < TYPES.length; i++) {
            tiles[i] = new BinTile(context, i);
            addView(tiles[i], new FrameLayout.LayoutParams(0, 0));
        }
    }

    void onOpen(Runnable action) { open = action; }
    void onUp(Runnable action) { up = action; }
    void onTileFocusChange(View.OnFocusChangeListener listener) { tileFocus = listener; }
    void onTileHover(View.OnHoverListener listener) { tileHover = listener; }
    void onRowFocusChange(View.OnFocusChangeListener listener) { rowFocus = listener; }

    boolean focusFirst() { return requestFocus(); }

    void update(String heading, String description, List<BinReminder.Collection> upcoming) {
        BinReminder.Collection[] next = new BinReminder.Collection[TYPES.length];
        if (upcoming != null) for (BinReminder.Collection item : upcoming) {
            int index = indexFor(item.type);
            if (index >= 0 && next[index] == null) next[index] = item;
        }
        sortByNextDate(next);
        for (int i = 0; i < tiles.length; i++) tiles[i].setCollection(next[i]);
        StringBuilder summary = new StringBuilder("Bin collection. ");
        for (int position = 0; position < TYPES.length; position++) {
            int index = displayOrder[position];
            if (position > 0) summary.append(". ");
            summary.append(TYPES[index]).append(". ")
                    .append(next[index] == null ? "Date unavailable" : next[index].detail());
        }
        setContentDescription(summary.toString());
    }

    private int indexFor(String type) {
        String lower = type.toLowerCase(java.util.Locale.ROOT);
        if (lower.startsWith("general") || lower.startsWith("brown") || lower.startsWith("refuse")) return 0;
        if (lower.startsWith("recycling") || lower.startsWith("green")) return 1;
        if (lower.startsWith("food")) return 2;
        return -1;
    }

    private void sortByNextDate(BinReminder.Collection[] next) {
        for (int i = 0; i < displayOrder.length; i++) displayOrder[i] = i;
        for (int i = 0; i < displayOrder.length; i++) for (int j = i + 1; j < displayOrder.length; j++) {
            int left = displayOrder[i], right = displayOrder[j];
            BinReminder.Collection a = next[left], b = next[right];
            boolean swap = a == null && b != null;
            if (a != null && b != null) {
                int byDate = a.date.compareTo(b.date);
                swap = byDate > 0 || byDate == 0 && TYPES[left].compareTo(TYPES[right]) > 0;
            }
            if (swap) { displayOrder[i] = right; displayOrder[j] = left; }
        }
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int slot = getWidth() / tiles.length;
        for (int position = 0; position < tiles.length; position++) {
            int left = position * slot;
            int right = position == tiles.length - 1 ? getWidth() : left + slot;
            BinTile tile = tiles[displayOrder[position]];
            tile.measure(MeasureSpec.makeMeasureSpec(right - left, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(getHeight(), MeasureSpec.EXACTLY));
            tile.layout(left, 0, right, getHeight());
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float sx = getWidth() / 852f, sy = getHeight() / 106f;
        canvas.save(); canvas.scale(sx, sy);
        dividers.setColor(Ui.DIVIDER);
        dividers.setStrokeWidth(1.5f);
        canvas.drawLine(0, 1, 852, 1, dividers);
        canvas.drawLine(0, 105, 852, 105, dividers);
        canvas.drawLine(284, 12, 284, 94, dividers);
        canvas.drawLine(568, 12, 568, 94, dividers);
        canvas.restore();
    }

    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous);
        invalidate();
        if (rowFocus != null) rowFocus.onFocusChange(this, focused);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (code == KeyEvent.KEYCODE_DPAD_UP && up != null) { up.run(); return true; }
        if (code == KeyEvent.KEYCODE_DPAD_DOWN) return true;
        return super.onKeyDown(code, event);
    }

    private final class BinTile extends View {
        private final int index;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Drawable icon;
        private BinReminder.Collection collection;

        BinTile(Context context, int index) {
            super(context);
            this.index = index;
            icon = context.getDrawable(R.drawable.material_bin);
            setFocusable(false);
            setClickable(false);
            setContentDescription(TYPES[index] + " bin. Collection date unavailable.");
            setOnHoverListener((view, event) -> tileHover != null && tileHover.onHover(view, event));
        }

        void setCollection(BinReminder.Collection value) {
            collection = value;
            String detail = value == null ? "Date unavailable" : value.detail();
            setContentDescription(TYPES[index] + " bin. " + detail + ". Select to change the saved address.");
            invalidate();
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float sy = getHeight() / 106f;
            float sx = getWidth() / (284f * sy);
            canvas.save(); canvas.scale(sx, sy);
            int colour = colour(index);
            icon.setTint(colour);
            icon.setBounds(32, 30, 80, 78);
            icon.draw(canvas);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextAlign(Paint.Align.LEFT);
            paint.setTypeface(Ui.MEDIUM);
            paint.setTextSize(20);
            paint.setColor(Ui.TEXT);
            canvas.drawText(TYPES[index], 91, 43, paint);
            paint.setTypeface(Ui.REGULAR);
            paint.setTextSize(15);
            paint.setColor(collection == null ? Ui.FAINT : Ui.MUTED);
            String date = collection == null ? "Date unavailable" : collection.weekday + ", " + pretty(collection.date);
            canvas.drawText(date, 91, 73, paint);
            if (collection != null && collection.subscriptionRequired) {
                paint.setTextSize(12);
                paint.setColor(Ui.FAINT);
                canvas.drawText("Subscription required", 91, 91, paint);
            }
            canvas.restore();
        }

        private int colour(int i) {
            if (i == 0) return 0xFF9A6547;
            if (i == 1) return 0xFF39A875;
            if (i == 2) return 0xFFAAB2B6;
            return Ui.MUTED;
        }
    }

    private static String pretty(String date) {
        try {
            java.text.SimpleDateFormat input = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.UK);
            input.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));
            java.text.SimpleDateFormat output = new java.text.SimpleDateFormat("d MMM", java.util.Locale.UK);
            output.setTimeZone(java.util.TimeZone.getTimeZone("Europe/London"));
            return output.format(input.parse(date));
        } catch (Exception ignored) { return date; }
    }
}
