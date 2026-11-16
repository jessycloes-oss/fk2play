#!/usr/bin/env bash
# Glissement tactile sur l'emulateur 4.0 (pas de « input swipe ») : x1 -> x2 a la hauteur y.
A="timeout 25 ${ADB:-adb}"
x1=$1; x2=$3; y=$2
c="sendevent /dev/input/event0 3 0 $x1; sendevent /dev/input/event0 3 1 $y; sendevent /dev/input/event0 1 330 1; sendevent /dev/input/event0 0 0 0;"
x=$x1; step=$(( (x2 - x1) / 12 ))
for i in $(seq 1 12); do x=$((x + step)); c="$c sendevent /dev/input/event0 3 0 $x; sendevent /dev/input/event0 0 0 0;"; done
c="$c sendevent /dev/input/event0 1 330 0; sendevent /dev/input/event0 0 0 0"
$A shell "$c"
