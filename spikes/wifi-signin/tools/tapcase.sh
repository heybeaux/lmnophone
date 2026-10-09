#!/bin/bash
# THROWAWAY SPIKE (PLA-18). Q4: tap a portal element by coordinates (WebView nodes are not always in the a11y dump).
cd "$(dirname "$0")/.."; source tools/pp.sh "${1:?serial}" || exit 1
X=$2; Y=$3; T=$4
A logcat -c
echo "## $T: tap ($X,$Y)"; A shell input tap $X $Y; sleep 5
echo "top: $(toppkg)"; echo "screen: $(texts 260)"; shot "evidence/q4-$T.png"
A logcat -d | grep -E "START u0|ActivityNotFound|No Activity|Abort|LockTask|CaptivePortalLoginActivity|PPEMU.*signin" | grep -v "cmd \[" | cut -c1-240 | tail -4
A shell dumpsys activity activities | grep -m1 mLockTaskModeState
