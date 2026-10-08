#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). Helpers for driving the spike DPC.
# Usage: SERIAL=SPARE_DEVICE_SERIAL tools/pp.sh <subcommand> [args]
#   cmd "<dpc command line>"   send a command to the spike DPC (see Commands.kt)
#   top                        print the top resumed activity
#   shot <file.png>            screenshot to file
#   tap "<text prefix>"        tap a visible node by text/content-desc prefix
#   ui                         list visible texts
#   log                        dump PPSPIKE + lock task log lines
set -euo pipefail
SERIAL="${SERIAL:-SPARE_DEVICE_SERIAL}"
case "$SERIAL" in emulator-5590|emulator-5604) echo "refusing: $SERIAL belongs to another team" >&2; exit 2;; esac
a() { adb -s "$SERIAL" "$@"; }
sub="$1"; shift
case "$sub" in
  cmd) a shell am broadcast -n com.plainphone.spike.dpc/.CommandReceiver -a com.plainphone.spike.CMD \
         --es cmd "'$*'" | sed -n 's/.*data="\(.*\)"/\1/p' ;;
  top) a shell dumpsys activity activities | grep -m1 -E 'topResumedActivity|ResumedActivity:' | sed 's/^ *//' ;;
  shot) a exec-out screencap -p > "$1"; echo "saved $1" ;;
  # tap the first on-screen node whose text or content-desc starts with $1
  tap) a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null
       b=$(a shell cat /sdcard/pp-ui.xml | tr '>' '\n' | grep -E "(text|content-desc)=\"$1" | head -1 \
           | sed -n 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]".*/\1 \2 \3 \4/p')
       [ -z "$b" ] && { echo "no node starting with: $1"; exit 1; }
       set -- $b; x=$(( ($1+$3)/2 )); y=$(( ($2+$4)/2 )); a shell input tap $x $y; echo "tapped $x,$y" ;;
  ui) a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null; a shell cat /sdcard/pp-ui.xml | tr '>' '\n' \
        | grep -oE '(text|content-desc)="[^"]+"' | sort -u ;;
  log) a logcat -d | grep -E 'PPSPIKE|LockTaskController|Displayed .*BlockedAppActivity|User restriction prevents' || true ;;
  *) echo "unknown $sub" >&2; exit 2 ;;
esac
