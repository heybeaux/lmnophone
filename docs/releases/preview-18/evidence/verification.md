# Preview 18 author verification

Scope: [PLA-389](/PLA/issues/PLA-389), based exactly on preview-17 `8f8241be`.
Author worktree: `plainphone-policy-preview18`; branch: `release/preview-18`.
Code checkpoint: `4a832638`; failing-test checkpoint: `e47fbe78`.

## Built

Version **18 / 0.1.0-preview-18**, package `org.plainphone`, debug/testOnly.
APK SHA-256: `2dcf99555ac91dce374699333cb578269cc9581c31aa7212f93d672b18d6deaf`.
Signer certificate SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`,
verified equal to the build-17 APK. No signing configuration changed.

Commands from `app/`, JDK 17:

```
./gradlew :device:test :plainphone:test :plainphone:assembleDebug
./gradlew :ui:recordPaparazziDebug --tests '*AboutScreenScreenshotTest'
./gradlew :ui:verifyPaparazziDebug --tests '*AboutScreenScreenshotTest'
python3 -m unittest discover -s tools/preview-18 -p 'test_*.py'
```

Required device/app tests and APK build pass. Device: 715 tests per debug/release;
app: 184 per debug/release/each of four negative-control build variants. UI:
15 new goldens (one/two/long synthetic names × 1.0/1.3/1.5/1.8/2.0). Gate host:
9 tests. No tests disabled and no unresolved baseline failure in these checks.
Goldens were recorded because this is a new screen, then each visually inspected
and verified. Frames are 360×640 dp with existing system-bar reserves.

The new About route test first failed against NotInBuild; screenshot fixtures
first failed compilation without the new screen. See `navigation-red.txt` and
`about-red.txt`. A later legacy shell test still expected OpenAbout to be unwired;
its remaining-unwired list was corrected. The explicit test-build unwired-row
test remains, and OK and Back both return to More without side effects.

## Installed on the owned emulator

Only API 35 AVD `pla363-policy-a35` / `emulator-5880` was driven. In-place 17→18
installation completed **2026-10-12T05:12:14Z**; readback confirms 18 and
`0.1.0-preview-18`. First install remains `2026-10-11 14:05:10` (emulator time).
See `install.json`, `upgrade-before.txt`, and `authority-preservation.json`.
All four authority/fence files were byte-identical immediately across upgrade.
No storage was cleared, credentials replaced, owner removed or phone reset.

**PIN evidence limit:** the build-17 author generated a random PIN and kept it
only in that process's memory. It was deliberately not saved or logged. That
process has ended. Therefore this run cannot re-enter the same correct PIN and
does not claim that check passed. Prior successful entry/reboot evidence remains
in preview-17. Exact storage preservation and the existing PIN-entry route are
separate evidence, not a newly successful correct-PIN entry. The Lead was told
before handoff; the review must assess this limit without fabricating PASS.

## Copy and scope

U8 from `docs/ux/flows-user.md`, including the literal two-person wording and
extra equals line. Names are the existing PreviewPhone sample; Sam is the sample
helper. Product name comes from the app's existing product-name resource, and
versionName from PackageManager. The supplied test-build caveat precedes the
product-description body. No helper number or help action is invented.

SOS is omitted: `spikes/device-moto/RESULT.md` Q14 reports the gesture blocked
with the planned list and working only after Personal Safety was added in that
throwaway test. It does not confirm SOS with this preview. No SOS gesture, call
intent or Call button was used. Pending-change starts still need a recovery
profile; Update remains disabled. No policy/authority implementation changed.
The dormant cancel and attention branches now explicitly require READY.

The single opposite-model review and any spare install belong to the Lead.
This author report is not review approval or physical-device acceptance.

## Exercised

The unchanged preview gate (only version strings changed) passed with `exit=0`.
It rebooted, checked Home after full readback, asserted exactly 911 for both
More → Emergency and Phone → keypad, asserted zero current Telecom calls,
cleared the digits and returned Home. testOnly, Device Owner and LOCKED passed.
The existing owned-AVD root-host workaround for shell sdcard EFAULT was reused;
the app UID and every gate assertion remain unchanged. See `gate.log`.

Installed-screen tests passed at all five scales on 360×640 dp: every body
section and all three 9-1-1 instructions were reachable, OK stayed visible and
returned to More, and Android Back returned to More. No helper-number action or
SOS text appeared. Real-device font scaling was supplied by Android on API 35;
no text sizes were calculated by hand. The display size/density and font scale
were restored afterward. See `about-route.json` and `about_route.py`.

After reboot and screen checks, the four authority/fence records were still
byte-identical to build 17. The trusted-person route offered existing PIN entry,
not new setup (`pin-entry.json`). No PIN was guessed or entered. Final safety
readback matches the before-install owner, lock-task packages/features, active
restrictions, count preference and first-install time; no newly hidden, disabled
or suspended package. All new images were inspected and contain only synthetic
preview text, empty dialers except the intended 911 digits, and no personal data.
