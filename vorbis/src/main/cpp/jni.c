#include <jni.h>
#include <vorbis/vorbisenc.h>
#include <stdlib.h>
#include <stdio.h>

typedef struct {
    vorbis_info vi;
    vorbis_dsp_state vd;
    vorbis_comment vc;
    vorbis_block vb;
    ogg_stream_state os;
    ogg_page og;
    ogg_packet op;
    int header;
} vorbis_t;

// https://svn.xiph.org/trunk/vorbis/examples/encoder_example.c

JNIEXPORT void JNICALL
Java_com_github_axet_vorbisjni_Vorbis_open(JNIEnv *env, jobject thiz, jint channels,
                                        jint sampleFreq, jfloat q) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");

    vorbis_t *v = malloc(sizeof(vorbis_t));
    v->header = 0;
    (*env)->SetLongField(env, thiz, fid, (jlong) v);

    int ret;

    vorbis_info_init(&v->vi);

    ret = vorbis_encode_init_vbr(&v->vi, channels, sampleFreq, q);
    if (ret) {
        jclass class_rex = (*env)->FindClass(env, "java/io/RuntimeException");
        (*env)->ThrowNew(env, class_rex, "Bad vorbis_encode_init_vbr");
        return;
    }

    ret = vorbis_analysis_init(&v->vd, &v->vi);
    if (ret) {
        jclass class_rex = (*env)->FindClass(env, "java/io/RuntimeException");
        (*env)->ThrowNew(env, class_rex, "Bad vorbis_analysis_init");
        return;
    }

    vorbis_comment_init(&v->vc);
    vorbis_comment_add_tag(&v->vc, "ENCODER", "android-audio-recorder");

    vorbis_analysis_init(&v->vd, &v->vi);
    vorbis_block_init(&v->vd, &v->vb);

    ogg_stream_init(&v->os, rand());
    return;
}

int bwrite(const char *buf, int len, char **out, int *outlen) {
    if (*out == 0) {
        *out = malloc(len);
    } else {
        *out = realloc(*out, *outlen + len);
    }
    char *o = *out + *outlen;
    memcpy(o, buf, len);
    *outlen = *outlen + len;
    return len;
}

JNIEXPORT jbyteArray JNICALL
Java_com_github_axet_vorbisjni_Vorbis_encode(JNIEnv *env, jobject thiz,
                                          jshortArray array, jint len) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");
    vorbis_t *v = (vorbis_t *) (*env)->GetLongField(env, thiz, fid);

    char *out = 0;
    int outlen = 0;

    if (v->header == 0) {
        ogg_packet header;
        ogg_packet header_comm;
        ogg_packet header_code;
        vorbis_analysis_headerout(&v->vd, &v->vc, &header, &header_comm, &header_code);
        ogg_stream_packetin(&v->os, &header);
        ogg_stream_packetin(&v->os, &header_comm);
        ogg_stream_packetin(&v->os, &header_code);
        while (ogg_stream_flush(&v->os, &v->og)) {
            bwrite(v->og.header, v->og.header_len, &out, &outlen);
            bwrite(v->og.body, v->og.body_len, &out, &outlen);
        }
        v->header = 1;
    }

    long i;

    if (len == 0) {
        /* end of file.  this can be done implicitly in the mainline,
           but it's easier to see here in non-clever fashion.
           Tell the library we're at end of stream so that it can handle
           the last frame and mark end of stream in the output properly */
        vorbis_analysis_wrote(&v->vd, 0);
    } else {
        jshort *bufferPtr = (*env)->GetShortArrayElements(env, array, NULL);

        /* expose the buffer to submit data */
        float **buffer = vorbis_analysis_buffer(&v->vd, len);

        long samples = len / v->vi.channels;
        for (i = 0; i < samples; i++) {
            for (int j = 0; j < v->vi.channels; j++)
                buffer[j][i] = bufferPtr[i * v->vi.channels + j] / 32768.f;
        }

        /* tell the library how much we actually submitted */
        vorbis_analysis_wrote(&v->vd, i);

        (*env)->ReleaseShortArrayElements(env, array, bufferPtr, 0);
    }

    /* vorbis does some data preanalysis, then divvies up blocks for
       more involved (potentially parallel) processing.  Get a single
       block for encoding now */
    while (vorbis_analysis_blockout(&v->vd, &v->vb) == 1) {
        /* analysis, assume we want to use bitrate management */
        vorbis_analysis(&v->vb, NULL);
        vorbis_bitrate_addblock(&v->vb);

        while (vorbis_bitrate_flushpacket(&v->vd, &v->op)) {
            /* weld the packet into the bitstream */
            ogg_stream_packetin(&v->os, &v->op);

            /* write out pages (if any) */
            while (ogg_stream_pageout(&v->os, &v->og)) {
                bwrite(v->og.header, v->og.header_len, &out, &outlen);
                bwrite(v->og.body, v->og.body_len, &out, &outlen);
            }
        }
    }

    jbyteArray ret = (*env)->NewByteArray(env, outlen);
    (*env)->SetByteArrayRegion(env, ret, 0, outlen, out);
    free(out);
    return ret;
}

JNIEXPORT void JNICALL
Java_com_github_axet_vorbisjni_Vorbis_close(JNIEnv *env, jobject thiz) {
    jclass cls = (*env)->GetObjectClass(env, thiz);
    jfieldID fid = (*env)->GetFieldID(env, cls, "handle", "J");
    vorbis_t *v = (vorbis_t *) (*env)->GetLongField(env, thiz, fid);

    ogg_stream_clear(&v->os);
    vorbis_block_clear(&v->vb);
    vorbis_dsp_clear(&v->vd);
    vorbis_comment_clear(&v->vc);
    vorbis_info_clear(&v->vi);
    free(v);

    (*env)->SetLongField(env, thiz, fid, 0);
}
