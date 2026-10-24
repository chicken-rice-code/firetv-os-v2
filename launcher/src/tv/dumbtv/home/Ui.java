package tv.dumbtv.home;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.util.TypedValue;
import android.widget.TextView;

final class Ui {
    static final int TEXT = 0xFFF2F2F3;
    static final int MUTED = 0x99FFFFFF;
    static final int FAINT = 0x66FFFFFF;
    static final int SURFACE = 0xFF1C1D21;
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
    static final int PAGE_GUTTER = 40;

    private Ui() {}

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    static Drawable gradient(int[] colors, String direction) {
        return new GradientBackground(colors, direction);
    }
    static GradientDrawable menuSurface(Context c, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xF51D2B3E, 0xF5142031});
        d.setCornerRadius(radius);
        d.setStroke(dp(c, 1), 0x66FFFFFF);
        return d;
    }

    static GradientDrawable glass(Context c, float radius) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x42FFFFFF, 0x18FFFFFF, 0x10FFFFFF});
        d.setCornerRadius(radius);
        d.setStroke(dp(c, 1), 0x40FFFFFF);
        return d;
    }
    static GradientDrawable glassOutline(Context c, float radius, int accent) {
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x38FFFFFF, 0x16FFFFFF});
        d.setCornerRadius(radius);
        d.setStroke(dp(c, 2), (accent & 0x00FFFFFF) | 0xD0000000);
        return d;
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

    static GradientDrawable round(int color, float radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        return d;
    }

    /** Accent fill when focused, a faint fill otherwise. */
    static void focusStyle(TextView v, int accent, int idle, float radius) {
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, round(accent, radius));
        bg.addState(new int[] {android.R.attr.state_hovered}, glassOutline(v.getContext(), radius, accent));
        bg.addState(new int[0], glass(v.getContext(), radius));
        v.setBackground(bg);
        v.setTextColor(new ColorStateList(
                new int[][] {{android.R.attr.state_focused}, {android.R.attr.state_hovered}, {}},
                new int[] {ink(accent), TEXT, 0xD9FFFFFF}));
    }

}
