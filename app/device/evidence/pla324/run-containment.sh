#!/usr/bin/env bash
# PLA-324: the full first-unlock-containment.sh set, then lock-screen Emergency, on the final APK.
# Emulators only; emulator-only PIN. Never dials or places a call.
set -uo pipefail
export PIN=${PIN:-1234}   # emulator-only PIN
here=$(cd "$(dirname "$0")" && pwd)
check=$here/../../../plainphone/check/first-unlock-containment.sh
out=$here/containment; mkdir -p "$out"
: > "$out/SUMMARY.txt"
for v in ${VARIANTS:-explicit view notification keyguard}; do
    for keep in 0 1; do
        name=$v; [ $keep = 1 ] && name=$v-kept
        rm -f "$out/$name.txt"
        KEEP_LIST=$keep bash "$check" $v "$out" > /dev/null 2>&1; rc=$?
        echo "$name: exit $rc $(tail -1 "$out/$name.txt" 2>/dev/null | cut -d' ' -f2-)" | tee -a "$out/SUMMARY.txt"
    done
done
[ "${SKIP_EMERGENCY:-0}" = 1 ] && exit 0
# Lock-screen Emergency, after a reboot that lost the KEYGUARD/BLOCK feature bits (list kept).
sh() { adb shell "$@" | tr -d '\r'; }
log() { echo "$(date +%T) $*" | tee -a "$out/emergency-kept.txt"; }
state() { sh dumpsys activity activities | grep -E "topResumedActivity|mKeyguardShowing=|mLockTaskModeState" | xargs; }
: > "$out/emergency-kept.txt"
sh am broadcast -n org.plainphone/.boot.TestHook -a org.plainphone.test.DROP --es restriction no_safe_boot --ei features 16 --ez keepList true >/dev/null
log "dropped: $(sh dumpsys device_policy | grep -m1 LockTaskPolicy | xargs)"
adb logcat -c; adb reboot; adb wait-for-device
until [ "$(sh am get-started-user-state 0)" = RUNNING_LOCKED ]; do sleep 0.5; done
until adb logcat -d -s PlainphoneBoot | grep -q 'reason=locked-boot'; do sleep 0.5; done
sleep 3
log "locked: user=$(sh am get-started-user-state 0) $(state)"
sh input keyevent KEYCODE_WAKEUP; sleep 0.5; sh input swipe 540 1900 540 600 200; sleep 1.5
sh uiautomator dump /data/local/tmp/ui.xml >/dev/null
b=$(sh cat /data/local/tmp/ui.xml | grep -o 'text="Emergency"[^>]*bounds="[^"]*"' | grep -o 'bounds="[^"]*"' | head -1)
log "Emergency button $b"
adb exec-out screencap -p > "$out/emergency-kept-bouncer.png"
set -- $(echo "$b" | sed 's/bounds="\[\([0-9]*\),\([0-9]*\)\]\[\([0-9]*\),\([0-9]*\)\]"/\1 \2 \3 \4/')
sh input tap $(( ($1 + $3) / 2 )) $(( ($2 + $4) / 2 )); sleep 2.5
log "after Emergency tap: user=$(sh am get-started-user-state 0) $(state)"
adb exec-out screencap -p > "$out/emergency-kept-dialer.png"
sh input keyevent KEYCODE_BACK; sleep 1
log "after back: user=$(sh am get-started-user-state 0) $(state)"
log "no number dialled, no call placed"
r=FAIL; sh dumpsys activity activities | grep -q "topResumedActivity" ; grep -q "after Emergency tap:.*\(safetyhub\|EmergencyDialer\).*mLockTaskModeState=LOCKED" "$out/emergency-kept.txt" && r=PASS
echo "emergency-kept: $r" | tee -a "$out/SUMMARY.txt"
# Leave the AVD unlocked on Home for the next steps.
sh input keyevent KEYCODE_WAKEUP; sh input swipe 540 1900 540 600 200; sleep 1; sh input text 1234; sh input keyevent KEYCODE_ENTER
