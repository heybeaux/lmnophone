# Recovery-code preview evidence (PLA-521)

Author branch: `pla-recovery/code`, based on preview-46 `0daeb39e`.
Production checkpoint: `46a03976`. Test-first checkpoints: `be61f369`, `7de64021`.
The first policy test run failed compilation because `replaceRecoveryCode` did not exist.

## Behavior and limits

- First-use enrollment returns a single-use code handoff and displays the code after storage succeeds.
- Your settings → New recovery code asks for a fresh PIN. The replacement transaction changes only the recovery verifier and recovery counter; it preserves the PIN, its counter, and credential generation. Normal fresh-PIN authentication still uses its ordinary counter rules.
- Both ordinary PIN entry and Change PIN lead to recovery entry, a new PIN entered twice, and a replacement code shown once. Case, spaces and hyphens are normalized by the existing vault.
- Recovery has its own wait, shown from the policy clock. PIN lockout does not prevent recovery. The code screen and inputs use the existing FLAG_SECURE activity. Owned code buffers are zeroed on acknowledgment, pause, and stale callbacks; diagnostic strings are redacted. Codes are never saved as UI state or logged.
- Recovery resets the PIN, then asks for PIN entry again. It grants no later privileged-action authority. App add/remove and turn-off remain fresh-PIN routes. Waiting-change skip, reset-protection accounts and full setup/D1 integration remain outside this preview.

Requirements: trusted-person-controls, “Recovery code has its own counter” (including interrupted recovery), PIN lockout schedule, and fresh-authority/admin-screen boundaries. Copy: T1c/T1d and S3b. The new menu label is recorded in T2 under the issue's explicit authorization. `docs/mvp/lanes.md` records the interface changes.

## Host verification

Initial changed-module suites: policy 664, device 1,001, app 272 tests; zero failures, errors or skips. Subsequent focused checks cover the added tests and final changes: policy 5, device 9 (enrollment + recovery), app 20 (flow + production mapper); zero failures, errors or skips. These counts overlap; do not add them together.

The device fault test arms each of the five write points, asserts that its hook fired, reopens storage, and proves the surviving old-or-new recovery code works with the PIN and generation unchanged. The policy tests also reject false acknowledgments and stale readback. Existing vault recovery tests cover atomic PIN/code reset and independent counters.

Paparazzi: 14 new synthetic images, 360×640 dp at 1.0 and 2.0, with 32 dp status and 48 dp navigation space. Every image was visually inspected, then verified against the recorded image. Body content scrolls at large text; primary actions clear the reserved navigation area. No real recovery code is in any committed image.

Commands from `app/`:

```sh
./gradlew :policy:test :device:testDebugUnitTest :plainphone:testDebugUnitTest :plainphone:assembleDebug --no-watch-fs
./gradlew :policy:test --tests '*RecoveryRotationTest' :device:testDebugUnitTest --tests '*PreviewRecoveryTest' --no-watch-fs
./gradlew :device:testDebugUnitTest --tests '*PinEnrollmentTest' --tests '*PreviewRecoveryTest' :plainphone:testDebugUnitTest --tests '*PinScreen*Test' --no-watch-fs
./gradlew :ui:verifyPaparazziDebug --tests '*RecoveryPreviewScreenshotTest' --no-watch-fs
```

`openspec validate --all`: 4 passed, 0 failed. `git diff --check`: clean.

## Emulator installation and exercise

Private AVD `pla521-ui-a35`, API 35 arm64, launched `-read-only -port 5922`. No physical device was touched. No emergency call was placed.

Built: versionCode 46, versionName `0.1.0-preview-46`; the delivery driver assigns the next preview number.
Installed/read back: 2026-10-13 15:15:20 America/Vancouver (22:15:20 UTC).
Host and installed APK SHA-256 both:

```text
0f3700f1ae19316687a801124c85d5e37b7f01e8934b4d675964c5257120a7bb
```

Device Owner, LMNOPhone Home, testOnly, and lock task LOCKED were read back. At 100%, the production-screen instrumentation passed enrollment, fresh-PIN rotation (including wrong PIN), old-code refusal, uppercase recovery, old-PIN refusal, new-PIN access, and the Change PIN recovery route. It checked FLAG_SECURE, removal of the displayed code after acknowledgment, an unchanged lock-task package list, and zero grouped or ungrouped code matches in logcat.

At 200%, the final runner passed the same journey plus five wrong PIN attempts followed by successful recovery during the PIN wait. It measured acknowledgment-button bounds against the actual navigation-bar inset. The lock-task list remained unchanged, FLAG_SECURE remained set, and grouped/ungrouped logcat code matches were zero. The earlier 200% harness failures were fixed by waiting for enabled fields and menu readiness, settling scroll animations, and searching from the top; production code was unchanged during those harness fixes.

The runner is `org.plainphone.RecoveryInstrumentation`. It requires a fresh, synthetic private emulator credential store and an already-open Home. Between independent runs only this private emulator's synthetic authority files were cleared to repeat first-use enrollment; code rotation itself was exercised without any reset. Initial harness runs failed during startup/Compose settling or menu scrolling and are not PASS evidence.

```sh
./gradlew :plainphone:assembleDebugAndroidTest -PpreviewTestRunner=org.plainphone.RecoveryInstrumentation
adb -s emulator-5922 install -r -t plainphone/build/outputs/apk/androidTest/debug/plainphone-debug-androidTest.apk
adb -s emulator-5922 shell am start -n org.plainphone/.MainActivity
adb -s emulator-5922 shell am instrument -w org.plainphone.test/org.plainphone.RecoveryInstrumentation
```

Spare installation and spare exercise are pending the Lead's single Claude review and the Android Platform Engineer's delivery. Host tests and emulator results are not spare-device acceptance.
