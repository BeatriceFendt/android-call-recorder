package com.github.axet.callrecorder.widgets;

import android.content.Context;
import android.support.v7.preference.SwitchPreferenceCompat;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.Toast;

import com.github.axet.callrecorder.R;
import com.github.axet.callrecorder.app.MixerPaths;

public class MixerPathsPreferenceCompat extends SwitchPreferenceCompat {
    public static String TAG = MixerPathsPreferenceCompat.class.getSimpleName();

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
        try {
            MixerPaths m = new MixerPaths();
            m.setEnabled(b);
            m.save();
            m.load();
            if (b != m.isEnabled()) {
                throw new RuntimeException("Unable to write changes");
            } else {
                Toast.makeText(getContext(), R.string.mixer_paths_done, Toast.LENGTH_SHORT).show();
            }
        } catch (RuntimeException e) {
            Log.d(TAG, "uanble to patch", e);
            Toast.makeText(getContext(), getContext().getString(R.string.mixer_paths_failed) + " " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
