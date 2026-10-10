#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# PLA-219 host side of WindowContainmentCheck, against $ANDROID_SERIAL (an emulator you own).
#   run-check.sh <phase> [extra am-instrument args...]
# For uid, pause-preparing and pause-editing it starts the phase in the background, waits for PAUSED in
# logcat, tries every way into the target, then kills the test process (am force-stop, else kill -9; no finally).
set -uo pipefail
: "${ANDROID_SERIAL:?set ANDROID_SERIAL}"
phase="$1"; shift
runner=org.plainphone.device.test/org.plainphone.device.storage.DirectBootJournalCheck
target=org.plainphone.launchtarget
sh() { adb shell "$@" | tr -d '\r'; }
top() { sh dumpsys activity activities | grep -m1 topResumedActivity | sed -E 's/.*u0 ([^ ]+) t[0-9]+.*/\1/'; }
mode() { sh dumpsys activity activities | grep -m1 -o 'mLockTaskModeState=[A-Z]*'; }
try() {
  local what="$1"; shift
  echo "## attempt: $what"
  echo "\$ $*"
  sh "$@" 2>&1 | grep -E 'Status|Activity:|Error|Warning|Starting' | sed 's/^/  /'
  sleep 1.5
  after
}
# After each attempt: what is on top, whether the target's process exists, then BACK to Plainphone's Home.
after() {
  echo "  -> top resumed: $(top); $(mode); target process: '$(sh pidof $target)'"
  sh input keyevent BACK; sleep 1
  echo "  -> after BACK: $(top)"
}
tap_notification() {
  echo "## attempt: notification tap"
  sh cmd statusbar expand-notifications; sleep 1.5
  sh uiautomator dump /sdcard/pla219.xml >/dev/null 2>&1
  local bounds; bounds=$(sh cat /sdcard/pla219.xml | grep -o 'text="PLA219 open target"[^>]*bounds="\[[0-9]*,[0-9]*\]\[[0-9]*,[0-9]*\]"' | grep -o 'bounds="[^"]*"' | head -1)
  echo "  shade expanded; notification row: ${bounds:-not found}"
  if [ -n "$bounds" ]; then
    read -r x1 y1 x2 y2 <<<"$(echo "$bounds" | tr -c '0-9' ' ')"
    sh input tap $(( (x1 + x2) / 2 )) $(( (y1 + y2) / 2 )); echo "  tapped"
  fi
  sleep 1.5
  sh cmd statusbar collapse; sh rm -f /sdcard/pla219.xml
  after
}
case "$phase" in
uid|pause-preparing|pause-editing)
  adb logcat -c
  adb shell am instrument -w -e phase "$phase" "$@" "$runner" > "/tmp/pla219-$phase.out" 2>&1 &
  for _ in $(seq 1 120); do adb logcat -d -s PLA113 | grep -q 'PAUSED' && break; sleep 1; done
  adb logcat -d -s PLA113 | sed -E 's/^.*PLA113 *: //' | grep '^PLA219'
  echo "## paused; lock task: $(mode); top resumed: $(top)"
  if [ "$phase" = uid ]; then
    try "uid-b (shares uid-a's UID, not listed)" am start -W -n org.plainphone.uidtarget.b/android.app.Activity
    try "launch-target (own UID, not listed; control)" am start -W -n $target/android.app.Activity
  else
    try "explicit start" am start -W -n $target/android.app.Activity
    try "deep link" am start -W -a android.intent.action.VIEW -d plainphone-check://open
    try "share (SEND text/plain to the target)" am start -W -a android.intent.action.SEND -t text/plain --es android.intent.extra.TEXT hello -p $target
    tap_notification
    echo "## attempt: recents (KEYCODE_APP_SWITCH)"; sh input keyevent KEYCODE_APP_SWITCH; sleep 1.5
    echo "  -> top resumed: $(top); $(mode); recents activity resumed: $(sh dumpsys activity activities | grep -c 'RecentsActivity\|QuickstepRecents' | head -1)"
    echo "## target state while paused: hidden=$(sh dumpsys package $target | grep -m1 -o 'hidden=[a-z]*')"
  fi
  # API 35 ignores am force-stop for the Device Owner ("Ignoring request to force stop protected package"),
  # so the kill is SIGKILL as root (userdebug emulator): no finally, no shutdown hook.
  pid=$(sh pidof org.plainphone.device.test)
  echo "## kill: am force-stop org.plainphone.device.test (then kill -9 $pid as root if it survives)"
  sh am force-stop org.plainphone.device.test; sleep 1
  if [ -n "$(sh pidof org.plainphone.device.test)" ]; then
    echo "  force-stop ignored: $(adb logcat -d | grep -m1 'Ignoring request to force stop protected package' | sed -E 's/^.*ActivityManager: //')"
    sh su 0 kill -9 "$pid"; sleep 2
  fi
  echo "  -> test-package process after kill: '$(sh pidof org.plainphone.device.test)' (killed pid $pid; a new pid is Android restarting the Device Owner app, not the check)"
  echo "  -> lock task after kill: $(mode); top resumed: $(top)"
  echo "## am instrument output after the kill:"; sed 's/^/  /' "/tmp/pla219-$phase.out" | tail -3
  ;;
*)
  adb shell am instrument -w -e phase "$phase" "$@" "$runner" | tr -d '\r' | grep -E '^PLA219|RESULT'
  ;;
esac
