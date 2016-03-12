package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.DataSetObserver;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.animations.RecordingAnimation;
import com.github.axet.audiorecorder.app.Storage;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;

public class MainActivity extends AppCompatActivity implements  AbsListView.OnScrollListener{
    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    final int[] ALL = {TYPE_COLLAPSED, TYPE_EXPANDED};

    int selected;

    int scrollState;

    Recordings recordings;
    Storage storage;
    ListView list;

    public class Recordings extends ArrayAdapter<File> {

        public Recordings(Context context) {
            super(context, 0);
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.recording, parent, false);
                convertView.setTag(-1);
            }

            File f = getItem(position);

            TextView title = (TextView) convertView.findViewById(R.id.recording_title);
            title.setText(f.getName());

            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            TextView time = (TextView) convertView.findViewById(R.id.recording_time);
            time.setText(s.format(new Date(f.lastModified())));

            View player = convertView.findViewById(R.id.recording_player);
            player.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            if(selected == position) {
                RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_COLLAPSED);
                convertView.setTag(TYPE_EXPANDED);

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selected = -1;
                        notifyDataSetChanged();
                    }
                });
            }else {
                RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_EXPANDED);
                convertView.setTag(TYPE_COLLAPSED);

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selected = position;
                        notifyDataSetChanged();
                    }
                });
            }

            return convertView;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        FloatingActionButton fab = (FloatingActionButton) findViewById(R.id.fab);
        fab.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(MainActivity.this, RecordingActivity.class);
                startActivity(intent);
//                Snackbar.make(view, "Replace with your own action", Snackbar.LENGTH_LONG)
//                        .setAction("Action", null).show();
            }
        });

        list = (ListView) findViewById(R.id.list);
        list.setOnScrollListener(this);
        list.setEmptyView(findViewById(R.id.empty_list));

        if (permitted())
            load();
    }

    // load recordings
    void load() {
        storage = new Storage(this);

        File f = storage.getStoragePath();
        File[] ff = f.listFiles();

        ArrayList<File> a = null;

        if (ff != null) {
            a = new ArrayList<File>(Arrays.asList(ff));
        } else {
            a = new ArrayList<File>();
        }

        recordings = new Recordings(this);
        recordings.addAll(a);
        list.setAdapter(recordings);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);

        getMenuInflater().inflate(R.menu.menu_main, menu);

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
            Intent intent = new Intent(this, SettingsActivity.class);
            startActivity(intent);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (permitted(PERMISSIONS))
            load();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (permitted(permissions))
                    load();
                else
                    Toast.makeText(this, "Not permitted", Toast.LENGTH_SHORT).show();
        }
    }

    public static final String[] PERMISSIONS = new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE};

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

    @Override
    public void onScrollStateChanged(AbsListView view, int scrollState) {
        this.scrollState = scrollState;
    }

    @Override
    public void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount) {
    }
}
