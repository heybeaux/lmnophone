# THROWAWAY: temporary Location Wi-Fi probe

Assigned API 35 emulator only (`pla363-policy-a35`, `emulator-5880`). The Java
receiver is a fixture, not part of the candidate. To repeat, temporarily compile
it from `:device` debug sources and register it in the test app's debug manifest
as an exported receiver requiring `android.permission.DUMP`. Build/install in
place with the existing debug signer. Start/unlock Home, then broadcast to
`org.plainphone/org.plainphone.spike437.WifiProbe`. Read only the `WifiJoinProbe`
log tag. Remove the receiver and registration before building the candidate.

The probe requires DO and LOCKED. It enables Location, counts named scan results,
adds and removes one synthetic unsaved open configuration, then selects the
existing emulator network without rewriting it. It disables Location, verifies
the live Wi-Fi transport remains, switches Wi-Fi off/on, verifies reconnection
while Location is 0, and confirms identity after re-enabling Location. Finally
it restores the prior Location mode. Evidence contains booleans/counts only.

The emulator exposes one AP, already saved. Configuration addition and connection
are therefore separate checks. No new-AP association or real WPA password was
exercised. Copying an existing configuration into addNetwork was rejected because
it attempted to rewrite a protected MAC-randomization setting; product code
selects an existing configuration unchanged instead. With Location 0, connection
identity is redacted: identity-based polling produced false negatives even while
the Wi-Fi transport remained connected. The final probe checks transport first.

Result: `docs/previews/preview-33/probe.txt`. Auto-off and reconnection passed under
LOCKED. The fixture receiver is absent from the build-33 APK.

For the UI gate, `DisconnectReceiver.java` is a separate temporary shell-only
receiver compiled/registered the same way. `--ez disconnect true` holds the lone
emulator AP disconnected for at most 60 seconds without rewriting configuration;
`--ez disconnect false` stops. This makes an unconnected row available while the
real Activity remains running. `app/tools/preview-33/wifi_join_check.py` exercises
Back, Home, crash-marker recovery, Cancel, and Connect. The receiver is removed
before the final candidate and final accepted-32-to-33 gate. No fixture ships.
