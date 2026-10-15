# Timed Play opening — emulator proof and blocking reinstall finding

Run: 2026-10-15 UTC. PLA-620, build-63 base. **NOT accepted for delivery.**
Built and installed on the isolated read-only `pla104-a36` only. Nothing installed
on the spare; no physical-device commands. Existing reviewer/driver remain assigned.

## Artifact and test boundary

App version remains 63 / `0.1.0-preview-63`, TEST_ONLY.
Debug APK SHA-256:
`5c19a8cc17beba9449072a0ee6fd2214b591843a2073e2e7e0d2cdd9345e0642`.
Final androidTest APK SHA-256:
`010b2ce48b3ed742ae12a609e76c47f9cab4f6c0208053c9654c17af211c3c47`.

`PlaySessionProofInstrumentation` lives only in androidTest. It uses the actual
runtime, shared controller/journal, PIN vault, effects and UI. It supplies a
synthetic PIN and advances only the running clock in the test process: ten minutes
to complete a wait, and a rolling day between independent proof runs. No shortcut
exists in src/debug. The package fixture has no app code or network permission.
No Google account data is supplied or read. The incoming-call test uses an emulator
synthetic call; no outgoing or emergency call is placed.

## Policy and opening

Strict starter adapter: VALID; standard absent full-engine store: BOTH_INVALID.
No policy.rec/policy.lkg is created. Complete floor read and system dialer passed.
The only added lock-task package during access is `com.android.vending`.

Baseline and restored membership (same set):

```text
com.android.camera2
com.android.phone
com.android.server.telecom
com.google.android.apps.maps
com.google.android.apps.messaging
com.google.android.apps.safetyhub
com.google.android.cellbroadcastreceiver
com.google.android.dialer
org.plainphone
```

Play is hidden and unsuspended before and after; unhidden and unsuspended during.
Successful runs read back LOCKED before, during and after (screen-off has no
foreground activity). No Google Play services/account package is added.

## Passing observations

| Path | Observation |
|---|---|
| Actual UI | Start the wait → fresh PIN → Home countdown → Ready notice → Start → Play; Back returns to LMNOPhone and closes access. |
| Done | Runtime Done closes access and consumes the grant; exact baseline and empty shared journal. |
| Home / leaving | Bringing LMNOPhone forward closes access; exact baseline and empty shared journal. |
| Deadline | Test-clock deadline closes access; exact baseline and empty shared journal. |
| Screen off | KEYCODE_POWER closes access; exact baseline and empty shared journal. |
| Incoming call | Synthetic emulator ringing closes access; exact baseline and empty shared journal. |
| First install | Fresh `org.plainphone.playcandidate` install succeeds, candidate includes signer, becomes hidden, and appears in Add a hidden app after Done. |
| Process restart | Active grant disappears and Play is hidden on the start pass. |
| Actual reboot | Test reports OPEN, host confirms Play membership, then reboots before test ends. After unlock/start: no grant, Play hidden, LOCKED baseline. |
| Re-entry | After each successful close Play has no launch intent; market intent and Recents do not bring Play forward. |

Play's foreground component is
`com.android.vending/com.google.android.finsky.unauthenticated.activity.UnauthenticatedMainActivity`.
It displays the signed-out welcome with a Sign in button. No sign-in was attempted;
account-screen behavior and actual Play downloads remain unknown. The fixture was
installed through the emulator shell, as requested by the proof plan.

## Blocking result: reinstall after a hidden installation

Reproduced independently with both the old calculator fixture and the dedicated
Play fixture:

1. Install fixture while opening is active; normal policy hides it; close.
2. Uninstall that fixture on this emulator.
3. Start another opening with the same runtime.
4. Install the same fixture APK again.
5. Installation reports Success, but `hidden=false`, `suspended=false`, enabled
   default. The normal HIDES readback fails. The controller closes Play and does
   not report ready to hand back. The instrumented hide wait times out.

For the dedicated-fixture reproduction, both before and during readbacks were
LOCKED and only Play was added. This is not the earlier instrumentation-start
lock-task timing issue. For the calculator reproduction, an explicit device-port
setHidden(true) also read back false. After an emulator reboot the same preview
plan hid it successfully; there were no hidden/shown mismatches. That recovery is
an observation, not an accepted per-install workaround or evidence of a cause.

Cause is not established. A stale platform policy value across uninstall/reinstall
is a hypothesis only. Do not bypass readback or claim first-install success covers
this failure. Lead must route the device-side correction and repeat the reinstall
case before the existing review/driver can accept this slice.

The stronger proof also caught and fixed a runtime event-prefix bug:
BootReceivers emits `package:PACKAGE_ADDED:...`, not `package:ACTION_PACKAGE_ADDED:...`.
A red-first regression test now covers the real prefix. The receiver captures the
current session ID before queueing, and the engine rejects events after closure.

## Host and visual checks

- Full `:policy:test`: 691 passing at runtime checkpoint.
- Full `:device:testDebugUnitTest`: 1,210 passing at runtime checkpoint.
- Full `:plainphone:testDebugUnitTest` and `:ui:testDebugUnitTest`: passing.
  The expected old UI baseline failures did not occur in this run.
- Subsequent targeted Play/dispatcher tests pass after notice-priority,
  retained-grant closure, unreadable-eligibility closure and event-prefix fixes.
- Paparazzi: 12 new images (six stages at 1.0 and 2.0), recorded deliberately,
  individually inspected, and focused verify passes. FLAG_SECURE remains enabled.
- App and instrumentation builds pass. No check was disabled to hide a failure.

## Reproduction

Use only a verified isolated read-only Play image emulator. Choose a free 59xx
pair, verify `emu avd name`, and set PLAY_EMULATOR to that target. Never use a
physical device or connectedAndroidTest.

```sh
cd app
./gradlew :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest \
  -PpreviewTestRunner=org.plainphone.play.PlaySessionProofInstrumentation
bash tools/preview-2/setup.sh "$PLAY_EMULATOR" \
  plainphone/build/outputs/apk/debug/plainphone-debug.apk
adb -s "$PLAY_EMULATOR" install -r -t \
  plainphone/build/outputs/apk/androidTest/debug/plainphone-debug-androidTest.apk
bash tools/play-session/build-fixture.sh
adb -s "$PLAY_EMULATOR" push \
  "$PAPERCLIP_RUN_SCRATCH_DIR/play-fixture/play-candidate.apk" \
  /data/local/tmp/play-candidate.apk
adb -s "$PLAY_EMULATOR" shell am instrument -w -e phase install \
  org.plainphone.test/org.plainphone.play.PlaySessionProofInstrumentation
adb -s "$PLAY_EMULATOR" uninstall org.plainphone.playcandidate
adb -s "$PLAY_EMULATOR" shell am instrument -w -e phase install \
  org.plainphone.test/org.plainphone.play.PlaySessionProofInstrumentation
```

Other phases: ui, done, home, deadline, screen, call, reboot, restart, diagnose.
Call waits for readyForCall before the host injects synthetic ringing. Reboot
waits for readyForReboot; the host must reboot then, not after instrumentation
finishes (finishing instrumentation already kills/restarts the app).
Wake and dismiss the emulator keyguard after screen-off/reboot before a new case.
The diagnostic phase only reads mismatches after a normal runtime policy pass.
