#!/usr/bin/env bash
# THROWAWAY SPIKE (PLA-5). A3 lock-task escape matrix.
# Launches each spec from the spike's MainActivity (an allowed app) and records what ends up on top.
# Usage: tools/a3-matrix.sh <outdir>
set -uo pipefail
: "${SERIAL:?Set SERIAL to the explicitly approved test device}"
case "$SERIAL" in emulator-*) echo "refusing: physical-device helper requires an approved physical device" >&2; exit 2;; esac
export SERIAL
a() { adb -s "$SERIAL" "$@"; }
here="$(cd "$(dirname "$0")" && pwd)"
out="$1"; mkdir -p "$out"
SPIKE=com.plainphone.spike.dpc

CASES=(
  "settings-newtask|action=android.settings.SETTINGS newtask=1"
  "settings-sametask|action=android.settings.SETTINGS"
  "settings-explicit-newtask|action=android.intent.action.MAIN comp=com.android.settings/.Settings newtask=1"
  "settings-explicit-sametask|action=android.intent.action.MAIN comp=com.android.settings/.Settings"
  "appinfo-sametask|action=android.settings.APPLICATION_DETAILS_SETTINGS data=package:com.google.android.dialer"
  "panel-internet-newtask|action=android.settings.panel.action.INTERNET_CONNECTIVITY newtask=1"
  "panel-internet-sametask|action=android.settings.panel.action.INTERNET_CONNECTIVITY"
  "panel-volume-sametask|action=android.settings.panel.action.VOLUME"
  "play-market-newtask|data=market://details?id=com.google.android.apps.fitness newtask=1"
  "play-market-sametask|data=market://details?id=com.google.android.apps.fitness"
  "play-explicit-newtask|action=android.intent.action.MAIN comp=com.android.vending/com.google.android.finsky.activities.MainActivity newtask=1"
  "play-explicit-sametask|action=android.intent.action.MAIN comp=com.android.vending/com.google.android.finsky.activities.MainActivity"
  "browser-https-sametask|data=https://example.com"
  "share-chooser-sametask|action=android.intent.action.SEND type=text/plain text=hello_from_spike chooser=1"
  "share-to-messages-sametask|action=android.intent.action.SEND type=text/plain text=hello_from_spike pkg=com.google.android.apps.messaging"
)

# Optional second argument: a file of "name|spec" lines that replaces the built-in cases.
if [ $# -ge 2 ]; then mapfile -t CASES < <(grep -v '^#' "$2" | grep '|'); fi

wake() { a shell input keyevent KEYCODE_WAKEUP; a shell wm dismiss-keyguard >/dev/null 2>&1; sleep 0.5; }

back_to_spike() {
  wake
  for _ in 1 2 3 4; do
    "$here/pp.sh" top | grep -q "$SPIKE/" && return 0
    a shell input keyevent KEYCODE_BACK; sleep 0.8
  done
  a shell am start -n "$SPIKE/.MainActivity" >/dev/null 2>&1; sleep 1.5
}

printf 'case\ttop_after_launch\tlockTaskModeState\tverdict\tscreen\tlog\n' > "$out/matrix.tsv"
for c in "${CASES[@]}"; do
  name="${c%%|*}"; spec="${c#*|}"
  back_to_spike
  a logcat -c
  wake
  "$here/pp.sh" cmd "launch $spec" >/dev/null
  sleep 2.5
  awake=$(a shell dumpsys power | grep -m1 -oE 'mWakefulness=[A-Za-z]+' | cut -d= -f2)
  # Settings panels on 14+ can be SystemUI dialogs, not activities: look for a focused non-activity window too.
  focus=$(a shell dumpsys window | grep -m1 mCurrentFocus | sed -E 's/.*u0 ([^}]+)}.*/\1/')
  top=$("$here/pp.sh" top | sed -E 's/.*u0 ([^ ]+) t[0-9]+.*/\1/')
  st=$(a shell dumpsys activity activities | grep -m1 -oE 'mLockTaskModeState=[A-Z]+' | cut -d= -f2)
  log=$(a logcat -d | grep -E 'LockTaskController|BlockedAppActivity|PPSPIKE: launch|not permitted|startActivity blocked' \
        | sed -E 's/^.{31}//' | tr '\n' ' ' | cut -c1-400)
  case "$top" in
    $SPIKE/*) case "$focus" in $SPIKE/*|"") verdict=BLOCKED ;; *) verdict="OPENED_WINDOW($focus)" ;; esac ;;
    *BlockedAppActivity*) verdict=BLOCKED_SCREEN ;;
    *) verdict=OPENED ;;
  esac
  "$here/pp.sh" shot "$out/$name.png" >/dev/null
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "$name" "$top" "$st" "$verdict" "$awake" "$log" | tee -a "$out/matrix.tsv"
done
back_to_spike
