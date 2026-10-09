#!/bin/bash
# THROWAWAY SPIKE (PLA-38). Helpers. Run under bash. Always pass the serial: never touch emulator-5590, emulator-5604, emulator-5640 or any physical device.
# Usage: source tools/pp.sh emulator-5650
S="${1:?serial}"
case "$S" in emulator-*) ;; *) echo "REFUSED: emulator serial required" >&2; return 1 2>/dev/null || exit 1;; esac
case "$S" in emulator-5590|emulator-5604|emulator-5640) echo "REFUSED: $S is not ours"; return 1 2>/dev/null || exit 1;; esac
A() { adb -s "$S" "$@" < /dev/null; }
cmd() { A shell am broadcast -a com.plainphone.spike.grey.CMD -n com.plainphone.spike.grey/.CommandReceiver --es cmd "'$*'" | perl -0ne 'if (/data="(.*)"\s*$/s) { print "$1\n" } elsif (/result=0\s*$/) { print "(no data)\n" }'; }
shot() { A exec-out screencap -p > "$1"; }
keys() { echo "enabled=$(A shell settings get secure accessibility_display_daltonizer_enabled) mode=$(A shell settings get secure accessibility_display_daltonizer)"; }
grant() { A shell dumpsys package com.plainphone.spike.grey | grep -E "WRITE_SECURE_SETTINGS|versionCode" | sed 's/^ *//'; }
# SurfaceFlinger colour matrix: identity when off, luminance weights when grey
sfmatrix() { A shell dumpsys SurfaceFlinger | grep -iA5 -m1 "color matrix\|colorMatrix\|Color transform" ; }
