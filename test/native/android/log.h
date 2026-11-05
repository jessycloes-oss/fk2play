#include <stdio.h>
#define ANDROID_LOG_INFO 4
#define ANDROID_LOG_ERROR 6
#define __android_log_print(p, t, ...) (fprintf(stderr, "[%s] ", t), fprintf(stderr, __VA_ARGS__), fprintf(stderr, "\n"))
