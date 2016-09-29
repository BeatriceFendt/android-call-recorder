package com.github.axet.audiorecorder.encoders;

public interface Encoder {
    void encode(short[] buf, int len);

    void flush();

    void close();
}
