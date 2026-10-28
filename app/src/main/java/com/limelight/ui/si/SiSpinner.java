package com.limelight.ui.si;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import com.limelight.R;

/** Spinner (DESIGN.md): a 2px accent arc, sized by its layout (20, inline 12, hero 48). */
public class SiSpinner extends View {
    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();
    private int color;

    public SiSpinner(Context context) {
        this(context, null);
    }

    public SiSpinner(Context context, AttributeSet attrs) {
        super(context, attrs);
        float stroke = Si.dp(context, 2);
        color = Si.color(context, R.color.si_accent);
        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeWidth(stroke);
        arc.setStrokeCap(Paint.Cap.ROUND);
        arc.setColor(color);
        track.setStyle(Paint.Style.STROKE);
        track.setStrokeWidth(stroke);
        track.setColor(Si.color(context, R.color.si_raised));
    }

    public void setColor(int c) {
        color = c;
        arc.setColor(c);
        invalidate();
    }

    /** For spinners drawn on accent (inside a primary button): arc color and track color (0 = none). */
    public void setColors(int arcColor, int trackColor) {
        setColor(arcColor);
        track.setColor(trackColor);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float inset = arc.getStrokeWidth() / 2f + 0.5f;
        oval.set(inset, inset, getWidth() - inset, getHeight() - inset);
        canvas.drawOval(oval, track);
        long t = System.currentTimeMillis();
        float start = (t % 900L) / 900f * 360f;
        canvas.drawArc(oval, start, 90f, false, arc);
        if (getVisibility() == VISIBLE && isAttachedToWindow()) {
            postInvalidateOnAnimation();
        }
    }
}
