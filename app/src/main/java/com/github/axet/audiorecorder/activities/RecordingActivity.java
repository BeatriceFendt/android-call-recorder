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
import android.os.*;
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
import com.github.axet.audiorecorder.encoders.Format3GP;
import com.github.axet.audiorecorder.encoders.FormatM4A;
import com.github.axet.audiorecorder.encoders.FormatWAV;
import com.github.axet.audiorecorder.widgets.PitchView;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;

public class RecordingActivity extends AppCompatActivity {
    public static int AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT;
    public static int CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO;
    public static int MAXIMUM_ALTITUDE = 5000;

    public static final String TAG = RecordingActivity.class.getSimpleName();
    public static final String START_RECORDING = RecordingActivity.class.getCanonicalName() + ".START_RECORDING";
    public static final String CLOSE_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".CLOSE_ACTIVITY";
    public static final int NOTIFICATION_RECORDING_ICON = 0;
    public static String SHOW_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".SHOW_ACTIVITY";

    public static final String PHONE_STATE = "android.intent.action.PHONE_STATE";

    RecordingReceiver receiver;
    PhoneStateChangeListener pscl = new PhoneStateChangeListener();
    Handler handle = new Handler();
    FileEncoder encoder;

    Thread thread;
    // dynamic buffer size. big for backgound recording. small for realtime view updates.
    Integer bufferSize = 0;
    // variable from settings. how may samples per second.
    int sampleRate;
    // how many samples count need to update view. 4410 for 100ms update.
    int samplesUpdate;
    // output target file 2016-01-01 01.01.01.wav
    File targetFile;

    TextView title;
    TextView time;
    TextView state;
    ImageButton pause;
    PitchView pitch;

    Runnable progress;

    int soundMode;

    Storage storage;

    public class RecordingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_SCREEN_ON)) {
                // showRecordingActivity();
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
            targetFile = storage.getNewFile();
        } catch (RuntimeException e) {
            Toast.makeText(this, e.getMessage(), Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        title.setText(targetFile.getName());

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

        sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));

        if (isEmulator() && Build.VERSION.SDK_INT < 23) {
            Toast.makeText(this, "Emulator Detected. Reducing Sample Rate to 8000 Hz", Toast.LENGTH_SHORT).show();
            sampleRate = 8000;
        }

        updateBufferSize(false);

        updateSamples(getSamples(storage.getTempRecording().length()));

        View cancel = findViewById(R.id.recording_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelDialog(new Runnable() {
                    @Override
                    public void run() {
                        stopRecording();
                        storage.delete(storage.getTempRecording());
                        //storage.delete(targetFile);
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
                    if (permitted(PERMISSIONS)) {
                        resumeRecording();
                    }
                }
            }
        });

        View done = findViewById(R.id.recording_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                stopRecording("encoding");
                encoding(new Runnable() {
                    @Override
                    public void run() {
                        finish();
                    }
                });
            }
        });
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

        // start once
        if (thread == null) {
            if (permitted()) {
                record();
            }
        }

        Log.d(TAG, "onResume");
        updateBufferSize(false);
        pitch.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        updateBufferSize(true);
        pitch.pause();
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
        pitch.pause();
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
        pitch.resume();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

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
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                int p = android.os.Process.getThreadPriority(android.os.Process.myTid());

                if (p != android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
                    Log.e(TAG, "Unable to set Thread Priority " + android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                }

                // how many samples passed
                long samplesTime;

                DataOutputStream os = null;
                AudioRecord recorder = null;
                try {
                    File tmp = storage.getTempRecording();
                    samplesTime = getSamples(tmp.length());

                    os = new DataOutputStream(new BufferedOutputStream(storage.open(tmp)));

                    int min = AudioRecord.getMinBufferSize(sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT);
                    if (min <= 0) {
                        throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");
                    }

                    // make it 5 seconds buffer
                    int min2 = 5 * sampleRate
                            * (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 2 : 1)
                            * (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1);

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, CHANNEL_CONFIG, AUDIO_FORMAT, Math.min(min2, min));
                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        throw new RuntimeException("Unable to initialize AudioRecord");
                    }

                    recorder.startRecording();

                    int samplesUpdateCount = 0;
                    int samplesTimeCount = 0;
                    // how many samples we need to update 'samples'. time clock. every 1000ms.
                    int samplesTimeUpdate = 1000 / 1000 * sampleRate * (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);

                    short[] buffer = null;

                    while (!Thread.currentThread().isInterrupted()) {
                        synchronized (bufferSize) {
                            if (buffer == null || buffer.length != bufferSize)
                                buffer = new short[bufferSize];
                        }

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

                        int amplitude = (int) (Math.sqrt(sum / readSize));
                        int s = CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? readSize : readSize / 2;

                        samplesUpdateCount += s;
                        if (samplesUpdateCount >= samplesUpdate) {
                            pitch.add((int) (amplitude / (float) MAXIMUM_ALTITUDE * 100) + 1);
                            samplesUpdateCount -= samplesUpdate;
                        }

                        samplesTime += s;
                        samplesTimeCount += s;
                        if (samplesTimeCount > samplesTimeUpdate) {
                            final long m = samplesTime;
                            handle.post(new Runnable() {
                                @Override
                                public void run() {
                                    updateSamples(m);
                                }
                            });
                            samplesTimeCount -= samplesTimeUpdate;
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

    long getSamples(long len) {
        if (AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT) {
            len = len / 2;
        }
        if (CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO) {
            len = len / 2;
        }
        return len;
    }

    // calcuale buffer length dynamically, this way we can reduce thread cycles when activity in background
    // or phone screen is off.
    void updateBufferSize(boolean pause) {
        synchronized (bufferSize) {
            if (pause) {
                samplesUpdate = (int) (1000 * sampleRate / 1000.0);
            } else {
                samplesUpdate = (int) (pitch.getPitchTime() * sampleRate / 1000.0);
            }

            bufferSize = CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? samplesUpdate : samplesUpdate * 2;
        }
    }

    void updateSamples(long samplesTime) {
        long ms = samplesTime / sampleRate * 1000;

        time.setText(MainApplication.formatDuration(ms));
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
            soundMode = am.getRingerMode();

            if (soundMode == AudioManager.RINGER_MODE_SILENT) {
                // we already in SILENT mode. keep all unchanged.
                soundMode = -1;
                return;
            }

            am.setStreamVolume(AudioManager.STREAM_RING, am.getStreamVolume(AudioManager.STREAM_RING), AudioManager.FLAG_SHOW_UI);
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }
    }

    void unsilent() {
        // keep unchanged
        if (soundMode == -1)
            return;

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
        final int channels = CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        final int bps = AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

        return new EncoderInfo(channels, sampleRate, bps);
    }

    void encoding(final Runnable run) {
        final File in = storage.getTempRecording();
        final File out = targetFile;

        EncoderInfo info = getInfo();

        Encoder e = null;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        if (ext.equals("wav")) {
            e = new FormatWAV(info, out);
        }
        if (ext.equals("m4a")) {
            e = new FormatM4A(info, out);
        }
        if (ext.equals("3gp")) {
            e = new Format3GP(info, out);
        }

        encoder = new FileEncoder(this, in, e);

        final ProgressDialog d = new ProgressDialog(this);
        d.setTitle("Encoding...");
        d.setMessage(".../" + targetFile.getName());
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

                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MainApplication.PREFERENCE_LAST, out.getName());
                edit.commit();

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

}
