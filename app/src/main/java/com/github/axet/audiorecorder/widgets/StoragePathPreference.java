package com.github.axet.audiorecorder.widgets;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.res.TypedArray;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Parcel;
import android.os.Parcelable;
import android.preference.DialogPreference;
import android.preference.EditTextPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import com.github.axet.androidlibrary.widgets.OpenFileDialog;
import com.github.axet.audiorecorder.R;
import com.github.axet.audiorecorder.activities.SettingsActivity;

import java.io.File;

public class StoragePathPreference extends EditTextPreference {
    OpenFileDialog f;

    public StoragePathPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public StoragePathPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public StoragePathPreference(Context context) {
        super(context);
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
    }

    @Override
    protected void showDialog(Bundle state) {
        f = new OpenFileDialog(getContext());
        f.setCurrentPath(new File(getText()));
        f.setFolderIcon(R.drawable.ic_folder_24dp);
        f.setFileIcon(R.drawable.ic_file);
        f.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                File ff = f.getCurrentPath();
                String fileName = ff.getPath();
                if (!ff.isDirectory())
                    fileName = ff.getParent();
                if (callChangeListener(fileName)) {
                    setText(fileName);
                }
            }
        });
        f.show();
    }

    @Override
    protected Object onGetDefaultValue(TypedArray a, int index) {
        String s = a.getString(index);
        if (s.isEmpty()) {
            s = new File(Environment.getExternalStorageDirectory(), "Audio Recorder").getAbsolutePath();
        }
        return s;
    }

    @Override
    protected void onRestoreInstanceState(Parcelable state) {
        super.onRestoreInstanceState(state);
    }
}
