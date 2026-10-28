package com.limelight.ui.si;

import android.content.Context;
import android.util.TypedValue;

/** Small helpers shared by the Stand-In components. */
public final class Si {
    private Si() {}

    public static int dp(Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }

    public static int color(Context c, int res) {
        return c.getResources().getColor(res, c.getTheme());
    }
}
