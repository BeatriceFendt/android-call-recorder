package com.github.axet.audiorecorder.animations;

import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;

/**
 * Animation Tranformation.getMatrix() has no issues. But animation properies does. Here are few
 * issues  about animations properties.
 * <p/>
 * First:
 * <p/>
 * If your animation touches some elements, then hide them (GONE), some properties may not be
 * recalculated because element is hidden.
 * <p/>
 * Here is no solution. You have to restore all properties of evey view which may be affected by
 * running animation to its initial state.
 * <p/>
 * If you dont you may see routated (setRotate) or hidded (setAlpha) elements even if endAnimation()
 * restore thier initial states (do setRotate(0) or setApha(1) do not make them not rotated or
 * visible).
 * <p/>
 * Second:
 * <p/>
 * On normal run we have onAnimationEnd() is not final call applyTransformation() called after that.
 * <p/>
 * applyTransformation()
 * onAnimationEnd()
 * applyTransformation()
 * <p/>
 * On animation cancel we have:
 * applyTransformation()
 * onAnimationEnd()
 * <p/>
 * Which makes unpredictable where do we have to finish animation with initial values on top of first
 * statement.
 */
public class MarginAnimation extends StepAnimation {

    View view;
    ViewGroup.MarginLayoutParams viewLp;
    ViewGroup.MarginLayoutParams viewLpOrig;
    int marginSlide;
    boolean expand;

    public static void apply(final View v, final boolean expand, boolean animate) {
        apply(new LateCreator() {
            @Override
            public MarginAnimation create() {
                return new MarginAnimation(v, expand);
            }
        }, v, expand, animate);
    }

    public MarginAnimation(View v, boolean expand) {
        this.expand = expand;

        setDuration(500);

        view = v;
        viewLp = (ViewGroup.MarginLayoutParams) view.getLayoutParams();
        viewLpOrig = new ViewGroup.MarginLayoutParams(viewLp);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
        view.setVisibility(View.VISIBLE);

        int h;
        int w;

        if (height == 0 && parentHeight == 0)
            h = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        else
            h = View.MeasureSpec.makeMeasureSpec(Math.max(height, parentHeight), View.MeasureSpec.AT_MOST);

        w = View.MeasureSpec.makeMeasureSpec(Math.max(width, parentWidth), View.MeasureSpec.AT_MOST);

        view.measure(w, h);
        marginSlide = view.getMeasuredHeight() + viewLpOrig.bottomMargin;
    }

    void calc(float i) {
        i = expand ? i : 1 - i;

        viewLp.topMargin = (int) (viewLpOrig.topMargin * i - marginSlide * (1 - i));
        view.requestLayout();
    }

    void restore() {
        viewLp.topMargin = viewLpOrig.topMargin;
    }

    void end() {
        view.setVisibility(expand ? View.VISIBLE : View.GONE);
        view.requestLayout();
    }

}