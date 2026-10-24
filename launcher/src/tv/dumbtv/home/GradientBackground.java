package tv.dumbtv.home;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** A still, layered gradient that adds soft color blooms and a quiet vignette. */
final class GradientBackground extends Drawable {
    private final int[] colors;
    private final String direction;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    private int alpha = 255;
    private ColorFilter colorFilter;
    private Shader base, upperGlow, lowerGlow, vignette;

    GradientBackground(int[] colors, String direction) {
        this.colors = colors.clone();
        this.direction = direction;
    }

    @Override
    protected void onBoundsChange(Rect bounds) {
        float left = bounds.left, top = bounds.top;
        float right = bounds.right, bottom = bounds.bottom;
        float width = Math.max(1, bounds.width());
        float height = Math.max(1, bounds.height());
        float x0 = left, y0 = top, x1 = right, y1 = bottom;
        if ("reverse".equals(direction)) {
            x0 = right;
            y0 = top;
            x1 = left;
            y1 = bottom;
        } else if ("vertical".equals(direction)) {
            x1 = left;
            y1 = bottom;
        } else if ("horizontal".equals(direction)) {
            y1 = top;
        }
        base = new LinearGradient(x0, y0, x1, y1, colors, null, Shader.TileMode.CLAMP);

        float radius = Math.max(width, height);
        int bright = lift(colors[0], 54, 72, 106);
        int soft = lift(colors[Math.min(1, colors.length - 1)], 30, 48, 76);
        upperGlow = new RadialGradient(left + width * 0.76f, top + height * 0.12f,
                radius * 0.92f,
                new int[] {alpha(bright, 0x50), alpha(bright, 0x1A), Color.TRANSPARENT},
                new float[] {0f, 0.48f, 1f}, Shader.TileMode.CLAMP);
        lowerGlow = new RadialGradient(left + width * 0.12f, top + height * 0.88f,
                radius * 0.72f,
                new int[] {alpha(soft, 0x26), alpha(soft, 0x0C), Color.TRANSPARENT},
                new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        vignette = new RadialGradient(left + width * 0.5f, top + height * 0.48f,
                radius * 0.76f,
                new int[] {Color.TRANSPARENT, 0x10020814, 0x46020814},
                new float[] {0f, 0.66f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override
    public void draw(Canvas canvas) {
        Rect bounds = getBounds();
        if (bounds.isEmpty() || base == null) return;
        paint.setAlpha(alpha);
        paint.setColorFilter(colorFilter);
        drawLayer(canvas, bounds, base);
        drawLayer(canvas, bounds, upperGlow);
        drawLayer(canvas, bounds, lowerGlow);
        drawLayer(canvas, bounds, vignette);
        paint.setShader(null);
    }

    private void drawLayer(Canvas canvas, Rect bounds, Shader shader) {
        paint.setShader(shader);
        canvas.drawRect(bounds, paint);
    }

    private static int lift(int color, int red, int green, int blue) {
        return Color.rgb(Math.min(255, Color.red(color) + red),
                Math.min(255, Color.green(color) + green),
                Math.min(255, Color.blue(color) + blue));
    }

    private static int alpha(int color, int value) {
        return (color & 0x00FFFFFF) | (value << 24);
    }

    @Override
    public void setAlpha(int value) {
        alpha = value;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter filter) {
        colorFilter = filter;
        invalidateSelf();
    }

    @Override
    public int getOpacity() {
        return PixelFormat.OPAQUE;
    }
}