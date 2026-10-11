#!/usr/bin/env bash
# Copyright 2026 Plainphone contributors
# SPDX-License-Identifier: Apache-2.0
#
# The emergency release gate (task 4.4), one command. A qualification run tests the real build (debug,
# so adb still works: a *debug qualification run*, never a release result) and all four negative-control
# builds, and judges each build's GATE lines with tools/emergency_gate_judge.py (the same table as
# GateSchema.kt).
#
#   tools/emergency-gate.sh -s emulator-5770 [--no-build] [--out DIR]          qualification run
#   tools/emergency-gate.sh -s emulator-5770 --quick | --builds "debug ..."    diagnostic run, never qualifies
#
# Emulators only (userdebug, for `su`). Per build it provisions a testOnly Device Owner, sets and clears a
# lock-screen PIN (GATE_PIN, default 2580), reboots twice, and holds the checking screen (debug TestHook).
# The only number it ever dials is the test-mode emergency number 7357 (AGENTS.md "Never do these" rule 3),
# registered with `cmd phone emergency-number-test-mode` and ended before the next check. There is no
# override. It never triggers the power-button SOS gesture: SOS is reported NOT_COVERED.
# Emulator results are not proof that a real emergency call gets through.
#
# Exit status: 0 qualified; 5 qualified except alert-shown INCONCLUSIVE (needs the board's decision);
# 1 not qualified (a FAIL, an undetected control, an ERROR or a missing check); 2 refused before starting;
# 3 diagnostic run finished (never qualifies); 4 stopped: a call, the PIN or cleanup could not be confirmed.
set -uo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP="$ROOT/app"
readonly NUMBER=7357
readonly QUALIFY_BUILDS="debug ctlNoPhone ctlNoDialerLockTask ctlNoAlert ctlNoAlertLockTask"
SERIAL=""
QUICK=0
BUILD=1
BUILDS="$QUALIFY_BUILDS"
MODE=qualification
APK_DIR=""
OUT=""
PIN="${GATE_PIN:-2580}"
ADMIN="org.plainphone/.PlainphoneAdminReceiver"
HOOK="org.plainphone/.boot.TestHook"
GATE="org.plainphone.emergencygate/.EmergencyGate"
KILL_MARK=/data/local/tmp/gate-cannot-start

while [ $# -gt 0 ]; do
    case "$1" in
        -s) SERIAL="$2"; shift 2 ;;
        --quick) QUICK=1; MODE=diagnostic; shift ;;
        --no-build) BUILD=0; shift ;;
        --builds) BUILDS="$2"; MODE=diagnostic; shift 2 ;;
        --apk-dir) APK_DIR="$2"; shift 2 ;;
        --out) OUT="$2"; shift 2 ;;
        *) echo "unknown argument: $1" >&2; exit 2 ;;
    esac
done

if [ -n "${GATE_NUMBER:-}" ] && [ "$GATE_NUMBER" != "$NUMBER" ]; then
    echo "refusing GATE_NUMBER='$GATE_NUMBER': the gate dials only the test-mode number $NUMBER (no override)" >&2; exit 2
fi
case "$SERIAL" in
    emulator-5590|emulator-5604) echo "refusing $SERIAL: it belongs to another team" >&2; exit 2 ;;
    emulator-*) ;;
    *) echo "refusing '$SERIAL': emulators only (-s emulator-NNNN)" >&2; exit 2 ;;
esac

a() { adb -s "$SERIAL" "$@"; }
sh_() { a shell "$@" | tr -d '\r'; }
log() { echo "[gate $(date +%H:%M:%S)] $*" >&2; }

if [ "$(sh_ getprop ro.kernel.qemu)" != 1 ] && [ "$(sh_ getprop ro.boot.qemu)" != 1 ]; then
    echo "refusing $SERIAL: not an emulator (ro.kernel.qemu/ro.boot.qemu)" >&2; exit 2
fi
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
    if [ -n "$APK_DIR" ]; then echo "$APK_DIR/plainphone-$1.apk"
    elif [ "$1" = debug ]; then echo "$APP/plainphone/build/outputs/apk/debug/plainphone-debug.apk"
    else echo "$APP/plainphone/build/outputs/apk/$1/plainphone-$1.apk"; fi
}
gate_apk() {
    if [ -n "$APK_DIR" ]; then echo "$APK_DIR/emergencygate-debug.apk"
    else echo "$APP/emergencygate/build/outputs/apk/debug/emergencygate-debug.apk"; fi
}
sha() { shasum -a 256 "$1" | cut -d' ' -f1; }

