#!/bin/bash
# THROWAWAY SPIKE (PLA-22). Helpers. Always pass the serial: never touch emulator-5590, emulator-5604 or SPARE_DEVICE_SERIAL.
# Usage: source tools/bl.sh emulator-5620
S="${1:?serial}"
case "$S" in emulator-5590|emulator-5604|SPARE_DEVICE_SERIAL) echo "REFUSED: $S is not ours"; return 1 2>/dev/null || exit 1;; esac
A() { adb -s "$S" "$@" < /dev/null; }
cmd() { A shell am broadcast -a com.plainphone.spike.bootloop.CMD -n com.plainphone.spike.bootloop/.CommandReceiver --es cmd "'$*'" | perl -0ne 'if (/data="(.*)"\s*$/s) { print "$1\n" } else { print }'; }
# wait for sys.boot_completed=1 up to $1 seconds; prints seconds taken or TIMEOUT
waitboot() { local t=0 lim=${1:-180}; while [ $t -lt $lim ]; do [ "$(A shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" = 1 ] && { echo "booted ${t}s"; return 0; }; sleep 3; t=$((t+3)); done; echo TIMEOUT; return 1; }
# count system_server crashes in the current logcat buffer
crashes() { A logcat -d -b crash 2>/dev/null | grep -c 'FATAL EXCEPTION IN SYSTEM PROCESS'; }
bootid() { A shell cat /proc/sys/kernel/random/boot_id 2>/dev/null | tr -d '\r'; }
# wait until the boot id changes (device actually rebooted), up to 120 s
waitnewboot() { local old="$1" t=0 b; while [ $t -lt 120 ]; do b=$(bootid); [ -n "$b" ] && [ "$b" != "$old" ] && return 0; sleep 2; t=$((t+2)); done; echo "NO NEW BOOT"; return 1; }
# trial <hide|unhide> <pkg> <delay-s> <adb|svc|kill> <label>
# Assumes the opposite state is already durable. Logs to evidence/<label>.txt. Prints one table row.
trial() {
  local dir=$1 pkg=$2 d=$3 kind=$4 label=$5 out=evidence/$5.txt old r
  old=$(bootid)
  { echo "## $label: $dir $pkg, reboot=$kind after ${d}s ($(date +%T))"; echo "boot_id before: $old"; cmd state $pkg; cmd $dir $pkg; } > $out
  sleep $d
  case $kind in
    adb) A reboot ;;
    svc) A shell svc power reboot ;;
    kill) A emu kill; sleep 3; nohup ~/Library/Android/sdk/emulator/emulator -avd pp11-a35 -port 5620 -no-snapshot-load -no-boot-anim -no-window >> "$PAPERCLIP_RUN_SCRATCH_DIR/emu35.log" 2>&1 & sleep 5; A wait-for-device ;;
  esac
  waitnewboot "$old" >> $out
  if waitboot ${BOOTLIM:-150} >> $out; then r=BOOTED; { A shell uptime; A shell getprop sys.boot.reason; cmd state $pkg; } >> $out; else r=LOOP; fi
  echo "system_server crashes in crash buffer: $(crashes)" >> $out
  A logcat -d -b crash 2>/dev/null | grep -m3 -E 'Caused by|onBootPhase|IllegalState' >> $out
  echo "RESULT: $r" >> $out
  echo "| $label | $dir | $pkg | ${d}s | $kind | $r | $(crashes) |"
}
# reset: load clean snapshot, then make <pkg> durably <state> (hidden|visible) by waiting past the 10 s write delay
reset() { A emu avd snapshot load do-clean2 >/dev/null; sleep 3; waitboot 30 >/dev/null; A logcat -c -b all; case $2 in hidden) cmd hide $1;; suspended) cmd suspend $1;; *) true;; esac >/dev/null; sleep ${SETTLE:-15}; }
