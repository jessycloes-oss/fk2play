#!/usr/bin/env bash
# Teste fk2video.c sur PC Linux (ANativeWindow simule). Necessite gcc, ffmpeg (CLI) et un FFmpeg 4.4 compile dans $FFHOST.
set -euo pipefail
cd "$(dirname "$0")"
FFHOST="${FFHOST:-/tmp/ffhost}"
JDK="${JAVA_HOME:-$(dirname "$(dirname "$(readlink -f "$(which javac)")")")}"
ffmpeg -loglevel error -y -f lavfi -i testsrc=size=800x480:rate=30 -t 2 -c:v libx264 -profile:v baseline -pix_fmt yuv420p -f h264 /tmp/fk2test.h264
gcc -O2 -I. -I"$JDK/include" -I"$JDK/include/linux" -I"$FFHOST/include" harness.c ../../app/src/main/jni/fk2video.c \
  -L"$FFHOST/lib" -lavcodec -lswscale -lavutil -lpthread -lm -o /tmp/fk2harness
/tmp/fk2harness /tmp/fk2test.h264