# ---- device state the cleanup must undo ----
# A flag is cleared only when its state is read back as restored, so the EXIT cleanup tries again.
NUMBER_ADDED=0; PIN_SET=0; HOLD_ON=0; KILL_ON=0; CLEANED=0; RESTORED=0

call_state() { sh_ dumpsys telephony.registry | grep -m1 -o 'mCallState=[0-9]' | cut -d= -f2; }

# Telecom's own call list (CallsManager `mCalls:`, not the history below it): "none live", "N live" (any
# entry that is not DISCONNECTED) or "unreadable" (a failed dump or no mCalls section is never empty).
telecom_calls() {
    local out
    out="$(sh_ dumpsys telecom)" || { echo unreadable; return; }
    echo "$out" | awk '
        !found && /^[ \t]*mCalls:[ \t]*$/ { found = 1; match($0, /^[ \t]*/); ind = RLENGTH; next }
        found && !done {
            if ($0 ~ /^[ \t]*$/) next
            match($0, /^[ \t]*/); if (RLENGTH <= ind) { done = 1; next }
            if ($0 !~ /state=DISCONNECTED/) live++
        }
        END { if (!found) print "unreadable"; else if (live) print live " live"; else print "none live" }'
}

# The emulated modem's own call list (`adb emu gsm list`, the emulator console): "empty", "N listed", or
# "unreadable" (a failed command or no console OK is never empty).
modem_calls() {
    local out n
    out="$(a emu gsm list 2>&1 | tr -d '\r')" || { echo unreadable; return; }
    echo "$out" | grep -qx 'OK' || { echo unreadable; return; }
    n="$(echo "$out" | grep -cEv '^[[:space:]]*(OK)?[[:space:]]*$')"
    if [ "$n" = 0 ]; then echo empty; else echo "$n listed"; fi
}

# When a call counts as ended (PLA-296 R2), the observation that bounds it: six mCallState reads 0.5 s
# apart are all 0 (a 3 s hold: on API 36 a call read idle once, then came back), then Telecom's mCalls list
# holds no live call and the modem's call list is empty, then mCallState is still 0 after both. That is an
# observation over about 3.5 s, not proof that the modem never brings a call back later. The gate makes it
# before every call-capable check, after the test call, and twice at cleanup (before and after the rest of
# the cleanup), and a call seen after a full hold stops the run (exit 4). Sets CALL_EVIDENCE, and
# CALL_RETURNED=1 when the hold passed but the read after it was not idle.
CALL_EVIDENCE=""; CALL_RETURNED=0
call_ended() {
    local i st tel modem
    CALL_RETURNED=0
    for i in 1 2 3 4 5 6; do
        st="$(call_state)"
        if [ "$st" != 0 ]; then CALL_EVIDENCE="mCallState=${st:-unreadable} on hold read $i"; return 1; fi
        sleep 0.5
    done
    tel="$(telecom_calls)"
    modem="$(modem_calls)"
    st="$(call_state)"
    CALL_EVIDENCE="mCallState=0 held 3 s, Telecom mCalls $tel, modem call list $modem, mCallState=${st:-unreadable} after both"
    [ "$st" = 0 ] || CALL_RETURNED=1
    [ "$tel" = "none live" ] && [ "$modem" = empty ] && [ "$st" = 0 ]
}

# Ends any live call through the gate's hang-up (KEYCODE_ENDCALL does not end an emergency call).
# The emulated modem keeps a call through an Android reboot, so nothing goes on until this succeeds.
# Returns 0 when call_ended passes; 2 when the call came back after a full hold (never retried into a
# success); 1 when it could not be ended.
hang_up() {
    local n
    for n in 1 2 3; do
        call_ended && return 0
        [ "$CALL_RETURNED" = 1 ] && return 2
        log "ending the call (attempt $n): $CALL_EVIDENCE"
        instrument -e check hangup >/dev/null
    done
    call_ended && return 0
    [ "$CALL_RETURNED" = 1 ] && return 2
    return 1
}

