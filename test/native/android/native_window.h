#pragma once
#include <stdint.h>
#define WINDOW_FORMAT_RGB_565 4
typedef struct ANativeWindow ANativeWindow;
typedef struct { int32_t width, height, stride, format; void *bits; uint32_t reserved[6]; } ANativeWindow_Buffer;
int32_t ANativeWindow_setBuffersGeometry(ANativeWindow*, int32_t, int32_t, int32_t);
int32_t ANativeWindow_lock(ANativeWindow*, ANativeWindow_Buffer*, void*);
int32_t ANativeWindow_unlockAndPost(ANativeWindow*);
void ANativeWindow_release(ANativeWindow*);
