package com.github.axet.audiorecorder.encoders;

// Based on "wav IO based on code by Evan Merz"

// Wav file format parsed by Evan X. Merz
// www.thisisnotalabel.com

// Example Wav file input and output
// this was written for educational purposes, but feel free to use it for anything you like 
// as long as you credit me appropriately ("wav IO based on code by Evan Merz")

// if you catch any bugs in this, or improve upon it significantly, send me the changes
// at evan at thisisnotalabel dot com, so we can share your changes with the world

// http://computermusicblog.com/blog/2008/08/29/reading-and-writing-wav-files-in-java/

import java.io.*;

public class Wav implements Encoder {
/*
     WAV File Specification
     FROM http://ccrma.stanford.edu/courses/422/projects/WaveFormat/
    The canonical WAVE format starts with the RIFF header:
    0         4   ChunkID          Contains the letters "RIFF" in ASCII form
                                   (0x52494646 big-endian form).
    4         4   ChunkSize        36 + SubChunk2Size, or more precisely:
                                   4 + (8 + SubChunk1Size) + (8 + SubChunk2Size)
                                   This is the size of the rest of the chunk 
                                   following this number.  This is the size of the 
                                   entire file in bytes minus 8 bytes for the
                                   two fields not included in this count:
                                   ChunkID and ChunkSize.
    8         4   Format           Contains the letters "WAVE"
                                   (0x57415645 big-endian form).

    The "WAVE" format consists of two subchunks: "fmt " and "data":
    The "fmt " subchunk describes the sound data's format:
    12        4   Subchunk1ID      Contains the letters "fmt "
                                   (0x666d7420 big-endian form).
    16        4   Subchunk1Size    16 for PCM.  This is the size of the
                                   rest of the Subchunk which follows this number.
    20        2   AudioFormat      PCM = 1 (i.e. Linear quantization)
                                   Values other than 1 indicate some 
                                   form of compression.
    22        2   NumChannels      Mono = 1, Stereo = 2, etc.
    24        4   SampleRate       8000, 44100, etc.
    28        4   ByteRate         == SampleRate * NumChannels * BitsPerSample/8
    32        2   BlockAlign       == NumChannels * BitsPerSample/8
                                   The number of bytes for one sample including
                                   all channels. I wonder what happens when
                                   this number isn't an integer?
    34        2   BitsPerSample    8 bits = 8, 16 bits = 16, etc.

    The "data" subchunk contains the size of the data and the actual sound:
    36        4   Subchunk2ID      Contains the letters "data"
                                   (0x64617461 big-endian form).
    40        4   Subchunk2Size    == NumSamples * NumChannels * BitsPerSample/8
                                   This is the number of bytes in the data.
                                   You can also think of this as the size
                                   of the read of the subchunk following this 
                                   number.
    44        *   Data             The actual sound data.


NOTE TO READERS:

The thing that makes reading wav files tricky is that java has no unsigned types.  This means that the
binary data can't just be read and cast appropriately.  Also, we have to use larger types
than are normally necessary.

In many languages including java, an integer is represented by 4 bytes.  The issue here is
that in most languages, integers can be signed or unsigned, and in wav files the  integers
are unsigned.  So, to make sure that we can store the proper values, we have to use longs
to hold integers, and integers to hold shorts.

Then, we have to convert back when we want to save our wav data.

It's complicated, but ultimately, it just results in a few extra functions at the bottom of
this file.  Once you understand the issue, there is no reason to pay any more attention
to it.


ALSO:

This code won't read ALL wav files.  This does not use to full specification.  It just uses
a trimmed down version that most wav files adhere to.
*/

    long NumSamples;
    EncoderInfo info;
    int BytesPerSample;
    RandomAccessFile outFile;

    // I made this public so that you can toss whatever you want in here
    // maybe a recorded buffer, maybe just whatever you want
    public byte[] myData;

    // empty constructor
    public Wav() {
    }

    public Wav(EncoderInfo info, File out) {
        this.info = info;
        NumSamples = 0;

        BytesPerSample = info.bps / 8;

        try {
            outFile = new RandomAccessFile(out, "rw");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        save();
    }

    public void save() {
        long SubChunk2Size = NumSamples * info.channels * BytesPerSample;

        long SubChunk1Size = 16;

        int BlockAlign = info.bps * info.channels;

        short Format = 1;

        int ByteRate = info.sampleRate * info.channels * BytesPerSample;

        long ChunkSize = 4 + (8 + SubChunk1Size) + (8 + SubChunk2Size);

        try {
            // write the wav file per the wav file format
            outFile.writeBytes("RIFF");                    // 00 - RIFF
            outFile.write(intToByteArray((int) ChunkSize), 0, 4);        // 04 - how big is the rest of this file?
            outFile.writeBytes("WAVE");                    // 08 - WAVE
            outFile.writeBytes("fmt ");                    // 12 - fmt
            outFile.write(intToByteArray((int) SubChunk1Size), 0, 4);    // 16 - size of this chunk
            outFile.write(shortToByteArray((short) Format), 0, 2);        // 20 - what is the audio format? 1 for PCM = Pulse Code Modulation
            outFile.write(shortToByteArray((short) info.channels), 0, 2);    // 22 - mono or stereo? 1 or 2?  (or 5 or ???)
            outFile.write(intToByteArray((int) info.sampleRate), 0, 4);        // 24 - samples per second (numbers per second)
            outFile.write(intToByteArray((int) ByteRate), 0, 4);        // 28 - bytes per second
            outFile.write(shortToByteArray((short) BlockAlign), 0, 2);    // 32 - # of bytes in one sample, for all channels
            outFile.write(shortToByteArray((short) info.bps), 0, 2);    // 34 - how many bits in a sample(number)?  usually 16 or 24
            outFile.writeBytes("data");                    // 36 - data
            outFile.write(intToByteArray((int) SubChunk2Size), 0, 4);        // 40 - how big is this data chunk
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void encode(byte[] buf, int o, int l) {
        NumSamples += buf.length / info.channels / BytesPerSample;
        try {
            outFile.write(buf, o, l);                        // 44 - the actual data itself - just a long string of numbers
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void close() {
        try {
            outFile.seek(0);
            save();
            outFile.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    // returns a byte array of length 4
    private static byte[] intToByteArray(int i) {
        byte[] b = new byte[4];
        b[0] = (byte) (i & 0x00FF);
        b[1] = (byte) ((i >> 8) & 0x000000FF);
        b[2] = (byte) ((i >> 16) & 0x000000FF);
        b[3] = (byte) ((i >> 24) & 0x000000FF);
        return b;
    }

    // convert a short to a byte array
    public static byte[] shortToByteArray(short data) {
        return new byte[]{(byte) (data & 0xff), (byte) ((data >>> 8) & 0xff)};
    }

    public EncoderInfo getInfo() {
        return info;
    }

}