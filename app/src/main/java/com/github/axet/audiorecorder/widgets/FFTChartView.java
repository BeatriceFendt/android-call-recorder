package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.View;

import com.github.axet.audiorecorder.app.RawSamples;

public class FFTChartView extends View {
    public static final String TAG = FFTChartView.class.getSimpleName();

    Paint paint;
    short[] buffer;

    Paint textPaint;
    Rect textBounds;

    public FFTChartView(Context context) {
        this(context, null);
    }

    public FFTChartView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public FFTChartView(Context context, AttributeSet attrs, int defStyleAttr) {
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
            buffer = simple();
            //buffer = RawSamples.generateSound(16000, 4000, 100);
            //buffer = RawSamples.fft(buffer, 0, buffer.length);
        }
    }

    public void setBuffer(short[] buf) {
        buffer = RawSamples.fft(buf, 0, buf.length);
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
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
    }

    int dp2px(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    public void onDraw(Canvas canvas) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;

        for (int i = 0; i < buffer.length; i++) {
            min = Math.min(buffer[i], min);
            max = Math.max(buffer[i], max);
        }

        int h = getHeight();

        if (min < 0) {
            h = h / 2;
        }

        float startX = 0, startY = h;

        float step = canvas.getWidth() / (float) buffer.length;

        for (int i = 0; i < buffer.length; i++) {
            float endX = startX;
            float endY = h - h * (buffer[i] / (float) 0x7fff);

            canvas.drawLine(startX, startY, endX, endY, paint);

            startX = endX + step;
            startY = endY;
        }

        String tMin = "" + min;
        canvas.drawText(tMin, 0, getHeight(), textPaint);

        String tMax = "" + max;
        textPaint.getTextBounds(tMax, 0, tMax.length(), textBounds);
        canvas.drawText("" + max, getWidth() - textBounds.width(), getHeight(), textPaint);
    }

}
