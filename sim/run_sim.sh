#!/usr/bin/env bash
# Simulation FK2Play sur Android 4.0.4 ARM : faux dongle + surveillance plantages / memoire / fuites.
A=${ADB:-adb}
OUT=${OUT:-./sim-out}
mkdir -p "$OUT"; rm -f "$OUT"/*
DUR=${1:-150}

pid() { $A shell ps | tr -d '\r' | awk '$NF=="com.fk2play"{print $2}'; }
stats() {
  local p; p=$(pid)
  [ -z "$p" ] && { echo "$1 PROCESSUS ABSENT"; return; }
  local fd thr pss
  fd=$($A shell ls /proc/$p/fd 2>/dev/null | wc -l)
  thr=$($A shell ls /proc/$p/task 2>/dev/null | wc -l)
  pss=$($A shell dumpsys meminfo $p | tr -d '\r' | awk '/TOTAL/{print $2; exit}')
  echo "$1 pid=$p fd=$fd threads=$thr pss_ko=$pss"
}

$A logcat -c
$A shell am force-stop com.fk2play
$A shell am start -n com.fk2play/.CarPlayActivity --es sim_file /data/local/tmp/fk2sim.bin >/dev/null
sleep 12; stats "t=12s " | tee -a "$OUT/stats.txt"
$A shell screencap -p /data/local/tmp/a.png; $A pull /data/local/tmp/a.png "$OUT/ecran_12s.png" >/dev/null
sleep 8;  $A shell screencap -p /data/local/tmp/b.png; $A pull /data/local/tmp/b.png "$OUT/ecran_20s.png" >/dev/null
stats "t=20s " | tee -a "$OUT/stats.txt"
# Aller-retour vers l'accueil pendant la lecture (onPause -> deconnexion, onResume -> reconnexion)
$A shell input keyevent 3; sleep 6; stats "accueil" | tee -a "$OUT/stats.txt"
$A shell am start -n com.fk2play/.CarPlayActivity --es sim_file /data/local/tmp/fk2sim.bin >/dev/null
T=40
while [ $T -lt "$DUR" ]; do sleep 20; T=$((T+20)); stats "t=${T}s" | tee -a "$OUT/stats.txt"; done
$A shell screencap -p /data/local/tmp/c.png; $A pull /data/local/tmp/c.png "$OUT/ecran_fin.png" >/dev/null
$A shell dumpsys meminfo com.fk2play | tr -d '\r' > "$OUT/meminfo.txt"
$A logcat -d -v time | tr -d '\r' > "$OUT/logcat.txt"
echo "--- bilan"
grep -cE 'FATAL EXCEPTION|Fatal signal|ANR in|SIGSEGV|SIGILL|OutOfMemory' "$OUT/logcat.txt" | sed 's/^/plantages\/ANR: /'
grep -c 'Simulation : lecture' "$OUT/logcat.txt" | sed 's/^/sessions ouvertes: /'
grep -c 'transport ferme apres' "$OUT/logcat.txt" | sed 's/^/sessions fermees: /'
grep -E 'FK2Play.*i/s' "$OUT/logcat.txt" | tail -5
