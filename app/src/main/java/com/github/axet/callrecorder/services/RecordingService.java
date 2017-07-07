package com.github.axet.callrecorder.services;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.support.annotation.Nullable;
import android.support.v4.app.NotificationCompat;
import android.telephony.PhoneNumberUtils;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.webkit.MimeTypeMap;
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
import com.github.axet.callrecorder.activities.SettingsActivity;
import com.github.axet.callrecorder.app.MainApplication;
import com.github.axet.callrecorder.app.Storage;

import org.apache.commons.io.IOUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;

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
    Uri targetUri;
    PhoneStateChangeListener pscl;
    Handler handle = new Handler();
    // variable from settings. how may samples per second.
    int sampleRate;
    // how many samples passed for current recording
    long samplesTime;
    FileEncoder encoder;
    Runnable encoding; // current encoding
    HashMap<File, CallInfo> mapTarget = new HashMap<>();
    OptimizationPreferenceCompat.ServiceReceiver optimization;
    String phone = "";
    String contact = "";
    String contactId = "";
    String call;
    long now;
    int source = -1; // audiotrecorder source

    public static void startService(Context context) {
        context.startService(new Intent(context, RecordingService.class));
    }

    public static boolean isEnabled(Context context) {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(context);
        boolean b = shared.getBoolean(MainApplication.PREFERENCE_CALL, false);
        if (!Storage.permitted(context, MainActivity.MUST))
            b = false;
        return b;
    }

    public static void startIfEnabled(Context context) {
        if (isEnabled(context))
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

    public static class CallInfo {
        public Uri targetUri;
        public String phone;
        public String contact;
        public String contactId;
        public String call;
        public long now;

        public CallInfo() {
        }

        public CallInfo(Uri t, String p, String c, String cid, String call, long now) {
            this.targetUri = t;
            this.phone = p;
            this.contact = c;
            this.contactId = cid;
            this.call = call;
            this.now = now;
        }
    }

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

    class PhoneStateReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
                setPhone(intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER), call);
            }
            if (a.equals(Intent.ACTION_NEW_OUTGOING_CALL)) {
                setPhone(intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER), MainApplication.CALL_OUT);
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
            try {
                switch (s) {
                    case TelephonyManager.CALL_STATE_RINGING:
                        setPhone(incomingNumber, MainApplication.CALL_IN);
                        wasRinging = true;
                        break;
                    case TelephonyManager.CALL_STATE_OFFHOOK:
                        setPhone(incomingNumber, call);
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
                        phone = "";
                        contactId = "";
                        contact = "";
                        call = "";
                        break;
                }
            } catch (RuntimeException e) {
                Error(e);
            }
        }
    }

    public RecordingService() {
    }

    public void setPhone(String s, String c) {
        if (s == null || s.isEmpty())
            return;

        phone = PhoneNumberUtils.formatNumber(s);

        contact = "";
        contactId = "";
        Uri uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(s));
        if (Storage.permitted(this, SettingsActivity.CONTACTS)) {
            try {
                ContentResolver contentResolver = getContentResolver();
                Cursor contactLookup = contentResolver.query(uri, null, null, null, null);
                if (contactLookup != null) {
                    try {
                        if (contactLookup.moveToNext()) {
                            contact = contactLookup.getString(contactLookup.getColumnIndex(ContactsContract.Data.DISPLAY_NAME));
                            contactId = contactLookup.getString(contactLookup.getColumnIndex(BaseColumns._ID));
                        }
                    } finally {
                        contactLookup.close();
                    }
                }
            } catch (RuntimeException e) {
                Error(e);
            }
        }

        call = c;
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
        sampleRate = Sound.getValidRecordRate(MainApplication.getInMode(this), sampleRate);
        if (sampleRate == -1)
            sampleRate = Sound.DEFAULT_RATE;

        shared.registerOnSharedPreferenceChangeListener(this);

        encodingNext();
    }

    void deleteOld() {
        SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String d = shared.getString(MainApplication.PREFERENCE_DELETE, "off");
        if (d.equals("off"))
            return;

        List<Uri> list = new ArrayList<>();

        String[] ee = Factory.getEncodingValues(this);
        Uri path = storage.getStoragePath();
        String s = path.getScheme();
        if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            ContentResolver resolver = getContentResolver();
            Uri childId = DocumentsContract.buildChildDocumentsUriUsingTree(path, DocumentsContract.getTreeDocumentId(path));
            Cursor c = resolver.query(childId, null, null, null, null);
            if (c != null) {
                while (c.moveToNext()) {
                    String id = c.getString(c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID));
                    Uri dd = DocumentsContract.buildDocumentUriUsingTree(path, id);
                    list.add(dd);
                }
                c.close();
            }
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File dir = Storage.getFile(path);
            File[] ff = dir.listFiles();
            if (ff == null)
                return;
            for (File f : ff) {
                list.add(Uri.fromFile(f));
            }
        } else {
            throw new RuntimeException("unknown uri");
        }

        for (Uri f : list) {
            String n = Storage.getDocumentName(f).toLowerCase();
            for (String e : ee) {
                e = e.toLowerCase();
                if (n.endsWith(e)) {
                    Calendar c = Calendar.getInstance();
                    c.setTimeInMillis(storage.getLast(f));
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
                        if (!MainApplication.getStar(this, f)) // do not delete favorite recorings
                            storage.delete(f);
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

    public String getSource() {
        switch (source) {
            case MediaRecorder.AudioSource.VOICE_UPLINK:
                return "(VOICE_UPLINK)";
            case MediaRecorder.AudioSource.VOICE_DOWNLINK:
                return "(VOICE_DOWNLINK)";
            case MediaRecorder.AudioSource.VOICE_CALL:
                return getString(R.string.source_line);
            case MediaRecorder.AudioSource.VOICE_COMMUNICATION:
                return "(VoIP)";
            case MediaRecorder.AudioSource.MIC:
                return getString(R.string.source_mic);
            case MediaRecorder.AudioSource.DEFAULT:
                return getString(R.string.source_default);
            case MediaRecorder.AudioSource.UNPROCESSED:
                return "(RAW)";
            default:
                return "";
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

            String title = encoding != null ? getString(R.string.encoding_title) : (getString(R.string.recording_title) + " " + getSource());
            String text = ".../" + storage.getDocumentName(targetUri);

            title = title.trim();

            view.setOnClickPendingIntent(R.id.status_bar_latest_event_content, main);
            view.setTextViewText(R.id.notification_title, title);
            view.setTextViewText(R.id.notification_text, text);
            view.setOnClickPendingIntent(R.id.notification_pause, pe);
            view.setImageViewResource(R.id.notification_pause, thread == null ? R.drawable.ic_play_arrow_black_24dp : R.drawable.ic_pause_black_24dp);
            view.setViewVisibility(R.id.notification_record, View.GONE);

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
        final RawSamples rs;
        AudioRecord r = null;

        rs = new RawSamples(storage.getTempRecording());

        rs.open(samplesTime);

        int c = MainApplication.getInMode(RecordingService.this);
        final int min = AudioRecord.getMinBufferSize(sampleRate, c, Sound.DEFAULT_AUDIOFORMAT);
        if (min <= 0)
            throw new RuntimeException("Unable to initialize AudioRecord: Bad audio values");

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        Integer[] ss = new Integer[]{
                MediaRecorder.AudioSource.VOICE_CALL,
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // mic source VOIP
                MediaRecorder.AudioSource.MIC, // mic
                MediaRecorder.AudioSource.DEFAULT, // mic
                MediaRecorder.AudioSource.UNPROCESSED,
        };
        List<Integer> list = Arrays.asList(ss);
        int i = Integer.valueOf(shared.getString(MainApplication.PREFERENCE_SOURCE, "-1"));
        if (i == -1)
            i = 0;
        else
            i = list.indexOf(i);
        for (; i < ss.length; i++) {
            int s = ss[i];
            try {
                r = new AudioRecord(s, sampleRate, c, Sound.DEFAULT_AUDIOFORMAT, min * 2);
                if (r.getState() == AudioRecord.STATE_INITIALIZED)
                    break;
            } catch (IllegalArgumentException e) {
                Log.d(TAG, "Recorder Create Failed: " + s, e);
            }
        }
        if (r == null || r.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new RuntimeException("Unable to initialize AudioRecord");
        }
        source = r.getAudioSource();

        final AudioRecord recorder = r;

        final Thread old = thread;

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                if (old != null) {
                    old.interrupt();
                    try {
                        old.join();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                int p = android.os.Process.getThreadPriority(android.os.Process.myTid());

                if (p != android.os.Process.THREAD_PRIORITY_URGENT_AUDIO) {
                    Log.e(TAG, "Unable to set Thread Priority " + android.os.Process.THREAD_PRIORITY_URGENT_AUDIO);
                }

                try {
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

                        int samples = readSize / MainApplication.getChannels(RecordingService.this);

                        if (stableRefresh || diff >= samples) {
                            stableRefresh = true;

                            rs.write(buffer, 0, readSize);

                            samplesTime += samples;
                            samplesTimeCount += samples;
                            if (samplesTimeCount > samplesTimeUpdate) {
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
        final int bps = Sound.DEFAULT_AUDIOFORMAT == AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;
        return new EncoderInfo(channels, sampleRate, bps);
    }

    void encoding(final File in, final Uri uri, final Runnable done, final Runnable success) {
        final File out;

        final String s = uri.getScheme();
        if (s.startsWith(ContentResolver.SCHEME_CONTENT)) {
            out = storage.getTempEncoding();
        } else if (s.startsWith(ContentResolver.SCHEME_FILE)) {
            File f = Storage.getFile(uri);
            File parent = f.getParentFile();
            if (!parent.exists() && !parent.mkdirs()) { // in case if it were manually deleted
                throw new RuntimeException("Unable to create: " + parent);
            }
            out = f;
        } else {
            throw new RuntimeException("unknown uri");
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
                if (Build.VERSION.SDK_INT >= 21 && s.startsWith(ContentResolver.SCHEME_CONTENT)) {
                    ContentResolver resolver = getContentResolver();
                    try {
                        String d = storage.getDocumentName(uri);
                        String ee = storage.getExt(uri);
                        Uri docUri = DocumentsContract.buildDocumentUriUsingTree(uri, DocumentsContract.getTreeDocumentId(uri));
                        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ee);
                        Uri childrenUri = DocumentsContract.createDocument(resolver, docUri, mime, d);

                        InputStream is = new FileInputStream(out);
                        OutputStream os = resolver.openOutputStream(childrenUri);
                        IOUtils.copy(is, os);
                        is.close();
                        os.close();
                        Storage.delete(out); // delete tmp encoding file
                    } catch (IOException e) {
                        Storage.delete(out); // delete tmp encoding file
                        try {
                            storage.delete(uri); // delete SAF encoding file
                        } catch (RuntimeException ee) {
                            Log.d(TAG, "unable to delete target uri", e); // ignore, not even created?
                        }
                        Error(e);
                        return;
                    }
                }

                MainActivity.showProgress(RecordingService.this, false, phone, samplesTime / sampleRate, false);
                Storage.delete(in); // delete raw recording

                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MainApplication.PREFERENCE_LAST, storage.getDocumentName(uri));
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
                if (t == null)
                    t = e;
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
        now = System.currentTimeMillis();
        targetUri = storage.getNewFile(now, phone, contact, call);
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
            mapTarget.put(in, new CallInfo(targetUri, phone, contact, contactId, call, now));
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
        CallInfo c = mapTarget.get(inFile);
        if (c == null) { // restarted
            c = new CallInfo();
            c.targetUri = null;
            c.phone = "";
            c.contact = "";
            c.contactId = "";
            c.call = "";
            c.now = inFile.lastModified();
        }
        targetUri = c.targetUri; // update notification encoding name
        final String phone = c.phone;
        final String contact = c.contact;
        final String contactId = c.contactId;
        final String call = c.call;
        long now = c.now;
        if (targetUri == null) { // service restart
            targetUri = storage.getNewFile(now, phone, contact, call);
        }
        final Uri targetUri = RecordingService.this.targetUri;
        encoding = new Runnable() { // calledn when done
            @Override
            public void run() {
                deleteOld();
                showNotificationAlarm(false);
                encoding = null;
                encoder = null;
            }
        };
        showNotificationAlarm(true); // update status (encoding)
        Log.d(TAG, "Encoded " + inFile.getName() + " to " + storage.getTargetName(targetUri));
        encoding(inFile, targetUri, encoding, new Runnable() {
            @Override
            public void run() { // called when success
                mapTarget.remove(inFile);
                MainApplication.setContact(RecordingService.this, targetUri, contactId);
                MainApplication.setCall(RecordingService.this, targetUri, call);
                MainActivity.last(RecordingService.this);
            }
        });
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        if (key.equals(MainApplication.PREFERENCE_DELETE)) {
            deleteOld();
        }
        if (key.equals(MainApplication.PREFERENCE_STORAGE)) {
            encodingNext();
        }
    }

    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        optimization.onTaskRemoved(rootIntent);
    }

}
