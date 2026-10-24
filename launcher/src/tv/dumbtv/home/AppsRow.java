package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Rect;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;

/** One remote-driven strip. Padding reserves the complete focus animation envelope. */
final class AppsRow extends HorizontalScrollView {
    final LinearLayout strip;
    AppsRow(Context c) {
        super(c);
        setFocusable(false);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipToPadding(false);
        setClipChildren(false);
        strip = new LinearLayout(c);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        addView(strip, new FrameLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));
    }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        int target = DashboardLogic.scrollTarget(strip.getWidth(), getWidth(), rect.centerX());
        return target - getScrollX();
    }
}
