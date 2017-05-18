package com.github.axet.callrecorder.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OptimizationPreferenceCompat;
import com.github.axet.audiolibrary.app.RawSamples;
import com.github.axet.audiolibrary.app.Sound;
import com.github.axet.audiolibrary.encoders.Encoder;
import com.github.axet.audiolibrary.encoders.EncoderInfo;
import com.github.axet.audiolibrary.encoders.Factory;
import com.github.axet.audiolibrary.encoders.FileEncoder;
import com.github.axet.callrecorder.R;
import com.github.axet.callrecorder.activities.MainActivity;
import com.github.axet.callrecorder.app.MainApplication;
import com.github.axet.callrecorder.app.Storage;

import java.io.File;
import java.util.Calendar;
import java.util.HashMap;

/**
 * RecordingActivity more likly to be removed from memory when paused then service. Notification button
 * does not handle getActvity without unlocking screen. The only option is to have Service.
 * <p/>
 * So, lets have it.
 * <p/>
 * Maybe later this class will be converted for fully feature recording service with recording thread.
 */
public class RecordingService extends Service implements SharedPreferences.OnSharedPreferenceChangeListener {
    public static final String TAG = RecordingService.class.getSimpleName();

    public static final int NOTIFICATION_RECORDING_ICON = 1;

    public static String SHOW_ACTIVITY = RecordingService.class.getCanonicalName() + ".SHOW_ACTIVITY";
    public static String PAUSE_BUTTON = RecordingService.class.getCanonicalName() + ".PAUSE_BUTTON";
    public static String STOP_BUTTON = RecordingService.class.getCanonicalName() + ".STOP_BUTTON";

    Sound sound;
    Thread thread;
    Storage storage;
    RecordingReceiver receiver;
    PhoneStateReceiver state;
    // output target file 2016-01-01 01.01.01.wav
    File targetFile;
    PhoneStateChangeListener pscl;
    Handler handle = new Handler();
    // variable from settings. how may samples per second.
    int sampleRate;
    // how many samples passed for current recording
    long samplesTime;
    FileEncoder encoder;
    Runnable encoding; // current encoding
    HashMap<File, File> map = new HashMap<>();
    OptimizationPreferenceCompat.ServiceReceiver optimization;

