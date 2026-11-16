#!/usr/bin/env bash
# Banc d'essai de reactivite FK2Play (emulateur Android 4.0.4 + faux dongle realiste).
# Usage : sim/run_bench.sh <apk de test> <nom> [honda]
#   honda : simule le Honda qui rouvre FK2Play des la fin de l'appel (cas de la 1.5).
# Prerequis : /data/local/tmp/fk2bench.bin(+.idr) generes par sim/make_bench.py.
set -u
APK=$1; NAME=$2; HONDA=${3:-}
A="timeout 25 ${ADB:-adb}"
OUT=${OUT:-./bench-out}/$NAME
mkdir -p "$OUT"; rm -f "$OUT"/*
TAP=$(dirname "$0")/bench_touch.sh

$A uninstall com.fk2play >/dev/null 2>&1
$A install -r "$APK" | tail -1
# Reglages du mode hybride valide en voiture (musique USB, appels via HandsFreeLink)
$A shell am start -n com.fk2play/.SettingsActivity >/dev/null; sleep 8
$A shell am force-stop com.fk2play
cat > /tmp/fk2prefs.xml <<EOF
<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<map>
<boolean name="audio_usb" value="true" />
<boolean name="calls_car_bt" value="true" />
<boolean name="car_mic" value="false" />
${PREFS_EXTRA:-}
</map>
EOF
$A shell mkdir -p /data/data/com.fk2play/shared_prefs
$A push /tmp/fk2prefs.xml /data/data/com.fk2play/shared_prefs/com.fk2play_preferences.xml >/dev/null
$A shell chmod 666 /data/data/com.fk2play/shared_prefs/com.fk2play_preferences.xml
$A shell svc power stayon true
$A shell input keyevent 82; sleep 1; $A shell input keyevent 3; sleep 3
$A shell dumpsys window | grep -q 'mCurrentFocus=.*Keyguard' && { $A shell input keyevent 82; sleep 2; }

$A logcat -c
T0=$(date +%s)
el() { echo $(( $(date +%s) - T0 )); }
$A shell am start -n com.fk2play/.CarPlayActivity --es sim_file /data/local/tmp/fk2bench.bin >/dev/null
sleep 10
# Pic de charge de 2 s (le Honda calcule un itineraire, la camera de recul demarre...)
$A shell 'while :; do :; done & p=$!; sleep 2; kill $p'
sleep 2; $A shell top -n 1 -d 1 | grep -m1 com.fk2play > "$OUT/cpu_15s.txt"
while [ "$(el)" -lt 20 ]; do sleep 1; done
for i in 1 2 3 4 5 6; do "$TAP" 600 200 240; sleep 1; done          # glissements CarPlay
while [ "$(el)" -lt 43 ]; do sleep 1; done
$A shell input keyevent 3                                          # le Honda affiche son ecran telephone
# Attente de la fin de l'appel
for i in $(seq 1 40); do
  $A logcat -d -s FK2Stat:I | grep -q fin_appel && break; sleep 0.5
done
if [ "$HONDA" = honda ]; then
  $A shell am start -n com.fk2play/.CarPlayActivity >/dev/null       # le Honda rouvre FK2Play
fi
sleep 6; $A shell dumpsys activity activities | grep -m1 mFocusedActivity > "$OUT/focus_apres_appel.txt"
# Sortie de CarPlay hors appel (radio Honda...) puis retour par l'icone
while [ "$(el)" -lt 58 ]; do sleep 1; done
$A shell input keyevent 3; sleep 4
$A shell am start -n com.fk2play/.CarPlayActivity >/dev/null
while [ "$(el)" -lt 76 ]; do sleep 1; done
$A logcat -d -v time | tr -d '\r' > "$OUT/logcat.txt"
grep -E 'FK2Stat' "$OUT/logcat.txt" | sed 's/^.*FK2Stat[^:]*: //' > "$OUT/stats.txt"
grep -cE 'FATAL EXCEPTION|Fatal signal|ANR in|SIGSEGV' "$OUT/logcat.txt" > "$OUT/plantages.txt"
grep -E 'GC_' "$OUT/logcat.txt" | grep -c com.fk2play > /dev/null
pid=$(grep -m1 -oE 'I/FK2Play \( *[0-9]+\)' "$OUT/logcat.txt" | grep -oE '[0-9]+')
grep -E "D/dalvikvm\( *$pid\)" "$OUT/logcat.txt" | grep -E 'GC_' > "$OUT/gc.txt"
echo "== $NAME"; cat "$OUT/stats.txt"; echo "GC: $(wc -l < "$OUT/gc.txt") pauses ; plantages: $(cat "$OUT/plantages.txt")"
cat "$OUT/focus_apres_appel.txt" "$OUT/cpu_15s.txt"
