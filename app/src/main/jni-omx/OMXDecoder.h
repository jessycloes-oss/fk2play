// Decodeur H.264 materiel (OMXCodec, libstagefright Android 4.0.4) avec rendu direct a l'ecran.
// Adapte du module omxvideocodec d'OpenDroidAuto (MaKi983 / MaKi83), avec son accord.
#pragma once

#include <utils/Errors.h>
#include <media/stagefright/OMXClient.h>
#include <media/stagefright/MediaSource.h>
#include <system/window.h>
#include "OMXSource.h"

using namespace android;

class OMXDecoder {
public:
    OMXDecoder();
    ~OMXDecoder();

    void setNativeWindow(const sp<ANativeWindow> &nativeWindow) { nativeWindow_ = nativeWindow; }
    void setSource(const sp<OMXSource> &source) { source_ = source; }
    /** false uniquement pour les tests dans l'emulateur (decodeur logiciel OMX d'Android). */
    void setHardwareOnly(bool on) { hardwareOnly_ = on; }
    /** Cree et demarre le decodeur materiel ; != OK s'il n'y en a pas. */
    status_t init();
    /** Une image decodee -> affichee. Bloquant. */
    status_t read();
    void stop();

    const char *componentName() const { return componentName_; }
    unsigned rendered() const { return rendered_; }

private:
    OMXClient client_;
    bool connected_;
    sp<MediaSource> decoder_;
    sp<ANativeWindow> nativeWindow_;
    sp<OMXSource> source_;
    char componentName_[64];
    bool hardwareOnly_ = true;
    unsigned reads_ = 0;
    unsigned queueErrors_ = 0;
    volatile unsigned rendered_;
};
