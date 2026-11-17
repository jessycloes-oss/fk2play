// Journal Android pour le decodeur materiel de FK2Play.
#pragma once
#include <android/log.h>

#define FK_TAG "FK2Omx"
#define Log_i(...) __android_log_print(ANDROID_LOG_INFO, FK_TAG, __VA_ARGS__)
#define Log_w(...) __android_log_print(ANDROID_LOG_WARN, FK_TAG, __VA_ARGS__)
#define Log_e(...) __android_log_print(ANDROID_LOG_ERROR, FK_TAG, __VA_ARGS__)
#define Log_d(...) do { } while (0)
#define Log_v(...) do { } while (0)
