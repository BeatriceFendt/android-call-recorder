package com.github.axet.audiorecorder.encoders;

import android.annotation.TargetApi;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.os.Build;

import org.ebml.io.FileDataWriter;
import org.ebml.matroska.MatroskaFileFrame;
import org.ebml.matroska.MatroskaFileTrack;
import org.ebml.matroska.MatroskaFileWriter;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

@TargetApi(16) // mp4/aac codec
public class FormatMKA implements Encoder {
    EncoderInfo info;
    MediaCodec encoder;
    long NumSamples;
    ByteBuffer input;
    int inputIndex;
    MatroskaFileWriter writer;
    MatroskaFileTrack track;
    MatroskaFileTrack.MatroskaAudioTrack audio;

    MatroskaFileFrame old;

    public FormatMKA(EncoderInfo info, File out) {
        MediaFormat format = new MediaFormat();
        format.setString(MediaFormat.KEY_MIME, Factory.MP4A);
        format.setInteger(MediaFormat.KEY_SAMPLE_RATE, info.sampleRate);
        format.setInteger(MediaFormat.KEY_CHANNEL_COUNT, info.channels);
        format.setInteger(MediaFormat.KEY_BIT_RATE, 64 * 1024);
        format.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectHE);
        format.setInteger(MediaFormat.KEY_AAC_SBR_MODE, 0);
        create(info, format, out);
    }

    public void create(EncoderInfo info, MediaFormat format, File out) {
        this.info = info;
        try {
            encoder = MediaCodec.createEncoderByType(format.getString(MediaFormat.KEY_MIME));
            encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
            encoder.start();
            writer = new MatroskaFileWriter(new FileDataWriter(out.getAbsolutePath()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void encode(short[] buf, int len) {
        for (int offset = 0; offset < len; offset++) {
            if (input == null) {
                inputIndex = encoder.dequeueInputBuffer(-1);
                if (inputIndex < 0)
                    throw new RuntimeException("unable to open encoder input buffer");
                if (Build.VERSION.SDK_INT >= 21)
                    input = encoder.getInputBuffer(inputIndex);
                else
                    input = encoder.getInputBuffers()[inputIndex];
                input.clear();
            }
            input.putShort(buf[offset]);
            if (!input.hasRemaining()) {
                queue();
            }
        }
    }

    void queue() {
        if (input == null)
            return;
        encoder.queueInputBuffer(inputIndex, 0, input.position(), getCurrentTimeStamp(), 0);
        NumSamples += input.position() / info.channels / (Short.SIZE / 8);
        input = null;
        while (encode())
            ;// do encode()
    }

    public static ByteBuffer clone(ByteBuffer original) {
        ByteBuffer clone = ByteBuffer.allocate(original.capacity());
        original.rewind();//copy from the beginning
        clone.put(original);
        original.rewind();
        clone.flip();
        return clone;
    }

    public static final int BUFFER_FLAG_KEY_FRAME = 1; // MediaCodec.BUFFER_FLAG_KEY_FRAME

    boolean encode() {
        MediaCodec.BufferInfo outputInfo = new MediaCodec.BufferInfo();
        int outputIndex = encoder.dequeueOutputBuffer(outputInfo, 0);
        if (outputIndex == MediaCodec.INFO_TRY_AGAIN_LATER)
            return false;

        if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
            audio = new MatroskaFileTrack.MatroskaAudioTrack();
            audio.setSamplingFrequency(info.sampleRate);
            audio.setOutputSamplingFrequency(info.sampleRate);
            audio.setBitDepth(info.bps);
            audio.setChannels((short) info.channels);
            track = new MatroskaFileTrack();
            track.setCodecID("A_AAC");
            track.setAudio(audio);
            track.setTrackType(MatroskaFileTrack.TrackType.AUDIO);
            writer.addTrack(track);
            return true;
        }

        if (outputIndex >= 0) {
            ByteBuffer output;
            if (Build.VERSION.SDK_INT >= 21)
                output = encoder.getOutputBuffer(outputIndex);
            else
                output = encoder.getOutputBuffers()[outputIndex];
            output.position(outputInfo.offset);
            output.limit(outputInfo.offset + outputInfo.size);
            old(outputInfo.presentationTimeUs / 1000);
            if ((outputInfo.flags & MediaCodec.BUFFER_FLAG_CODEC_CONFIG) == MediaCodec.BUFFER_FLAG_CODEC_CONFIG) {
                track.setCodecPrivate(clone(output));
                writer.flush();
                encoder.releaseOutputBuffer(outputIndex, false);
            } else {
                MatroskaFileFrame frame = new MatroskaFileFrame();
                frame.setKeyFrame((outputInfo.flags & BUFFER_FLAG_KEY_FRAME) == BUFFER_FLAG_KEY_FRAME);
                frame.setTimecode(outputInfo.presentationTimeUs / 1000);
                frame.setTrackNo(track.getTrackNo());
                frame.setData(clone(output));
                encoder.releaseOutputBuffer(outputIndex, false);
                old = frame;
            }
        }

        return true;
    }

    void old(long cur) {
        if (old != null) {
            old.setDuration(cur - old.getTimecode());
            writer.addFrame(old);
            writer.flush();
            old = null;
        }
    }

    public void close() {
        end();
        encoder.release();
        writer.close();
    }

    long getCurrentTimeStamp() {
        return NumSamples * 1000 * 1000 / info.sampleRate;
    }

    public void end() {
        if (input != null) {
            queue();
        }
        int inputIndex = encoder.dequeueInputBuffer(-1);
        if (inputIndex >= 0) {
            ByteBuffer input;
            if (Build.VERSION.SDK_INT >= 21)
                input = encoder.getInputBuffer(inputIndex);
            else
                input = encoder.getInputBuffers()[inputIndex];
            input.clear();
            encoder.queueInputBuffer(inputIndex, 0, 0, getCurrentTimeStamp(), MediaCodec.BUFFER_FLAG_END_OF_STREAM);
        }
        while (encode())
            ;// do encode()
        old(getCurrentTimeStamp() / 1000);
        writer.setDuration(getCurrentTimeStamp() / 1000);
        encoder.stop();
    }

    public EncoderInfo getInfo() {
        return info;
    }
}
