#!/bin/bash
# THROWAWAY SPIKE (PLA-18). Fresh sign-in state: reconnect Wi-Fi (re-posts the notification), start a session, tap the notification.
cd "$(dirname "$0")/.."; source tools/pp.sh "${1:?serial}" || exit 1
echo captive > "${PORTAL_MODEFILE:?}"
cmd signin-end reset >/dev/null
A shell svc wifi disable; sleep 2; A shell svc wifi enable; sleep 12
A shell input keyevent KEYCODE_HOME; sleep 4
echo "start: $(cmd signin-start ${2:-600})"
A shell cmd statusbar expand-notifications; sleep 1.5; tapp "Sign in to" || tapp "Sign in"; sleep 4
echo "top: $(toppkg)"
