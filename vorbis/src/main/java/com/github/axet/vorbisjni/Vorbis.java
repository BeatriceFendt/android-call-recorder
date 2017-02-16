package com.github.axet.vorbisjni;

public class Vorbis {
    static {
        System.loadLibrary("ogg"); // API16 failed to find ogg
        System.loadLibrary("vorbis"); // API16 failed to find vorbis
        System.loadLibrary("vorbisjni");
    }

    private long handle;

    public native void open(int channels, int sampleRate, float quality);

    public native byte[] encode(short[] buf, int len);

    public native void close();

}
