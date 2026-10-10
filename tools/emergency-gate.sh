#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# The emergency release gate (task 4.4), one command. Runs the gate on the real build and on the three
# negative-control builds, and prints PASS / FAIL / INCONCLUSIVE / NOT_BUILT for every check.
#
#   tools/emergency-gate.sh -s emulator-5770 [--quick] [--no-build] [--builds "debug ctlNoPhone ..."]
#
# Emulators only (userdebug, for `su`): it provisions each build as a testOnly Device Owner, sets and
# clears a lock-screen PIN (GATE_PIN, default 2580), reboots twice per build unless --quick, adds a
# test-mode emergency number (GATE_NUMBER, default 7357; never a real emergency number) and removes the
# Device Owner at the end. Emulator results are not proof that a real 9-1-1 call gets through.
#
# Exit status 0 only when the real build does not FAIL and every control FAILs.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
SERIAL=""
QUICK=0
BUILD=1
BUILDS="debug ctlNoPhone ctlNoDialerLockTask ctlNoAlert ctlNoAlertLockTask"
PIN="${GATE_PIN:-2580}"
NUMBER="${GATE_NUMBER:-7357}"
ADMIN="org.plainphone/.PlainphoneAdminReceiver"
GATE="org.plainphone.emergencygate/.EmergencyGate"

while [ $# -gt 0 ]; do
    case "$1" in
        -s) SERIAL="$2"; shift 2 ;;
        --quick) QUICK=1; shift ;;
        --no-build) BUILD=0; shift ;;
        --builds) BUILDS="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

case "$SERIAL" in
    emulator-5590|emulator-5604) echo "refusing $SERIAL: it belongs to another team" >&2; exit 2 ;;
    emulator-*) ;;
    *) echo "refusing '$SERIAL': emulators only (-s emulator-NNNN)" >&2; exit 2 ;;
esac

a() { adb -s "$SERIAL" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
log() { echo "[gate $(date +%H:%M:%S)] $*" >&2; }

if [ "$(sh_ getprop ro.build.type)" != "userdebug" ]; then
    echo "refusing $SERIAL: needs a userdebug image (su for the test emergency number)" >&2; exit 2
fi
if [ "$(sh_ locksettings get-disabled)" != "true" ]; then
    echo "refusing $SERIAL: a lock-screen credential is set; clear it first (locksettings clear --old <pin>)" >&2; exit 2
fi

control_name() {
    case "$1" in
        debug) echo none ;; ctlNoPhone) echo no-phone ;;
        ctlNoDialerLockTask) echo no-dialer-lock-task ;; ctlNoAlert) echo no-alert ;;
        ctlNoAlertLockTask) echo no-alert-lock-task ;;
        *) echo "?" ;;
    esac
}

apk_for() {
    if [ "$1" = debug ]; then echo "$APP/plainphone/build/outputs/apk/debug/plainphone-debug.apk"
    else echo "$APP/plainphone/build/outputs/apk/$1/plainphone-$1.apk"; fi
}

wait_boot() {
    a wait-for-device
    for _ in $(seq 1 180); do [ "$(sh_ getprop sys.boot_completed)" = 1 ] && return 0; sleep 1; done
    log "boot did not complete"; return 1
}

keyguard_showing() { sh_ dumpsys window | grep -q "isKeyguardShowing=true"; }

