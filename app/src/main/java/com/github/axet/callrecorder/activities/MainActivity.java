package com.github.axet.callrecorder.activities;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.androidlibrary.app.MainLibrary;
import com.github.axet.audiolibrary.app.Recordings;
import com.github.axet.callrecorder.R;
import com.github.axet.callrecorder.app.MainApplication;
import com.github.axet.callrecorder.app.Storage;
import com.github.axet.callrecorder.services.RecordingService;

import java.io.File;
import java.util.Collections;

public class MainActivity extends AppCompatActivity {
    public final static String TAG = MainActivity.class.getSimpleName();

    public static String SHOW_PROGRESS = MainActivity.class.getCanonicalName() + ".SHOW_PROGRESS";
    public static String SET_PROGRESS = MainActivity.class.getCanonicalName() + ".SET_PROGRESS";
    public static String SHOW_LAST = MainActivity.class.getCanonicalName() + ".SHOW_LAST";

    FloatingActionButton fab;
    FloatingActionButton fab_stop;
    View fab_panel;
    TextView status;
    boolean show;
    boolean play;
    int encoding;
    String phone;
    long sec;

    Recordings recordings;
    Storage storage;
    ListView list;
    Handler handler = new Handler();

    int themeId;

    BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String a = intent.getAction();
            if (a.equals(SHOW_PROGRESS)) {
                encoding = -1;
                show = intent.getBooleanExtra("show", false);
                play = intent.getBooleanExtra("play", false);
                sec = intent.getLongExtra("sec", 0);
                phone = intent.getStringExtra("phone");
                updatePanel();
            }
            if (a.equals(SET_PROGRESS)) {
                encoding = intent.getIntExtra("set", 0);
                updatePanel();
            }
            if (a.equals(SHOW_LAST)) {
                last();
            }
        }
    };

    public static void showProgress(Context context, boolean show, String phone, long sec, boolean play) {
        Intent intent = new Intent(SHOW_PROGRESS);
        intent.putExtra("show", show);
        intent.putExtra("play", play);
        intent.putExtra("sec", sec);
        intent.putExtra("phone", phone);
        context.sendBroadcast(intent);
    }

    public static void setProgress(Context context, int p) {
        Intent intent = new Intent(SET_PROGRESS);
        intent.putExtra("set", p);
        context.sendBroadcast(intent);
    }

    public static void last(Context context) {
        Intent intent = new Intent(SHOW_LAST);
        context.sendBroadcast(intent);
    }

    public static void startActivity(Context context) {
        Intent i = new Intent(context, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        i.addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(i);
    }

    public void setAppTheme(int id) {
        super.setTheme(id);
        themeId = id;
    }

    int getAppTheme() {
        return MainApplication.getTheme(this, R.style.RecThemeLight_NoActionBar, R.style.RecThemeDark_NoActionBar);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setAppTheme(getAppTheme());
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);

        storage = new Storage(this);

        IntentFilter ff = new IntentFilter();
        ff.addAction(SHOW_PROGRESS);
        ff.addAction(SET_PROGRESS);
        ff.addAction(SHOW_LAST);
        registerReceiver(receiver, ff);

        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        fab_panel = findViewById(R.id.fab_panel);
        status = (TextView) fab_panel.findViewById(R.id.status);

        fab_stop = (FloatingActionButton) findViewById(R.id.fab_stop);
        fab_stop.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                RecordingService.stopButton(MainActivity.this);
            }
        });

        fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                RecordingService.pauseButton(MainActivity.this);
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
            }
        });

        updatePanel();

        list = (ListView) findViewById(R.id.list);
        recordings = new Recordings(this, list) {
            @Override
            public void sort() {
                sort(Collections.reverseOrder(new SortFiles()));
            }
        };
        list.setAdapter(recordings);
        list.setEmptyView(findViewById(R.id.empty_list));

        if (Storage.permitted(this, PERMISSIONS, 1)) {
            storage.migrateLocalStorage();
        }

        RecordingService.startIfEnabled(this);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.menu_main, menu);

        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);

        MenuItem i = menu.findItem(R.id.action_call);
        i.setChecked(shared.getBoolean(MainApplication.PREFERENCE_CALL, false));
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar base clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_call) {
            item.setChecked(!item.isChecked());
            final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
            SharedPreferences.Editor edit = shared.edit();
            edit.putBoolean(MainApplication.PREFERENCE_CALL, item.isChecked());
            edit.commit();
            if (item.isChecked()) {
                RecordingService.startService(this);
                Toast.makeText(this, "Recording enabled", Toast.LENGTH_SHORT).show();
            } else {
                RecordingService.stopService(this);
                Toast.makeText(this, "Recording disabled", Toast.LENGTH_SHORT).show();
            }
        }

        if (id == R.id.action_show_folder) {
            Uri selectedUri = Uri.fromFile(storage.getStoragePath());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(selectedUri, "resource/folder");
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, R.string.no_folder_app, Toast.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume");

        if (themeId != getAppTheme()) {
            finish();
            MainActivity.startActivity(this);
            return;
        }

        if (Storage.permitted(this, PERMISSIONS))
            recordings.load();
        else
            recordings.load();

        updateHeader();

        fab.setClickable(true);
    }

    void last() {
        recordings.load();
        final int selected = getLastRecording();
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (selected != -1) {
                    recordings.select(selected);
                    list.smoothScrollToPosition(selected);
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            list.setSelection(selected);
                        }
                    });
                }
            }
        });
    }

    int getLastRecording() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String last = shared.getString(MainApplication.PREFERENCE_LAST, "");
        last = last.toLowerCase();
        for (int i = 0; i < recordings.getCount(); i++) {
            File f = recordings.getItem(i);
            String n = f.getName().toLowerCase();
            if (n.equals(last)) {
                SharedPreferences.Editor edit = shared.edit();
                edit.putString(MainApplication.PREFERENCE_LAST, "");
                edit.commit();
                return i;
            }
        }
        return -1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (Storage.permitted(this, permissions)) {
                    storage.migrateLocalStorage();
                    recordings.load();
                } else {
                    Toast.makeText(this, R.string.not_permitted, Toast.LENGTH_SHORT).show();
                }
        }
    }

    public static final String READ_EXTERNAL_STORAGE = "android.permission.READ_EXTERNAL_STORAGE"; // Manifest.permission.READ_EXTERNAL_STORAGE

    public static final String[] PERMISSIONS = new String[]{READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.PROCESS_OUTGOING_CALLS
    };

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        handler.post(new Runnable() {
            @Override
            public void run() {
                list.smoothScrollToPosition(recordings.getSelected());
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        recordings.close();
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    void updatePanel() {
        fab_panel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (encoding >= 0) {
            status.setText(getString(R.string.encoding_title) + encoding + "%");
            fab.setVisibility(View.GONE);
            fab_stop.setVisibility(View.INVISIBLE);
        } else {
            status.setText(phone + " - " + MainLibrary.formatDuration(this, sec * 1000));
            fab.setVisibility(show ? View.VISIBLE : View.GONE);
            fab_stop.setVisibility(View.VISIBLE);
        }
        if (play) {
            fab.setImageResource(R.drawable.ic_pause_black_24dp);
        } else {
            fab.setImageResource(R.drawable.ic_play_arrow_black_24dp);
        }
    }

    void updateHeader() {
        File f = storage.getStoragePath();
        long free = storage.getFree(f);
        long sec = storage.average(free);
        TextView text = (TextView) findViewById(R.id.space_left);
        text.setText(((MainApplication) getApplication()).formatFree(free, sec));
    }
}
