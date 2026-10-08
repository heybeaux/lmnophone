# THROWAWAY SPIKE (PLA-11). Builds the QR provisioning payload for the test-only spike DPC.
import json, sys, qrcode
p = {
 "android.app.extra.PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME": "com.plainphone.spike.emu/.AdminReceiver",
 "android.app.extra.PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION": "http://10.0.2.2:8098/dpc-debug.apk",
 "android.app.extra.PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM": "me3Vo352uzQXoEdEMoox0XREF3hpczTmxODCODrIEb0",
 "android.app.extra.PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED": True,
 "android.app.extra.PROVISIONING_SKIP_ENCRYPTION": True,
}
s = json.dumps(p); print(s)
qrcode.make(s, box_size=8, border=6).save(sys.argv[1])
