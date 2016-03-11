package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.app.MainApplication;
import com.github.axet.audiorecorder.app.Storage;
import com.github.axet.audiorecorder.encoders.Encoder;
import com.github.axet.audiorecorder.encoders.EncoderInfo;
import com.github.axet.audiorecorder.encoders.FileEncoder;
import com.github.axet.audiorecorder.encoders.FormatM4A;
import com.github.axet.audiorecorder.encoders.FormatWAV;
import com.github.axet.audiorecorder.widgets.PitchView;

import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class RecordingActivity extends AppCompatActivity {
    public static final String TAG = RecordingActivity.class.getSimpleName();
    public static final String START_RECORDING = RecordingActivity.class.getCanonicalName() + ".START_RECORDING";
    public static final String CLOSE_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".CLOSE_ACTIVITY";
    public static final int NOTIFICATION_RECORDING_ICON = 0;
    public static String SHOW_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".SHOW_ACTIVITY";

    public static final String PHONE_STATE = "android.intent.action.PHONE_STATE";

    int maximumAltitude;

    PitchView pitch;

    RecordingReceiver receiver;
    PhoneStateChangeListener pscl = new PhoneStateChangeListener();
    Handler handle = new Handler();
    Thread thread;
    short[] buffer;
    FileEncoder encoder;

    TextView title;
    TextView time;
    TextView state;
    ImageButton pause;

    int sampleRate;
    int channelConfig;
    int audioFormat;
    // how many samples count need to update view. 4410 for 100ms update.
    int samplesUpdate;

    File file;

    Runnable progress;

    int soundMode;

    // how many samples passed
    long samples;

    Storage storage;

    public class RecordingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                showRecordingActivity();
            }
            if (intent.getAction().equals(Intent.ACTION_SCREEN_OFF)) {
                // do nothing. do not annoy user. he will see alarm screen on next screen on event.
            }
            if (intent.getAction().equals(SHOW_ACTIVITY)) {
                showRecordingActivity();
            }
        }
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            switch (s) {
                case TelephonyManager.CALL_STATE_RINGING:
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    stopRecording("pause (hold by call)");
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (wasRinging && permitted()) {
                        resumeRecording();
                    }
                    wasRinging = false;
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        pitch = (PitchView) findViewById(R.id.recording_pitch);
        time = (TextView) findViewById(R.id.recording_time);
        state = (TextView) findViewById(R.id.recording_state);
        title = (TextView) findViewById(R.id.recording_title);

        storage = new Storage(this);

        try {
            file = storage.getNewFile();
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        title.setText(file.getName());

        receiver = new RecordingReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(SHOW_ACTIVITY);
        registerReceiver(receiver, filter);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (shared.getBoolean(MainApplication.PREFERENCE_CALL, false)) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE);
        }

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        maximumAltitude = 1000;
        sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));

        if (isEmulator() && Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "Emulator Detected. Reducing Sample Rate to 8000 Hz", Toast.LENGTH_SHORT).show();
            sampleRate = 8000;
        }

        channelConfig = AudioFormat.CHANNEL_IN_MONO;
        audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        updateBufferSize(false);

        addSamples(0);

        View cancel = findViewById(R.id.recording_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelDialog(new Runnable() {
                    @Override
                    public void run() {
                        storage.delete(storage.getTempRecording());
                        finish();
                    }
                });
            }
        });

        pause = (ImageButton) findViewById(R.id.recording_pause);
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (thread != null) {
                    stopRecording("pause");
                } else {
                    if (permitted()) {
                        resumeRecording();
                    }
                }
            }
        });

        View done = findViewById(R.id.recording_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                encoding(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });

        if (permitted()) {
            record();
        }
    }

    boolean isEmulator() {
        return Build.FINGERPRINT.contains("generic");
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    public void showRecordingActivity() {
        Intent intent = new Intent(this, RecordingActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }

    @Override
    protected void onResume() {
        super.onResume();
        pitch.onResume();
        updateBufferSize(false);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pitch.onPause();
        updateBufferSize(true);
    }

    void stopRecording(String status) {
        state.setText(status);
        pause.setImageResource(R.drawable.ic_mic_24dp);

        stopRecording();
    }

    void stopRecording() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        pitch.onPause();
        unsilent();
    }

    @Override
    public void onBackPressed() {
        cancelDialog(new Runnable() {
            @Override
            public void run() {
                RecordingActivity.super.onBackPressed();
            }
        });
    }

    void cancelDialog(final Runnable run) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Confirm cancel");
        builder.setMessage("Are you sure you want to cancel?");
        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                run.run();
            }
        });
        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

    void resumeRecording() {
        if (thread == null) {
            record();
        }
        pitch.onResume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        stopRecording();

        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }

        if (pscl != null) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
            pscl = null;
        }

        showNotificationAlarm(false);
    }

    void record() {
        state.setText("recording");

        showNotificationAlarm(true);

        silent();

        pause.setImageResource(R.drawable.ic_pause_24dp);

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                DataOutputStream os = null;
                AudioRecord recorder = null;
                try {
                    File tmp = storage.getTempRecording();

                    {
                        long ss = tmp.length();
                        if (audioFormat == AudioFormat.ENCODING_PCM_16BIT) {
                            ss = ss / 2;
                        }
                        if (channelConfig == AudioFormat.CHANNEL_IN_STEREO) {
                            ss = ss / 2;
                        }

                        final long s = ss;
                        handle.post(new Runnable() {
                            @Override
                            public void run() {
                                samples = 0;
                                addSamples(s);
                            }
                        });
                    }

                    os = new DataOutputStream(storage.open(tmp));

                    int min = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);
                    if (min <= 0) {
                        throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");
                    }

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, min);
                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        throw new RuntimeException("Unable to initialize AudioRecord");
                    }

                    recorder.startRecording();

                    // AudioRecord eats thread Inetrrupted exception
                    while (!Thread.currentThread().isInterrupted()) {
                        synchronized (thread) {
                            final int readSize = recorder.read(buffer, 0, buffer.length);
                            if (readSize <= 0) {
                                break;
                            }

                            double sum = 0;
                            for (int i = 0; i < readSize; i++) {
                                try {
                                    os.writeShort(buffer[i]);
                                } catch (IOException e) {
                                    throw new RuntimeException(e);
                                }
                                sum += buffer[i] * buffer[i];
                            }

                            final int amplitude = (int) (Math.sqrt(sum / readSize));
                            pitch.add((int) (amplitude / (float) maximumAltitude * 100) + 1);

                            handle.post(new Runnable() {
                                @Override
                                public void run() {
                                    addSamples(channelConfig == AudioFormat.CHANNEL_IN_MONO ? readSize : readSize / 2);
                                }
                            });
                        }
                    }
                } catch (final RuntimeException e) {
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            Log.e(TAG, Log.getStackTraceString(e));
                            Toast.makeText(RecordingActivity.this, "AudioRecord error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                            finish();
                        }
                    });
                } finally {
                    if (os != null) {
                        try {
                            os.close();
                        } catch (IOException ignore) {
                        }
                    }
                    if (recorder != null)
                        recorder.release();
                }
            }
        }, "RecordingThread");
        thread.start();
    }

    void addSamples(long s) {
        samples += s;

        long ms = samples / sampleRate * 1000;

        int diffSeconds = (int) (ms / 1000 % 60);
        int diffMinutes = (int) (ms / (60 * 1000) % 60);
        int diffHours = (int) (ms / (60 * 60 * 1000) % 24);
        int diffDays = (int) (ms / (24 * 60 * 60 * 1000));

        String t = String.format("%02d:%02d", diffMinutes, diffSeconds);

        time.setText(t);
    }

    // alarm dismiss button
    public void showNotificationAlarm(boolean show) {
        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (!show) {
            notificationManager.cancel(NOTIFICATION_RECORDING_ICON);
        } else {
            PendingIntent main = PendingIntent.getBroadcast(this, 0,
                    new Intent(SHOW_ACTIVITY),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            RemoteViews view = new RemoteViews(getPackageName(), R.layout.notifictaion_recording);
            view.setOnClickPendingIntent(R.id.notification_base, main);

            Notification.Builder builder = new Notification.Builder(this)
                    .setOngoing(true)
                    .setContentTitle("Recording")
                    .setSmallIcon(R.drawable.ic_mic_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(Notification.VISIBILITY_PUBLIC);

            notificationManager.notify(NOTIFICATION_RECORDING_ICON, builder.build());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    record();
                } else {
                    Toast.makeText(this, "Not permitted", Toast.LENGTH_SHORT).show();
                    finish();
                }
        }
    }

    public static final String[] PERMISSIONS = new String[]{
            Manifest.permission.RECORD_AUDIO
    };

    boolean permitted(String[] ss) {
        for (String s : ss) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    boolean permitted() {
        for (String s : PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, s) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, PERMISSIONS, 1);
                return false;
            }
        }
        return true;
    }

    void silent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            AudioManager am = (AudioManager) getBaseContext().getSystemService(Context.AUDIO_SERVICE);
            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
            soundMode = am.getRingerMode();
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
    }

    void unsilent() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            AudioManager am = (AudioManager) getBaseContext().getSystemService(Context.AUDIO_SERVICE);
            int soundMode = am.getRingerMode();
            if (soundMode == AudioManager.RINGER_MODE_SILENT) {
                am.setRingerMode(this.soundMode);
                am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
            }
        }
    }

    EncoderInfo getInfo() {
        final int channels = channelConfig == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        final int bps = audioFormat == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

        return new EncoderInfo(channels, sampleRate, bps);
    }

    void encoding(final Runnable run) {
        stopRecording("encoding");

        final File in = storage.getTempRecording();
        final File out = file;

        EncoderInfo info = getInfo();

        Encoder e = null;

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        if (ext.equals("wav")) {
            e = new FormatWAV(info, out);
        }
        if (ext.equals("m4a")) {
            e = new FormatM4A(info, out);
        }

        encoder = new FileEncoder(this, in, e);

        final ProgressDialog d = new ProgressDialog(this);
        d.setTitle("Encoding...");
        d.setMessage(".../" + file.getName());
        d.setMax(100);
        d.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        d.setIndeterminate(false);
        d.show();

        encoder.run(new Runnable() {
            @Override
            public void run() {
                d.setProgress(encoder.getProgress());
            }
        }, new Runnable() {
            @Override
            public void run() {
                d.cancel();
                storage.delete(in);
                run.run();
            }
        }, new Runnable() {
            @Override
            public void run() {
                Toast.makeText(RecordingActivity.this, encoder.getException().getMessage(), Toast.LENGTH_SHORT).show();
                finish();
            }
        });
    }

    // calcuale buffer length dynamically, this way we can reduce thread cycles when activity in background
    // or phone screen is off.
    void updateBufferSize(boolean pause) {
        Thread t = thread;

        if (t == null) {
            t = new Thread();
        }

        synchronized (t) {
            if (pause)
                samplesUpdate = (int) (1000 * sampleRate / 1000.0);
            else
                samplesUpdate = (int) (pitch.getPitchTime() * sampleRate / 1000.0);

            buffer = new short[channelConfig == AudioFormat.CHANNEL_IN_MONO ? samplesUpdate : samplesUpdate * 2];
        }
    }
}
