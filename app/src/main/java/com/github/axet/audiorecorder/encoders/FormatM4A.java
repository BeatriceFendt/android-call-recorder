package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.media.MediaMuxer;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(21)
public class FormatM4A implements Encoder {
    EncoderInfo info;
    MediaCodec encoder;
    MediaMuxer muxer;
    int audioTrackIndex;
    long NumSamples;

    public FormatM4A() {
    }

    public FormatM4A(EncoderInfo info, File out) {
        this.info = info;

        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);

        try {
            encoder = MediaCodec.createEncoderByType("audio/mp4a-latm");
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();

            muxer = new MediaMuxer(out.getAbsolutePath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void encode(short[] buf) {
        int len = buf.length * 2;
        int inputIndex = encoder.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer input = encoder.getInputBuffer(inputIndex);
            input.clear();
            for (int i = 0; i < buf.length; i++)
                input.putShort(buf[i]);
            encoder.queueInputBuffer(inputIndex, 0, len, getCurrentTimeStamp(), 0);
        }

        NumSamples += buf.length / info.channels;

        encode();
    }

    void encode() {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0);
        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audioTrackIndex = muxer.addTrack(encoder.getOutputFormat());
            muxer.start();
        }

        while (outputIndex >= 0) {
            ByteBuffer output = encoder.getOutputBuffer(outputIndex);
            output.position(outputInfo.offset);
            output.limit(outputInfo.offset + outputInfo.size);

//            byte[] out = new byte[outputInfo.size];
//            output.get(out);

            muxer.writeSampleData(audioTrackIndex, output, outputInfo);

            encoder.releaseOutputBuffer(outputIndex, false);
            outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0);
        }
    }

    public void close() {
        end();
        encode();

        encoder.stop();
        encoder.release();

        muxer.stop();
        muxer.release();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 * 1000 / info.sampleRate;
    }

    void end() {
        int inputIndex = encoder.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer input = encoder.getInputBuffer(inputIndex);
            input.clear();
            encoder.queueInputBuffer(inputIndex, 0, 0, getCurrentTimeStamp(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
    }

    public EncoderInfo getInfo() {
        return info;
    }

}