# Prints the GATE ERROR line and stops the whole matrix; the cleanup ends any call and undoes the set-up.
stop() {
    echo "GATE $1 ERROR $2; stopping the matrix"
    log "$1: $2; stopping the matrix"
    exit 4
}

# Before any check that can reach a dialer or a call: the full end-of-call check, not one read. A call
# that is live or unconfirmed here came back after it was ended (or was never ended): the run stops.
guard_idle() {
    call_ended || stop "$1" "a call is live or unconfirmed before $1 ($CALL_EVIDENCE)"
}

# The test number's state (PLA-296 R3): "listed", "absent", or "unknown (...)". A failed `-p` read and an
# empty or unrecognised one are unknown, never absence.
number_state() {
    local out rc
    out="$(sh_ su 0 cmd phone emergency-number-test-mode -p 2>&1)"; rc=$?
    [ "$rc" = 0 ] || { echo "unknown (read failed, exit $rc)"; return; }
    case "$out" in
        \[*\]) ;;
        *) echo "unknown (unrecognised read '$(echo "$out" | head -1 | cut -c1-60)')"; return ;;
    esac
    if echo "$out" | tr '[], ' '\n\n\n\n' | grep -qx "$NUMBER"; then echo listed; else echo absent; fi
}

# Runs in a command substitution: the caller sets NUMBER_ADDED first so the cleanup always clears it.
register_number() {
    local out st
    out="$(sh_ su 0 cmd phone emergency-number-test-mode -a "$NUMBER" 2>&1)" || { echo "registration command failed: $out"; return 1; }
    st="$(number_state)"
    [ "$st" = listed ] || { echo "$NUMBER is not in the test-mode list after registration ($st)"; return 1; }
}

# The list updates asynchronously (API 36 still listed it right after -c): poll for up to 10 s. Only a
# confirmed absence clears the flag; a failed clear or an unknown read keeps it.
clear_number() {
    local out st
    out="$(sh_ su 0 cmd phone emergency-number-test-mode -c 2>&1)" || { log "the test number clear failed: $out"; return 1; }
    for _ in $(seq 1 20); do
        st="$(number_state)"
        if [ "$st" = absent ]; then NUMBER_ADDED=0; return 0; fi
        sleep 0.5
    done
    log "test number $NUMBER is not confirmed removed: $st"; return 1
}

# The lock-screen credential: "set", "cleared", or "unknown (...)" (a failed or empty read).
pin_state() {
    local out rc
    out="$(sh_ locksettings get-disabled 2>&1)"; rc=$?
    [ "$rc" = 0 ] || { echo "unknown (read failed, exit $rc)"; return; }
    case "$out" in
        true) echo cleared ;; false) echo set ;;
        *) echo "unknown (unrecognised read '$(echo "$out" | head -1 | cut -c1-60)')" ;;
    esac
}

set_pin() {
    sh_ locksettings set-pin "$PIN" >/dev/null 2>&1
    PIN_SET=1
    [ "$(pin_state)" = set ]
}

# The clear's own result is not trusted either way: the read-back decides.
clear_pin() {
    local st
    sh_ locksettings clear --old "$PIN" >/dev/null 2>&1
    st="$(pin_state)"
    [ "$st" = cleared ] || { log "the PIN is not confirmed cleared: $st"; return 1; }
    PIN_SET=0
}

