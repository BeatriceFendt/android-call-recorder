package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.app.AlertDialog;
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
import android.graphics.Point;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.RemoteViews;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.app.MainApplication;
import com.github.axet.audiorecorder.app.RawSamples;
import com.github.axet.audiorecorder.app.Sound;
import com.github.axet.audiorecorder.app.Storage;
import com.github.axet.audiorecorder.encoders.Encoder;
import com.github.axet.audiorecorder.encoders.EncoderInfo;
import com.github.axet.audiorecorder.encoders.FileEncoder;
import com.github.axet.audiorecorder.encoders.Format3GP;
import com.github.axet.audiorecorder.encoders.FormatM4A;
import com.github.axet.audiorecorder.encoders.FormatWAV;
import com.github.axet.audiorecorder.widgets.PitchView;

import java.io.File;

public class RecordingActivity extends AppCompatActivity {
    public static int MAXIMUM_ALTITUDE = 5000;

    public static final String TAG = RecordingActivity.class.getSimpleName();
    public static final String START_RECORDING = RecordingActivity.class.getCanonicalName() + ".START_RECORDING";
    public static final String CLOSE_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".CLOSE_ACTIVITY";
    public static final int NOTIFICATION_RECORDING_ICON = 0;
    public static String SHOW_ACTIVITY = RecordingActivity.class.getCanonicalName() + ".SHOW_ACTIVITY";
    public static String PAUSE = RecordingActivity.class.getCanonicalName() + ".PAUSE";
    public static String START_PAUSE = RecordingActivity.class.getCanonicalName() + ".START_PAUSE";

    public static final String PHONE_STATE = "android.intent.action.PHONE_STATE";

    RecordingReceiver receiver;
    PhoneStateChangeListener pscl = new PhoneStateChangeListener();
    Handler handle = new Handler();
    FileEncoder encoder;

    // do we need to start recording immidiatly?
    boolean start = true;

    Thread thread;
    // dynamic buffer size. big for backgound recording. small for realtime view updates.
    Integer bufferSize = 0;
    // variable from settings. how may samples per second.
    int sampleRate;
    // pitch size in samples. how many samples count need to update view. 4410 for 100ms update.
    int samplesUpdate;
    // output target file 2016-01-01 01.01.01.wav
    File targetFile;
    // how many samples passed for current recording
    long samplesTime;
    // current cut position in samples from begining of file
    long editSample = -1;
    // current sample index in edit mode while playing;
    long playIndex;
    // send ui update every 'playUpdate' samples.
    int playUpdate;

    AudioTrack play;

    TextView title;
    TextView time;
    TextView state;
    ImageButton pause;
    PitchView pitch;

