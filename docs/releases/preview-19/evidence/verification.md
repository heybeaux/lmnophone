# Build 19 author verification

[PLA-392](/PLA/issues/PLA-392), exact base preview-18 `109370bf`.
Branch `release/preview-19`; failing-test checkpoint `1ddba9e0`; implementation `daa9dfa2`.

## Built

Version **19 / 0.1.0-preview-19**, `org.plainphone`, debug/testOnly.
APK SHA-256: `6d89b84e7a4bc340fac7e15c5b0c15b242586108c8bcb52afe49e3c12a656d4f`.
Gzip SHA-256: `e8cd43f920c6033cb13a95a611eac8126f1a61ec268e519c6b960a917a7c3016`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`, verified equal to build 18.

From `app/`, with JDK 17:

```
./gradlew :plainphone:test :plainphone:assembleDebug
./gradlew :ui:recordPaparazziDebug --tests '*AboutScreenScreenshotTest'
./gradlew :ui:verifyPaparazziDebug --tests '*AboutScreenScreenshotTest'
python3 -m unittest discover -s tools/preview-19 -p 'test_*.py'
```

App tests: 184 in each of debug, release and four control variants; zero failures.
This includes the existing navigator tests. About: 35 screenshot/semantic tests,
360×640 dp, scales 1.0/1.3/1.5/1.8/2.0. One/two/long names each cover secure true
and false; three names additionally cover the more-than-two case. Semantic checks
assert complete rendered choices, texts, privacy, equals and emergency instructions,
including content below the initial viewport. All five original one-person goldens
are byte-identical. Every new or changed golden was visually inspected.
The failing-test checkpoint has 25 assertion failures and five passing unchanged
one-person cases before the fix. Gate host tests: 9 pass. No checks disabled.

## Installed

Owned API 35 AVD `pla363-policy-a35` / `emulator-5880` only.
In-place **18→19** at **2026-10-12T06:25:28.587662+00:00**; package readback confirms
19 and `0.1.0-preview-19`. No storage clear, reset or reprovision.
The APK is attached to the issue as gzip. Physical-spare install belongs to the Lead.

## Exercised

The build-18 upgrade gate was copied to `app/tools/preview-19`, changing only its
expected version/label. It passed with **exit=0**: reboot, Home after full readback,
Device Owner retained, testOnly retained, lock task LOCKED, exactly 911 via both
More → Emergency prefill and Phone → keypad, and zero current Telecom calls.
Digits were cleared and Home restored. No call button, call intent or SOS gesture.
The existing root-host workaround for the owned AVD's sdcard EFAULT was reused;
app UID and all gate assertions are unchanged.

```
PATH=<sdk>/platform-tools:$PATH SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py app/tools/preview-19 emulator-5880 app/plainphone/build/outputs/apk/debug/plainphone-debug.apk docs/releases/preview-19/evidence
```

The installed no-PIN host path is checked by `about_route.py`; its final result is
`about-route.json`. It passed at all five scales: corrected plural paragraphs,
no lock-screen instruction, emergency section reachable, and OK/Back to More.
Display size/density and font scale were restored afterward. Final safety readback
retains the owner, lock-task list/features, first-install time, restrictions and
count preference, with no newly hidden/disabled/suspended packages. All captured
images were inspected and show only synthetic preview text.
Secure=true is covered by rendered UI state tests, not by
changing the emulator's lock credential. Existing PIN evidence from build 18 is
retained; no correct-PIN re-entry is claimed for this copy-only increment.

## Scope and handoff

Only the two U8 defects change. One-person resources, privacy and equals are
unchanged. Host `MainActivity` reads `KeyguardManager.isDeviceSecure` and passes
it through the shell into `AboutUiState`; the seam is documented in lanes.md.
Approved plural text and lock condition are reflected in `flows-user.md`.
Pending-change starts, Update and recovery profile remain unwired.

[Release notes case](/PLA/cases/PLA-C72), [emulator gate case](/PLA/cases/PLA-C73).
Lead owns the existing single opposite-model review and subsequent spare upgrade.
Author checks are not review approval or physical-device acceptance.
