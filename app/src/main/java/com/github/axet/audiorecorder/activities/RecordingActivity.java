package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
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
import com.github.axet.audiorecorder.widgets.PitchView;

import org.w3c.dom.Text;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;

public class RecordingActivity extends AppCompatActivity {
    public static final String TAG = RecordingActivity.class.getSimpleName();
    public static final String START_RECORDING = RecordingActivity.class.getCanonicalName() + ".START_RECORDING";
    public static final String CLOSE_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".CLOSE_ACTIVITY";
    public static final int NOTIFICATION_RECORDING_ICON = 0;
    public static String SHOW_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".SHOW_ACTIVITY";

    public static final String PHONE_STATE = "android.intent.action.PHONE_STATE";

    int maximumAltitude;

    PitchView pitch;

    RecordingReceiver receiver = new RecordingReceiver();
    PhoneStateChangeListener pscl = new PhoneStateChangeListener();
    Handler handle = new Handler();
    Thread thread;

    TextView title;
    TextView time;
    TextView state;
    ImageButton pause;

    int sampleRate;
    int channelConfig;
    int audioFormat;
    String fileName;
    File file;

    // how many samples passed
    long samples;

    public class RecordingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.d(RecordingReceiver.class.getSimpleName(), "RecordingReceiver " + intent.getAction());

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
                    Log.i(TAG, "RINGING");
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    Log.i(TAG, "OFFHOOK");
                    state.setText("pause (hold by call)");
                    pauseRecording();
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    Log.i(TAG, "IDLE");
                    wasRinging = false;
                    resumeRecording();
                    break;
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_recording);

        title = (TextView) findViewById(R.id.recording_title);

        getNewFile();

        title.setText(fileName);

        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        registerReceiver(receiver, filter);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (shared.getBoolean(MainApplication.PREFERENCE_CALL, false)) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE);
        }

        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            AudioManager am = (AudioManager) getBaseContext().getSystemService(Context.AUDIO_SERVICE);
            am.setRingerMode(AudioManager.RINGER_MODE_SILENT);
        }

        maximumAltitude = 1000;
        sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        channelConfig = AudioFormat.CHANNEL_IN_MONO;
        audioFormat = AudioFormat.ENCODING_PCM_16BIT;

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD |
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);

        pitch = (PitchView) findViewById(R.id.recording_pitch);
        time = (TextView) findViewById(R.id.recording_time);
        state = (TextView) findViewById(R.id.recording_state);

        addSamples(0);

        View cancel = findViewById(R.id.recording_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        pause = (ImageButton) findViewById(R.id.recording_pause);
        pause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (thread != null) {
                    state.setText("pause");
                    pauseRecording();
                } else {
                    if (permitted())
                        resumeRecording();
                }
            }
        });

        View done = findViewById(R.id.recording_done);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });

        showNotificationAlarm(true);

        if (permitted()) {
            record();
        }
    }

    void getNewFile() {
        SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH.mm.ss");
        fileName = String.format("%s.wav", s.format(new Date()));
        file = new File(fileName);
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
    }

    @Override
    protected void onPause() {
        super.onPause();
        pitch.onPause();
    }

    void pauseRecording() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        pitch.onPause();

        pause.setImageResource(R.drawable.ic_mic_24dp);
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

        if (thread != null) {
            thread.interrupt();
            Log.d(TAG, "INTERRUPT " + thread + " " + thread.isInterrupted());
            thread = null;
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }

        if (pscl != null) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
            pscl = null;
        }

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        if (shared.getBoolean(MainApplication.PREFERENCE_SILENT, false)) {
            AudioManager am = (AudioManager) getBaseContext().getSystemService(Context.AUDIO_SERVICE);
            am.setRingerMode(AudioManager.RINGER_MODE_NORMAL);
        }

        showNotificationAlarm(false);
    }

    void record() {
        state.setText("recording");

        pause.setImageResource(R.drawable.ic_pause_24dp);

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                // how many samples count need to update view. 4410 for 100ms update.
                final int samplesUpdate = (int) (pitch.getPitchTime() * sampleRate / 1000.0);

                // two channels buffer
                short[] buffer = new short[channelConfig == AudioFormat.CHANNEL_IN_MONO ? samplesUpdate : samplesUpdate * 2];

                int min = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

                AudioRecord recorder;
                recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, channelConfig, audioFormat, min);
                if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                    thread = null;
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(RecordingActivity.this, "Unable to initialize AudioRecord", Toast.LENGTH_SHORT).show();
                        }
                    });
                    return;
                }

                recorder.startRecording();

                // AudioRecord eats thread Inetrrupted exception
                while (!Thread.currentThread().isInterrupted()) {
                    final int readSize = recorder.read(buffer, 0, buffer.length);
                    if (readSize <= 0) {
                        break;
                    }

                    double sum = 0;
                    for (int i = 0; i < readSize; i++) {
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

                recorder.release();
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
                    .setSmallIcon(R.drawable.ic_notifications_black_24dp)
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

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.RECORD_AUDIO};

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
}
