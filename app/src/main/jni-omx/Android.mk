# Decodeur video materiel (OMX / libstagefright du Honda Connect, Android 4.0.4).
# Adapte du module omxvideocodec d'OpenDroidAuto (MaKi83), utilise avec son accord.
# Compilation : scripts/build_omx.sh (en-tetes et bibliotheques AOSP 4.0.4 : scripts/get_ics_sdk.sh).
LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE := fk2omx
LOCAL_SRC_FILES := OMXSource.cpp OMXDecoder.cpp fk2omx_jni.cpp
LOCAL_C_INCLUDES := $(ICS_SDK)/include/frameworks_base \
                    $(ICS_SDK)/include/frameworks_base/media/stagefright/openmax \
                    $(ICS_SDK)/include/system_core \
                    $(ICS_SDK)/include/libhardware
LOCAL_CFLAGS := -DHAVE_PTHREADS -O2 -Wno-unused-parameter -Wno-deprecated-register -fno-omit-frame-pointer
LOCAL_CPPFLAGS := -fno-rtti
# Bibliotheques systeme : uniquement pour l'edition de liens (celles du Honda sont utilisees a l'execution).
LOCAL_LDLIBS := -llog -landroid -L$(ICS_SDK)/lib -lstagefright -lutils -lcutils -lbinder
LOCAL_LDFLAGS := -Wl,--allow-shlib-undefined -Wl,--allow-multiple-definition
LOCAL_ARM_NEON := true
include $(BUILD_SHARED_LIBRARY)
