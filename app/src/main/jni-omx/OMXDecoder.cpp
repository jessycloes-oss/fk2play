// Decodeur H.264 materiel (voir OMXDecoder.h pour l'origine du code).
#include "OMXDecoder.h"
#include "fk2log.h"
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MediaErrors.h>
#include <media/stagefright/MetaData.h>
#include <media/stagefright/OMXCodec.h>
#include <string.h>

using namespace android;

OMXDecoder::OMXDecoder() : connected_(false), rendered_(0) {
    strcpy(componentName_, "?");
}

status_t OMXDecoder::init() {
    status_t err = client_.connect();
    Log_i("OMXClient connect : %d", err);
    if (err != OK) return err;
    connected_ = true;

    // Le Honda a peut-etre deja affiche l'ecran en mode logiciel (FFmpeg) : on libere la
    // connexion « CPU » de la fenetre pour que le decodeur puisse y deposer ses images.
    native_window_api_disconnect(nativeWindow_.get(), NATIVE_WINDOW_API_CPU);

    uint32_t flags = hardwareOnly_ ? OMXCodec::kHardwareCodecsOnly : 0;
    decoder_ = OMXCodec::Create(client_.interface(), source_->getFormat(), false,
                                source_, NULL, flags, nativeWindow_);
    if (decoder_ == NULL) {
        Log_w("Aucun decodeur H.264 materiel");
        return UNKNOWN_ERROR;
    }
    err = decoder_->start();
    Log_i("Decodeur materiel demarre : %d", err);
    if (err == OK) {
        const char *name = NULL;
        sp<MetaData> fmt = decoder_->getFormat();
        if (fmt != NULL && fmt->findCString(kKeyDecoderComponent, &name) && name != NULL) {
            strncpy(componentName_, name, sizeof(componentName_) - 1);
            componentName_[sizeof(componentName_) - 1] = 0;
        }
        Log_i("Composant : %s", componentName_);
    } else {
        decoder_.clear();
    }
    return err;
}

status_t OMXDecoder::read() {
    if (decoder_ == NULL) return NO_INIT;
    MediaBuffer *videoBuffer = NULL;
    MediaSource::ReadOptions options;
    options.setLateBy(0);
    status_t ret = decoder_->read(&videoBuffer, &options);

    if (reads_ < 8) {
        reads_++;
        Log_i("read #%u : %d, buffer=%p, taille=%u, graphicBuffer=%d", reads_, ret, videoBuffer,
              videoBuffer ? (unsigned) videoBuffer->range_length() : 0u,
              videoBuffer && videoBuffer->graphicBuffer() != NULL ? 1 : 0);
    }
    if (ret == OK && videoBuffer != NULL && videoBuffer->range_length() > 0
            && videoBuffer->graphicBuffer() != NULL) {
        sp<MetaData> metaData = videoBuffer->meta_data();
        native_window_set_buffers_timestamp(nativeWindow_.get(), NATIVE_WINDOW_TIMESTAMP_AUTO);
        int qerr = nativeWindow_->queueBuffer(nativeWindow_.get(),
                                              videoBuffer->graphicBuffer().get());
        if (qerr == 0) {
            metaData->setInt32(kKeyRendered, 1);
            rendered_++;
        } else if (queueErrors_++ < 5) {
            Log_w("queueBuffer : erreur %d", qerr);
        }
    }
    else if (!hardwareOnly_ && ret == OK && videoBuffer != NULL && videoBuffer->range_length() > 0) {
        // Tests dans l'emulateur : le decodeur logiciel OMX d'Android ne rend pas a l'ecran ;
        // l'image decodee est comptee pour verifier le reste de la chaine.
        rendered_++;
    }
    if (videoBuffer != NULL) videoBuffer->release();
    return ret;
}

void OMXDecoder::stop() {
    if (decoder_ != NULL) {
        status_t ret = decoder_->stop();
        Log_i("Decodeur materiel arrete : %d", ret);
    }
    decoder_.clear();
    if (connected_) {
        client_.disconnect();
        connected_ = false;
    }
}

OMXDecoder::~OMXDecoder() {
    stop();
}
