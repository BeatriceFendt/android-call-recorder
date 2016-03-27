package com.github.axet.audiorecorder.animations;

import android.util.Log;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;

public class StepAnimation extends Animation {
    View view;
    boolean expand;

    public interface LateCreator {
        StepAnimation create();
    }

    public static void apply(LateCreator c, View v, boolean expand, boolean animate) {
        Animation old = v.getAnimation();
        if (old != null && old instanceof StepAnimation) {
            StepAnimation m = (StepAnimation) old;

            long cur = AnimationUtils.currentAnimationTimeMillis();
            long past = cur - m.getStartTime() - m.getStartOffset();
            long left = m.getDuration() - past;
            long offset = cur - m.getStartTime() - left;

            if (animate) {
                if (m.hasEnded()) {
                    StepAnimation mm = c.create();
                    if (mm.animationReady()) {
                        mm.startAnimation(v);
                    } else {
                        // do nothing, already visible view
                    }
                } else {
                    if (m.expand != expand) {
                        m.expand = expand;
                        m.setStartOffset(offset);
                    } else {
                        // keep rolling. do nothing
                    }
                }
            } else {
                if (!m.hasEnded()) {
                    v.clearAnimation();
                    m.restore();
                }
                StepAnimation mm = c.create();
                mm.restore();
                mm.end();
            }
        } else {
            StepAnimation mm = c.create();
            if (animate) {
                if (mm.animationReady()) {
                    mm.startAnimation(v);
                } else {
                    // do nothing. already visible
                }
            } else {
                mm.restore();
                mm.end();
            }
        }
    }

    public StepAnimation(View view, boolean expand) {
        this.view = view;
        this.expand = expand;
    }

    // start animation only if view in proper visible state. gone for expanding, and visible for collapsing.
    public boolean animationReady() {
        return (expand && view.getVisibility() == View.GONE) || (!expand && view.getVisibility() == View.VISIBLE);
    }

    public void startAnimation(View v) {
        init();
        // do first step to hide view (we animation does it).
        //
        // but some old androids API does not start animation on 0dp views.
        calc(0.01f, new Transformation());
        v.startAnimation(this);
    }

    public void init() {
        // animation does not start on older API if inital state of view is hidden.
        // show view here.
        view.setVisibility(View.VISIBLE);
    }

    public void calc(float i, Transformation t) {
    }

    public void restore() {
    }

    public void end() {
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        super.applyTransformation(interpolatedTime, t);

        calc(interpolatedTime, t);

        if (interpolatedTime >= 1) {
            restore();
            end();
        }
    }
}