#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). A3: from a launch spec, tap through a path of on-screen labels and record where we end up.
# Usage: tools/a3-probe.sh <outdir> <name> "<launch spec>" "<label1>" ["<label2>" ...]
# A label of "@x,y" taps a raw coordinate (for icon-only buttons).
set -uo pipefail
: "${SERIAL:?Set SERIAL to the explicitly approved test device}"
case "$SERIAL" in emulator-*) echo "refusing: physical-device helper requires an approved physical device" >&2; exit 2;; esac
export SERIAL
a() { adb -s "$SERIAL" "$@"; }
here="$(cd "$(dirname "$0")" && pwd)"
out="$1"; name="$2"; spec="$3"; shift 3; mkdir -p "$out"
SPIKE=com.plainphone.spike.dpc
top() { "$here/pp.sh" top | sed -E 's/.*u0 ([^ ]+) t[0-9]+.*/\1/'; }
focus() { a shell dumpsys window | grep -m1 mCurrentFocus | sed -E 's/.*u0 ([^}]+)}.*/\1/'; }
a shell input keyevent KEYCODE_WAKEUP; a shell wm dismiss-keyguard >/dev/null 2>&1
for _ in 1 2 3 4; do top | grep -q "^$SPIKE/" && break; a shell input keyevent KEYCODE_BACK; sleep 0.8; done
"$here/pp.sh" cmd "launch $spec" >/dev/null; sleep 2.5
line="$name: start=$(top)"
i=0
for label in "$@"; do
  i=$((i+1))
  a shell input keyevent KEYCODE_WAKEUP
  if [[ "$label" == @* ]]; then xy="${label#@}"; a shell input tap "${xy%,*}" "${xy#*,}"; r="tapped $xy"
  else r=$("$here/pp.sh" tap "$label" 2>&1 | tail -1); fi
  sleep 2.5
  line="$line | [$label] $r -> top=$(top) focus=$(focus)"
  "$here/pp.sh" shot "$out/$name-$i.png" >/dev/null
done
st=$(a shell dumpsys activity activities | grep -m1 -oE 'mLockTaskModeState=[A-Z]+' | cut -d= -f2)
echo "$line | lockTask=$st" | tee -a "$out/probes.txt"
