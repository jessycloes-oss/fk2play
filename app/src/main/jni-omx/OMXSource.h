// Source d'images H.264 pour le decodeur materiel OMX (libstagefright, Android 4.0.4).
// Adapte du module omxvideocodec d'OpenDroidAuto, (c) MaKi983 / MaKi83 :
// https://github.com/MaKi983/OpenDroidAuto — reutilise dans FK2Play (licence MIT) avec l'accord
// de son auteur (fil XDA « Honda Connect Android System », 16 nov. 2026).
#pragma once

#include <media/stagefright/MediaSource.h>
#include <media/stagefright/MediaBuffer.h>
#include <media/stagefright/MetaData.h>
#include <utils/RefBase.h>
#include <chrono>
#include <condition_variable>
#include <cstddef>
#include <mutex>
#include <queue>

using namespace android;

class OMXSource : public MediaSource, public MediaBufferObserver {
public:
    OMXSource(int width, int height, int fps);

    status_t read(MediaBuffer **buffer, const MediaSource::ReadOptions *options) override;
    status_t start(MetaData *params) override;
    status_t stop() override;
    sp<MetaData> getFormat() override;
    void queueBuffer(MediaBuffer* buffer);

    status_t pause() override { return ERROR_UNSUPPORTED; }
    status_t setBuffers(const Vector<MediaBuffer *> &buffers) override { return ERROR_UNSUPPORTED; }

    void signalBufferReturned(MediaBuffer *buffer) override;

    /** Images jetees depuis le debut (decodeur en retard). */
    size_t droppedTotal() const { return droppedTotal_; }
    /** Le decodeur a jete des images : FK2Play doit demander une image cle au dongle. */
    bool takeKeyframeRequest() { std::lock_guard<std::mutex> l(mutex_); bool r = keyframeWanted_; keyframeWanted_ = false; return r; }

    ~OMXSource() override;

private:
    sp<MetaData> format_;
    std::queue<MediaBuffer*> pbuffers_;
    std::mutex mutex_;
    std::condition_variable cond_;

    bool quitFlag_;
    bool waitingForSync_;
    bool keyframeWanted_ = false;
    std::size_t droppedBuffers_;
    std::size_t droppedTotal_;
    std::size_t syncWaitDroppedBuffers_;
    std::chrono::steady_clock::time_point syncWaitStarted_;

    MediaBuffer* nextBuffer();
    void clearQueuedBuffers();
};
