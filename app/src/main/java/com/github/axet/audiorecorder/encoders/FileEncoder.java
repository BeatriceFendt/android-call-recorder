package com.github.axet.audiorecorder.encoders;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FileEncoder {
    Context context;
    Handler handler;

    File in;
    EncoderInfo info;
    Encoder encoder;
    Thread thread;
    long samples;
    long cur;
    Runnable progress;
    Runnable done;
    Throwable t;

    public FileEncoder(Context context, File in, Encoder encoder) {
        this.context = context;
        this.in = in;
        this.encoder = encoder;
        this.info = encoder.getInfo();

        handler = new Handler();
    }

    public void run(final Runnable progress, final Runnable done, final Runnable error) {
        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                samples = getSamples(in.length());

                cur = 0;

                FileInputStream is = null;
                try {
                    is = new FileInputStream(in);

                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] buf = new byte[info.channels * info.bps / 8 * 100];

                        int len = is.read(buf);
                        if (len <= 0) {
                            Log.d("23", "end");
                            handler.post(done);
                            return;
                        } else {
                            short[] shorts = new short[len / 2];
                            ByteBuffer.wrap(buf, 0, len).order(ByteOrder.BIG_ENDIAN).asShortBuffer().get(shorts);
                            encoder.encode(shorts);
                            handler.post(progress);
                            synchronized (thread) {
                                cur += getSamples(len);
                            }
                        }
                    }
                    Log.d("23", "interrupted " + Thread.currentThread().isInterrupted());
                } catch (IOException e) {
                    Log.d("23", "error " + e.getMessage());
                    t = e;
                    handler.post(error);
                } finally {
                    Log.d("23", "close");
                    encoder.close();
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }
        });
        thread.start();
    }

    long getSamples(long samples) {
        samples = samples / (info.bps / 8);
        samples = samples / info.channels;
        return samples;
    }

    public int getProgress() {
        synchronized (thread) {
            return (int) (cur * 100 / samples);
        }
    }

    public Throwable getException() {
        return t;
    }

    public void close() {
        thread.interrupt();
    }
}
