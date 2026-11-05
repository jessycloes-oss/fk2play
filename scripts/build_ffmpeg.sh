#!/usr/bin/env bash
# Compile un FFmpeg minimal (decodeur H.264 + swscale) pour Android 4.0.4 / ARMv7.
# Usage : NDK=/chemin/android-ndk-r17c ./scripts/build_ffmpeg.sh
#
# Pourquoi le NDK r17c ? C'est l'une des dernieres versions qui cible encore l'API 15
# (Android 4.0.4). Les NDK recents imposent l'API 19 ou 21 minimum.
set -euo pipefail

FFMPEG_VERSION="${FFMPEG_VERSION:-n4.4.4}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WORK="$ROOT/build-ffmpeg"
OUT="$ROOT/ffmpeg-android/armeabi-v7a"
API=15

: "${NDK:?Definissez NDK=/chemin/vers/android-ndk-r17c}"

mkdir -p "$WORK"
cd "$WORK"

# 1) Toolchain autonome API 15
TC="$WORK/toolchain-arm-$API"
if [ ! -d "$TC" ]; then
  python3 "$NDK/build/tools/make_standalone_toolchain.py" \
    --arch arm --api "$API" --stl libc++ --install-dir "$TC" --force
fi

# 2) Sources FFmpeg
if [ ! -d "ffmpeg-src" ]; then
  curl -L -o ffmpeg.tar.gz "https://github.com/FFmpeg/FFmpeg/archive/refs/tags/${FFMPEG_VERSION}.tar.gz"
  mkdir ffmpeg-src
  tar xzf ffmpeg.tar.gz -C ffmpeg-src --strip-components=1
fi

cd ffmpeg-src
make distclean >/dev/null 2>&1 || true

# -mfpu=vfpv3-d16 pour le C (compatible meme sans NEON) ; l'assembleur NEON
# de FFmpeg est active et choisi a l'execution si le CPU le supporte (Tegra 3 : oui).
./configure \
  --prefix="$OUT" \
  --enable-cross-compile \
  --target-os=android \
  --arch=arm \
  --cpu=armv7-a \
  --cc="$TC/bin/arm-linux-androideabi-clang" \
  --cxx="$TC/bin/arm-linux-androideabi-clang++" \
  --cross-prefix="$TC/bin/arm-linux-androideabi-" \
  --nm="$TC/bin/arm-linux-androideabi-nm" \
  --ar="$TC/bin/arm-linux-androideabi-ar" \
  --ranlib="$TC/bin/arm-linux-androideabi-ranlib" \
  --strip="$TC/bin/arm-linux-androideabi-strip" \
  --sysroot="$TC/sysroot" \
  --extra-cflags="-march=armv7-a -mfloat-abi=softfp -mfpu=vfpv3-d16 -mthumb -fPIC -O3 -DANDROID -D__ANDROID_API__=$API" \
  --extra-ldflags="-Wl,--fix-cortex-a8" \
  --enable-static --disable-shared --enable-pic \
  --disable-everything \
  --enable-decoder=h264 --enable-parser=h264 \
  --enable-avcodec --enable-avutil --enable-swscale \
  --disable-avformat --disable-avdevice --disable-avfilter --disable-swresample --disable-postproc \
  --disable-programs --disable-doc --disable-network --disable-debug \
  --disable-zlib --disable-bzlib --disable-iconv --disable-lzma \
  --disable-mediacodec --disable-jni \
  --enable-pthreads --enable-neon --enable-asm --enable-inline-asm --enable-runtime-cpudetect

make -j"$(nproc)"
make install

echo "FFmpeg installe dans $OUT"
