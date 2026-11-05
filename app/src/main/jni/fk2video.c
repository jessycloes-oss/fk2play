/*
 * FK2Play - decodeur H.264 logiciel (FFmpeg) avec rendu direct dans une Surface Android.
 * Compatible Android 4.0.4 (API 15) : n'utilise que ANativeWindow (API 9) et libavcodec.
 */
#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>

#define TAG "FK2Video"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

typedef struct {
    AVCodecContext *ctx;
    AVFrame *frame;
    AVPacket *pkt;
    struct SwsContext *sws;
    ANativeWindow *window;
    int sws_w, sws_h, sws_fmt;
} Decoder;

static void render(Decoder *d) {
    AVFrame *f = d->frame;
    if (d->sws == NULL || d->sws_w != f->width || d->sws_h != f->height || d->sws_fmt != f->format) {
        if (d->sws) sws_freeContext(d->sws);
        d->sws = sws_getContext(f->width, f->height, (enum AVPixelFormat) f->format,
                                f->width, f->height, AV_PIX_FMT_RGB565LE,
                                SWS_FAST_BILINEAR, NULL, NULL, NULL);
        d->sws_w = f->width;
        d->sws_h = f->height;
        d->sws_fmt = f->format;
        /* Le compositeur Android met a l'echelle le tampon sur toute la SurfaceView. */
        ANativeWindow_setBuffersGeometry(d->window, f->width, f->height, WINDOW_FORMAT_RGB_565);
        LOGI("Rendu %dx%d", f->width, f->height);
    }
    if (d->sws == NULL) return;

    ANativeWindow_Buffer buf;
    if (ANativeWindow_lock(d->window, &buf, NULL) != 0) return;
    uint8_t *dst[4] = {(uint8_t *) buf.bits, NULL, NULL, NULL};
    int dst_stride[4] = {buf.stride * 2, 0, 0, 0}; /* RGB565 = 2 octets/pixel */
    int h = f->height < buf.height ? f->height : buf.height;
    sws_scale(d->sws, (const uint8_t *const *) f->data, f->linesize, 0, h, dst, dst_stride);
    ANativeWindow_unlockAndPost(d->window);
}

JNIEXPORT jlong JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeInit(JNIEnv *env, jclass cls, jobject surface,
                                                 jint width, jint height, jint threads) {
#if LIBAVCODEC_VERSION_MAJOR < 58
    avcodec_register_all();
#endif
    AVCodec *codec = (AVCodec *) avcodec_find_decoder(AV_CODEC_ID_H264);
    if (!codec) { LOGE("Decodeur H.264 absent"); return 0; }

    Decoder *d = (Decoder *) calloc(1, sizeof(Decoder));
    if (!d) return 0;
    d->ctx = avcodec_alloc_context3(codec);
    d->ctx->width = width;
    d->ctx->height = height;
    d->ctx->flags |= AV_CODEC_FLAG_LOW_DELAY;
    d->ctx->flags2 |= AV_CODEC_FLAG2_FAST;
    /* Threads par tranches : pas de latence supplementaire (contrairement aux threads par image). */
    d->ctx->thread_type = FF_THREAD_SLICE;
    d->ctx->thread_count = threads > 0 ? threads : 1;
    /* Gain de vitesse important sur CPU lent, artefacts negligeables en usage voiture. */
    d->ctx->skip_loop_filter = AVDISCARD_NONREF;

    if (avcodec_open2(d->ctx, codec, NULL) < 0) {
        LOGE("avcodec_open2 a echoue");
        avcodec_free_context(&d->ctx);
        free(d);
        return 0;
    }
    d->frame = av_frame_alloc();
    d->pkt = av_packet_alloc();
    d->window = ANativeWindow_fromSurface(env, surface);
    if (!d->window) {
        LOGE("ANativeWindow_fromSurface a echoue");
        av_frame_free(&d->frame);
        av_packet_free(&d->pkt);
        avcodec_free_context(&d->ctx);
        free(d);
        return 0;
    }
    LOGI("Decodeur pret (%d threads)", d->ctx->thread_count);
    return (jlong) (intptr_t) d;
}

JNIEXPORT jint JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeDecode(JNIEnv *env, jclass cls, jlong handle,
                                                   jbyteArray data, jint off, jint len) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d || len <= 0) return -1;

    /* Copie avec le padding exige par FFmpeg. */
    uint8_t *buf = (uint8_t *) av_malloc(len + AV_INPUT_BUFFER_PADDING_SIZE);
    if (!buf) return -1;
    (*env)->GetByteArrayRegion(env, data, off, len, (jbyte *) buf);
    memset(buf + len, 0, AV_INPUT_BUFFER_PADDING_SIZE);

    if (av_packet_from_data(d->pkt, buf, len) < 0) {
        av_free(buf);
        return -1;
    }
    int ret = avcodec_send_packet(d->ctx, d->pkt);
    av_packet_unref(d->pkt);
    if (ret < 0 && ret != AVERROR(EAGAIN)) return ret;

    while ((ret = avcodec_receive_frame(d->ctx, d->frame)) == 0) {
        render(d);
    }
    return (ret == AVERROR(EAGAIN) || ret == AVERROR_EOF) ? 0 : ret;
}

JNIEXPORT void JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeRelease(JNIEnv *env, jclass cls, jlong handle) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d) return;
    if (d->sws) sws_freeContext(d->sws);
    if (d->window) ANativeWindow_release(d->window);
    av_frame_free(&d->frame);
    av_packet_free(&d->pkt);
    avcodec_free_context(&d->ctx);
    free(d);
}
