#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5 B-Q4). Record one step: top activity, visible text, starts since last call, screenshot.
# Usage: tools/bq4-probe.sh <app> <step-label> [--no-text] [--no-shot]
# --no-text / --no-shot: for screens that may show account data (Gmail inbox).
set -euo pipefail
SERIAL="${SERIAL:-SPARE_DEVICE_SERIAL}"; app="$1"; step="$2"; shift 2
notext=0; noshot=0; for f in "$@"; do [ "$f" = --no-text ] && notext=1; [ "$f" = --no-shot ] && noshot=1; done
d="$(dirname "$0")/../evidence/phase-b/bq4/$app"; mkdir -p "$d"
a() { adb -s "$SERIAL" "$@"; }
{
  echo "### $step  ($(date -u +%FT%TZ))"
  echo "top: $(a shell dumpsys activity activities | grep -m1 -E 'topResumedActivity' | grep -oE 'u0 [^ ]+')"
  echo "lock: $(a shell dumpsys activity activities | grep -m1 -oE 'mLockTaskModeState=[A-Z]+')"
  echo "starts:"; a logcat -d -v time | grep -E 'START u0 \{' | sed -E 's/^[0-9-]+ ([0-9:.]+).*START u0 \{([^}]*)\}.*/  \1 {\2}/' | sed -E 's/dat=([a-z]+:\/\/[^/ ]*)[^ ]*/dat=\1\/.../' | cut -c1-220 || true
  if [ $notext = 0 ]; then echo "text:"; a shell uiautomator dump /sdcard/pp-ui.xml >/dev/null; a shell cat /sdcard/pp-ui.xml | tr '>' '\n' | grep -oE '(text|content-desc)="[^"]+"' | sort -u | sed 's/^/  /' | head -40; a shell rm -f /sdcard/pp-ui.xml; fi
} | tee -a "$d/$app.log"
[ $noshot = 0 ] && a exec-out screencap -p > "$d/$(echo "$step" | tr -c 'A-Za-z0-9-' '_' | cut -c1-40).png"
a logcat -c
