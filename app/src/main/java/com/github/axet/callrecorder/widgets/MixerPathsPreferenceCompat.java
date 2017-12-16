package com.github.axet.callrecorder.widgets;

import android.content.Context;
import android.content.DialogInterface;
import android.support.v7.app.AlertDialog;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.github.axet.callrecorder.R;
import com.github.axet.callrecorder.app.MixerPaths;

public class MixerPathsPreferenceCompat extends SwitchPreferenceCompat {
    public static String TAG = MixerPathsPreferenceCompat.class.getSimpleName();

    public static void save(Context context, boolean b) {
        try {
            MixerPaths m = new MixerPaths();
            m.save(b);
            Toast.makeText(context, R.string.mixer_paths_done, Toast.LENGTH_SHORT).show();
        } catch (RuntimeException e) {
            Log.d(TAG, "uanble to patch", e);
            Toast.makeText(context, context.getString(R.string.mixer_paths_failed) + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    public static void show(final Context context) {
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setTitle(R.string.pref_mixerpaths_title);
        builder.setMessage(R.string.pref_mixerpaths_summary);
        builder.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                save(context, true);
            }
        });
        builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
            }
        });
        builder.show();
    }

    public MixerPathsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        onResume();
    }

    public MixerPathsPreferenceCompat(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        onResume();
    }

    public MixerPathsPreferenceCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
        onResume();
    }

    public MixerPathsPreferenceCompat(Context context) {
        super(context);
        onResume();
    }

    public void onResume() {
        MixerPaths m = new MixerPaths();
        if (!m.isCompatible()) {
            setVisible(false);
            return;
        }
        setVisible(true);
        setChecked(m.isEnabled());
    }

    @Override
    protected void onClick() {
        super.onClick();
        boolean b = isChecked();
        save(getContext(), b);
    }
}
