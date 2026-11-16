/*
 * FK2Play - decodeur H.264 logiciel (FFmpeg) avec rendu direct dans une Surface Android.
 * Compatible Android 4.0.4 (API 15) : n'utilise que ANativeWindow (API 9) et libavcodec.
 *
 * 1.7 : le decodeur vit independamment de l'ecran, et l'affichage se fait dans un autre
 * thread que le decodage (boite aux lettres d'une image : si l'affichage prend du retard,
 * l'image en attente est remplacee par la plus recente).
 *  - il peut decoder sans afficher (rattrapage d'un retard, ou CarPlay en arriere-plan
 *    pendant un appel) et reafficher instantanement la derniere image au retour ;
 *  - mode « allege » (filtre de boucle saute) quand l'autoradio est trop charge ;
 *  - conversion YUV -> RGB : RGB565 (C) ou RGBX (NEON), choisie en mesurant les deux
 *    sur l'appareil lui-meme a la premiere image.
 */
#include <jni.h>
#include <pthread.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <android/log.h>
#include <android/native_window.h>
#include <android/native_window_jni.h>

#include <libavcodec/avcodec.h>
#include <libavutil/imgutils.h>
#include <libswscale/swscale.h>

#define TAG "FK2Video"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* Valeurs de retour de nativeDecode */
#define R_NOTHING 0   /* paquet accepte, pas encore d'image */
#define R_QUEUED 1    /* image decodee, deposee pour l'affichage */
#define R_DECODED 2   /* image decodee, non affichee */
#define R_REPLACED 3  /* image deposee pour l'affichage, la precedente (pas encore affichee) est sautee */

enum { FMT_UNKNOWN = 0, FMT_565, FMT_RGBX };

typedef struct {
    AVCodecContext *ctx;
    AVFrame *frame;
    pthread_mutex_t mail_m; /* protege last / mail (decodage <-> affichage) */
    pthread_mutex_t win_m;  /* protege la fenetre et la conversion (affichage <-> changement d'ecran) */
    AVFrame *last;          /* derniere image decodee (pour reaffichage instantane) */
    int has_last;
    AVFrame *mail;          /* image en attente d'affichage */
    int mail_full;
    AVFrame *rframe;        /* image en cours d'affichage */
    AVPacket *pkt;
    uint8_t *in;            /* tampon d'entree reutilise (avec padding FFmpeg) */
    int in_cap;
    struct SwsContext *sws;
    ANativeWindow *window;
    int geom_ok;            /* geometrie de la fenetre reglee pour sws_w x sws_h */
    int sws_w, sws_h, sws_fmt, sws_out;
    int out_fmt;            /* FMT_565 ou FMT_RGBX ; FMT_UNKNOWN = calibrage en cours */
    int forced_fmt;         /* reglage de diagnostic : 0 = automatique */
    int cal_fmt, cal_n;     /* calibrage : format essaye, images mesurees */
    int64_t cal_t[3];       /* cout moyen d'affichage (conversion + ecran) par format */
    /* Diagnostic : temps moyens par image (journal toutes les 300 images) */
    int64_t t_dec, t_conv, t_post;
    int n_dec, n_shown;
} Decoder;

static int64_t now_us(void) {
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC, &t);
    return (int64_t) t.tv_sec * 1000000 + t.tv_nsec / 1000;
}

static enum AVPixelFormat av_out(int f) { return f == FMT_RGBX ? AV_PIX_FMT_RGBA : AV_PIX_FMT_RGB565LE; }
static int win_out(int f) { return f == FMT_RGBX ? WINDOW_FORMAT_RGBX_8888 : WINDOW_FORMAT_RGB_565; }
static int bpp(int f) { return f == FMT_RGBX ? 4 : 2; }

/*
 * Calibrage du format d'affichage, sur l'appareil lui-meme et avec le vrai ecran :
 * 24 images en RGB565 (conversion C, 2 octets/pixel) puis 24 en RGBX (conversion NEON,
 * 4 octets/pixel a composer). On mesure conversion + attente de l'ecran, et on garde le plus
 * rapide (RGBX seulement s'il gagne nettement : il charge davantage le compositeur).
 */
#define CAL_FRAMES 24
#define CAL_SKIP 4

static int current_fmt(Decoder *d, AVFrame *f) {
    if (d->out_fmt != FMT_UNKNOWN) return d->out_fmt;
    if (d->forced_fmt) return d->out_fmt = d->forced_fmt;
    if (d->cal_fmt == FMT_UNKNOWN) d->cal_fmt = FMT_565;
    return d->cal_fmt;
}