# Unlocks with the PIN. Never types it into an emergency dialer: that screen would take the digits.
unlock() {
    local top focus
    for _ in 1 2 3 4; do
        top="$(sh_ dumpsys activity activities | grep -m1 topResumedActivity)"
        case "$top" in *safetyhub*|*com.android.phone/*|*dialer*) sh_ input keyevent KEYCODE_BACK >/dev/null; sleep 1 ;; *) break ;; esac
    done
    sh_ input keyevent KEYCODE_WAKEUP >/dev/null
    keyguard_showing || return 0
    sh_ wm dismiss-keyguard >/dev/null
    sleep 1.5
    focus="$(sh_ dumpsys window | grep -m1 mCurrentFocus)"
    case "$focus" in
        *NotificationShade*|*Keyguard*|*Bouncer*) ;;
        *) log "not on the PIN screen ($focus); not typing the PIN"; return 1 ;;
    esac
    sh_ input text "$PIN" >/dev/null
    sh_ input keyevent KEYCODE_ENTER >/dev/null
    for _ in $(seq 1 20); do keyguard_showing || return 0; sleep 0.5; done
    log "unlock did not finish"; return 1
}

CHECK_TIMEOUT="${GATE_CHECK_TIMEOUT:-150}"

# One am instrument run, killed after CHECK_TIMEOUT seconds so a stuck check can never hang the gate.
instrument() {
    local out rc
    out="$(perl -e 'alarm shift; exec @ARGV' "$CHECK_TIMEOUT" adb -s "$SERIAL" shell am instrument -w "$@" "$GATE" 2>&1)"
    rc=$?
    if [ "$rc" = 142 ]; then
        sh_ am force-stop org.plainphone.emergencygate >/dev/null
        echo "TIMEOUT after ${CHECK_TIMEOUT}s"
    fi
    echo "$out" | tr -d '\r'
}

# Runs one gate check and prints its GATE lines. A missing line or a timeout is a FAIL, never silence.
check() {
    local name="$1"; shift
    local out
    out="$(instrument -e check "$name" "$@")"
    if echo "$out" | grep -q '^GATE '; then echo "$out" | grep '^GATE '
    else echo "GATE $name FAIL gate produced no result: $(echo "$out" | head -3 | tr '\n' ' ')"; fi
}

# Right after a reboot am instrument is refused for a few seconds, and an early attempt can block
# without ever starting the gate (seen on API 35). Retry with a short timeout per attempt.
check_early() {
    local name="$1" out end=$(( $(date +%s) + 180 ))
    while [ "$(date +%s)" -lt "$end" ]; do
        out="$(CHECK_TIMEOUT=25 instrument -e check "$name")"
        if echo "$out" | grep -q '^GATE '; then echo "$out" | grep '^GATE '; return; fi
        sleep 0.5
    done
    echo "GATE $name FAIL gate could not start within 180 s of boot"
}

# The emulated modem keeps a live call through an Android reboot. End the test call, or stop here.
end_call() {
    local out
    for _ in 1 2 3; do
        [ "$(sh_ dumpsys telephony.registry | grep -m1 -o 'mCallState=[0-9]')" = "mCallState=0" ] && return 0
        out="$(instrument -e check hangup)"
        echo "$out" | grep -q '^GATE hangup PASS' && return 0
    done
    echo "GATE hangup FAIL the test call is still live; stopping this build before any reboot"
    sh_ dpm remove-active-admin "$ADMIN" >&2
    return 1
}

provision() {
    local build="$1" control
    control="$(control_name "$build")"
    if sh_ dpm list-owners | grep -q "org.plainphone/"; then sh_ dpm remove-active-admin "$ADMIN" >/dev/null; fi
    a uninstall org.plainphone >/dev/null 2>&1
    a install -t -r "$(apk_for "$build")" >/dev/null || { log "install failed"; return 1; }
    a logcat -c
    sh_ dpm set-device-owner "$ADMIN" >&2 || return 1
    sh_ input keyevent KEYCODE_HOME >/dev/null
    for _ in $(seq 1 60); do
        if a logcat -d -s PlainphoneBoot | grep -q "control=$control locked=false ready=true"; then
            sh_ input keyevent KEYCODE_HOME >/dev/null; sleep 2; return 0
        fi
        sleep 1
    done
    log "no ready re-apply for $build"; return 1
}

run_build() {
    local build="$1"
    log "== $build (control=$(control_name "$build")) =="
    if ! provision "$build"; then echo "GATE provision FAIL could not provision $build as Device Owner"; return; fi
    a logcat -d -s PlainphoneBoot | grep "reapply" | tail -1 | sed 's/^/# /' >&2
    for name in home-phone app-home-phone more-emergency sos alert; do check "$name"; done
    sh_ su 0 cmd phone emergency-number-test-mode -a "$NUMBER" >/dev/null
    check telecom-emergency-call -e number "$NUMBER"
    end_call || return
    sh_ su 0 cmd phone emergency-number-test-mode -c >/dev/null
    if [ "$QUICK" = 0 ]; then
        sh_ locksettings set-pin "$PIN" >/dev/null
        check lock-screen
        unlock
        a reboot; wait_boot
        check locked-boot
        unlock
        sh_ locksettings clear --old "$PIN" >/dev/null
        a reboot; a wait-for-device
        check_early boot-checking
        wait_boot
    fi
    sh_ input keyevent KEYCODE_BACK >/dev/null   # any blocked screen left by a failing check
    sh_ dpm remove-active-admin "$ADMIN" >&2
    sleep 1
    echo "# removal: $(sh_ dpm list-owners | tr '\n' ' ') lockTask=$(sh_ dumpsys activity activities | grep -o 'mLockTaskModeState=[A-Z]*' | head -1)"
}

overall() {
    if grep -q '^GATE [^ ]* FAIL' "$1" || ! grep -q '^GATE ' "$1"; then echo FAIL
    elif grep -q '^GATE [^ ]* INCONCLUSIVE' "$1"; then echo INCONCLUSIVE
    else echo PASS; fi
}

if [ "$BUILD" = 1 ]; then
    tasks=":emergencygate:assembleDebug"
    for b in $BUILDS; do tasks="$tasks :plainphone:assemble$(echo "${b:0:1}" | tr a-z A-Z)${b:1}"; done
    (cd "$APP" && ./gradlew -q $tasks) || exit 1
fi
a install -t -r "$APP/emergencygate/build/outputs/apk/debug/emergencygate-debug.apk" >/dev/null || exit 1

OUT="$(mktemp -d)"
echo "Emergency gate on $SERIAL: $(sh_ getprop ro.build.fingerprint), API $(sh_ getprop ro.build.version.sdk)"
for b in $BUILDS; do run_build "$b" | tee "$OUT/$b.txt" >&2; done

status=0
echo
printf '%-24s' "check"; for b in $BUILDS; do printf '%-22s' "$b"; done; echo
checks="$(cat "$OUT"/*.txt | awk '/^GATE /{print $2}' | awk '!seen[$0]++')"
for c in $checks; do
    printf '%-24s' "$c"
    for b in $BUILDS; do printf '%-22s' "$(awk -v c="$c" '$1=="GATE" && $2==c {print $3}' "$OUT/$b.txt" | head -1)"; done
    echo
done
printf '%-24s' "OVERALL"
for b in $BUILDS; do
    v="$(overall "$OUT/$b.txt")"; printf '%-22s' "$v"
    if [ "$b" = debug ]; then [ "$v" = FAIL ] && status=1
    else [ "$v" = FAIL ] || status=1; fi
done
echo
echo "Expectation: the real build (debug) does not FAIL; every ctl* build FAILs. Inconclusive is never a pass."
echo "Raw lines: $OUT"
exit $status
