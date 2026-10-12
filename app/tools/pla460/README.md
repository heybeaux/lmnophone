# Tap-time Wi-Fi Forget candidate

Base: delivered preview 36, `9c18b15a`. Authorization: PLA-448 board card
`60fbdc40`; author task PLA-460. Branch `pla448/forget`.

Forget is available only for connected, validated Wi-Fi. It opens the shared
owned Location window at the tap, waits up to two seconds for Wi-Fi identity,
rechecks and removes that one connected saved configuration, then verifies
that the configuration is gone, the connection is no longer that ID, and every
other distinct saved ID remains. Readback is bounded to ten seconds. All exit
paths reuse durable Location cleanup, including call deferral, unknown-call
warning and retry. Already-on Location stays on. Missing identity removes
nothing and shows the existing plain Wi-Fi status failure. The calls-first
problem message is reachable; when unvalidated, its only action is OK.

No emergency, Bluetooth, join, update-postpone or lock-task policy changes.
The spare must never have its real Wi-Fi forgotten. This is an author candidate,
not an approved spare installation; the Lead reviews and chooses the next build
number, and the sole spare driver installs in place.

## Host checks

From `app/` with JDK 17 and Android SDK 36:

```sh
./gradlew :device:testDebugUnitTest --tests '*Wifi*' --tests '*Bluetooth*' --tests '*update*' \
  :plainphone:testDebugUnitTest --tests '*shell*' --tests '*EmergencyDialerTest' --tests '*update*' \
  :ui:verifyPaparazziDebug --tests '*Wifi*ScreenshotTest' --tests '*Bluetooth*ScreenshotTest' \
  :plainphone:assembleDebug
```

`openspec validate --all` from the repository root. Counts and artifact hash:
[evidence/host-checks.txt](evidence/host-checks.txt).

Red commits: `ce300372` (absent implementation), `ae11e0c4` (unavailable
message action), `06b1154c` (Location-to-Wi-Fi identity propagation).
The stale Back-only screenshot action assertion was updated to include the
restored clickable problem message. No checks were disabled. All forty new or
changed goldens were inspected; longer content scrolls above the pinned action.

## Private emulator gate

`WifiForgetProbe` is restricted at runtime to API 35 emulator hardware. It
creates one synthetic unconnected saved configuration, removes the connected
emulator configuration through production adapters/controller, verifies the
exact saved ID set, and then checks the no-connected-ID path. Output contains
booleans and counts only. It never invokes any call API.

Build the test runner with:

```sh
./gradlew :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest \
  -PpreviewTestRunner=org.plainphone.settings.WifiForgetProbe
```

Use a fresh private API 35 emulator with no accounts, a connected validated
synthetic Wi-Fi network, this testOnly app as Device Owner, and Location off.
Install only via `adb -s "$OWNED_EMULATOR" install -r -t ...`; verify the target
is the dedicated emulator first. Invoke `am instrument -w
org.plainphone.test/org.plainphone.settings.WifiForgetProbe` on that same named
emulator, and start `org.plainphone/.MainActivity` one second after instrumentation
starts. Foreground access matters. Never use `connectedAndroidTest` or a spare.

The probe's setup and independent saved-set checks use separate temporary
Location windows because Location-off configuration data was unreliable in the
initial run. It verifies Location is off immediately before the Forget operation
and after its cleanup. Production Forget itself gets no pre-read identity.

Initial attempts exposed a background-read limitation and the need to allow
Wi-Fi identity to catch up after Location reads on. The independent probe's
original Location-off saved-set assertion was invalid and corrected. One run
also lost its private emulator transport and was discarded. Only the final
explicit PASS is acceptance evidence:
[evidence/emulator-forget.txt](evidence/emulator-forget.txt).

The same built APK was then installed through the existing preview setup on this
private emulator. An actual UI tap followed Home → More → Phone settings → Wi-Fi
→ Forget this network, from Location 0 to Not connected and Location 0 again:
[evidence/ui-tap.txt](evidence/ui-tap.txt). Installed APK bytes match the host hash:
[evidence/install-readback.txt](evidence/install-readback.txt).
