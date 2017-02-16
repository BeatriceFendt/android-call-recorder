package com.github.axet.audiorecorder.encoders;

import com.github.axet.vorbisjni.Vorbis;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

public class FormatOGG implements Encoder {
    FileOutputStream writer;
    Vorbis vorbis;

    public FormatOGG(EncoderInfo info, File out) {
        vorbis = new Vorbis();
        vorbis.open(info.channels, info.sampleRate, 0.4f);
        try {
            writer = new FileOutputStream(out);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int len) {
        byte[] bb = vorbis.encode(buf, len);
        try {
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            byte[] bb = vorbis.encode(null, 0);
            writer.write(bb);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        vorbis.close();
    }
}
