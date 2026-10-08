#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). Taps the in-app RELEASE button twice within its 3 s confirm window.
# A uiautomator dump between taps takes >3 s, so find the button once and tap the same point twice.
set -euo pipefail
SERIAL="${SERIAL:-SPARE_DEVICE_SERIAL}"
case "$SERIAL" in emulator-5590|emulator-5604) echo "refusing: $SERIAL belongs to another team" >&2; exit 2;; esac
a() { adb -s "$SERIAL" "$@"; }
a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null
b=$(a shell cat /sdcard/pp-ui.xml | tr '>' '\n' | grep -E 'text="RELEASE' | head -1 \
    | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
[ -z "$b" ] && { echo "RELEASE button not on screen"; exit 1; }
set -- $b; x=$(( ($1+$3)/2 )); y=$(( ($2+$4)/2 ))
a shell input tap "$x" "$y"; sleep 0.5; a shell input tap "$x" "$y"
echo "tapped RELEASE twice at $x,$y"
