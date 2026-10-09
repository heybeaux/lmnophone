#!/bin/bash
# THROWAWAY SPIKE (PLA-23, V6). Wi-Fi: WPA2 availability, sign-in network handling with calls/Emergency, the
# sign-in notification with a working control, and Forget with read-back. Never places a call.
# Needs the host fake portal: python3 -I tools/portal.py 8123 <modefile>  (started by this script)
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
source tools/detect.sh
LABEL="$2"; D=evidence/pla23/v6-$LABEL; mkdir -p $D
BASE="com.google.android.dialer,com.google.android.apps.messaging,com.android.camera2,com.android.phone"
MODEF=$D/portal-mode; echo open > $MODEF
python3 -I tools/portal.py 8123 $MODEF 2> $D/portal-requests.log & PORTAL=$!
trap 'kill $PORTAL' EXIT
rtap() { local id="$1" how="$2" node="$3" res; ui | grep -qE "(text|content-desc)=\"[^\"]*$node" || { printf '%s\tTEST_FAILED\tNO NODE %s\n' "$id" "$node"; return; }
  A logcat -b all -c; $how "$node" >/dev/null; sleep 3
  res=$(A logcat -b all -d | grep -oE "wm_set_resumed_activity: \[[0-9]+,[^,]+" | sed -E 's/.*,//' | sort -u | tr '\n' ' ')
  printf '%s\tresumed=%s\tstatus=%s\n' "$id" "$res" "$(pol | grep -oE 'lockTaskModeState=[0-9]')"; }
reconnect() { A shell svc wifi disable; sleep 2; A shell svc wifi enable; sleep ${1:-15}; }
echo "# $(date -u +%FT%TZ) start policy: $(pol)"
echo "## WPA2 availability"
A shell cmd wifi start-scan; sleep 4
A shell cmd wifi list-scan-results
echo "saved: $(cmd wifi-list)"
echo "## sign-in network (fake portal returns 302)"
for k in http https fallback other_fallback; do v=http://10.0.2.2:8123/generate_204; [ $k = other_fallback ] && A shell settings put global captive_portal_other_fallback_urls $v || A shell settings put global captive_portal_${k}_url $v; done
echo captive > $MODEF; reconnect 15
echo "net: $(cmd net)"
A shell input keyevent KEYCODE_HOME; sleep 1.5; shot $D/signin-home.png
echo "home texts: $(texts 400)"
echo "### incoming call while on the sign-in network"
adb -s "$S" emu gsm call 6505550100 >/dev/null; sleep 4
echo "telecom: $(A shell dumpsys telecom | grep -m2 -oE 'state=(RINGING|ACTIVE|DIALING)[^ ]*' | tr '\n' ' ')"
shot $D/signin-incoming-call.png
adb -s "$S" emu gsm cancel 6505550100 >/dev/null; sleep 2
echo "### Emergency button while on the sign-in network"
A shell input keyevent KEYCODE_HOME; sleep 1
rtap signin-emergency-button tapt "Emergency"; shot $D/signin-emergency.png
A shell input keyevent KEYCODE_BACK; sleep 1; A shell input keyevent KEYCODE_HOME; sleep 1
echo "### sign-in notification, final policy (CaptivePortalLogin hidden)"
A shell cmd statusbar expand-notifications; sleep 1.5; echo "shade: $(texts 300)"
rtap notif-hidden tapp "Sign in to"; shot $D/notif-hidden.png
A shell cmd statusbar collapse; A shell input keyevent KEYCODE_HOME; sleep 1
echo "### control: CaptivePortalLogin unhidden and on the lock-task list (PLA-18 Q3b setup)"
echo "$(cmd unhide com.google.android.captiveportallogin) / $(cmd approved-set $BASE,com.google.android.captiveportallogin) / $(cmd floor-apply)"
reconnect 15
A shell cmd statusbar expand-notifications; sleep 1.5
rtap notif-control tapp "Sign in to"; shot $D/notif-control.png
echo "top: $(toppkg)"
A shell input keyevent KEYCODE_HOME; sleep 1
echo "restore: $(cmd hide com.google.android.captiveportallogin) / $(cmd approved-set $BASE) / $(cmd floor-apply)"
sleep 15
echo "### Forget this network (home button), with read-back"
echo "before: $(cmd wifi-list) net: $(cmd net)"
A shell input keyevent KEYCODE_HOME; sleep 1.5
rtap forget-button tapt "Forget this network"; sleep 3
echo "after: $(cmd wifi-list) net: $(cmd net)"
A shell cmd wifi list-networks
echo "## restore ordinary Wi-Fi"
echo open > $MODEF
for k in captive_portal_http_url captive_portal_https_url captive_portal_fallback_url captive_portal_other_fallback_urls; do A shell settings delete global $k >/dev/null; done
echo "$(cmd wifi-add AndroidWifi)"; sleep 12
echo "net: $(cmd net) saved: $(cmd wifi-list)"
echo "# end policy: $(pol)"