static void calibrate(Decoder *d, AVFrame *f, int64_t cost) {
    if (d->out_fmt != FMT_UNKNOWN) return;
    if (++d->cal_n > CAL_SKIP) d->cal_t[d->cal_fmt] += cost;
    if (d->cal_n < CAL_FRAMES) return;
    d->cal_t[d->cal_fmt] /= (CAL_FRAMES - CAL_SKIP);
    d->cal_n = 0;
    if (d->cal_fmt == FMT_565 && f->width % 16 == 0) {
        d->cal_fmt = FMT_RGBX;          /* essai suivant */
        return;
    }
    int64_t a = d->cal_t[FMT_565], b = d->cal_fmt == FMT_RGBX ? d->cal_t[FMT_RGBX] : INT64_MAX;
    d->out_fmt = (b != INT64_MAX && b * 10 < a * 8) ? FMT_RGBX : FMT_565;
    LOGI("Affichage : RGB565 %.1f ms, RGBX %.1f ms par image -> %s", a / 1000.0,
         b == INT64_MAX ? -1.0 : b / 1000.0, d->out_fmt == FMT_RGBX ? "RGBX" : "RGB565");
}

static int render(Decoder *d, AVFrame *f) {
    if (!d->window) return 0;
    int fmt = current_fmt(d, f);
    if (d->sws == NULL || d->sws_w != f->width || d->sws_h != f->height || d->sws_fmt != f->format
            || d->sws_out != fmt) {
        if (d->sws) sws_freeContext(d->sws);
        d->sws = sws_getContext(f->width, f->height, (enum AVPixelFormat) f->format,
                                f->width, f->height, av_out(fmt),
                                SWS_FAST_BILINEAR, NULL, NULL, NULL);
        d->sws_out = fmt;
        d->sws_w = f->width;
        d->sws_h = f->height;
        d->sws_fmt = f->format;
        d->geom_ok = 0;
    }
    if (d->sws == NULL) return 0;
    if (!d->geom_ok) {
        /* Le compositeur Android met a l'echelle le tampon sur toute la SurfaceView. */
        ANativeWindow_setBuffersGeometry(d->window, f->width, f->height, win_out(fmt));
        d->geom_ok = 1;
        LOGI("Rendu %dx%d", f->width, f->height);
    }

    ANativeWindow_Buffer buf;
    int64_t t0 = now_us();
    if (ANativeWindow_lock(d->window, &buf, NULL) != 0) return 0;
    if (buf.width < f->width) { ANativeWindow_unlockAndPost(d->window); return 0; }
    int64_t t1 = now_us();
    uint8_t *dst[4] = {(uint8_t *) buf.bits, NULL, NULL, NULL};
    int dst_stride[4] = {buf.stride * bpp(fmt), 0, 0, 0};
    int h = f->height < buf.height ? f->height : buf.height;
    sws_scale(d->sws, (const uint8_t *const *) f->data, f->linesize, 0, h, dst, dst_stride);
    int64_t t2 = now_us();
    ANativeWindow_unlockAndPost(d->window);
    int64_t t3 = now_us();
    d->t_conv += t2 - t1;
    d->t_post += (t1 - t0) + (t3 - t2);
    d->n_shown++;
    calibrate(d, f, t3 - t0);
    return 1;
}

JNIEXPORT jlong JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeInit(JNIEnv *env, jclass cls,
                                                 jint width, jint height, jint threads, jint format) {
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
    d->ctx->skip_loop_filter = AVDISCARD_NONREF;

    if (avcodec_open2(d->ctx, codec, NULL) < 0) {
        LOGE("avcodec_open2 a echoue");
        avcodec_free_context(&d->ctx);
        free(d);
        return 0;
    }
    d->forced_fmt = (format == FMT_565 || format == FMT_RGBX) ? format : 0;
    d->frame = av_frame_alloc();
    d->last = av_frame_alloc();
    d->mail = av_frame_alloc();
    d->rframe = av_frame_alloc();
    pthread_mutex_init(&d->mail_m, NULL);
    pthread_mutex_init(&d->win_m, NULL);
    d->pkt = av_packet_alloc();
    LOGI("Decodeur pret (%d threads)", d->ctx->thread_count);
    return (jlong) (intptr_t) d;
}

/**
 * Attache (ou detache si surface == null) l'ecran. Si une image recente est en memoire,
 * elle est affichee tout de suite : retour instantane sur CarPlay. Renvoie 1 si une image a ete affichee.
 */
JNIEXPORT jint JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeSetWindow(JNIEnv *env, jclass cls, jlong handle,
                                                      jobject surface, jboolean showLast) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d) return 0;
    int shown = 0;
    pthread_mutex_lock(&d->win_m);
    if (d->window) {
        ANativeWindow_release(d->window);
        d->window = NULL;
    }
    d->geom_ok = 0;
    if (surface != NULL) {
        d->window = ANativeWindow_fromSurface(env, surface);
        if (!d->window) LOGE("ANativeWindow_fromSurface a echoue");
    }
    if (d->window && showLast) {
        AVFrame *tmp = av_frame_alloc();
        pthread_mutex_lock(&d->mail_m);
        int ok = d->has_last && av_frame_ref(tmp, d->last) == 0;
        pthread_mutex_unlock(&d->mail_m);
        if (ok) shown = render(d, tmp);
        av_frame_free(&tmp);
    }
    pthread_mutex_unlock(&d->win_m);
    return shown;
}