# Plainphone's Device Owner: "ours", "absent", or "unknown (...)" (a failed, empty or unrecognised read).
owner_state() {
    local out rc
    out="$(sh_ dpm list-owners 2>&1)"; rc=$?
    [ "$rc" = 0 ] || { echo "unknown (read failed, exit $rc)"; return; }
    case "$out" in
        *org.plainphone/*) echo ours ;;
        "no owners") echo absent ;;
        *) echo "unknown (unrecognised read '$(echo "$out" | head -1 | cut -c1-60)')" ;;
    esac
}

hold() {
    sh_ am broadcast -a org.plainphone.test.HOLD --ez hold "$1" -n "$HOOK" >/dev/null 2>&1
    if [ "$1" = true ]; then HOLD_ON=1; else HOLD_ON=0; fi
}

# Plainphone can't start: a root loop on the device kills its process while the marker file exists
# (`am force-stop` is a no-op for a Device Owner).
start_kill_loop() {
    sh_ su 0 touch "$KILL_MARK"
    KILL_ON=1
    a shell "su 0 sh -c 'while [ -e $KILL_MARK ]; do p=\$(pidof org.plainphone); [ -n \"\$p\" ] && kill -9 \$p; sleep 0.2; done'" >/dev/null 2>&1 &
    sleep 2
}
stop_kill_loop() {
    sh_ su 0 rm -f "$KILL_MARK"
    KILL_ON=0
    sleep 1
}

# Undoes everything the gate set up and confirms it by read-back (PLA-296 R2, R3): the call by the full
# end-of-call check before and after the rest of the cleanup, and the test number, the PIN and the Device
# Owner by a successful read that shows them gone. A failed command, a failed read and an empty or
# unrecognised read are unknown, never restored. Returns 1 unless every part is confirmed.
restore() {
    local ok=1 r calls num pin own
    [ "$KILL_ON" = 1 ] && stop_kill_loop
    hang_up; r=$?
    if [ "$r" = 2 ]; then
        ok=0; log "cleanup: the call came back after it read ended ($CALL_EVIDENCE); ending it"
        hang_up || log "cleanup: the call is still live or unconfirmed ($CALL_EVIDENCE)"
    elif [ "$r" != 0 ]; then
        ok=0; log "cleanup: a call is still live or unconfirmed ($CALL_EVIDENCE)"
    fi
    if [ "$NUMBER_ADDED" = 1 ] && ! clear_number; then ok=0; fi
    [ "$HOLD_ON" = 1 ] && hold false
    if [ "$PIN_SET" = 1 ] && ! clear_pin; then ok=0; fi
    own="$(owner_state)"
    if [ "$own" = ours ]; then sh_ dpm remove-active-admin "$ADMIN" >/dev/null 2>&1; own="$(owner_state)"; fi
    [ "$own" = absent ] || { ok=0; log "cleanup: the Device Owner is not confirmed removed: $own"; }
    # The final read-back. The call check runs last, so a call back during the cleanup is never certified.
    num="$(number_state)"; [ "$num" = absent ] || ok=0
    pin="$(pin_state)"; [ "$pin" = cleared ] || ok=0
    if call_ended; then calls="$CALL_EVIDENCE"; else
        calls="$CALL_EVIDENCE"; ok=0; log "cleanup: the final end-of-call check failed ($calls); ending the call"
        hang_up >/dev/null || log "cleanup: the call is still live or unconfirmed ($CALL_EVIDENCE)"
    fi
    log "cleanup ok=$ok: call: $calls; test number $NUMBER: $num; PIN: $pin; Device Owner: $own"
    [ -n "$OUT" ] && [ -d "$OUT" ] && echo "# cleanup ok=$ok call: $calls; test number $NUMBER: $num; PIN: $pin; Device Owner: $own" >> "$OUT/cleanup.txt"
    [ "$ok" = 1 ]
}

# The EXIT trap: a run that stops early (or a confirmed run whose restore failed) cleans up here. Any
# unconfirmed part forces exit 4.
cleanup() {
    local rc=$?
    [ "$CLEANED" = 1 ] && return
    CLEANED=1
    trap - EXIT INT TERM
    [ "$RESTORED" = 1 ] && exit "$rc"
    if ! restore; then log "cleanup could not be confirmed: check the emulator by hand"; exit 4; fi
    exit "$rc"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

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

# A failed unlock leaves the device in an unknown state: stop the whole run (cleanup clears the PIN).
must_unlock() {
    unlock && return 0
    echo "GATE $1 ERROR could not unlock with the PIN; stopping the matrix"
    exit 4
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

# Runs one gate check and prints its GATE lines. No GATE line (a crash, a timeout) is an ERROR, never silence.
check() {
    local name="$1"; shift
    local out
    case "$name" in capabilities|not-covered) ;; *) guard_idle "$name" ;; esac
    out="$(instrument -e check "$name" "$@")"
    if echo "$out" | grep -q '^GATE '; then echo "$out" | grep '^GATE '
    else echo "GATE $name ERROR gate produced no result: $(echo "$out" | head -3 | tr '\n' ' ')"; fi
}

wait_ready() {
    local control="$1"
    for _ in $(seq 1 90); do
        if a logcat -d -s PlainphoneBoot | grep -q "control=$control locked=false ready=true"; then return 0; fi
        sleep 1
    done
    return 1
}

restrictions() {
    sh_ dumpsys user | awk '/Effective restrictions:/{f=1; next} f { if ($0 ~ /^ +[a-z_]+$/) print $1; else exit }' | paste -sd, -
}

provision() {
    local build="$1" apk
    apk="$(apk_for "$build")"
    [ -f "$apk" ] || { echo "GATE provision ERROR missing APK $apk"; return 1; }
    if sh_ dpm list-owners | grep -q "org.plainphone/"; then sh_ dpm remove-active-admin "$ADMIN" >/dev/null; fi
    a uninstall org.plainphone >/dev/null 2>&1
    a install -t -r "$apk" >/dev/null 2>&1 || { echo "GATE provision ERROR install failed for $build"; return 1; }
    a logcat -c
    sh_ dpm set-device-owner "$ADMIN" >&2 || { echo "GATE provision ERROR set-device-owner failed for $build"; return 1; }
    sh_ input keyevent KEYCODE_HOME >/dev/null
    wait_ready "$(control_name "$build")" || { echo "GATE provision ERROR no ready re-apply for $build"; return 1; }
    sh_ input keyevent KEYCODE_HOME >/dev/null
    sleep 2
    echo "# variant=$build control=$(control_name "$build") apk=$(basename "$apk") apk_sha256=$(sha "$apk")"
    echo "# restrictions=$(restrictions)"
}

run_build() {
    local build="$1" detail
    log "== $build (control=$(control_name "$build")) =="
    echo "# $RUN_LABEL: $build on $SERIAL, API $API, $FINGERPRINT"
    echo "# gate_apk_sha256=$(sha "$(gate_apk)") commit=$COMMIT"
    provision "$build" || return 0
    a logcat -d -s PlainphoneBoot | grep "reapply" | tail -1 | sed 's/^/# /'
    check capabilities
    check not-covered
    for name in home-phone app-home-phone more-emergency alert; do check "$name"; done

    guard_idle telecom-emergency-call
    NUMBER_ADDED=1
    if detail="$(register_number)"; then
        check telecom-emergency-call -e number "$NUMBER"
    else
        echo "GATE telecom-emergency-call ERROR not dialed: $detail"
    fi
    hang_up
    case $? in
        0) ;;
        2) stop telecom-emergency-call "the test call came back after it read ended ($CALL_EVIDENCE)" ;;
        *) stop telecom-emergency-call "the test call is still live or unconfirmed ($CALL_EVIDENCE)" ;;
    esac
    echo "# after telecom-emergency-call: $CALL_EVIDENCE"
    clear_number || stop telecom-emergency-call "could not confirm the test number removed"

    if [ "$QUICK" = 0 ]; then
        if set_pin; then
            check lock-screen
            must_unlock lock-screen
            if [ "$build" = debug ]; then
                start_kill_loop
                check cannot-start
                stop_kill_loop
                must_unlock cannot-start
                sh_ input keyevent KEYCODE_HOME >/dev/null
                for _ in $(seq 1 30); do [ -n "$(sh_ pidof org.plainphone)" ] && break; sleep 1; done
                echo "# after cannot-start: plainphone pid=$(sh_ pidof org.plainphone)"
                sleep 3
            fi
            a reboot; wait_boot
            check locked-boot
            must_unlock locked-boot
            clear_pin || { echo "GATE locked-boot ERROR could not clear the PIN; stopping the matrix"; exit 4; }
        else
            echo "GATE lock-screen ERROR could not set the PIN"
        fi
        a logcat -c
        a reboot; wait_boot
        wait_ready "$(control_name "$build")" || log "no ready re-apply after the second boot"
        hold true
        check boot-checking
        hold false
    fi
    sh_ input keyevent KEYCODE_BACK >/dev/null   # any blocked screen left by a failing check
    sh_ dpm remove-active-admin "$ADMIN" >&2
    sleep 1
    echo "# removal: $(sh_ dpm list-owners | tr '\n' ' ') lockTask=$(sh_ dumpsys activity activities | grep -o 'mLockTaskModeState=[A-Z]*' | head -1)"
}

if [ "$BUILD" = 1 ]; then
    tasks=":emergencygate:assembleDebug"
    for b in $BUILDS; do tasks="$tasks :plainphone:assemble$(echo "${b:0:1}" | tr a-z A-Z)${b:1}"; done
    (cd "$APP" && ./gradlew -q $tasks) || { echo "build failed" >&2; exit 1; }
fi
[ -f "$(gate_apk)" ] || { echo "missing gate APK $(gate_apk)" >&2; exit 1; }
a install -t -r "$(gate_apk)" >/dev/null 2>&1 || { echo "gate APK install failed" >&2; exit 1; }

if [ "$MODE" = qualification ]; then RUN_LABEL="debug qualification run"; else RUN_LABEL="diagnostic run (never qualifies)"; fi
API="$(sh_ getprop ro.build.version.sdk)"
FINGERPRINT="$(sh_ getprop ro.build.fingerprint)"
COMMIT="$(git -C "$ROOT" rev-parse --short HEAD 2>/dev/null || echo unknown)"
[ -z "$(git -C "$ROOT" status --porcelain -- app tools 2>/dev/null)" ] || COMMIT="$COMMIT+dirty"
[ -n "$OUT" ] || OUT="$(mktemp -d)"
mkdir -p "$OUT"
echo "Emergency gate, $RUN_LABEL, on $SERIAL: $FINGERPRINT, API $API, commit $COMMIT"
echo "Debug builds keep adb; the release artifact is not tested here (DISALLOW_DEBUGGING_FEATURES ends adb)."
# Not in a pipeline: run_build must run in this shell so its exit 4 stops the run and the cleanup sees its state.
for b in $BUILDS; do run_build "$b" > "$OUT/$b.txt"; cat "$OUT/$b.txt" >&2; done
# Undo and confirm the set-up before any result, so a call back during the cleanup is never certified.
if restore; then RESTORED=1; else
    echo "RESULT STOPPED: cleanup could not be confirmed (a call, the test number, the PIN or the Device Owner); see $OUT/cleanup.txt"
    exit 4
fi

echo
printf '%-24s' "check"; for b in $BUILDS; do printf '%-22s' "$b"; done; echo
checks="$(cat "$OUT"/*.txt | awk '/^GATE /{print $2}' | awk '!seen[$0]++')"
for c in $checks; do
    printf '%-24s' "$c"
    for b in $BUILDS; do printf '%-22s' "$(awk -v c="$c" '$1=="GATE" && $2==c {print $3}' "$OUT/$b.txt" | head -1)"; done
    echo
done
printf '%-24s' "BUILD"
real="" controls_ok=1
for b in $BUILDS; do
    v="$(python3 -I "$ROOT/tools/emergency_gate_judge.py" "$(control_name "$b")" "$OUT/$b.txt" | head -1)"
    printf '%-22s' "$v"
    if [ "$b" = debug ]; then real="$v"; elif [ "$v" != DETECTED ]; then controls_ok=0; fi
done
echo
for b in $BUILDS; do
    python3 -I "$ROOT/tools/emergency_gate_judge.py" "$(control_name "$b")" "$OUT/$b.txt" | tail -n +2 | sed "s/^/  $b: /"
done
echo "Real build: PASS, or INCONCLUSIVE only for alert-shown. Each control: DETECTED (its named checks FAIL, every other check as the real build)."
echo "Raw lines: $OUT"

if [ "$MODE" = diagnostic ]; then echo "RESULT DIAGNOSTIC: --quick/--builds runs never qualify"; exit 3; fi
if [ "$controls_ok" = 1 ] && [ "$real" = PASS ]; then echo "RESULT QUALIFIED ($RUN_LABEL)"; exit 0; fi
if [ "$controls_ok" = 1 ] && [ "$real" = INCONCLUSIVE ]; then
    echo "RESULT QUALIFIED EXCEPT alert-shown INCONCLUSIVE ($RUN_LABEL; the board decides the alert-shown exception)"; exit 5
fi
echo "RESULT NOT QUALIFIED (real build $real; controls $([ "$controls_ok" = 1 ] && echo all DETECTED || echo not all DETECTED))"
exit 1
