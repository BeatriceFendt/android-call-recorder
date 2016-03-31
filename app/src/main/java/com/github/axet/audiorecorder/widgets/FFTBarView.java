package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.github.axet.audiorecorder.app.RawSamples;

public class FFTBarView extends View {
    public static final String TAG = FFTBarView.class.getSimpleName();

    Paint paint;
    short[] buffer;

    int barCount;
    float barWidth;
    float barDeli;

    int max;

    Paint textPaint;
    Rect textBounds;

    public FFTBarView(Context context) {
        this(context, null);
    }

    public FFTBarView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FFTBarView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        create();
    }

    void create() {
        paint = new Paint();
        paint.setColor(0xff0433AE);
        paint.setStrokeWidth(dp2px(1));

        textBounds = new Rect();

        textPaint = new Paint();
        textPaint.setColor(Color.GRAY);
        textPaint.setAntiAlias(true);
        textPaint.setTextSize(20f);

        if (isInEditMode()) {
            //buffer = simple();
            buffer = RawSamples.generateSound(16000, 4000, 100);
            buffer = RawSamples.fft(buffer, 0, buffer.length);
        }
    }

    public void setBuffer(short[] buf) {
        buffer = RawSamples.fft(buf, 0, buf.length);

        max = Integer.MIN_VALUE;
        for (int i = 0; i < buffer.length; i++) {
            max = Math.max(buffer[i], max);
        }
    }

    short[] simple() {
        int sampleRate = 1000;
        int count = sampleRate;
        short[] samples = new short[count];
        for (int i = 0; i < count; i++) {
            double x = i / (double) sampleRate;
            double y = 0;
            y += 0.9 * Math.sin(50 * 2 * Math.PI * x);
            y += 0.5 * Math.sin(80 * 2 * Math.PI * x);
            y += 0.7 * Math.sin(40 * 2 * Math.PI * x);
            samples[i] = (short) (y / 2.1 * 0x7fff);
        }
        return samples;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        // set initial width
        int w = dp2px(15);
        int d = dp2px(4);
        int s = w + d;

        int mw = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();

        // get count of bars and delimeters
        int dc = (mw - w) / s;
        int bc = dc + 1;

        // get rate
        float k = w / d;

        // get one part of (bar+del) size
        float e = mw / (bc * k + dc);

        barCount = bc;
        barWidth = e * k;
        barDeli = e;
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    int dp2px(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDraw(Canvas canvas) {
        if (barCount == 0)
            return;

        int h = getHeight() - getPaddingTop() - getPaddingBottom();

        float left = getPaddingLeft();

        for (int i = 0; i < barCount; i++) {
            double max = 0;

            if (buffer != null) {
                int step = buffer.length / barCount;
                int offset = i * step;
                int end = Math.min(offset + step, buffer.length);
                for (int k = offset; k < end; k++) {
                    short s = buffer[k];
                    max = Math.max(max, s);
                }
            }

            float y = getPaddingTop() + h - h * ((float) max / 0x7fff) - dp2px(1);

            if (y < getPaddingTop())
                y = getPaddingTop();

            canvas.drawRect(left, y, left + barWidth, getPaddingTop() + h, paint);
            left += barWidth + barDeli;
        }
    }

}
