package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Build;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.util.TypedValue;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;

import java.util.LinkedList;
import java.util.List;

public class PitchView extends ViewGroup {
    public static final String TAG = PitchView.class.getSimpleName();

    // pitch delimiter length in dp
    public static final float PITCH_DELIMITER = 1f;
    // pitch length in dp
    public static final float PITCH_WIDTH = 2f;

    // update pitchview in milliseconds
    public static final int UPDATE_SPEED = 10;

    // edit update time
    public static final int EDIT_UPDATE_SPEED = 250;

    // 'pitch length' in milliseconds.
    // in other words how many milliseconds do we need to show whole pitch.
    int pitchTime;

    Paint paint;
    List<Float> data = new LinkedList<>();

    // how many pitches we can fit on screen
    int pitchScreenCount;
    // how many pitches we should fit in memory
    int pitchMemCount;
    // pitch delimiter length in px
    int pitchDlimiter;
    // pitch length in px
    int pitchWidth;
    // pitch length in pn + pitch delimiter length in px
    int pitchSize;

    PitchGraphView graph;
    PitchCurrentView current;

    long time = 0;

    // how many samples were cut from 'data' list
    long samples = 0;

    Runnable edit;
    // index
    int editPos = 0;
    int editCount = 0;
    int playPos = -1;

    Runnable draw;
    Thread thread;

    int pitchColor = 0xff0433AE;
    Paint cutColor = new Paint();
    int bg;

    public class PitchGraphView extends SurfaceView implements SurfaceHolder.Callback {
        SurfaceHolder holder;
        Paint editPaint;
        Paint playPaint;

        public PitchGraphView(Context context) {
            this(context, null);
        }

        public PitchGraphView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PitchGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            editPaint = new Paint();
            editPaint.setColor(Color.BLACK);
            editPaint.setStrokeWidth(pitchWidth);

            playPaint = new Paint();
            playPaint.setColor(Color.BLUE);
            playPaint.setStrokeWidth(pitchWidth / 2);

            getHolder().addCallback(this);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);

            int w = MeasureSpec.getSize(widthMeasureSpec);

            pitchScreenCount = w / pitchSize + 1;

            pitchMemCount = pitchScreenCount + 1;
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        public void draw() {
            float offset = 0;

            if (data.size() >= pitchMemCount) {
                if (time == 0)
                    time = System.currentTimeMillis();

                long cur = System.currentTimeMillis();

                float tick = (cur - time) / (float) pitchTime;

                // force clear queue
                if (data.size() > pitchMemCount + 1) {
                    tick = 0;
                    time = cur;
                    int cut = data.size() - pitchMemCount;
                    data.subList(0, cut).clear();
                    samples += cut;
                }

                if (tick > 1) {
                    if (data.size() > pitchMemCount) {
                        tick -= 1;
                        time += pitchTime;
                    } else if (data.size() == pitchMemCount) {
                        tick = 0;
                        time = cur;
                    }
                    data.subList(0, 1).clear();
                    samples += 1;
                }

                offset = pitchSize * tick;
            }

            draw(offset);
        }

