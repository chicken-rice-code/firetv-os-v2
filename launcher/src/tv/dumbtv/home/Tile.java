package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.MotionEvent;
import android.view.View;

/** Installed app artwork above a real, accessible app label. No recreated brand wordmarks. */
final class Tile extends View {
    final Item item;
    final String kind;
    private final CharSequence title;
    private final int cardW, cardH;
    private final float artH, radius, unit;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextPaint text = new TextPaint(Paint.ANTI_ALIAS_FLAG);
    private final Path clip = new Path();
    private final RectF art = new RectF();
    private boolean moving, hovered;

    Tile(Context c, Item item, String kind, CharSequence title, int width, int height, String labels, int accent) {
        super(c); this.item = item; this.kind = kind; this.title = title; cardW = width; cardH = height;
        unit = width / 264f; artH = 154 * unit; radius = 17 * unit;
        art.set(0, 0, width, artH); clip.addRoundRect(art, radius, radius, Path.Direction.CW);
        text.setTypeface(Ui.REGULAR); text.setTextAlign(Paint.Align.CENTER); text.setTextSize(24 * unit);
        setFocusable(true); setClickable(true); setLongClickable(true); setContentDescription(title);
        setId(View.generateViewId());
    }
    void setMoving(boolean value) { moving = value; invalidate(); }
    @Override protected void onMeasure(int w, int h) { setMeasuredDimension(cardW, cardH); }
    @Override protected void onFocusChanged(boolean focused, int direction, Rect previous) {
        super.onFocusChanged(focused, direction, previous); Ui.animateFocus(this, focused || hovered); invalidate();
    }
    @Override public boolean onHoverEvent(MotionEvent e) {
        boolean active = e.getActionMasked() != MotionEvent.ACTION_HOVER_EXIT;
        if (hovered != active) { hovered = active; Ui.animateFocus(this, active || isFocused()); invalidate(); }
        return super.onHoverEvent(e);
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        boolean active = isFocused() || hovered || moving;
        canvas.save(); canvas.clipPath(clip);
        paint.setStyle(Paint.Style.FILL); paint.setColor(active ? Ui.SURFACE_FOCUSED : Ui.SURFACE);
        canvas.drawRect(art, paint);
        Drawable artwork = item.banner != null ? item.banner : item.icon;
        if (artwork != null) {
            float maxW = item.banner != null ? cardW : 82 * unit;
            float maxH = item.banner != null ? artH : 82 * unit;
            int iw = Math.max(1, artwork.getIntrinsicWidth()), ih = Math.max(1, artwork.getIntrinsicHeight());
            float fit = Math.min(maxW / iw, maxH / ih);
            int w = Math.round(iw * fit), h = Math.round(ih * fit);
            int left = (cardW - w) / 2, top = Math.round((artH - h) / 2);
            artwork.setBounds(left, top, left + w, top + h); artwork.draw(canvas);
        }
        if (moving) canvas.drawColor(0x77000000);
        canvas.restore();
        paint.setStyle(Paint.Style.STROKE);
        if (active) {
            paint.setStrokeWidth(4 * unit); paint.setColor(0xFF080A0C);
            canvas.drawRoundRect(5 * unit, 5 * unit, cardW - 5 * unit, artH - 5 * unit,
                    radius - 3 * unit, radius - 3 * unit, paint);
        }
        float stroke = (active ? 3 : 1) * unit;
        paint.setStrokeWidth(stroke); paint.setColor(active ? Ui.TEXT : Ui.CARD_BORDER);
        paint.setPathEffect(moving ? new DashPathEffect(new float[] {8 * unit, 5 * unit}, 0) : null);
        canvas.drawRoundRect(stroke / 2, stroke / 2, cardW - stroke / 2, artH - stroke / 2,
                radius, radius, paint);
        paint.setPathEffect(null);
        text.setColor(active ? Ui.TEXT : Ui.MUTED);
        CharSequence label = TextUtils.ellipsize(title, text, cardW - 8 * unit, TextUtils.TruncateAt.END);
        canvas.drawText(label, 0, label.length(), cardW / 2f, 203 * unit, text);
    }
}
