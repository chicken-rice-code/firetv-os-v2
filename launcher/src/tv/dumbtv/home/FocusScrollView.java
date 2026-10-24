package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Rect;
import android.widget.ScrollView;

/** Lets focus animation draw into page gutters and scrolls its full vertical envelope into view. */
final class FocusScrollView extends ScrollView {
    FocusScrollView(Context context) {
        super(context);
        setFocusable(false);
        setVerticalScrollBarEnabled(false);
        setClipChildren(false);
        setClipToPadding(false);
    }
    @Override protected int computeScrollDeltaToGetChildRectOnScreen(Rect rect) {
        Rect envelope = new Rect(rect);
        envelope.inset(0, -Ui.dp(getContext(), 8));
        return super.computeScrollDeltaToGetChildRectOnScreen(envelope);
    }
}
