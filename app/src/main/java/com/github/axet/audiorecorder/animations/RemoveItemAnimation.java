package com.github.axet.audiorecorder.animations;

import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;
import android.widget.ListView;

public class RemoveItemAnimation extends Animation {
    ListView list;
    View convertView;
    Runnable run;
    int h;
    int w;
    ViewGroup.LayoutParams lp;
    ViewGroup.LayoutParams lpOrig;
    float mPivotX, mPivotY;

    int duration = 500;

    int alphaOffset = 0;
    int alphaDuration = (int) (duration * 0.6);

    int scaleOffset = alphaDuration;
    int scaleDuration = (int) (duration * 0.4);

    Handler handler;

    public static void apply(final ListView list, final View v, final Runnable run) {
        v.startAnimation(new RemoveItemAnimation(list, v, run));
    }

    public static void restore(final View v) {
        Animation a = v.getAnimation();
        if (a != null && a instanceof RemoveItemAnimation) {
            v.clearAnimation();

            RemoveItemAnimation m = (RemoveItemAnimation) a;
            m.restore();
        }
    }

    public RemoveItemAnimation(ListView list, View v, Runnable run) {
        this.convertView = v;
        this.list = list;
        this.run = run;

        handler = new Handler();

        setInterpolator(new AccelerateInterpolator());

        setDuration(duration);

        this.h = v.getHeight();
        this.w = v.getWidth();
        this.lp = v.getLayoutParams();
        lpOrig = new ViewGroup.LayoutParams(lp);
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);

        convertView.setVisibility(View.VISIBLE);

        mPivotX = resolveSize(RELATIVE_TO_SELF, 0.5f, width, parentWidth);
        mPivotY = resolveSize(RELATIVE_TO_SELF, 0.5f, height, parentHeight);
    }

    float intTime(int off, int dur) {
        long cur = AnimationUtils.currentAnimationTimeMillis();
        long start = getStartTime();

        if (start == -1)
            start = cur;

        long past = cur - start;
        long wait = off - past;
        long left = dur + wait;

        if (wait > 0)
            return -1;

        if (left < 0)
            return 1f;
        else
            return getInterpolator().getInterpolation((dur - left) / (float) dur);
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        super.applyTransformation(interpolatedTime, t);

        {
            float ia = intTime(alphaOffset, alphaDuration);
            if (ia >= 0) {
                t.setAlpha(1 - ia);
                t.getMatrix().setTranslate(w * ia, 0);
            }
            float is = intTime(scaleOffset, scaleDuration);
            if (is >= 0) {
                is = 1 - is;
                t.getMatrix().setScale(is, is, mPivotX, 0);
                lp.height = (int) (h * is);
                convertView.requestLayout();
            }
        }

        if (interpolatedTime >= 1) {
            end();
        }
    }

    void restore() {
        lp.height = lpOrig.height;
        convertView.setVisibility(View.VISIBLE);
        convertView.requestLayout();
    }

    void end() {
        convertView.setVisibility(View.GONE);
        handler.post(run);
    }
}
