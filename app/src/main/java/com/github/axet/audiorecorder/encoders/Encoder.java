package com.github.axet.audiorecorder.encoders;

public interface Encoder {
    public EncoderInfo getInfo();

    public void encode(byte[] buf, int offset, int len);
}
