# Preview-5 preparation and emulator verification

Date: 2026-10-11 UTC. Author: UI Engineer. Review owner: Plainphone Lead on
[PLA-333](/PLA/issues/PLA-333). Work: [PLA-335](/PLA/issues/PLA-335).

## Divider correction — current candidate

The branding evidence below is retained from the accepted build. The current
APK and repeat upgrade evidence are in [divider-correction/verification.md](divider-correction/verification.md).
Only More row dividers and their eight goldens changed.

## Prior branding candidate

- Branch: `release/preview-5`, pinned base `4fed6cd`.
- Test-first checkpoint: `066152e`; 12 navigation tests, one expected old-name
  failure, before implementation. See `test-first.txt`.
- Candidate source: `9cfd253a24f941e3ab5a13eaec95b346ded9b1a5`.
- APK SHA-256: `192e6cde91523b385d55c04eeb52cdedf0688f074712f65ce6aa618f26a1298a`.
- Verified signing certificate SHA-256:
  `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- `org.plainphone`, versionCode 5, versionName `0.1.0-preview-5`, `testOnly=true`.
- `aapt dump badging` reads label `LMNOPhone (preview)`.
- Exact artifact attached to this issue as `preview-5.apk.gz`; decompress before
  checking the APK hash. No physical phone was addressed.

## Every changed string

| Location / key | New text |
|---|---|
| App `app_name` | LMNOPhone (preview) |
| App `product_name` (new, untranslatable) | LMNOPhone |
| App `not_in_build_body` | This part of LMNOPhone comes in a later test build. Nothing was changed or sent. |
| UI `notice_attention` | LMNOPhone needs attention |
| UI `notice_turn_off` | LMNOPhone will turn off after %1$s. Tap to cancel. |
| UI `wifi_signin` | This Wi-Fi needs a sign-in page, which LMNOPhone doesn't allow. Use mobile data or another Wi-Fi. |
| Shared-link text | LMNOPhone: {user} would like to look at this link with you: {sanitizedTarget} |
| Launch fixture label | LMNOPhone launch target |
| Permission fixture label | LMNOPhone permission target |
| Signer fixture label | LMNOPhone check target |
| Update fixture label | LMNOPhone update target |
| Emergency-gate application and instrumentation labels | LMNOPhone emergency gate |

MainActivity resolves the dedicated `product_name` resource and passes it through
ShellViewModel into pure navigation. The existing UI `app_name` and app module
`app_name` collision remains deliberate: the merged app label is the preview
label. Shared-link text never uses that colliding key. ProductNameTest reads the
resource and asserts the complete generated text, without sending anything.
The on-screen shared-link fixtures now use the synthetic sample name Alex.
Identifiers, package names, log tags, copyright and KDoc remain unchanged.
The source/resource/fixture sweep found no other old user-facing product names;
the emergency gate's diagnostic report prose is not shipped UI.

One small lint correction accompanies the branding: the existing READ_SMS
permission implied a telephony hardware requirement without a feature declaration.
The requested lint task failed with PermissionImpliesUnsupportedChromeOsHardware.
The manifest now declares optional telephony. No permission, component identity,
Device Owner, policy or emergency-path code changed. This is not a suppression.

## Checks

JDK 17, offline, from `app/`:

```
./gradlew --offline :plainphone:testDebugUnitTest :ui:testDebugUnitTest :ui:verifyPaparazziDebug :device:testDebugUnitTest :ui:lint :plainphone:lint --continue
./gradlew --offline :plainphone:lint :plainphone:assembleDebug
```

| Suite | Result |
|---|---|
| App JVM | 84/84 passed |
| UI JVM / Paparazzi tests | 110/110 passed |
| Paparazzi pixel verification | Passed; 168 committed images |
| Device JVM | 488/493 passed; five baseline failures below |
| UI lint | 0 errors, 11 warnings |
| App lint after feature declaration | 0 errors, 8 warnings |
| Debug APK | Built, signature verified |
| Copied upgrade tool regression suite | 7/7 passed |

The combined run exited nonzero for the five baseline device tests and the
initial lint error. The subsequent lint/build run passed after the declaration
fix. Logs are `checks.txt`, `build.txt`, `tool-tests.txt`; exact JVM counts are in
`test-totals.json`. No failure is reported as a pass or disabled.

Unchanged device baseline failures, all in `Pla241ReviewTest`:

- `throwingJournalMustStillRepairEmergencyFloor`
- `lockedBootFlipFailureMustReachFirstUnlockedHome`
- `repairMustPreserveFlipFailureFromFirstAttempt`
- `enabledStateChangingBeforeFinalReadbackMustBlockSetup`
- `failedPlanMustStillRestoreFixedRestrictions`

## Emulator installation and exercise

Owned AVD `pla306-policy-a35`, API 35, serial `emulator-5862`. Its installed
preview-4 APK was pulled and hashed before upgrading: exactly
`cbf8ad4a80f7cbb1a0eb3eb63b09a4f1d53e1b36de0a55e783db8fa31f4a5232`.
No reset, uninstall, data clear or reprovision occurred.

- Turned the optional count switch on in preview-4 before upgrading.
- Installed with `install -r -t`; package update timestamp is **16:06:13 UTC**.
- Version 4 → 5; Device Owner and LOCKED retained; original first-install time
  **2026-10-10 22:29:07 America/Vancouver** unchanged.
- Count preference and switch read back on. The preference file modification
  time was **16:04:31 UTC**, before installation, proving no post-upgrade write
  was needed to retain it. The first evidence read used the wrong storage path;
  the corrected device-protected read and timestamps are preserved in
  `emulator/install-readback.json`.
- Temporarily switched counts off for the existing route check's Phone selector.
  The copied version-5 tool retained all original target, call-control, exact
  digits, clear-field and zero-current-call guards.
- Restart returned Home after full policy readback, with Device Owner, testOnly
  and LOCKED still present. See `emulator/upgrade-check.txt` (exit 0).
- More > Emergency prefills exactly **911**; Home > Phone, cleared then typed,
  reads exactly **911**. Both routes assert zero current calls. Call controls
  were never pressed and no call/message was sent.
- Restored count switch on after route checks and confirmed its readback.

This is emulator evidence, not physical-device acceptance. The existing AVD has
synthetic aggregate count fixtures; no communication contents, contacts or
personal numbers were read or captured.

## Layout evidence and limits

Only 18 existing text-affected Paparazzi images were re-recorded; nine new images
cover attention, long turn-off and Wi-Fi at 130%, 150% and 180%. All 27 were
visually inspected before commit. Existing 100% and 200% cases remain covered.
Long turn-off notices retain the accepted three-line ellipsis; full text stays
in semantics. This preview does not redesign that behavior.

Emulator captures use 360×640 dp for Home at 100%/200%, More, Phone settings,
and the unfinished-feature explanation at 100/130/150/180/200%. Wi-Fi captures
show both ends of the scroll area at the same five scales. Primary actions stay
reachable. The app label is not drawn on calm Home; its exact value is verified
from the APK. An attention notice was not forced through policy changes; its
five-scale coverage is the Paparazzi evidence.

Physical-device readback and tester observations remain the explicit TODO(spare)
block in `../NOTES.md`. The recovery fix is deferred to build 6.
