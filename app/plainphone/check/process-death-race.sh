#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# Emulator regression for PLA-307 finding 2 ("Lock task must actually be running"): Plainphone's
# process dies and an unapproved activity reaches the front before it restarts. Emulators only
# (TEAM-POLICY gate 2); it never places a call.
#
# Needs: the debug build installed as Device Owner, the policy applied, the phone unlocked with Home
# LOCKED (flags 119). Usage:
#   ANDROID_SERIAL=emulator-NNNN process-death-race.sh explicit|notification|reapply [out-dir]
#
# explicit: kill -9 Home's process (run-as) and at once `am start` Settings.
# notification: post the debug TestHook notification for Settings, open the shade, kill the process
#   and tap the notification.
# reapply: kill, relaunch Home, kill again 0.15 s into the new process's start re-apply, then
#   `am start` Settings.
# Samples every 0.25 s for 15 s. Pass: lock task is LOCKED by the end with Home (or Android's lock-task
# block screen) on top, and a centre tap afterwards does not reach Settings. Prints how long Settings was top-resumed (the window).
set -euo pipefail
variant=${1:?explicit|notification|reapply}
out=${2:-.}
mkdir -p "$out"
name=race-$variant
log() { echo "$(date +%T) $*" | tee -a "$out/$name.txt"; }
sh() { adb shell "$@" | tr -d '\r'; }
lock_state() { sh dumpsys activity activities | sed -n 's/.*mLockTaskModeState=\([A-Z]*\).*/\1/p' | head -1; }
top() { sh dumpsys activity activities | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* u0 \([^ ]*\) .*/\1/p' | head -1; }
now() { python3 -c 'import time;print(time.time())'; }

: > "$out/$name.txt"
sh cmd statusbar collapse
sh am force-stop com.android.settings
sh am start -n org.plainphone/.MainActivity >/dev/null 2>&1
sleep 2
log "before: lockTask=$(lock_state) top=$(top) $(sh dumpsys device_policy | grep -m1 LockTaskPolicy | xargs)"
[ "$(lock_state)" = LOCKED ] || { log "FAIL precondition: Home not LOCKED"; exit 1; }
adb logcat -c
pid=$(sh pidof org.plainphone)
case $variant in
explicit)
    t0=$(now); sh run-as org.plainphone kill -9 "$pid"; sh am start -n com.android.settings/.Settings >/dev/null 2>&1;;
reapply)
    # The restarted process runs its "start" re-apply at once; kill it again 0.15 s into that run.
    sh run-as org.plainphone kill -9 "$pid"; sh am start -n org.plainphone/.MainActivity >/dev/null 2>&1; sleep 0.15
    pid=$(sh pidof org.plainphone)
    t0=$(now); sh run-as org.plainphone kill -9 "$pid"; sh am start -n com.android.settings/.Settings >/dev/null 2>&1;;
notification)
    sh pm grant org.plainphone android.permission.POST_NOTIFICATIONS
    sh am broadcast -n org.plainphone/.boot.TestHook -a org.plainphone.test.NOTIFY --es component com.android.settings/.Settings >/dev/null
    sleep 1; sh cmd statusbar expand-notifications; sleep 1.5
    sh uiautomator dump /data/local/tmp/ui.xml >/dev/null
    bounds=$(sh cat /data/local/tmp/ui.xml | grep -o 'text="Unapproved app"[^>]*bounds="[^"]*"' | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' || true)
    [ -n "$bounds" ] || { log "FAIL notification not found"; exit 1; }
    set -- $bounds
    pid=$(sh pidof org.plainphone)
    t0=$(now); sh run-as org.plainphone kill -9 "$pid"; sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 ));;
*) echo "unknown variant $variant" >&2; exit 2;;
esac
first=""; last=""
for i in $(seq 1 60); do
    s=$(lock_state); t=$(top); el=$(python3 -c "print(round($(now)-$t0,2))")
    log "t=+${el}s lockTask=$s top=$t pid=$(sh pidof org.plainphone || true)"
    case $t in ""|org.plainphone/*|android/com.android.internal.app.BlockedAppActivity) ;; *) [ -z "$first" ] && first=$el; last=$el;; esac
    sleep 0.25
done
log "unapproved activity top-resumed from +${first:-never}s to +${last:-never}s"
sh input tap 540 1200; sleep 1
s=$(lock_state); t=$(top)
log "after centre tap: lockTask=$s top=$t"
adb exec-out screencap -p > "$out/$name.png"
adb logcat -d | grep -E "PlainphoneBoot: (reapply reason=|lock task not running|home shown)|ActivityTaskManager: START" > "$out/$name-logcat.txt" || true
fail=0
[ "$s" = LOCKED ] || fail=1
# Android's own lock-task block screen counts as contained: the unapproved app is not usable.
case $t in org.plainphone/*|android/com.android.internal.app.BlockedAppActivity) ;; *) fail=1;; esac
[ "$fail" = 0 ] && log "PASS $name" || { log "FAIL $name"; exit 1; }
