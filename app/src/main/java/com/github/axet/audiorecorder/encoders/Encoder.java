package com.github.axet.audiorecorder.encoders;

public interface Encoder {
    void encode(short[] buf, int len);

    void end(); // flush stream. may throw state exceptions

    void close(); // release native resources, sholud not throw exceptions
}
