#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# Emulator regression for PLA-241 finding 1: an unapproved activity queued before the first unlock
# must not be visible or usable after unlock. Emulators only (TEAM-POLICY gate 2).
#
# Needs: the debug build installed as Device Owner, the full policy applied once (Home set), and an
# emulator-only lock PIN. Usage:
#   ANDROID_SERIAL=emulator-NNNN PIN=1234 first-unlock-containment.sh explicit|notification|view [out-dir]
#
# Each variant drops no_safe_boot, the whole lock-task list and the KEYGUARD/BLOCK feature bits through
# the debug TestHook (a power cut that lost those writes), reboots, waits for the locked-boot re-apply,
# checks that the keyguard is still up, queues the stimulus while the user is RUNNING_LOCKED, unlocks
# with the PIN and takes 12 samples. Pass: every sample after unlock has lock task LOCKED, the
# unapproved package is never top-resumed, and a tap where it would be leaves it unreached.
# It never places a call.
set -euo pipefail
variant=${1:?explicit|notification|view}
out=${2:-.}
pin=${PIN:?emulator PIN}
mkdir -p "$out"
log() { echo "$(date +%T) $*" | tee -a "$out/$variant.txt"; }
sh() { adb shell "$@" | tr -d '\r'; }
lock_state() { sh dumpsys activity activities | sed -n 's/.*mLockTaskModeState=\([A-Z]*\).*/\1/p' | head -1; }
top() { sh dumpsys activity activities | sed -n 's/.*topResumedActivity=ActivityRecord{[^ ]* u0 \([^ ]*\) .*/\1/p' | head -1; }
keyguard() { sh dumpsys activity activities | sed -n 's/.*mKeyguardShowing=\([a-z]*\).*/\1/p' | head -1; }
user() { sh am get-started-user-state 0; }

: > "$out/$variant.txt"
sh am broadcast -n org.plainphone/.boot.TestHook -a org.plainphone.test.DROP --es restriction no_safe_boot --ei features 16 >/dev/null
log "dropped: $(sh dumpsys device_policy | grep -m1 LockTaskPolicy | xargs)"
adb reboot
adb wait-for-device
until [ "$(user)" = RUNNING_LOCKED ]; do sleep 0.5; done
until adb logcat -d -s PlainphoneBoot | grep -q 'reason=locked-boot'; do sleep 0.5; done
sleep 3
log "locked: user=$(user) keyguard=$(keyguard) lockTask=$(lock_state) top=$(top)"
log "locked policy: $(sh dumpsys device_policy | grep -m1 LockTaskPolicy | xargs)"
[ "$(keyguard)" = true ] || { log "FAIL keyguard not showing before unlock"; exit 1; }

case $variant in
explicit) target=com.android.settings; log "queue: $(sh am start -n com.android.settings/.Settings 2>&1 | tail -1)";;
view) target=com.android.chrome; log "queue: $(sh am start -a android.intent.action.VIEW -d https://example.invalid/ 2>&1 | tail -1)";;
notification) target=com.android.settings
    sh pm grant org.plainphone android.permission.POST_NOTIFICATIONS
    sh am broadcast -n org.plainphone/.boot.TestHook -a org.plainphone.test.NOTIFY --es component com.android.settings/.Settings >/dev/null
    log "queue: notification posted";;
*) echo "unknown variant $variant" >&2; exit 2;;
esac
adb exec-out screencap -p > "$out/$variant-locked.png"

sh input keyevent KEYCODE_WAKEUP; sleep 0.5
sh input swipe 540 1900 540 600 200; sleep 1
sh input text "$pin"; sh input keyevent KEYCODE_ENTER
until [ "$(user)" = RUNNING_UNLOCKED ]; do sleep 0.2; done
fail=0
for i in $(seq 1 12); do
    s=$(lock_state); t=$(top)
    log "sample $i: user=$(user) lockTask=$s top=$t"
    [ "$s" = LOCKED ] || fail=1
    case $t in $target/*) fail=1;; esac
    sleep 0.5
done
adb exec-out screencap -p > "$out/$variant-unlocked.png"

if [ "$variant" = notification ]; then
    sh cmd statusbar expand-notifications; sleep 1.5
    adb exec-out screencap -p > "$out/$variant-shade.png"
    sh uiautomator dump /data/local/tmp/ui.xml >/dev/null
    bounds=$(sh cat /data/local/tmp/ui.xml | grep -o 'text="Unapproved app"[^>]*bounds="[^"]*"' | sed 's/.*bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/' || true)
    if [ -n "$bounds" ]; then
        set -- $bounds; sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2
        log "after tap: lockTask=$(lock_state) top=$(top)"
        case $(top) in $target/*) fail=1;; esac
    else
        log "FAIL notification not found in the shade; nothing was tapped"; fail=1
    fi
    sh cmd statusbar collapse
fi
# Interaction: tap the screen centre; the unapproved app must still not be on top.
sh input tap 540 1200; sleep 1
log "after centre tap: lockTask=$(lock_state) top=$(top)"
case $(top) in $target/*) fail=1;; esac
[ "$fail" = 0 ] && log "PASS $variant" || { log "FAIL $variant"; exit 1; }
