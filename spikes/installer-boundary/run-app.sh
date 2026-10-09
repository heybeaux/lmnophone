#!/bin/bash
# THROWAWAY SPIKE (PLA-86). App-level Device Owner self-update. Usage: ./run-app.sh <emulator-serial>
# Needs an emulator with no owner and no accounts. Build first: (cd app && ./gradlew -Pvc=N assembleDebug) for N=1..5 into out/upd-vN.apk.
cd "$(dirname "$0")"; A="adb -s ${1:?serial}"; P=org.plainphone.spike.updater
run() { $A shell am broadcast -n $P/.Command -a $P.RUN "$@" >/dev/null; }
vc() { $A shell dumpsys package $P | grep -m1 versionCode | tr -s ' ' | cut -d' ' -f2; }
log() { $A shell run-as $P cat files/log.txt 2>/dev/null; }
step() { echo; echo "== $*"; }
echo "device: $($A shell getprop ro.build.version.release) API $($A shell getprop ro.build.version.sdk)"
$A shell dpm remove-active-admin $P/.Admin >/dev/null 2>&1; $A uninstall $P >/dev/null 2>&1
$A install -t out/upd-v1.apk >/dev/null && $A shell dpm set-device-owner $P/.Admin
D=/sdcard/Android/data/$P/files; $A shell am start -a android.intent.action.MAIN -n $P/.Admin >/dev/null 2>&1
run --es op abandon-mine; sleep 1; $A shell mkdir -p $D; for v in 2 3 4 5; do $A push out/upd-v$v.apk $D/ >/dev/null; done

step "X1 wrong installer SHA-256 checksum (v2)"; run --es op commit --es apk upd-v2.apk --es checksum wrong; sleep 6; echo "installed $(vc)"
step "X2 right checksum, silent self-update v1 -> v2"; run --es op commit --es apk upd-v2.apk --es checksum right; sleep 8; echo "installed $(vc)"
step "X3 process killed after write, before commit (v3)"; run --es op commit --es apk upd-v3.apk --es kill before; sleep 3; echo "installed $(vc)"
run --es op abandon-mine; sleep 2
step "X4 process killed right after commit() returns (v4)"; run --es op commit --es apk upd-v4.apk --es kill after; sleep 8; echo "installed $(vc)"
step "X5 normal self-update v4 -> v5"; run --es op commit --es apk upd-v5.apk; sleep 8; echo "installed $(vc)"
step "app log (survives process replacement)"; log
step "cleanup"; run --es op clear-owner; sleep 2; $A shell dpm list-owners; $A uninstall $P
