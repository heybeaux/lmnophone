#!/bin/bash
# THROWAWAY SPIKE (PLA-11). Put a freshly provisioned emulator into the spike's lock-task configuration.
cd "$(dirname "$0")/.."
source tools/pp.sh "$1" || exit 1
cmd home-set
cmd lt-packages com.google.android.dialer,com.google.android.apps.messaging,com.android.camera2,com.android.phone
cmd lt-features "SYSTEM_INFO|NOTIFICATIONS|HOME|GLOBAL_ACTIONS|KEYGUARD|BLOCK"
cmd floor-apply
for p in com.android.camera2 com.google.android.apps.messaging com.google.android.dialer; do for perm in CAMERA RECORD_AUDIO ACCESS_FINE_LOCATION READ_MEDIA_IMAGES POST_NOTIFICATIONS; do cmd grant $p android.permission.$perm granted >/dev/null; done; done
cmd grant com.plainphone.spike.emu android.permission.ACCESS_FINE_LOCATION granted
A shell input keyevent KEYCODE_HOME; sleep 2
cmd lt-start; sleep 2
cmd status
