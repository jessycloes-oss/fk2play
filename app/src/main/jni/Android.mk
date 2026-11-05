LOCAL_PATH := $(call my-dir)

# FFmpeg compile par scripts/build_ffmpeg.sh dans ../../../../ffmpeg-android/<abi>
FFMPEG_DIR := $(LOCAL_PATH)/../../../../ffmpeg-android/$(TARGET_ARCH_ABI)

include $(CLEAR_VARS)
LOCAL_MODULE := avcodec
LOCAL_SRC_FILES := $(FFMPEG_DIR)/lib/libavcodec.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := swscale
LOCAL_SRC_FILES := $(FFMPEG_DIR)/lib/libswscale.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := avutil
LOCAL_SRC_FILES := $(FFMPEG_DIR)/lib/libavutil.a
include $(PREBUILT_STATIC_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE := fk2video
LOCAL_SRC_FILES := fk2video.c
LOCAL_C_INCLUDES := $(FFMPEG_DIR)/include
LOCAL_CFLAGS := -O2 -Wall
LOCAL_STATIC_LIBRARIES := avcodec swscale avutil
LOCAL_LDLIBS := -landroid -llog -lm -lz
include $(BUILD_SHARED_LIBRARY)
