package com.github.axet.audiorecorder.widgets;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
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
    // pitch delimiter length in dp
    public static final float PITCH_DELIMITER = 1f;
    // pitch length in dp
    public static final float PITCH_WIDTH = 2f;

    // update pitchview in milliseconds
    public static final int UPDATE_SPEED = 10;
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

    public class PitchGraphView extends SurfaceView implements SurfaceHolder.Callback {
        Thread thread;
        Runnable draw;

        public PitchGraphView(Context context) {
            this(context, null);
        }

        public PitchGraphView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PitchGraphView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            getHolder().addCallback(this);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        }

        @Override
        public void surfaceCreated(final SurfaceHolder holder) {
            draw = new Runnable() {
                @Override
                public void run() {
                    time = System.currentTimeMillis();
                    while (!Thread.currentThread().isInterrupted()) {
                        long time = System.currentTimeMillis();
                        Canvas canvas = holder.lockCanvas(null);
                        if (canvas == null)
                            return;
                        drawHolder(canvas);
                        holder.unlockCanvasAndPost(canvas);
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

            start();
        }

        void start() {
            thread = new Thread(draw, "PitchView");
            thread.start();
        }

        void drawHolder(Canvas canvas) {
            synchronized (data) {
                canvas.drawColor(Color.WHITE);

                int m = Math.min(pitchMemCount, data.size());

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
                        data.subList(0, data.size() - pitchMemCount).clear();
                        m = Math.min(pitchMemCount, data.size());
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
                        m = Math.min(pitchMemCount, data.size());
                    }

                    offset = pitchSize * tick;
                }

                for (int i = 0; i < m; i++) {
                    float left = data.get(i);
                    float right = data.get(i);

                    float mid = getHeight() / 2f;

                    float x = -offset + i * pitchSize + pitchSize / 2f;

                    canvas.drawLine(x, mid, x, mid - mid * left, paint);
                    canvas.drawLine(x, mid, x, mid + mid * right, paint);
                }
            }
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        }
    }

    public class PitchCurrentView extends View {
        Paint paint;

        public PitchCurrentView(Context context) {
            this(context, null);
        }

        public PitchCurrentView(Context context, AttributeSet attrs) {
            this(context, attrs, 0);
        }

        public PitchCurrentView(Context context, AttributeSet attrs, int defStyleAttr) {
            super(context, attrs, defStyleAttr);

            paint = new Paint();
            paint.setColor(0xff0433AE);
            paint.setStrokeWidth(pitchDlimiter);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int w = MeasureSpec.getSize(widthMeasureSpec);
            int h = Math.min(MeasureSpec.getSize(heightMeasureSpec), dp2px(pitchDlimiter + getPaddingTop() + getPaddingBottom()));

            pitchScreenCount = w / pitchSize + 1;

            pitchMemCount = pitchScreenCount + 1;

            setMeasuredDimension(w, h);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (data.size() == 0)
                return;

            int end = data.size() - 1;

            float left = data.get(end);
            float right = data.get(end);

            float mid = getWidth() / 2f;

            float y = getHeight() / 2f;

            canvas.drawLine(mid, y, mid - mid * left, y, paint);
            canvas.drawLine(mid, y, mid + mid * right, y, paint);
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
    }

    public void add(int a) {
        synchronized (data) {
            data.add(a / 100.0f);

            current.postInvalidate();
        }
    }

    public int getPitchTime() {
        return pitchTime;
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

    public void onPause() {
        if (graph.thread != null) {
            graph.thread.interrupt();
            graph.thread = null;
        }
    }

    public void onResume() {
        if (graph.thread == null && graph.draw != null) {
            graph.start();
        }
    }
}
