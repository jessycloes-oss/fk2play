// FK2Play — pont JNI du decodeur video materiel (com.fk2play.video.OmxDecoder).
// Organisation reprise du module omxvideocodec d'OpenDroidAuto (MaKi983 / MaKi83), avec son accord :
// un thread lit les images decodees et les affiche ; Java depose les unites d'acces H.264 (Annex-B).
#include <jni.h>
#include <atomic>
#include <condition_variable>
#include <mutex>
#include <thread>
#include <unistd.h>
#include <stdio.h>

#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <system/window.h>

#include "OMXDecoder.h"
#include "OMXSource.h"
#include "fk2log.h"

using namespace android;

// libandroid (NDK) : declarations directes pour ne pas melanger les en-tetes NDK et AOSP.
extern "C" ANativeWindow *ANativeWindow_fromSurface(JNIEnv *env, jobject surface);
extern "C" void ANativeWindow_release(ANativeWindow *window);

namespace {

/** Erreurs de lecture consecutives au-dela desquelles le decodeur est declare en panne. */
const int kMaxConsecutiveErrors = 100;

struct Codec {
    sp<ANativeWindow> window;
    sp<OMXSource> source;
    OMXDecoder *decoder = nullptr;
    std::thread thread;
    std::mutex mutex;
    std::condition_variable cond;
    bool ready = false;
    status_t initStatus = UNKNOWN_ERROR;
    std::atomic<bool> quit{false};
    std::atomic<bool> failed{false};
    unsigned lastRendered = 0;
    int width = 0, height = 0;
    bool hardwareOnly = true;

    void run() {
        decoder = new OMXDecoder();
        decoder->setNativeWindow(window);
        decoder->setSource(source);
        decoder->setHardwareOnly(hardwareOnly);
        status_t st = decoder->init();
        {
            std::lock_guard<std::mutex> l(mutex);
            initStatus = st;
            ready = true;
        }
        cond.notify_all();
        if (st != OK) return;

        int errors = 0;
        while (!quit) {
            status_t r = decoder->read();
            if (r == OK || r == INFO_FORMAT_CHANGED) {
                errors = 0;
            } else if (!quit) {
                if (++errors >= kMaxConsecutiveErrors) {
                    Log_e("Decodeur materiel en panne (erreur %d)", r);
                    failed = true;
                    break;
                }
                usleep(5000);
            }
        }
    }
};

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_com_fk2play_video_OmxDecoder_nativeCreate(JNIEnv *env, jclass, jobject surface,
                                               jint width, jint height, jint fps, jboolean hwOnly) {
    ANativeWindow *raw = ANativeWindow_fromSurface(env, surface);
    if (raw == nullptr) return 0;
    Codec *c = new Codec();
    c->window = raw;              // sp<> garde sa propre reference
    ANativeWindow_release(raw);   // rend celle de ANativeWindow_fromSurface
    c->width = width;
    c->height = height;
    c->source = new OMXSource(width, height, fps);
    c->hardwareOnly = hwOnly;
    c->thread = std::thread(&Codec::run, c);
    {
        std::unique_lock<std::mutex> l(c->mutex);
        while (!c->ready) c->cond.wait(l);
    }
    if (c->initStatus != OK) {
        c->quit = true;
        c->source->stop();
        if (c->thread.joinable()) c->thread.join();
        delete c->decoder;
        native_window_api_disconnect(c->window.get(), NATIVE_WINDOW_API_MEDIA);
        delete c;
        return 0;
    }
    return (jlong) (intptr_t) c;
}

/**
 * Depose une unite d'acces. Retour : -1 decodeur en panne ; sinon bit 0 = au moins une image
 * affichee depuis l'appel precedent, bit 1 = demander une image cle au dongle.
 */
extern "C" JNIEXPORT jint JNICALL
Java_com_fk2play_video_OmxDecoder_nativeDecode(JNIEnv *env, jclass, jlong handle,
                                               jbyteArray data, jint off, jint len, jlong timeUs) {
    Codec *c = (Codec *) (intptr_t) handle;
    if (c == nullptr || c->failed) return -1;
    if (len > 0) {
        MediaBuffer *mb = new MediaBuffer(len);
        env->GetByteArrayRegion(data, off, len, (jbyte *) mb->data());
        mb->set_range(0, (size_t) len);
        mb->meta_data()->clear();
        mb->meta_data()->setInt64(kKeyTime, (int64_t) timeUs);
        c->source->queueBuffer(mb);
    }
    int result = 0;
    unsigned r = c->decoder->rendered();
    if (r != c->lastRendered) {
        c->lastRendered = r;
        result |= 1;
    }
    if (c->source->takeKeyframeRequest()) result |= 2;
    return result;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_fk2play_video_OmxDecoder_nativeInfo(JNIEnv *env, jclass, jlong handle) {
    Codec *c = (Codec *) (intptr_t) handle;
    if (c == nullptr) return env->NewStringUTF("");
    char buf[160];
    snprintf(buf, sizeof(buf), "%s %dx%d, %u images, %u jetees", c->decoder->componentName(),
             c->width, c->height, c->decoder->rendered(), (unsigned) c->source->droppedTotal());
    return env->NewStringUTF(buf);
}

extern "C" JNIEXPORT void JNICALL
Java_com_fk2play_video_OmxDecoder_nativeRelease(JNIEnv *, jclass, jlong handle) {
    Codec *c = (Codec *) (intptr_t) handle;
    if (c == nullptr) return;
    c->quit = true;
    c->source->stop();
    if (c->thread.joinable()) c->thread.join();
    if (c->decoder != nullptr) {
        c->decoder->stop();
        delete c->decoder;
    }
    // Rend la fenetre au mode logiciel (FFmpeg) si besoin.
    native_window_api_disconnect(c->window.get(), NATIVE_WINDOW_API_MEDIA);
    delete c;
}