JNIEXPORT void JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeSetFast(JNIEnv *env, jclass cls, jlong handle, jboolean fast) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d) return;
    /* Filtre anti-blocs saute : ~20 a 30 % de calcul en moins, image un peu moins lisse. */
    d->ctx->skip_loop_filter = fast ? AVDISCARD_ALL : AVDISCARD_NONREF;
}

JNIEXPORT jint JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeDecode(JNIEnv *env, jclass cls, jlong handle,
                                                   jbyteArray data, jint off, jint len, jboolean show) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d || len <= 0) return -1;

    /* Tampon d'entree reutilise (pas d'allocation par image). */
    if (d->in_cap < len + AV_INPUT_BUFFER_PADDING_SIZE) {
        av_free(d->in);
        d->in_cap = len + AV_INPUT_BUFFER_PADDING_SIZE + 65536;
        d->in = (uint8_t *) av_malloc(d->in_cap);
        if (!d->in) { d->in_cap = 0; return -1; }
    }
    (*env)->GetByteArrayRegion(env, data, off, len, (jbyte *) d->in);
    memset(d->in + len, 0, AV_INPUT_BUFFER_PADDING_SIZE);

    d->pkt->data = d->in;
    d->pkt->size = len;
    int64_t t0 = now_us();
    int ret = avcodec_send_packet(d->ctx, d->pkt);
    d->pkt->data = NULL;
    d->pkt->size = 0;
    if (ret < 0 && ret != AVERROR(EAGAIN)) return ret;

    int result = R_NOTHING;
    while ((ret = avcodec_receive_frame(d->ctx, d->frame)) == 0) {
        d->t_dec += now_us() - t0;
        if (++d->n_dec % 300 == 0) {
            LOGI("Par image : decodage %.1f ms, conversion %.1f ms, affichage %.1f ms (%d/%d affichees)",
                 d->t_dec / 1000.0 / d->n_dec, d->n_shown ? d->t_conv / 1000.0 / d->n_shown : 0.0,
                 d->n_shown ? d->t_post / 1000.0 / d->n_shown : 0.0, d->n_shown, d->n_dec);
            d->t_dec = d->t_conv = d->t_post = 0;
            d->n_dec = d->n_shown = 0;
        }
        pthread_mutex_lock(&d->mail_m);
        av_frame_unref(d->last);
        av_frame_ref(d->last, d->frame);
        d->has_last = 1;
        int r = R_DECODED;
        if (show) {
            r = d->mail_full ? R_REPLACED : R_QUEUED;
            av_frame_unref(d->mail);
            av_frame_ref(d->mail, d->frame);
            d->mail_full = 1;
        }
        pthread_mutex_unlock(&d->mail_m);
        av_frame_unref(d->frame);
        if (r != R_DECODED || result == R_NOTHING) result = r;
    }
    if (ret != AVERROR(EAGAIN) && ret != AVERROR_EOF) return ret;
    return result;
}

/** Thread d'affichage : affiche l'image en attente. Renvoie 1 si une image a ete affichee. */
JNIEXPORT jint JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeRenderPending(JNIEnv *env, jclass cls, jlong handle) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d) return 0;
    pthread_mutex_lock(&d->win_m);
    pthread_mutex_lock(&d->mail_m);
    int have = d->mail_full;
    if (have) {
        av_frame_move_ref(d->rframe, d->mail);
        d->mail_full = 0;
    }
    pthread_mutex_unlock(&d->mail_m);
    int shown = have ? render(d, d->rframe) : 0;
    av_frame_unref(d->rframe);
    pthread_mutex_unlock(&d->win_m);
    return shown;
}

/** Oublie la derniere image (elle ne correspond plus a l'ecran de l'iPhone). */
JNIEXPORT void JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeForgetLast(JNIEnv *env, jclass cls, jlong handle) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d) return;
    pthread_mutex_lock(&d->mail_m);
    av_frame_unref(d->last);
    av_frame_unref(d->mail);
    d->has_last = 0;
    d->mail_full = 0;
    pthread_mutex_unlock(&d->mail_m);
}

JNIEXPORT void JNICALL
Java_com_fk2play_video_FfmpegDecoder_nativeRelease(JNIEnv *env, jclass cls, jlong handle) {
    Decoder *d = (Decoder *) (intptr_t) handle;
    if (!d) return;
    if (d->sws) sws_freeContext(d->sws);
    if (d->window) ANativeWindow_release(d->window);
    av_frame_free(&d->frame);
    av_frame_free(&d->last);
    av_frame_free(&d->mail);
    av_frame_free(&d->rframe);
    pthread_mutex_destroy(&d->mail_m);
    pthread_mutex_destroy(&d->win_m);
    av_packet_free(&d->pkt);
    av_free(d->in);
    avcodec_free_context(&d->ctx);
    free(d);
}