    Storage storage;
    Sound sound;

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
            if (intent.getAction().equals(PAUSE)) {
                pauseButton();
            }
        }
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;
        public boolean pausedByCall;

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            switch (s) {
                case TelephonyManager.CALL_STATE_RINGING:
                    wasRinging = true;
                    break;
                case TelephonyManager.CALL_STATE_OFFHOOK:
                    wasRinging = true;
                    if (thread != null) {
                        stopRecording("playerPause (hold by call)");
                        pausedByCall = true;
                    }
                    break;
                case TelephonyManager.CALL_STATE_IDLE:
                    if (pausedByCall) {
                        startRecording();
                    }
                    wasRinging = false;
                    pausedByCall = false;
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

        edit(false);

        storage = new Storage(this);
        sound = new Sound(this);

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
        filter.addAction(PAUSE);
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

        if (Build.VERSION.SDK_INT < 23 && isEmulator()) {
            // old emulators are not going to record on high sample rate.
            Toast.makeText(this, "Emulator Detected. Reducing Sample Rate to 8000 Hz", Toast.LENGTH_SHORT).show();
            sampleRate = 8000;
        }

        updateBufferSize(false);

        loadSamples();

        View cancel = findViewById(R.id.recording_cancel);
        cancel.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                cancelDialog(new Runnable() {
                    @Override
                    public void run() {
                        stopRecording();
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
                pauseButton();
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

        String a = getIntent().getAction();
        if (a != null && a.equals(START_PAUSE)) {
            // pretend we already start it
            start = false;
            stopRecording("pause");
        }
    }

    void loadSamples() {
        if (!storage.getTempRecording().exists()) {
            updateSamples(0);
            return;
        }

        RawSamples rs = new RawSamples(storage.getTempRecording());
        samplesTime = rs.getSamples();

        Display display = getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);

        int count = pitch.getMaxPitchCount(size.x);

        short[] buf = new short[count * samplesUpdate];
        long cut = samplesTime - buf.length;

        if (cut < 0)
            cut = 0;

        rs.open(cut, buf.length);
        int len = rs.read(buf);
        rs.close();

        pitch.clear(cut / samplesUpdate);
        for (int i = 0; i < len; i += samplesUpdate) {
            pitch.add(getPa(buf, i, samplesUpdate));
        }
        updateSamples(samplesTime);
    }

    boolean isEmulator() {
        return "goldfish".equals(Build.HARDWARE);
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

    void pauseButton() {
        if (thread != null) {
            stopRecording("pause");
        } else {
            editCut();

            startRecording();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        updateBufferSize(false);

        // start once
        if (start) {
            start = false;
            if (permitted()) {
                startRecording();
            }
        }

        if (thread != null)
            pitch.record();
    }

    @Override
    protected void onPause() {
        super.onPause();
        Log.d(TAG, "onPause");
        updateBufferSize(true);
        edit(false);
        pitch.stop();
    }

    void stopRecording(String status) {
        state.setText(status);
        pause.setImageResource(R.drawable.ic_mic_24dp);

        stopRecording();

        showNotificationAlarm(true);

        pitch.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                edit(true);
                editSample = pitch.edit(event.getX()) * samplesUpdate;
                return true;
            }
        });
    }

    void stopRecording() {
        if (thread != null) {
            thread.interrupt();
            thread = null;
        }
        pitch.stop();
        sound.unsilent();
    }

    void edit(boolean b) {
        if (b) {
            state.setText("edit");
            editPlay(false);

            View box = findViewById(R.id.recording_edit_box);
            box.setVisibility(View.VISIBLE);

            View cut = box.findViewById(R.id.recording_cut);
            cut.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    editCut();
                }
            });

            final ImageView playButton = (ImageView) box.findViewById(R.id.recording_play);
            playButton.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (play != null) {
                        editPlay(false);
                    } else {
                        editPlay(true);
                    }
                }
            });

            View done = box.findViewById(R.id.recording_edit_done);
            done.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    edit(false);
                }
            });
        } else {
            editSample = -1;
            state.setText("pause");
            editPlay(false);
            pitch.stop();
            View box = findViewById(R.id.recording_edit_box);
            box.setVisibility(View.GONE);
        }
    }

    void editPlay(boolean b) {
        View box = findViewById(R.id.recording_edit_box);
        final ImageView playButton = (ImageView) box.findViewById(R.id.recording_play);

        if (b) {
            playButton.setImageResource(R.drawable.pause);

            playIndex = editSample;

            playUpdate = PitchView.UPDATE_SPEED * sampleRate / 1000;

            final Handler handler = new Handler();

            AudioTrack.OnPlaybackPositionUpdateListener listener = new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    editPlay(false);
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {
                    if (play != null) {
                        playIndex += playUpdate;
                        float p = playIndex / (float) samplesUpdate;
                        pitch.play(p);
                    }
                }
            };

            RawSamples rs = new RawSamples(storage.getTempRecording());
            int len = (int) (rs.getSamples() - editSample);
            short[] buf = new short[len];
            rs.open(editSample, buf.length);
            int r = rs.read(buf);
            play = sound.generateTrack(sampleRate, buf, r);
            play.play();
            play.setPositionNotificationPeriod(playUpdate);
            play.setPlaybackPositionUpdateListener(listener, handler);
        } else {
            if (play != null) {
                play.release();
                play = null;
            }
            pitch.play(-1);
            playButton.setImageResource(R.drawable.play);
        }
    }

    void editCut() {
        if (editSample == -1)
            return;

        RawSamples rs = new RawSamples(storage.getTempRecording());
        rs.trunk(editSample);
        rs.close();
        edit(false);
        loadSamples();
        pitch.drawCalc();
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

    void startRecording() {
        edit(false);
        pitch.setOnTouchListener(null);

        state.setText("recording");

        sound.silent();

        pause.setImageResource(R.drawable.ic_pause_24dp);

        if (thread != null) {
            thread.interrupt();
        }

        pitch.record();

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                int p = android.os.Process.getThreadPriority(android.os.Process.myTid());

                if (p != android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
                    Log.e(TAG, "Unable to set Thread Priority " + android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                }

                long start = System.currentTimeMillis();
                RawSamples rs = null;
                AudioRecord recorder = null;
                try {
                    rs = new RawSamples(storage.getTempRecording());

                    rs.open(samplesTime);

                    int min = AudioRecord.getMinBufferSize(sampleRate, RawSamples.CHANNEL_CONFIG, RawSamples.AUDIO_FORMAT);
                    if (min <= 0) {
                        throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");
                    }

                    recorder = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, RawSamples.CHANNEL_CONFIG, RawSamples.AUDIO_FORMAT, min);
                    if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        throw new RuntimeException("Unable to initialize AudioRecord");
                    }

                    recorder.startRecording();

                    int samplesUpdateCount = 0;
                    int samplesTimeCount = 0;
                    // how many samples we need to update 'samples'. time clock. every 1000ms.
                    int samplesTimeUpdate = 1000 / 1000 * sampleRate * (RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? 1 : 2);

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

                        int s = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? readSize : readSize / 2;

                        if (pitch.stableRefresh()) {
                            rs.write(buffer);

                            samplesUpdateCount += s;
                            if (samplesUpdateCount >= samplesUpdate) {
                                final float pa = getPa(buffer, 0, readSize);
                                handle.post(new Runnable() {
                                    @Override
                                    public void run() {
                                        pitch.add(pa);
                                    }
                                });
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
                    // redraw view, we may add one last pich which is not been drawen because draw tread already interrupted.
                    // to prevent resume recording jump - draw last added pitch here.
                    handle.post(new Runnable() {
                        @Override
                        public void run() {
                            pitch.drawEnd();
                        }
                    });

                    if (rs != null)
                        rs.close();

                    if (recorder != null)
                        recorder.release();
                }
            }
        }, "RecordingThread");
        thread.start();

        showNotificationAlarm(true);
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

            bufferSize = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_MONO ? samplesUpdate : samplesUpdate * 2;
        }
    }

    void updateSamples(long samplesTime) {
        long ms = samplesTime / sampleRate * 1000;

        time.setText(MainApplication.formatDuration(ms));
    }

    float getPa(short[] buffer, int offset, int len) {
        double sum = 0;
        for (int i = offset; i < offset + len; i++) {
            sum += buffer[i] * buffer[i];
        }

        int amplitude = (int) (Math.sqrt(sum / len));
        float pa = amplitude / (float) MAXIMUM_ALTITUDE;

        return pa;
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

            PendingIntent pe = PendingIntent.getBroadcast(this, 0,
                    new Intent(PAUSE),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            RemoteViews view = new RemoteViews(getPackageName(), R.layout.notifictaion_recording);
            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            view.setTextViewText(R.id.notification_text, ".../" + targetFile.getName());
            view.setOnClickPendingIntent(R.id.notification_pause, pe);
            view.setImageViewResource(R.id.notification_pause, thread == null ? R.drawable.play : R.drawable.pause);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setContentTitle("Recording")
                    .setSmallIcon(R.drawable.ic_mic_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notificationManager.notify(NOTIFICATION_RECORDING_ICON, builder.build());
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    startRecording();
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

    EncoderInfo getInfo() {
        final int channels = RawSamples.CHANNEL_CONFIG == AudioFormat.CHANNEL_IN_STEREO ? 2 : 1;
        final int bps = RawSamples.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;

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
