#!/bin/bash
# THROWAWAY SPIKE (PLA-23). V3 link matrix additions, V4 floor replace + Emergency button, V5 harness boundary.
# PLA-23: re-run of the UI-dependent v345.sh sections after the pp.sh ui() fix.
# Usage: tools/v345-ui.sh <serial> <label>. Uses the result detector (tools/detect.sh). Never places a call.
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
source tools/detect.sh
LABEL="$2"; D=evidence/pla23/v345-$LABEL; mkdir -p $D
BASE="com.google.android.dialer,com.google.android.apps.messaging,com.android.camera2,com.android.phone"
FLOORPK="com.android.server.telecom,com.google.android.apps.safetyhub"
rc() { local id="$1"; shift; local r; r=$(run_case "$@"); printf '%s\t%s\n' "$id" "$r"; }
ppfx() { A logcat -d -b main | grep -E "PPFX|PPEMU.*(REJECTED|launch)" | sed -E 's/^.{19}//' | tail -${1:-6}; }
# tap a home/screen node with the detector discipline: assert node, clear log, tap, collect resumed activities
rtap() { local id="$1" how="$2" node="$3" res; ui | grep -qE "(text|content-desc)=\"$node\"" || { printf '%s\tTEST_FAILED\tNO NODE %s\n' "$id" "$node"; return; }
  A logcat -b all -c; $how "$node" >/dev/null; sleep 2.5
  res=$(A logcat -b all -d | grep -oE "wm_set_resumed_activity: \[[0-9]+,[^,]+" | sed -E 's/.*,//' | sort -u | tr '\n' ' ')
  printf '%s\tresumed=%s\tstatus=%s\n' "$id" "$res" "$(pol | grep -oE 'lockTaskModeState=[0-9]')"; }

echo "# $(date -u +%FT%TZ) start policy: $(pol)"
A shell input keyevent KEYCODE_BACK; A shell input keyevent KEYCODE_HOME; sleep 1
echo; echo "## V5 harness boundary"
A shell input keyevent KEYCODE_HOME; sleep 1
echo "home texts: $(texts 400)"
echo "home has a 'Stop lock task' node: $(ui | grep -c 'Stop lock task')"
A logcat -b all -c
A shell am start -n com.plainphone.spike.emu/.MainActivity --es op lt-stop >/dev/null 2>&1; sleep 2
echo "exported MainActivity op=lt-stop without token: $(ppfx 1) ; $(pol | grep -oE 'lockTaskModeState=[0-9]')"
A shell cmd statusbar expand-settings; sleep 1.5
rtap v5-shade-settings-gear tapd "Open settings"
A shell cmd statusbar collapse; A shell input keyevent KEYCODE_HOME; sleep 1

echo; echo "## V4 Emergency button (fx-emergency installed, declares the same filters at priority 1000)"
echo "$(cmd emergency-target)"
A shell input keyevent KEYCODE_HOME; sleep 1
rtap v4-home-emergency-button tapt "Emergency"
shot $D/v4-emergency-button.png
echo "emergency screen texts: $(texts 300)"
A shell input keyevent KEYCODE_BACK; sleep 1; A shell input keyevent KEYCODE_HOME; sleep 1
rc v4-control-implicit-emergency "action=com.android.phone.EmergencyDialer.DIAL newtask=1" 1
A shell input keyevent KEYCODE_BACK; sleep 1
rc v4-control-pinned-emergency "action=com.android.phone.EmergencyDialer.DIAL pkg=com.android.phone newtask=1" 1
A shell input keyevent KEYCODE_BACK; sleep 1

echo; echo "## V3 real Custom Tabs client (fx.urlbar approved)"
echo "$(cmd approved-set $BASE,com.plainphone.fx.urlbar) / $(cmd floor-apply)"
echo "$(cmd customtabs)"
rc v3-ctclient-fallback-view "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/.CtClientActivity text=https://example.com/ct-client newtask=1" 1; ppfx 3
rc v3-ctclient-fallback-webview "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/.CtClientActivity text=https://example.com/ct-client es.fallback=webview newtask=1" 1; ppfx 6
shot $D/v3-ctclient-webview.png
A shell input keyevent KEYCODE_BACK

echo; echo "## V3 WebView inside an approved fixture (expected, disclosed leak)"
WAIT=6 rc v3-webview-load "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/com.plainphone.fx.common.UrlBarActivity text=https://example.com/ newtask=1" 1; ppfx 3
shot $D/v3-webview-example.png
A logcat -b all -c
(tapp "Learn more" || tapp "More information") ; sleep 6
echo "after tapping the page link: $(ppfx 4 | tr '\n' ' ') status=$(pol | grep -oE 'lockTaskModeState=[0-9]') top=$(toppkg)"
shot $D/v3-webview-navigated.png
A shell input keyevent KEYCODE_HOME; sleep 1
WAIT=6 rc v3-webview-noclient-load "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/com.plainphone.fx.common.UrlBarActivity text=https://example.com/ es.client=0 newtask=1" 1
A logcat -b all -c
(tapp "Learn more" || tapp "More information"); sleep 3
echo "no-WebViewClient link tap -> resumed: $(A logcat -b all -d | grep -oE 'wm_set_resumed_activity: \[[0-9]+,[^,]+' | sed -E 's/.*,//' | sort -u | tr '\n' ' ') top=$(toppkg)"
shot $D/v3-webview-noclient-tap.png
A shell input keyevent KEYCODE_HOME; sleep 1
echo "control: fx.urlbar not approved: $(cmd approved-set $BASE) / $(cmd floor-apply)"
rc v3-webview-unapproved "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/com.plainphone.fx.common.UrlBarActivity text=https://example.com/ newtask=1" 1

echo; echo "# end policy: $(pol)"
