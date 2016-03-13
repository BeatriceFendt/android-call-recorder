package com.github.axet.audiorecorder.encoders;

import android.content.Context;
import android.media.AudioFormat;
import android.os.Handler;
import android.util.Log;
import android.widget.Toast;

import com.github.axet.audiorecorder.activities.RecordingActivity;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class FileEncoder {
    Context context;
    Handler handler;

    File in;
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
                        // temporary recording use global settings for encoding format.
                        // take 1000 samples at once.
                        byte[] buf = new byte[(RecordingActivity.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1) * 1000];

                        int len = is.read(buf);
                        if (len <= 0) {
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
                } catch (IOException e) {
                    t = e;
                    handler.post(error);
                } finally {
                    encoder.close();
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignore) {
                        }
                    }
                }
            }
        });
        thread.start();
    }

    long getSamples(long samples) {
        return samples / (RecordingActivity.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1);
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
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
    }
}
