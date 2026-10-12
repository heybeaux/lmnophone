# Build 26 — finish the name and self-managed confirmation

Based on preview 25 at `54e2fc1a`. All 44 remaining user-visible resource strings
now say LMNOPhone. Copyright comments, resource identifiers, package names and
file names stay unchanged. The launcher label was already `LMNOPhone (preview)`.

**Are you sure?** now says “LMNOPhone will be turned off on your phone.” for
self-managed and test-build screens, matching the introduction's wait-line
selection. Supervised release screens retain the user's name. **Last check**
already refers to the helper who uses a computer to set the phone up again;
that wording remains correct in both modes and is covered by additional goldens.

## Validation

- **Built:** `26 / 0.1.0-preview-26`, debug/testOnly. APK SHA-256
  `4c92adcc16e54245c946d3fe2f85a426271d33c46353105daa5999e4f335c371`.
  Signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`
  matches builds 23–25. [Download gzip APK](/api/attachments/fb3997fc-56f3-4694-a4c0-02761f19c442/content).
- **Installed:** owned API 35 `pla363-policy-a35`, `emulator-5880`, in-place
  25 → 26 at **2026-10-12 12:44:47 UTC** (package lastUpdateTime readback).
  Version, TEST_ONLY, Device Owner and LOCKED retained, including after reboot.
- **Exercised:** More → Emergency prefill and Phone manually typed digits both
  exactly `911`; zero Telecom current calls; no call control or call intent used.
  Gate exit 0. Cleared the dial field and returned Home.
- **Host:** 84 selected UI test methods passed with golden comparison, including
  the entire trusted-screen and name-variant sets. All 470 changed and 25 new
  images inspected. Five text scales: 1.0, 1.3, 1.5, 1.8, 2.0. Nine gate tests pass.
  Test-first commit `6bcee3c0`: the resource-name regression failed before the fix.
- **Spare:** not installed or exercised by this author. Lead owns the single
  opposite-model review and in-place spare installation on [PLA-417](/PLA/issues/PLA-417).

Repeat from `app/`:

```sh
./gradlew :ui:verifyPaparazziDebug --tests 'org.plainphone.ui.Trusted*ScreenshotTest' --tests 'org.plainphone.ui.PreviewTurnOffScreenshotTest' --tests 'org.plainphone.ui.UserVisibleBrandingTest' :plainphone:assembleDebug
```

The upgrade gate is copied from preview 25 with only version/evidence-path names
changed. No policy, storage, permissions, emergency routing or turn-off behavior
changed. Existing synthetic strings supplied by older screenshot fixtures remain
unchanged; they are not shipped resource copy.

## Lead's spare check

1. Verify the APK hash and install in place over build 25; retain data and the
   existing PIN. Read back 26, Device Owner and LOCKED.
2. Open More → For Sam and Jo, enter the existing PIN, and check that the menu
   says “LMNOPhone adds waits…” and “What LMNOPhone can't stop”.
3. Open Turn LMNOPhone off → Continue. Check “your phone” on Are you sure?.
4. Continue with the existing fresh PIN to Last check. It should still name the
   helper. Tap Cancel; this copy check needs no new wait.

Preview limits are unchanged: actual turn-off is disabled and this test build
sends no SMS. Resource renaming does not enable unavailable menu destinations.
No public/store release.

[Release record](/PLA/cases/PLA-C91) · [Emulator gate record](/PLA/cases/PLA-C92).