    class RecordingReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                if (intent.getAction().equals(PAUSE_BUTTON)) {
                    pauseButton();
                }
                if (intent.getAction().equals(STOP_BUTTON)) {
                    finish();
                }
            } catch (RuntimeException e) {
                Error(e);
            }
        }
    }

    String phone = "";

    public class PhoneStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                setPhone(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER));
            }
            if (a.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
                setPhone(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER));
            }
        }
    }

    class PhoneStateChangeListener extends PhoneStateListener {
        public boolean wasRinging;
        public boolean startedByCall;
        TelephonyManager tm;

        public PhoneStateChangeListener() {
            tm = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        }

        @Override
        public void onCallStateChanged(int s, String incomingNumber) {
            setPhone(incomingNumber);
            try {
                switch (s) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        wasRinging = true;
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        if (thread == null) { // handling restart while current call
                            begin(wasRinging);
                            startedByCall = true;
                        }
                        break;
                    case TelephonyManager.CALL_STATE_IDLE:
                        if (startedByCall) {
                            if (tm.getCallState() != TelephonyManager.CALL_STATE_OFFHOOK) { // current state maybe differed from queued (s) one
                                finish();
                            } else {
                                return; // fast clicking. new call already stared. keep recording. do not reset startedByCall
                            }
                        } else {
                            if (storage.recordingPending()) { // handling restart after call finished
                                finish();
                            } else if (storage.recordingNextPending()) {
                                if (encoding == null)
                                    encodingNext();
                            }
                        }
                        wasRinging = false;
                        startedByCall = false;
                        break;
                }
            } catch (RuntimeException e) {
                Error(e);
            }
        }
    }

    public static void startService(Context context) {
        context.startService(new Intent(context, RecordingService.class));
    }

    public static void startIfEnabled(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        if (shared.getBoolean(MainApplication.PREFERENCE_CALL, false))
            context.startService(new Intent(context, RecordingService.class));
    }

    public static void stopService(Context context) {
        context.stopService(new Intent(context, RecordingService.class));
    }

    public static void pauseButton(Context context) {
        Intent intent = new Intent(PAUSE_BUTTON);
        context.sendBroadcast(intent);
    }

    public static void stopButton(Context context) {
        Intent intent = new Intent(STOP_BUTTON);
        context.sendBroadcast(intent);
    }

    public RecordingService() {
    }

    public void setPhone(String s) {
        if (s == null)
            return;
        if (s.isEmpty())
            return;
        phone = PhoneNumberUtils.formatNumber(s);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "onCreate");

        optimization = new OptimizationPreferenceCompat.ServiceReceiver(this, getClass()) {
            @Override
            public void check() {
            }
        };

        receiver = new RecordingReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(PAUSE_BUTTON);
        filter.addAction(STOP_BUTTON);
        registerReceiver(receiver, filter);

        storage = new Storage(this);
        sound = new Sound(this);

        deleteOld();

        pscl = new PhoneStateChangeListener();
        TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        tm.listen(pscl, PhoneStateListener.LISTEN_CALL_STATE);

        filter = new IntentFilter();
        filter.addAction(TelephonyManager.ACTION_PHONE_STATE_CHANGED);
        filter.addAction(Intent.ACTION_NEW_OUTGOING_CALL);
        state = new PhoneStateReceiver();
        registerReceiver(state, filter);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        sampleRate = Integer.parseInt(shared.getString(MainApplication.PREFERENCE_RATE, ""));
        sampleRate = Sound.getValidRecordRate(MainApplication.getMode(this), sampleRate);

        shared.registerOnSharedPreferenceChangeListener(this);
    }

    void deleteOld() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String d = shared.getString(MainApplication.PREFERENCE_DELETE, "off");
        if (d.equals("off"))
            return;

        String[] ee = Factory.getEncodingValues(this);
        File path = storage.getStoragePath();
        File[] ff = path.listFiles();
        for (File f : ff) {
            String n = f.getName().toLowerCase();
            for (String e : ee) {
                e = e.toLowerCase();
                if (n.endsWith(e)) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(f.lastModified());
                    Calendar cur = c;

                    if (d.equals("1week")) {
                        cur = Calendar.getInstance();
                        c.add(Calendar.WEEK_OF_YEAR, 1);
                    }
                    if (d.equals("1month")) {
                        cur = Calendar.getInstance();
                        c.add(Calendar.MONTH, 1);
                    }
                    if (d.equals("3month")) {
                        cur = Calendar.getInstance();
                        c.add(Calendar.MONTH, 3);
                    }
                    if (d.equals("6month")) {
                        cur = Calendar.getInstance();
                        c.add(Calendar.MONTH, 6);
                    }

                    if (c.before(cur)) {
                        f.delete();
                    }
                }
            }
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Log.d(TAG, "onStartCommand");

        if (optimization.onStartCommand(intent, flags, startId)) {
            // nothing to restart
        }

        if (intent != null) {
            String a = intent.getAction();
            if (a == null) {
                ; // nothing
            } else if (a.equals(PAUSE_BUTTON)) {
                Intent i = new Intent(PAUSE_BUTTON);
                sendBroadcast(i);
            } else if (a.equals(SHOW_ACTIVITY)) {
                MainActivity.startActivity(this);
            }
        }

        return super.onStartCommand(intent, flags, startId);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    public class Binder extends android.os.Binder {
        public RecordingService getService() {
            return RecordingService.this;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "onDestory");

        showNotificationAlarm(false);

        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        shared.unregisterOnSharedPreferenceChangeListener(this);

        if (optimization != null) {
            optimization.close();
            optimization = null;
        }

        if (receiver != null) {
            unregisterReceiver(receiver);
            receiver = null;
        }

        if (state != null) {
            unregisterReceiver(state);
            state = null;
        }

        if (pscl != null) {
            TelephonyManager tm = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
            tm.listen(pscl, PhoneStateListener.LISTEN_NONE);
            pscl = null;
        }
    }

    // alarm dismiss button
    public void showNotificationAlarm(boolean show) {
        MainActivity.showProgress(RecordingService.this, show, phone, samplesTime / sampleRate, thread != null);

        NotificationManager notificationManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);

        if (!show) {
            notificationManager.cancel(NOTIFICATION_RECORDING_ICON);
        } else {
            PendingIntent main = PendingIntent.getService(this, 0,
                    new Intent(this, RecordingService.class).setAction(SHOW_ACTIVITY),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            PendingIntent pe = PendingIntent.getService(this, 0,
                    new Intent(this, RecordingService.class).setAction(PAUSE_BUTTON),
                    PendingIntent.FLAG_UPDATE_CURRENT);

            RemoteViews view = new RemoteViews(getPackageName(), MainApplication.getTheme(getBaseContext(),
                    R.layout.notifictaion_recording_light,
                    R.layout.notifictaion_recording_dark));

            String title = encoding != null ? getString(R.string.encoding_title) : getString(R.string.recording_title);
            String text = ".../" + targetFile.getName();

            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            view.setTextViewText(R.id.notification_title, title);
            view.setTextViewText(R.id.notification_text, text);
            view.setOnClickPendingIntent(R.id.notification_pause, pe);
            view.setImageViewResource(R.id.notification_pause, thread == null ? R.drawable.ic_play_arrow_black_24dp : R.drawable.ic_pause_black_24dp);

            if (encoding != null)
                view.setViewVisibility(R.id.notification_pause, View.GONE);

            NotificationCompat.Builder builder = new NotificationCompat.Builder(this)
                    .setOngoing(true)
                    .setContentTitle(title)
                    .setContentText(text)
                    .setTicker(title) // tooltip status bar message
                    .setSmallIcon(R.drawable.ic_mic_24dp)
                    .setContent(view);

            if (Build.VERSION.SDK_INT < 11) {
                builder.setContentIntent(main);
            }

            if (Build.VERSION.SDK_INT >= 21)
                builder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC);

            notificationManager.notify(NOTIFICATION_RECORDING_ICON, builder.build());
        }
    }

    void startRecording() {
        if (thread != null) {
            thread.interrupt();
        }

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                int p = android.os.Process.getThreadPriority(android.os.Process.myTid());

                if (p != android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
                    Log.e(TAG, "Unable to set Thread Priority " + android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                }

                RawSamples rs = null;
                AudioRecord recorder = null;
                try {
                    rs = new RawSamples(storage.getTempRecording());

                    rs.open(samplesTime);

                    int min = AudioRecord.getMinBufferSize(sampleRate, MainApplication.getMode(RecordingService.this), Sound.AUDIO_FORMAT);
                    if (min <= 0) {
                        throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");
                    }

                    int[] ss = new int[]{MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                            MediaRecorder.AudioSource.VOICE_CALL,
                            MediaRecorder.AudioSource.MIC,
                            MediaRecorder.AudioSource.DEFAULT,
                    };
                    for (int s : ss) {
                        try {
                            recorder = new AudioRecord(s, sampleRate, MainApplication.getMode(RecordingService.this), Sound.AUDIO_FORMAT, min * 2);
                            if (recorder.getState() == AudioRecord.STATE_INITIALIZED)
                                break;
                        } catch (IllegalArgumentException e) {
                            Log.d(TAG, "Recorder Create Failed: " + s, e);
                        }
                    }
                    if (recorder == null || recorder.getState() != AudioRecord.STATE_INITIALIZED) {
                        throw new RuntimeException("Unable to initialize AudioRecord");
                    }

                    long start = System.currentTimeMillis();
                    recorder.startRecording();

                    int samplesTimeCount = 0;
                    // how many samples we need to update 'samples'. time clock. every 1000ms.
                    int samplesTimeUpdate = 1000 / 1000 * sampleRate;

                    short[] buffer = new short[min];

                    boolean stableRefresh = false;

                    while (!Thread.currentThread().isInterrupted()) {
                        final int readSize = recorder.read(buffer, 0, buffer.length);
                        if (readSize <= 0) {
                            break;
                        }
                        long end = System.currentTimeMillis();

                        long diff = (end - start) * sampleRate / 1000;

                        start = end;

                        int s = readSize / MainApplication.getChannels(RecordingService.this);

                        if (stableRefresh || diff >= s) {
                            stableRefresh = true;

                            rs.write(buffer, readSize);

                            samplesTime += s;
                            samplesTimeCount += s;
                            if (samplesTimeCount > samplesTimeUpdate) {
                                final long m = samplesTime;
                                samplesTimeCount -= samplesTimeUpdate;
                                MainActivity.showProgress(RecordingService.this, true, phone, samplesTime / sampleRate, true);
                            }
                        }
                    }
                } catch (final RuntimeException e) {
                    Post(e);
                } finally {
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

    EncoderInfo getInfo() {
        final int channels = MainApplication.getChannels(this);
        final int bps = Sound.AUDIO_FORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;
        return new EncoderInfo(channels, sampleRate, bps);
    }

    void encoding(final File in, final File out, final Runnable done, final Runnable success) {
        File parent = out.getParentFile();

        if (!parent.exists()) {
            if (!parent.mkdirs()) { // in case if it were manually deleted
                throw new RuntimeException("Unable to create: " + parent);
            }
        }

        EncoderInfo info = getInfo();

        Encoder e = null;

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String ext = shared.getString(MainApplication.PREFERENCE_ENCODING, "");

        e = Factory.getEncoder(this, ext, info, out);

        encoder = new FileEncoder(this, in, e);

        encoder.run(new Runnable() {
            @Override
            public void run() {
                MainActivity.setProgress(RecordingService.this, encoder.getProgress());
            }
        }, new Runnable() {
            @Override
            public void run() {
                MainActivity.showProgress(RecordingService.this, false, phone, samplesTime / sampleRate, false);
                Storage.delete(in);

                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MainApplication.PREFERENCE_LAST, out.getName());
                edit.commit();

                success.run();
                done.run();
                encodingNext();
            }
        }, new Runnable() {
            @Override
            public void run() {
                MainActivity.showProgress(RecordingService.this, false, phone, samplesTime / sampleRate, false);
                Error(encoder.getException());
                done.run();
            }
        });
    }

    void Post(final Throwable e) {
        Log.e(TAG, Log.getStackTraceString(e));
        Post("AudioRecord error: " + e.getMessage());
    }

    void Post(final String msg) {
        handle.post(new Runnable() {
            @Override
            public void run() {
                Error(msg);
            }
        });
    }

    void Error(Throwable e) {
        Log.d(TAG, "Error", e);
        String msg = e.getMessage();
        if (msg == null || msg.isEmpty()) {
            Throwable t;
            if (encoder == null) {
                t = e;
            } else {
                t = encoder.getException();
            }
            while (t.getCause() != null)
                t = t.getCause();
            msg = t.getClass().getSimpleName();
        }
        Error(msg);
    }

    void Error(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    void pauseButton() {
        if (thread != null) {
            stopRecording();
        } else {
            startRecording();
        }
        MainActivity.showProgress(this, true, phone, samplesTime / sampleRate, thread != null);
    }

    void stopRecording() {
        if (thread != null) {
            thread.interrupt();
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            thread = null;
        }
    }

    void begin(boolean wasRinging) {
        targetFile = storage.getNewFile(phone);
        if (encoding != null) {
            encoder.pause();
        }
        if (storage.recordingPending()) {
            RawSamples rs = new RawSamples(storage.getTempRecording());
            samplesTime = rs.getSamples();
        } else {
            samplesTime = 0;
        }
        startRecording();
    }

    void finish() {
        stopRecording();
        if (storage.recordingPending()) {
            File tmp = storage.getTempRecording();
            File in = Storage.getNextFile(tmp.getParentFile(), Storage.TMP_REC, null);
            Storage.move(tmp, in);
            map.put(in, targetFile);
            if (encoding == null) { // double finish()? skip
                encodingNext();
            } else {
                encoder.resume();
            }
        }
    }

    void encodingNext() {
        final File inFile = storage.getTempNextRecording();
        if (!inFile.exists())
            return;
        targetFile = map.get(inFile);
        if (targetFile == null) { // service restart
            targetFile = storage.getNewFile(phone);
        }
        encoding = new Runnable() {
            @Override
            public void run() {
                deleteOld();
                showNotificationAlarm(false);
                encoding = null;
            }
        };
        showNotificationAlarm(true); // update status (encoding)
        Log.d(TAG, "Encode " + inFile.getName() + " to " + targetFile.getName());
        encoding(inFile, targetFile, encoding, new Runnable() {
            @Override
            public void run() { // success
                map.remove(inFile);
                MainActivity.last(RecordingService.this);
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_DELETE)) {
            deleteOld();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        optimization.onTaskRemoved(rootIntent);
    }
}
