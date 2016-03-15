package com.github.axet.audiorecorder.activities;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
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
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.animations.RecordingAnimation;
import com.github.axet.audiorecorder.animations.RemoveItemAnimation;
import com.github.axet.audiorecorder.app.MainApplication;
import com.github.axet.audiorecorder.app.Storage;
import com.github.axet.audiorecorder.widgets.PopupShareActionProvider;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity implements AbsListView.OnScrollListener {
    public final static String TAG = MainActivity.class.getSimpleName();

    static final int TYPE_COLLAPSED = 0;
    static final int TYPE_EXPANDED = 1;
    static final int TYPE_DELETED = 2;

    final int[] ALL = {TYPE_COLLAPSED, TYPE_EXPANDED};

    int selected = -1;

    int scrollState;

    Recordings recordings;
    Storage storage;
    ListView list;
    Handler handler;

    static class SortFiles implements Comparator<File> {
        @Override
        public int compare(File file, File file2) {
            if (file.isDirectory() && file2.isFile())
                return -1;
            else if (file.isFile() && file2.isDirectory())
                return 1;
            else
                return file.getPath().compareTo(file2.getPath());
        }
    }

    public class Recordings extends ArrayAdapter<File> {
        MediaPlayer player;
        Runnable updatePlayer;
        PopupShareActionProvider shareProvider;

        Map<File, Integer> duration = new TreeMap<>();

        public Recordings(Context context) {
            super(context, 0);
        }

        public void scan(File dir) {
            clear();
            duration.clear();

            List<File> ff = storage.scan(dir);

            for (File f : ff) {
                if (f.isFile()) {
                    MediaPlayer mp = MediaPlayer.create(getContext(), Uri.fromFile(f));
                    if (mp != null) {
                        int d = mp.getDuration();
                        mp.release();
                        duration.put(f, d);
                        add(f);
                    } else {
                        Log.e(TAG, f.toString());
                    }
                }
            }

            sort(new SortFiles());
        }

        String formatSize(long s) {
            if (s > 0.1 * 1024 * 1024) {
                float f = s / 1024f / 1024f;
                return String.format("%.1f MB", f);
            } else {
                float f = s / 1024f;
                return String.format("%.1f kb", f);
            }
        }

        public void close() {
            if (player != null) {
                player.release();
                player = null;
            }
        }

        @Override
        public View getView(final int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = LayoutInflater.from(getContext());

            if (convertView == null) {
                convertView = inflater.inflate(R.layout.recording, parent, false);
                convertView.setTag(-1);
            }

            if ((int) convertView.getTag() == TYPE_DELETED) {
                RemoveItemAnimation.restore(convertView);
                convertView.setTag(-1);
            }

            final View view = convertView;
            final View base = convertView.findViewById(R.id.recording_base);

            final File f = getItem(position);

            TextView title = (TextView) convertView.findViewById(R.id.recording_title);
            title.setText(f.getName());

            SimpleDateFormat s = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            TextView time = (TextView) convertView.findViewById(R.id.recording_time);
            time.setText(s.format(new Date(f.lastModified())));

            TextView dur = (TextView) convertView.findViewById(R.id.recording_duration);
            dur.setText(MainApplication.formatDuration(duration.get(f)));

            TextView size = (TextView) convertView.findViewById(R.id.recording_size);
            size.setText(formatSize(f.length()));

            View trash = convertView.findViewById(R.id.recording_player_trash);
            trash.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
                    builder.setTitle("Delete Recording");
                    builder.setMessage("...\\" + f.getName() + "\n\n" + "Are you sure ? ");
                    builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialog, int which) {
                            playerStop();
                            dialog.cancel();
                            RemoveItemAnimation.apply(list, base, new Runnable() {
                                @Override
                                public void run() {
                                    f.delete();
                                    view.setTag(TYPE_DELETED);
                                    selected = -1;
                                    load();
                                }
                            });
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
            });

            final View playerBase = convertView.findViewById(R.id.recording_player);
            playerBase.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                }
            });

            if (selected == position) {
                RecordingAnimation.apply(list, convertView, true, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_COLLAPSED);
                convertView.setTag(TYPE_EXPANDED);

                updatePlayerText(convertView, f);

                final View play = convertView.findViewById(R.id.recording_player_play);
                play.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        if (player == null) {
                            playerPlay(playerBase, f);
                        } else if (player.isPlaying()) {
                            playerPause();
                        } else {
                            playerPlay(playerBase, f);
                        }
                    }
                });

                final View share = convertView.findViewById(R.id.recording_player_share);
                share.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        shareProvider = new PopupShareActionProvider(getContext(), share);

                        Intent emailIntent = new Intent(Intent.ACTION_SEND);
                        emailIntent.setType("audio/mp4a-latm");
                        emailIntent.putExtra(Intent.EXTRA_EMAIL, "");
                        emailIntent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(f));
                        emailIntent.putExtra(Intent.EXTRA_SUBJECT, f.getName());
                        emailIntent.putExtra(Intent.EXTRA_TEXT, "Shared via App Recorder");

                        shareProvider.setShareIntent(emailIntent);

                        shareProvider.show();

                        Log.d("123", "show");
                    }
                });

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        playerStop();
                        selected = -1;
                        notifyDataSetChanged();
                    }
                });
            } else {
                RecordingAnimation.apply(list, convertView, false, scrollState == SCROLL_STATE_IDLE && (int) convertView.getTag() == TYPE_EXPANDED);
                convertView.setTag(TYPE_COLLAPSED);

                convertView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        selected = position;
                        notifyDataSetChanged();

                        playerStop();
                    }
                });
            }

            return convertView;
        }

        void playerPlay(View v, File f) {
            if (player == null)
                player = MediaPlayer.create(getContext(), Uri.fromFile(f));
            if (player == null) {
                Toast.makeText(MainActivity.this, "File not found", Toast.LENGTH_SHORT).show();
                return;
            }
            player.start();

            updatePlayerRun(v, f);
        }

        void playerPause() {
            if (player != null) {
                player.pause();
            }
            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }
        }

        void playerStop() {
            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }
            if (player != null) {
                player.stop();
                player.release();
                player = null;
            }
        }

        void updatePlayerRun(final View v, final File f) {
            boolean playing = updatePlayerText(v, f);

            if (updatePlayer != null) {
                handler.removeCallbacks(updatePlayer);
                updatePlayer = null;
            }

            if (!playing) {
                return;
            }

            updatePlayer = new Runnable() {
                @Override
                public void run() {
                    updatePlayerRun(v, f);
                }
            };
            handler.postDelayed(updatePlayer, 200);
        }

        boolean updatePlayerText(final View v, final File f) {
            ImageView i = (ImageView) v.findViewById(R.id.recording_player_play);

            final boolean playing = player != null && player.isPlaying();

            i.setImageResource(playing ? R.drawable.pause : R.drawable.play);

            TextView start = (TextView) v.findViewById(R.id.recording_player_start);
            SeekBar bar = (SeekBar) v.findViewById(R.id.recording_player_seek);
            TextView end = (TextView) v.findViewById(R.id.recording_player_end);

            int c = 0;
            int d = duration.get(f);

            if (player != null) {
                c = player.getCurrentPosition();
                d = player.getDuration();
            }

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    if (!fromUser)
                        return;

                    if (player == null)
                        playerPlay(v, f);

                    if (player != null) {
                        player.seekTo(progress);
                        if (!player.isPlaying())
                            playerPlay(v, f);
                    }
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {

                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {

                }
            });

            start.setText(MainApplication.formatDuration(c));
            bar.setMax(d);
            bar.setKeyProgressIncrement(1);
            bar.setProgress(c);
            end.setText("-" + MainApplication.formatDuration(d - c));

            return playing;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        storage = new Storage(this);
        handler = new Handler();

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
        recordings = new Recordings(this);
        list.setAdapter(recordings);
        list.setEmptyView(findViewById(R.id.empty_list));

        if (permitted()) {
            storage.migrateLocalStorage();
        }
    }

    // load recordings
    void load() {
        recordings.scan(storage.getStoragePath());
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

        if (id == R.id.action_show_folder) {
            Uri selectedUri = Uri.fromFile(storage.getStoragePath());
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setDataAndType(selectedUri, "resource/folder");
            if (intent.resolveActivityInfo(getPackageManager(), 0) != null) {
                startActivity(intent);
            } else {
                Toast.makeText(this, "No folder view application installed", Toast.LENGTH_SHORT).show();
            }
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (permitted(PERMISSIONS))
            load();
        else
            load();

        final int selected = getLastPosition();
        list.setSelection(selected);
        if (selected != -1) {
            handler.post(new Runnable() {
                @Override
                public void run() {
                    MainActivity.this.selected = selected;
                    recordings.notifyDataSetChanged();
                }
            });
        }
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
        SharedPreferences.Editor edit = shared.edit();
        edit.putString(MainApplication.PREFERENCE_LAST, "");
        edit.commit();
    }

    int getLastPosition() {
        final SharedPreferences shared = PreferenceManager.getDefaultSharedPreferences(this);
        String last = shared.getString(MainApplication.PREFERENCE_LAST, "");
        last = last.toLowerCase();

        for (int i = 0; i < recordings.getCount(); i++) {
            File f = recordings.getItem(i);
            String n = f.getName().toLowerCase();
            if (n.equals(last))
                return i;
        }
        return -1;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case 1:
                if (permitted(permissions)) {
                    storage.migrateLocalStorage();
                    load();
                } else {
                    Toast.makeText(this, "Not permitted", Toast.LENGTH_SHORT).show();
                }
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

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        handler.post(new Runnable() {
            @Override
            public void run() {
                list.smoothScrollToPosition(selected);
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        recordings.close();
    }
}
