# Play approvals — private emulator proof

2026-10-15. This is the slot 8b self-managed increment, based on the reviewed
8a reinstall-hide correction `e0e0eaad`. It is ready for focused review, not a
spare install or public release. No Google account, real contact or message data
was used. No calls were placed.

## Built and installed

- Built: debug app and a separate androidTest APK. The app retains the base
  version `0.1.0-preview-63` / code `63`; the sole delivery driver assigns the
  eventual preview build number. Version was read back from the emulator.
- App SHA-256: `233ebb5dda8ed669a7082457af101ffc866be5cdab1323ad43a6acc7bd547231`.
- Instrumentation SHA-256: `970b7e373c42808246771fdc938c0e5500de937197c7a6579a0b7cdc4f7f53b7`.
- Installed only on `pla104-a36`, API 36 Play Store image, launched `-read-only`.
  A free private 59xx console/ADB pair was checked before launch; `emu avd name`
  read back the expected AVD before any install. No spare-device commands.
- The emulator reported app install/update time `2026-10-15 02:28:57` in its local
  clock during the gate. Subsequent setup reapplied the same artifact for the final
  cases. Device timestamps are not host test times.

## Exercised

| Case | Result |
| --- | --- |
| Install during a live Play grant, then return to Home | PASS. The real MainActivity return path closes Play, verifies protected state, and opens the new-app list. |
| Choose Camera Allow, confirm | PASS. Existing review/permissions/confirm screens; no second PIN. Home contains the fixture, the actual permission reads GRANTED, hidden reads false, and lock-task membership contains the app. |
| Done after approval | PASS. The retained grant ends. Returning closes Play access; Done/Back ends the grant, without another approval stage. |
| Approval after original deadline | PASS. androidTest advances only the injected test clock. The ordinary runtime refuses; app remains hidden and absent from Home and lock task. |
| Same package, different signer | PASS. After recording the original signer, the host uninstalls the fixture and installs it signed with a second temporary key. The ordinary runtime refuses the retained selection; app remains hidden and absent from Home and lock task. |

The host uses targeted `adb install` while instrumentation holds the real Play
opening. All three final cases enter through the real PIN/wait/Start UI. This substitutes for a Play download without signing in. Actual Play
installation still needs a Google account and is not claimed here. Clock
acceleration exists only in the androidTest APK. Source-boundary tests reject
opening/approval bypasses in shipped debug sources.

Sanitized final assertions are in [approval-proof.txt](approval-proof.txt).
Early harness attempts exposed a missing install handoff, accessibility-label
selection, a click before Compose enabled Next, and setup/startup or screen-state timing. They
were not counted as passes. The driver now waits for enabled accessible targets
and the resumed Home surface. Wake and dismiss the emulator keyguard, let setup finish,
and use the ordinary PIN/wait/Start UI rather than racing a direct launch during startup.

## Host evidence

- `:policy:test`: 692 tests, zero failures.
- `:device:testDebugUnitTest`: 1,223 tests, zero failures. The final focused
  dispatcher/store delta also passed (23 tests).
- `:plainphone:testDebugUnitTest`: 347 tests, zero failures.
- `:ui:testDebugUnitTest`: 1,427 tests, zero failures in this run.
- `:ui:verifyPaparazziDebug --tests '*PlayApprovalScreenshotTest'`: all four new
  candidate/expiry images pass at font scales 1.0 and 2.0. Every image was viewed;
  long content scrolls and Done remains pinned. These secure screens use goldens
  only. This is not a claim that every historical golden was reverified.
- `openspec validate --all`: six changes passed.

The tests began with commit `334edc80`, before implementation. New tests cover a
live approval without another PIN; deadline, Done and restart refusal; foreign
package and changed signer refusal; two apps on the same deadline; low battery;
and duplicate approval refusal. Fault hooks assert they fired. The store test
checks the staged membership file exists and order is written while membership
is still unchanged, then refuses at the final callback and verifies rollback.
Existing slot 2 tests continue to cover permission failures and readback.

## Implementation boundary

The installed preview uses its existing starter membership file, not the full
MVP approval database. `commitPlayApproval` supplies a one-use final-check
callback. The store stages and fsyncs its file, invokes that callback, and does
the atomic membership replacement without yielding on the serial dispatcher.
The check uses the owned live Play grant, original package and signer, current
identity, credentials and battery. Replay memory and candidate authority vanish
on process restart; saved membership persists. This adapter seam is documented
in `docs/mvp/lanes.md`. It does not replace the full MVP joint-store protocol.

Permissions still use slot 2's contained permission window, grant/readback,
rehide and membership-last path. A failed final activation removes the newly
saved discretionary membership and reapplies containment, returning attention
instead of Added. No Settings, supervised, Approve or Update grant is enabled.

## Repeat

Build from `app/`:

```sh
./gradlew :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest \
  -PpreviewTestRunner=org.plainphone.play.PlaySessionProofInstrumentation
bash tools/play-session/build-approval-fixtures.sh
```

Choose and verify an isolated read-only Play AVD on a free private 59xx pair.
Use the existing `tools/preview-2/setup.sh` on that target, then install the test
APK. Wait for setup's Home to settle. Run `tools/play-session/run-approval-proof.py`
with that emulator target and `approve`, `approval-expiry`, or `approval-signer`.
The script rejects non-emulator targets and unexpected AVD names. Reset only the
synthetic starter list through setup between a successful add and the negative
cases; otherwise the previously approved fixture is intentionally still approved.
No connectedAndroidTest runner is used. Raw logs and ephemeral signing keys stay
in the run scratch directory; only sanitized assertions are committed.
