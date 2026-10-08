#!/bin/bash
# THROWAWAY SPIKE (PLA-11). Q1 blocked-launch matrix, run from inside the locked task (the DPC home acts as the approved app).
# Usage: tools/q1-matrix.sh <serial> <label> [cases-file]
# Each case line: id|spec   (spec as for the DPC "launch" command). Every case runs as: same task, new task, and inside a chooser.
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
LABEL="$2"
CASES="${3:-tools/q1-cases.txt}"
OUT="evidence/q1/matrix-$LABEL.tsv"
mkdir -p evidence/q1
echo -e "id\tmode\ttop_after_1.5s\tverdict\tspec" > "$OUT"
grep -v '^#' "$CASES" | grep -v '^$' | while IFS='|' read -r id spec; do
  for mode in same newtask chooser; do
    extra=""
    [ "$mode" = newtask ] && extra=" newtask=1"
    [ "$mode" = chooser ] && extra=" chooser=1"
    A shell input keyevent KEYCODE_HOME; sleep 0.8
    A logcat -c
    cmd launch "$spec$extra" >/dev/null
    sleep 1.5
    t=$(A shell dumpsys activity activities | grep -m1 topResumedActivity | sed -E 's/.* u0 ([^ ]+) t[0-9]+.*/\1/')
    case "$t" in com.plainphone.spike.emu/*) v=BLOCKED;; android/com.android.internal.app.BlockedAppActivity) v=BLOCKED_SCREEN;; *) v=OPENED;; esac
    why=$(A logcat -d | grep -E "PPEMU.*launch|Lock ?Task|lock task|LockTask" | grep -v "cmd \[" | tail -2 | sed -E 's/^.{0,33}//' | tr '\n' ' ' | cut -c1-220)
    echo -e "$id\t$mode\t$t\t$v\t$spec\t$why" | tee -a "$OUT" | cut -f1-4
  done
done
A shell input keyevent KEYCODE_HOME
