package com.github.axet.callrecorder.activities;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.Nullable;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.view.ActionMode;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.SearchEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.github.axet.callrecorder.R;
import com.github.axet.callrecorder.app.MainApplication;
import com.github.axet.callrecorder.app.Storage;
import com.github.axet.callrecorder.services.RecordingService;

public class RecentCallActivity extends AppCompatActivity {

    public static int AUTO_CLOSE = 5; // secs

    Handler handler = new Handler();
    Runnable update = new Runnable() {
        @Override
        public void run() {
            update();
        }
    };
    View close;
    TextView count;
    ImageView fav;
    ProgressBar progressBar;
    EditText name;
    View msg;
    int c = 0;
    Uri uri;
    Storage storage;
    String old;
    String ext;
    AlertDialog alertDialog;

    public static void startActivity(Context context, Uri targetUri, boolean count) {
        Intent intent = new Intent(context, RecentCallActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("uri", targetUri);
        intent.putExtra("count", count);
        context.startActivity(intent);
    }

    public static void showLocked(Window w) {
        w.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON);
        // enable popup keyboard while locked
        w.addFlags(android.view.WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
    }

    public static Rect getOnScreenRect(Window w) {
        int[] loc = new int[2];
        View v = w.getDecorView();
        v.getLocationOnScreen(loc);
        return new Rect(loc[0], loc[1], loc[0] + v.getWidth(), loc[1] + v.getHeight());
    }

    public void setAppTheme(int id) {
        super.setTheme(id);
    }

    int getAppTheme() {
        return MainApplication.getTheme(this, R.style.AppThemeDialogLight, R.style.Theme_AppCompat_DayNight_Dialog);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        showLocked(getWindow());

        storage = new Storage(this);

        LayoutInflater inflater = LayoutInflater.from(this);
        final View v = inflater.inflate(R.layout.activity_recentcall, null);

        close = v.findViewById(R.id.callrecent_close);
        progressBar = (ProgressBar) v.findViewById(R.id.callrecent_progress);
        count = (TextView) v.findViewById(R.id.callrecent_autosave);
        msg = v.findViewById(R.id.callrecent_msg);
        name = (EditText) v.findViewById(R.id.callrecent_name);
        fav = (ImageView) v.findViewById(R.id.callrecent_fav);

        final boolean count = getIntent().getBooleanExtra("count", true);
        uri = getIntent().getParcelableExtra("uri");
        String f = Storage.getDocumentName(uri);
        old = Storage.getNameNoExt(f);
        ext = Storage.getExt(f);
        name.setText(old);

        fav.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                boolean b = MainApplication.getStar(RecentCallActivity.this, uri);
                MainApplication.setStar(RecentCallActivity.this, uri, !b);
                updateFav();
            }
        });

        alertDialog = new AlertDialog.Builder(this, getAppTheme())
                .setTitle(R.string.app_name)
                .setIcon(R.drawable.ic_mic_24dp)
                .setPositiveButton(R.string.save_button, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        save();
                    }
                })
                .setNeutralButton(R.string.delete_recording, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                    }
                })
                .setOnDismissListener(new DialogInterface.OnDismissListener() {
                    @Override
                    public void onDismiss(DialogInterface dialog) {
                        close();
                    }
                })
                .setView(v).create();

        alertDialog.setCancelable(false);

        alertDialog.setOnShowListener(new DialogInterface.OnShowListener() {
            @Override
            public void onShow(DialogInterface dialog) {
                Button b = alertDialog.getButton(DialogInterface.BUTTON_NEUTRAL);
                b.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        delete();
                    }
                });

                final Window w = alertDialog.getWindow();

                showLocked(w);

                final Window.Callback c = w.getCallback();
                w.setCallback(new Window.Callback() {
                    @Override
                    public boolean dispatchKeyEvent(KeyEvent event) {
                        onUserInteraction();
                        return c.dispatchKeyEvent(event);
                    }

                    @Override
                    public boolean dispatchKeyShortcutEvent(KeyEvent event) {
                        return c.dispatchKeyEvent(event);
                    }

                    @Override
                    public boolean dispatchTouchEvent(MotionEvent event) {
                        Rect rect = getOnScreenRect(w);
                        if (rect.contains((int) event.getRawX(), (int) event.getRawY()))
                            onUserInteraction();
                        return c.dispatchTouchEvent(event);
                    }

                    @Override
                    public boolean dispatchTrackballEvent(MotionEvent event) {
                        return c.dispatchTrackballEvent(event);
                    }

                    @Override
                    @TargetApi(12)
                    public boolean dispatchGenericMotionEvent(MotionEvent event) {
                        return c.dispatchGenericMotionEvent(event);
                    }

                    @Override
                    public boolean dispatchPopulateAccessibilityEvent(AccessibilityEvent event) {
                        return c.dispatchPopulateAccessibilityEvent(event);
                    }

                    @Nullable
                    @Override
                    public View onCreatePanelView(int featureId) {
                        return c.onCreatePanelView(featureId);
                    }

                    @Override
                    public boolean onCreatePanelMenu(int featureId, Menu menu) {
                        return c.onCreatePanelMenu(featureId, menu);
                    }

                    @Override
                    public boolean onPreparePanel(int featureId, View view, Menu menu) {
                        return c.onPreparePanel(featureId, view, menu);
                    }

                    @Override
                    public boolean onMenuOpened(int featureId, Menu menu) {
                        return c.onMenuOpened(featureId, menu);
                    }

                    @Override
                    public boolean onMenuItemSelected(int featureId, MenuItem item) {
                        return c.onMenuItemSelected(featureId, item);
                    }

                    @Override
                    public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {
                        c.onWindowAttributesChanged(attrs);
                    }

                    @Override
                    public void onContentChanged() {
                        c.onContentChanged();
                    }

                    @Override
                    public void onWindowFocusChanged(boolean hasFocus) {
                        c.onWindowFocusChanged(hasFocus);
                    }

                    @Override
                    public void onAttachedToWindow() {
                        c.onAttachedToWindow();
                    }

                    @Override
                    public void onDetachedFromWindow() {
                        c.onDetachedFromWindow();
                    }

                    @Override
                    public void onPanelClosed(int featureId, Menu menu) {
                        c.onPanelClosed(featureId, menu);
                    }

                    @Override
                    public boolean onSearchRequested() {
                        return c.onSearchRequested();
                    }

                    @Override
                    @TargetApi(23)
                    public boolean onSearchRequested(SearchEvent searchEvent) {
                        return c.onSearchRequested(searchEvent);
                    }

                    @Nullable
                    @Override
                    @TargetApi(11)
                    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback) {
                        return c.onWindowStartingActionMode(callback);
                    }

                    @Nullable
                    @Override
                    @TargetApi(23)
                    public ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type) {
                        return c.onWindowStartingActionMode(callback, type);
                    }

                    @Override
                    @TargetApi(11)
                    public void onActionModeStarted(ActionMode mode) {
                        c.onActionModeStarted(mode);
                    }

                    @Override
                    @TargetApi(11)
                    public void onActionModeFinished(ActionMode mode) {
                        c.onActionModeFinished(mode);
                    }
                });

                name.setSelection(name.getText().length());
                if (count)
                    update();
                else
                    countClose();
            }
        });

        alertDialog.show();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    void update() {
        int p = c / 100;

        if (p >= AUTO_CLOSE) {
            close();
            return;
        }

        count.setText(getString(R.string.callrecent_auto_save, AUTO_CLOSE - p));
        progressBar.setProgress(c * 100 / AUTO_CLOSE / 100);
        updateFav();

        c++;

        handler.removeCallbacks(update);
        handler.postDelayed(update, 10);
    }

    void updateFav() {
        fav.setImageResource(MainApplication.getStar(this, uri) ? R.drawable.ic_star_black_24dp : R.drawable.ic_star_border_black_24dp);
    }

    void countClose() {
        handler.removeCallbacks(update);
        msg.setVisibility(View.GONE);
    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        countClose();
    }

    @Override
    protected void onStart() {
        super.onStart();
        setVisible(true);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        countClose();
    }

    void close() {
        finish();
    }

    void save() {
        String n = name.getText().toString();
        if (!old.equals(n)) {
            String s = String.format("%s.%s", n, ext);
            storage.rename(uri, s);
            MainActivity.last(this);
        }
    }

    void delete() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(com.github.axet.audiolibrary.R.string.delete_recording);
        builder.setMessage("...\\" + Storage.getDocumentName(uri) + "\n\n" + getString(com.github.axet.audiolibrary.R.string.are_you_sure));
        builder.setPositiveButton(com.github.axet.audiolibrary.R.string.yes, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                storage.delete(uri);
                MainActivity.last(RecentCallActivity.this);
                alertDialog.dismiss();
            }
        });
        builder.setNegativeButton(com.github.axet.audiolibrary.R.string.no, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        builder.show();
    }

}
