#!/bin/bash
# THROWAWAY SPIKE (PLA-11, rewritten PLA-23 for V5/V7). Ordered setup that reproduces the final spike policy on a
# freshly wiped emulator, then writes a provenance file (image, app versions, APK hashes, policy read-back).
# Usage: tools/setup.sh <serial> <label>     e.g. tools/setup.sh emulator-5620 a35
# NOTE: this is a test harness, not the restricted-user product. adb stays on (it is the test channel), so
# DISALLOW_DEBUGGING_FEATURES is the one intended restriction that is NOT applied here.
set -u
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
LABEL="${2:-run}"
OUT="evidence/pla23/provenance-$LABEL.txt"; mkdir -p evidence/pla23
APKS="apps/dpc/build/outputs/apk/debug/dpc-debug.apk apps/fx-browser/build/outputs/apk/debug/fx-browser-debug.apk apps/fx-applinks/build/outputs/apk/debug/fx-applinks-debug.apk apps/fx-httponly/build/outputs/apk/debug/fx-httponly-debug.apk apps/fx-urlbar/build/outputs/apk/debug/fx-urlbar-debug.apk apps/fx-emergency/build/outputs/apk/debug/fx-emergency-debug.apk"
APPROVED="com.google.android.dialer,com.google.android.apps.messaging,com.android.camera2,com.android.phone"
RESTRICTIONS="no_factory_reset no_safe_boot no_install_apps no_install_unknown_sources_globally no_add_user no_add_managed_profile no_user_switch no_modify_accounts no_config_vpn no_config_private_dns no_config_default_apps no_config_date_time no_network_reset no_usb_file_transfer no_config_cell_broadcasts no_system_error_dialogs no_create_windows"
{
echo "# provenance $LABEL  $(date -u +%FT%TZ)  git=$(git rev-parse --short HEAD)$(git diff --quiet || echo -dirty)"
echo "image: $(A shell getprop ro.build.fingerprint)"
echo "accounts: $(A shell dumpsys account | grep -c 'Account {')"
echo "## step 1 install (APK sha256)"
for a in $APKS; do echo "$(shasum -a 256 "$a" | cut -c1-64)  $a  -> $(A install -r -t "$a" 2>&1 | tail -1)"; done
echo "## step 2 device owner"
A shell dpm set-device-owner com.plainphone.spike.emu/.AdminReceiver 2>&1
echo "## step 3 home, approved list, features, floor-apply (replace)"
cmd home-set
cmd approved-set "$APPROVED"
cmd lt-features "SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|KEYGUARD|BLOCK"
cmd floor-apply
echo "## step 4 permission pre-grants (read back)"
for p in com.android.camera2 com.google.android.apps.messaging com.google.android.dialer; do
  for perm in CAMERA RECORD_AUDIO ACCESS_FINE_LOCATION ACCESS_COARSE_LOCATION READ_MEDIA_IMAGES READ_MEDIA_VIDEO READ_CONTACTS CALL_PHONE SEND_SMS POST_NOTIFICATIONS; do
    echo "$p $perm $(cmd grant $p android.permission.$perm granted | sed 's/.*; //')"; done; done
cmd grant com.plainphone.spike.emu android.permission.ACCESS_FINE_LOCATION granted
echo "## step 5 hiding: classifier (browsers, Google app), CaptivePortalLogin"
cmd classify
cmd hide com.google.android.captiveportallogin
echo "## step 6 restrictions (all intended except no_debugging_features)"
for r in $RESTRICTIONS; do cmd restrict-add $r; done
echo "## step 7 start lock task"
A shell input keyevent KEYCODE_HOME; sleep 2
cmd lt-start; sleep 3
echo "## read-back"
cmd status
cmd approved
cmd emergency-target
A shell dpm list-owners
for p in com.android.chrome com.plainphone.fx.browser com.google.android.googlequicksearchbox com.google.android.captiveportallogin; do cmd is-hidden $p; done
echo "## app versions"
for p in com.plainphone.spike.emu com.plainphone.fx.browser com.plainphone.fx.applinks com.plainphone.fx.httponly com.plainphone.fx.urlbar com.plainphone.fx.emergency com.google.android.dialer com.google.android.apps.messaging com.android.camera2 com.android.phone com.android.server.telecom com.google.android.apps.safetyhub com.android.chrome com.google.android.googlequicksearchbox com.google.android.captiveportallogin com.google.android.webview com.google.android.inputmethod.latin com.google.android.permissioncontroller com.google.android.documentsui com.google.android.providers.media.module com.android.settings; do
  echo "$p $(A shell dumpsys package $p | grep -m1 -oE 'versionName=[^ ]+') $(A shell pm path $p 2>/dev/null | head -1)"; done
} 2>&1 | tee "$OUT"
# PLA-22 boot-loop precaution: hides just happened; do not reboot or kill the emulator for 15 s.
sleep 15
