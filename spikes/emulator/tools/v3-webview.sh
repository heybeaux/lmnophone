#!/bin/bash
# THROWAWAY SPIKE (PLA-23, V3). Navigation inside an approved fixture's WebView. The page is one full-screen link to
# https://www.iana.org/ so a centre tap follows it. With a WebViewClient the WebView navigates itself (the disclosed
# leak); with client=0 the tap leaves the app as ACTION_VIEW. Control: fixture not approved -> launch denied.
cd "$(dirname "$0")/.."; source tools/pp.sh "$1" || exit 1; source tools/detect.sh; D=evidence/pla23/v345-$2
BASE="com.google.android.dialer,com.google.android.apps.messaging,com.android.camera2,com.android.phone"
PAGE='data:text/html,<a_href="https://www.iana.org/"_style="display:block;height:3000px;font-size:80px">FULL_PAGE_LINK_TO_IANA</a>'
echo "# $(date -u +%FT%TZ) policy: $(pol)"
echo "$(cmd approved-set $BASE,com.plainphone.fx.urlbar) / $(cmd floor-apply)"
for client in 1 0; do
  WAIT=4 run_case "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/com.plainphone.fx.common.UrlBarActivity text=$PAGE es.client=$client newtask=1" 1 | sed "s/^/load client=$client\t/"
  A logcat -b all -c
  A shell input tap 540 1300; sleep 7
  echo "tap client=$client -> resumed: $(A logcat -b all -d | grep -oE 'wm_set_resumed_activity: \[[0-9]+,[^,]+' | sed -E 's/.*,//' | sort -u | tr '\n' ' ') top=$(toppkg) $(pol | grep -oE 'lockTaskModeState=[0-9]')"
  A logcat -d -b main | grep -E "PPFX webview|PPEMU.*link" | sed -E 's/^.{19}//' | cut -c1-200
  shot $D/v3-webview-client$client-after-tap.png
  A shell input keyevent KEYCODE_HOME; sleep 1
done
echo "control: $(cmd approved-set $BASE) / $(cmd floor-apply)"
run_case "action=android.intent.action.MAIN comp=com.plainphone.fx.urlbar/com.plainphone.fx.common.UrlBarActivity text=https://example.com/ newtask=1" 1 | sed "s/^/unapproved\t/"
echo "# end policy: $(pol)"
