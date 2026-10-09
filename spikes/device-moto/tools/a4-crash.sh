#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). A4: crash the spike's foreground activity twice and record dialog, lock task state,
# and where an "App info" tap leads. Usage: tools/a4-crash.sh <outdir> <label>
set -uo pipefail
: "${SERIAL:?Set SERIAL to the explicitly approved test device}"
case "$SERIAL" in emulator-*) echo "refusing: physical-device helper requires an approved physical device" >&2; exit 2;; esac
export SERIAL
a() { adb -s "$SERIAL" "$@"; }
here="$(cd "$(dirname "$0")" && pwd)"; P="$here/pp.sh"
out="$1"; label="$2"; mkdir -p "$out"
top() { "$P" top | sed -E 's/.*u0 ([^ ]+) t[0-9]+.*/\1/'; }
focus() { a shell dumpsys window | grep -m1 mCurrentFocus | sed -E 's/.*u0 ([^}]+)}.*/\1/'; }
lt() { a shell dumpsys activity activities | grep -m1 -oE 'mLockTaskModeState=[A-Z]+' | cut -d= -f2; }
a shell input keyevent KEYCODE_WAKEUP
a shell am start -n com.plainphone.spike.dpc/.MainActivity >/dev/null 2>&1; sleep 1.5
echo "[$label] before: top=$(top) lockTask=$(lt)"
for n in 1 2; do
  a logcat -c
  "$P" cmd crash >/dev/null; sleep 3
  dlg=$("$P" ui | grep -oE 'text="[^"]+"' | grep -iE 'stopped|keeps stopping|App info|Close app|Open app again|Send feedback|Wait' | tr '\n' ' ')
  echo "[$label] crash $n: focus=$(focus) top=$(top) lockTask=$(lt) dialog=[${dlg:-none}]"
  a logcat -d | grep -E 'FATAL EXCEPTION|AppErrors|Showing crash dialog|Lock Task Mode violation' | head -4 | sed -E 's/^.{31}//' | cut -c1-200 | sed "s/^/[$label]   log: /"
  "$P" shot "$out/crash-$label-$n.png" >/dev/null
  if [ -n "$dlg" ] && echo "$dlg" | grep -q 'App info'; then
    "$P" tapx "App info" >/dev/null; sleep 2.5
    echo "[$label] tapped App info: top=$(top) focus=$(focus) lockTask=$(lt)"
    "$P" shot "$out/crash-$label-appinfo.png" >/dev/null
    a shell input keyevent KEYCODE_BACK; sleep 1
  elif [ -n "$dlg" ]; then
    "$P" tapx "Close app" >/dev/null 2>&1; sleep 1
  fi
  a shell am start -n com.plainphone.spike.dpc/.MainActivity >/dev/null 2>&1; sleep 2
done
echo "[$label] after: top=$(top) lockTask=$(lt) status: $("$P" cmd status | grep -oE 'deviceOwner=[a-z]+|lockTaskModeState=[0-9]|locked=[a-z]+' | tr '\n' ' ')"
