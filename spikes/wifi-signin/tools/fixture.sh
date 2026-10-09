#!/bin/bash
# THROWAWAY SPIKE (PLA-18). Point the emulator's captive-portal probes at the host fake portal.
# Usage: bash tools/fixture.sh <serial> captive|open|reset   (portal.py must be running on the host, port 8099)
S="${1:?serial}"; M="${2:?mode}"
case "$S" in emulator-5590|emulator-5604|SPARE_DEVICE_SERIAL) echo "REFUSED: $S is not ours"; exit 1;; esac
MODEFILE="${PORTAL_MODEFILE:?set PORTAL_MODEFILE}"
A() { adb -s "$S" "$@" < /dev/null; }
if [ "$M" = reset ]; then
  for k in captive_portal_http_url captive_portal_https_url captive_portal_fallback_url captive_portal_other_fallback_urls; do A shell settings delete global $k; done
  A shell svc data enable; exit 0
fi
echo "$M" > "$MODEFILE"
A shell settings put global captive_portal_http_url http://10.0.2.2:8099/generate_204
A shell settings put global captive_portal_https_url http://10.0.2.2:8099/generate_204
A shell settings put global captive_portal_fallback_url http://10.0.2.2:8099/generate_204
A shell settings put global captive_portal_other_fallback_urls http://10.0.2.2:8099/generate_204
A shell svc data disable
# Reconnect Wi-Fi so NetworkMonitor re-probes with the new mode.
A shell svc wifi disable; sleep 2; A shell svc wifi enable
