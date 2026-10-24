package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.view.View;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF5F4F0;
    static final int MUTED = 0xFF98A3AA;
    static final int FAINT = 0xFF6F7B83;
    static final int SURFACE = 0xFF202629;
    static final int SURFACE_FOCUSED = 0xFF293135;
    static final int CARD_BORDER = 0xFF282E32;
    static final int DIVIDER = 0xFF282E32;
    static final int BLUE = 0xFF9EDCFF;
    static final float CARD_RADIUS = 8f;
    static final float CONTROL_RADIUS = 8f;
    static final float TYPE_SECTION = 13f;
    static final float TYPE_BODY = 14f;
    static final float TYPE_TITLE = 22f;
    static final float TYPE_DISPLAY = 34f;
    static final float DASHBOARD_DISPLAY = 72f;
    static final int DIALOG = android.R.style.Theme_DeviceDefault_Dialog_Alert;
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface REGULAR = Typeface.create("sans-serif", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);
    static final Typeface BOLD = Typeface.create("sans-serif", Typeface.BOLD);

    // Shared spacing rhythm for page gutters, cards, controls, and small gaps.
    static final int SPACE_XXS = 2;
    static final int SPACE_XS = 4;
    static final int SPACE_SM = 8;
    static final int SPACE_MD = 12;
    static final int SPACE_LG = 16;
    static final int SPACE_XL = 24;
    static final int PAGE_GUTTER = 48;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Drawable gradient(int[] colors, String direction) {
        return new GradientBackground(colors, direction);
    }
    static GradientDrawable menuSurface(Context c, float radius) {
        GradientDrawable d = round(SURFACE, radius);
        d.setCornerRadius(radius);
        d.setStroke(dp(c, 1), CARD_BORDER);
        return d;
    }

    static GradientDrawable glass(Context c, float radius) {
        return round(SURFACE, radius);
    }
    static GradientDrawable glassOutline(Context c, float radius, int accent) {
        GradientDrawable d = round(SURFACE_FOCUSED, radius);
        d.setStroke(dp(c, 1.5f), TEXT);
        return d;
    }

    static StateListDrawable cardStates(Context c, float radius, int accent) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, glassOutline(c, radius, accent));
        bg.addState(new int[] {android.R.attr.state_hovered}, glassOutline(c, radius, accent));
        bg.addState(new int[] {android.R.attr.state_pressed}, glassOutline(c, radius, accent));
        bg.addState(new int[0], glass(c, radius));
        return bg;
    }

    /** Black on light accents, white on dark ones. */
    static int ink(int color) {
        return Color.luminance(color) > 0.5f ? 0xFF000000 : 0xFFFFFFFF;
    }

    static TextView text(Context c, float sp, int color, Typeface face) {
        TextView v = new TextView(c);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        v.setTextColor(color);
        v.setTypeface(face);
        v.setIncludeFontPadding(false);
        return v;
    }

    static TextView sectionLabel(Context c, String title) {
        TextView v = text(c, TYPE_SECTION, TEXT, MEDIUM);
        v.setText(title);
        return v;
    }

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    static void animateFocus(View view, boolean active) {
        view.animate().cancel();
        view.animate().scaleX(active ? 1.04f : 1f).scaleY(active ? 1.04f : 1f)
                .setDuration(160).start();
    }

    static void hoverScale(View view) {
        view.setOnHoverListener((v, event) -> {
            int action = event.getActionMasked();
            if (action == android.view.MotionEvent.ACTION_HOVER_ENTER
                    || action == android.view.MotionEvent.ACTION_HOVER_EXIT) {
                animateFocus(v, v.isFocused() || action == android.view.MotionEvent.ACTION_HOVER_ENTER);
            }
            return false;
        });
    }

    /** A white outline and a slightly lighter surface mark remote focus. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        v.setBackground(cardStates(v.getContext(), radius, accent));
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {android.R.attr.state_hovered},
                        {android.R.attr.state_pressed}, {}},
                new int[] {TEXT, TEXT, TEXT, MUTED}));
    }

}