        void draw(float offset) {
            Canvas canvas = holder.lockCanvas(null);

            int m = Math.min(pitchMemCount, data.size());
            canvas.drawColor(bg);

//            if (edit != null) {
//                float x = editPos * pitchSize + pitchSize / 2f;
//                canvas.drawRect(x, 0, getWidth(), getHeight(), bg_cut);
//            }

            for (int i = 0; i < m; i++) {
                float left = data.get(i);
                float right = data.get(i);

                float mid = getHeight() / 2f;

                float x = -offset + i * pitchSize + pitchSize / 2f;

                Paint p = paint;

                if (edit != null && i >= editPos)
                    p = cutColor;

                // left channel pitch
                canvas.drawLine(x, mid, x, mid - mid * left, p);
                // right channel pitch
                canvas.drawLine(x, mid, x, mid + mid * right, p);
            }

            if (edit != null && editCount == 0) {
                float x = editPos * pitchSize + pitchSize / 2f;
                canvas.drawLine(x, 0, x, getHeight(), editPaint);
            }

            if (edit != null && playPos != -1) {
                float x = playPos * pitchSize + pitchSize / 2f;
                canvas.drawLine(x, 0, x, getHeight(), playPaint);
            }

            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            synchronized (PitchView.this) {
                this.holder = holder;
                fit();
                draw(0);
            }
        }

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            synchronized (PitchView.this) {
                this.holder = holder;
                fit();
                draw(0);
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            synchronized (PitchView.this) {
                this.holder = null;
            }
        }
    }

    public class PitchCurrentView extends SurfaceView implements SurfaceHolder.Callback {
        Paint paint;
        SurfaceHolder holder;

        public PitchCurrentView(Context context) {
            this(context, null);
        }

        public PitchCurrentView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PitchCurrentView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            paint = new Paint();
            paint.setColor(pitchColor);
            paint.setStrokeWidth(pitchWidth);

            getHolder().addCallback(this);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int h = Math.min(MeasureSpec.getSize(heightMeasureSpec), dp2px(pitchDlimiter + getPaddingTop() + getPaddingBottom()));

            setMeasuredDimension(w, h);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        public void draw() {
            Canvas canvas = holder.lockCanvas(null);
            canvas.drawColor(bg);

            if (data.size() > 0) {
                int end = data.size() - 1;

                if (edit != null) {
                    end = editPos;
                }

                float left = data.get(end);
                float right = data.get(end);

                float mid = getWidth() / 2f;

                float y = getHeight() / 2f;

                canvas.drawLine(mid, y, mid - mid * left, y, paint);
                canvas.drawLine(mid, y, mid + mid * right, y, paint);
            }

            holder.unlockCanvasAndPost(canvas);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
            synchronized (PitchView.this) {
                this.holder = holder;
                draw();
            }
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            synchronized (PitchView.this) {
                this.holder = holder;
                draw();
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            synchronized (PitchView.this) {
                this.holder = null;
            }
        }
    }

    public PitchView(Context context) {
        this(context, null);
    }

    public PitchView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }

    public PitchView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);

        create();
    }

    void create() {
        pitchDlimiter = dp2px(PITCH_DELIMITER);
        pitchWidth = dp2px(PITCH_WIDTH);
        pitchSize = pitchWidth + pitchDlimiter;

        pitchTime = pitchSize * UPDATE_SPEED;

        bg = getThemeColor(android.R.attr.windowBackground);
        cutColor.setColor(0xff0443BE);//getThemeColor(android.R.attr.textColorPrimaryDisableOnly));

        graph = new PitchGraphView(getContext());
        addView(graph);

        current = new PitchCurrentView(getContext());
        current.setPadding(0, dp2px(5), 0, 0);
        addView(current);

        if (isInEditMode()) {
            for (int i = 0; i < 3000; i++) {
                data.add((float) Math.random());
            }
        }

        paint = new Paint();
        paint.setColor(0xff0433AE);
        paint.setStrokeWidth(pitchWidth);

        time = System.currentTimeMillis();
    }

    public int getMaxPitchCount(int width) {
        int pitchScreenCount = width / pitchSize + 1;

        int pitchMemCount = pitchScreenCount + 1;

        return pitchMemCount;
    }

    public void clear(long s) {
        data.clear();
        samples = s;
        edit = null;
        draw = null;
    }

    public void fit() {
        if (data.size() > pitchMemCount) {
            int cut = data.size() - pitchMemCount;
            data.subList(0, cut).clear();
            samples += cut;
        }
    }

    public void add(int a) {
        data.add(a / 100.0f);
    }

    public void draw() {
        synchronized (this) {
            if (graph.holder != null)
                graph.draw();
            if (current.holder != null)
                current.draw();
        }
    }

    // draw in edit mode
    public void drawEdit() {
        synchronized (this) {
            if (graph.holder != null)
                graph.draw(0);
            if (current.holder != null)
                current.draw();
        }
    }

    public int getPitchTime() {
        return pitchTime;
    }

    int getThemeColor(int id) {
        TypedValue typedValue = new TypedValue();
        Context context = getContext();
        Resources.Theme theme = context.getTheme();
        if (theme.resolveAttribute(id, typedValue, true)) {
            if (Build.VERSION.SDK_INT >= 23)
                return context.getResources().getColor(typedValue.resourceId, theme);
            else
                return context.getResources().getColor(typedValue.resourceId);
        } else {
            return Color.TRANSPARENT;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);

        graph.measure(widthMeasureSpec, heightMeasureSpec);
        current.measure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int gb = graph.getMeasuredHeight() - current.getMeasuredHeight();
        graph.layout(0, 0, graph.getMeasuredWidth(), gb);
        current.layout(0, gb, current.getMeasuredWidth(), gb + current.getMeasuredHeight());
    }

    int dp2px(float dp) {
        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp, getResources().getDisplayMetrics());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        graph.draw(canvas);
        current.draw(canvas);
    }

    public void pause() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        if (edit != null)
            edit = null;
        if (draw != null)
            draw = null;

        drawEdit();
    }

    public long edit(float offset) {
        synchronized (this) {
            if (offset < 0)
                offset = 0;
            editPos = ((int) offset) / pitchSize;

            if (editPos >= pitchScreenCount)
                editPos = pitchScreenCount - 1;

            if (editPos >= data.size())
                editPos = data.size() - 1;

            editCount = 0;
            drawEdit();
        }

        if (draw != null) {
            draw = null;
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
        }
        if (thread == null) {
            edit = new Runnable() {
                @Override
                public void run() {
                    while (!Thread.currentThread().isInterrupted()) {
                        long time = System.currentTimeMillis();
                        drawEdit();

                        editCount++;
                        if (editCount > 1)
                            editCount = 0;

                        long cur = System.currentTimeMillis();

                        long delay = EDIT_UPDATE_SPEED - (cur - time);

                        if (delay > 0) {
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            };
            thread = new Thread(edit, TAG);
            thread.start();
        }

        return samples + editPos;
    }

    public void resume() {
        if (edit != null) {
            edit = null;
            if (thread != null) {
                thread.interrupt();
                thread = null;
            }
        }
        if (thread == null) {
            draw = new Runnable() {
                @Override
                public void run() {
                    time = System.currentTimeMillis();
                    while (!Thread.currentThread().isInterrupted()) {
                        long time = System.currentTimeMillis();
                        draw();
                        long cur = System.currentTimeMillis();

                        long delay = UPDATE_SPEED - (cur - time);

                        if (delay > 0) {
                            try {
                                Thread.sleep(delay);
                            } catch (InterruptedException e) {
                                Thread.currentThread().interrupt();
                                return;
                            }
                        }
                    }
                }
            };
            thread = new Thread(draw, TAG);
            thread.start();
        }
    }

    public void play(long pos) {
        synchronized (this) {
            playPos = (int) (pos - samples);

            if (playPos < 0)
                playPos = -1;

            drawEdit();
        }
    }
}
