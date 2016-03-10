package com.github.axet.audiorecorder.animations;

import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.Transformation;

public class StepAnimation extends Animation {

    interface LateCreator {
        public StepAnimation create();
    }

    public static void apply(LateCreator c, View v, boolean expand, boolean animate) {
        Animation old = v.getAnimation();
        if (old != null && old instanceof MarginAnimation) {
            MarginAnimation m = (MarginAnimation) old;

            long cur = AnimationUtils.currentAnimationTimeMillis();
            long past = cur - m.getStartTime() - m.getStartOffset();
            long left = m.getDuration() - past;
            long offset = cur - m.getStartTime() - left;

            if (animate) {
                if (m.hasEnded()) {
                    StepAnimation mm = c.create();
                    v.startAnimation(c.create());
                } else {
                    if (m.expand != expand) {
                        m.expand = expand;
                        m.setStartOffset(offset);
                    } else {
                        // keep rolling do nothing
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
                v.startAnimation(mm);
            } else {
                mm.restore();
                mm.end();
            }
        }
    }

    public StepAnimation() {
    }

    @Override
    public void initialize(int width, int height, int parentWidth, int parentHeight) {
        super.initialize(width, height, parentWidth, parentHeight);
    }

    void calc(float i) {
    }

    void restore() {
    }

    void end() {
    }

    @Override
    protected void applyTransformation(float interpolatedTime, Transformation t) {
        super.applyTransformation(interpolatedTime, t);

        if (interpolatedTime < 1) {
            float i = interpolatedTime;
            calc(i);
        } else {
            restore();
            end();
        }
    }
}