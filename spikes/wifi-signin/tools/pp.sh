#!/bin/bash
# THROWAWAY SPIKE (PLA-11, reused by PLA-18). Helpers. Always pass the serial: never touch emulator-5590, emulator-5604 or any physical device.
# Usage: source tools/pp.sh emulator-5620
S="${1:?serial}"
case "$S" in emulator-*) ;; *) echo "REFUSED: emulator serial required" >&2; return 1 2>/dev/null || exit 1;; esac
case "$S" in emulator-5590|emulator-5604) echo "REFUSED: $S is not ours"; return 1 2>/dev/null || exit 1;; esac
A() { adb -s "$S" "$@" < /dev/null; }
cmd() { A shell am broadcast -a com.plainphone.spike.emu.CMD -n com.plainphone.spike.emu/.CommandReceiver --es cmd "'$*'" | perl -0ne 'if (/data="(.*)"\s*$/s) { print "$1\n" } elsif (/result=0\s*$/) { print "(no data)\n" }'; }
shot() { A exec-out screencap -p > "$1"; }
top() { A shell dumpsys activity activities | grep -E "topResumedActivity|ResumedActivity:" | head -2; }
ui() { A shell uiautomator dump /sdcard/ui.xml >/dev/null 2>&1; A shell cat /sdcard/ui.xml; }
# tap the first node whose text matches exactly
tapt() { local b; b=$(ui | tr '>' '\n' | grep -m1 "text=\"$1\"" | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | head -4 | tr '\n' ' '); [ -z "$b" ] && { echo "NO NODE $1"; return 1; }; set -- $b; A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
toppkg() { A shell dumpsys activity activities | grep -m1 topResumedActivity | sed -E 's/.* u0 ([^ ]+) t[0-9]+.*/\1/'; }
tapd() { local b; b=$(ui | tr '>' '\n' | grep -m1 "content-desc=\"$1\"" | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | head -4 | tr '\n' ' '); [ -z "$b" ] && { echo "NO NODE $1"; return 1; }; set -- $b; A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
texts() { ui | grep -o -E '(content-desc|text)="[^"]+"' | sed -E 's/^(content-desc|text)=//' | tr '\n' ' ' | cut -c1-${1:-700}; }
# open the Dialer from Plainphone home, close the keypad
dial() { for i in 1 2 3; do A shell input keyevent KEYCODE_HOME; sleep 1; tapt "Phone" >/dev/null; sleep 2; case $(toppkg) in com.google.android.dialer/*) break;; *) A shell input keyevent KEYCODE_BACK; sleep 1;; esac; done; ui | grep -q 'key pad' || { A shell input keyevent KEYCODE_BACK; sleep 1; }; }
dset() { dial; tapd "More options" >/dev/null; sleep 1; tapt "Settings" >/dev/null; sleep 2; }
# tap the lower-right area of the first node whose text contains $1 (for inline links at the end of a paragraph)
tapend() { local b; b=$(ui | tr '>' '\n' | grep -m1 "text=\"[^\"]*$1" | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | head -4 | tr '\n' ' '); [ -z "$b" ] && { echo "NO NODE $1"; return 1; }; set -- $b; A shell input tap $(( $1 + ($3-$1)*${2:-30}/100 )) $(( $4 - 25 )); }
msgs() { for i in 1 2 3; do A shell input keyevent KEYCODE_HOME; sleep 1; tapt "Messages" >/dev/null; sleep 2.5; case $(toppkg) in com.google.android.apps.messaging/*) return;; *) A shell input keyevent KEYCODE_BACK; sleep 1;; esac; done; }
mmenu() { msgs; tapd "Sign in" >/dev/null; sleep 1.8; }
mmenu() { msgs; ui | grep -q 'Use Messages without an account' && { tapt "Use Messages without an account" >/dev/null; sleep 2; }; tapd "Sign in" >/dev/null; sleep 1.8; }
mmenu() { A shell am force-stop com.google.android.apps.messaging; msgs; ui | grep -q 'Use Messages without an account' && { tapt "Use Messages without an account" >/dev/null; sleep 2; }; tapd "Sign in" >/dev/null; sleep 1.8; }

# tap the first node whose content-desc or text contains $1
tapp() { local b; b=$(ui | tr '>' '\n' | grep -m1 -E "(content-desc|text)=\"[^\"]*$1" | grep -o 'bounds="[^"]*"' | grep -oE '[0-9]+' | head -4 | tr '\n' ' '); [ -z "$b" ] && { echo "NO NODE ~$1"; return 1; }; set -- $b; A shell input tap $(( ($1+$3)/2 )) $(( ($2+$4)/2 )); }
