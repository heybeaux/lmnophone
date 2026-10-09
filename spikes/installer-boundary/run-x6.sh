#!/bin/bash
# THROWAWAY SPIKE (PLA-140 X6). Does abandoning an already-committed, still-installing own session cancel
# the install? Usage: ./run-x6.sh <emulator-serial> [phase 1|2]. Needs an emulator with no owner and no accounts.
# Build first: (cd app && ./gradlew -Pvc=1 assembleDebug), then -Pvc=N -Ppad=1 for N=6..11 into out/upd-vN.apk.
cd "$(dirname "$0")"; A="adb -s ${1:?serial}"; P=org.plainphone.spike.updater
run() { $A shell am broadcast -n $P/.Command -a $P.RUN "$@" >/dev/null; }
vc() { $A shell dumpsys package $P | grep -m1 versionCode | tr -s ' ' | cut -d' ' -f2; }
step() { echo; echo "== $*"; }
echo "device: $($A shell getprop ro.build.version.release) API $($A shell getprop ro.build.version.sdk) $($A emu avd name | head -1)"
$A shell dpm remove-active-admin $P/.Admin >/dev/null 2>&1; $A uninstall $P >/dev/null 2>&1
$A install -t out/upd-v1.apk >/dev/null && $A shell dpm set-device-owner $P/.Admin
D=/sdcard/Android/data/$P/files; $A shell am start -a android.intent.action.MAIN -n $P/.Admin >/dev/null 2>&1
run --es op abandon-mine; sleep 1; $A shell mkdir -p $D; for v in 6 7 8 9 10 11; do $A push out/upd-v$v.apk $D/ >/dev/null; done
echo "apk size $(stat -f %z out/upd-v6.apk) bytes (40 MB incompressible asset)"

if [ "${2:-1}" = 1 ]; then
step "X6a baseline: commit v6, no abandon (install duration)"; run --es op commit --es apk upd-v6.apk; sleep 15; echo "installed $(vc)"
step "X6b commit v7, then abandon ALL own sessions at once"; run --es op commit --es apk upd-v7.apk --es then abandon-all --ei delay 0; sleep 15; echo "installed $(vc)"
step "X6c commit v8, then abandon ALL own sessions after 150 ms"; run --es op commit --es apk upd-v8.apk --es then abandon-all --ei delay 150; sleep 15; echo "installed $(vc)"
step "X6d commit v9, then abandon only UNCOMMITTED sessions at once"; run --es op commit --es apk upd-v9.apk --es then abandon-uncommitted --ei delay 0; sleep 15; echo "installed $(vc)"
step "X6e kill after commit (v10); a new process at once abandons ALL"; run --es op commit --es apk upd-v10.apk --es kill after; run --es op abandon-mine --es which all; sleep 15; echo "installed $(vc)"
step "X6f kill after commit (v11); a new process at once abandons only UNCOMMITTED"; run --es op commit --es apk upd-v11.apk --es kill after; run --es op abandon-mine --es which uncommitted; sleep 15; echo "installed $(vc)"
else
step "X6g commit v6; poll until isCommitted() is true (no abandon)"; run --es op commit --es apk upd-v6.apk --es then poll-when-committed; sleep 15; echo "installed $(vc)"
step "X6h commit v7; once isCommitted() is true, abandon ALL own sessions"; run --es op commit --es apk upd-v7.apk --es then abandon-all-when-committed; sleep 15; echo "installed $(vc)"
step "X6i commit v8; once isCommitted() is true, abandon only UNCOMMITTED sessions"; run --es op commit --es apk upd-v8.apk --es then abandon-uncommitted-when-committed; sleep 15; echo "installed $(vc)"
step "X6h repeat with v9"; run --es op commit --es apk upd-v9.apk --es then abandon-all-when-committed; sleep 15; echo "installed $(vc)"
step "X6b repeat with v10: abandon ALL at once after commit() returns"; run --es op commit --es apk upd-v10.apk --es then abandon-all --ei delay 0; sleep 15; echo "installed $(vc)"
step "X6i repeat with v11"; run --es op commit --es apk upd-v11.apk --es then abandon-uncommitted-when-committed; sleep 15; echo "installed $(vc)"
fi
step "app log (survives process replacement)"; $A shell run-as $P cat files/log.txt 2>/dev/null
step "cleanup"; run --es op clear-owner; sleep 2; $A shell dpm list-owners; $A uninstall $P
