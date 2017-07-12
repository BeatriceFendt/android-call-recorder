# Permissions

Some devices may require adb command to Call Recorder app to work:

    adb shell pm grant com.github.axet.callrecorder android.permission.CAPTURE_AUDIO_OUTPUT

Some devices need Call Recorder by be signed with system keys and build within system image.
