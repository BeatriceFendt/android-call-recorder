package com.github.axet.audiorecorder.encoders;

public interface Encoder {
    void encode(short[] buf);

    void flush();

    void close();
}
