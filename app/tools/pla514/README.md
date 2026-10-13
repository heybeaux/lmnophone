<!-- Copyright 2026 LMNOPhone contributors; SPDX-License-Identifier: Apache-2.0 -->
# T5 preview: Apps on this phone

Base: `release/preview-45` at `7761bf65`. Author branch: `pla-apps/t5`.

The PIN menu opens the real app list in Home order. Move up/down saves a separate
atomic order file in the same credential-encrypted storage as the starter list.
New entries append; Phone and Messages retain their fixed bottom rows. Live floor,
Settings/Home and Safety Hub entries cannot be removed or moved. Unreadable floor
facts refuse edits. The normal floor, locked plan and lock-task lists are unchanged.

An app page shows its installed version. Remove uses the T5 confirmation and a
fresh app-bound PIN, atomically changes the existing starter file, then runs the
normal re-apply and readback. Only a ready result displays Removed. A pre-commit
failure says nothing changed; a post-save check failure shows Needs attention.
Back/pause invalidate pending work, and duplicate submits are ignored. The
configured starter file may be empty after removing the last discretionary app;
the floor continues to be supplied by the unchanged preview plan.

## Scope and limitations

This is the trusted-person wording subset using the existing preview PIN store
and synthetic Alex/Sam profile, not full D1 custody/profile integration. Omitted:
permissions and changing them, web-leak notes, Added/Last-updated dates,
self-managed wording, and adding hidden apps. No omitted item appears as TODO
text. Recovery-code enrollment remains the existing unsupported preview route.
The release driver owns numbering, merging with the voice-Send menu change and
installation on the spare. This branch keeps version 45 to avoid claiming a
release build number before that merge.

## Host evidence

Tests preceded implementation in `f9290998` and `98813746` and failed compilation
on the missing production types. The configured-empty-list case also failed on
missing `configured()` before implementation.

- `:policy:test`: 659 passed.
- `:device:testDebugUnitTest`: 991 passed after final changes.
- `:plainphone:testDebugUnitTest`: 261 passed after final changes.
- `:ui:testDebugUnitTest`: 1,116 passed in normal rendering mode.
- `:ui:verifyPaparazziDebug`: 1,116 tests, 11 established baseline failures;
  all 15 new T5 screenshots pass. Baselines: five PreviewTurnOff maturityNotice
  scales; Branding turnOff and attention at 1.3, 1.5 and 1.8. No baseline goldens
  were re-recorded.
- Fifteen new goldens cover list, details and confirmation with a long app name
  at 360×640 dp and font scales 1.0/1.3/1.5/1.8/2.0. Every image was inspected.
- Debug APK built. SHA-256:
  `b6ab6a03798a7929f03d536c98dae02c725a47bb1d0f675cd6add0f63e406f02`.

## Private emulator procedure

`pla500-ui-a35` runs read-only on the owned `emulator-5780`, without snapshot
loading/saving. Never use this script on the spare or another team's emulator.
`build-fixture.sh` builds a debuggable blank launcher activity labelled Calculator
in the run scratch directory; it is explicitly a synthetic app, not a real
calculator product. Install it and the candidate, provision the private emulator,
and seed the starter list with the default Phone/Messages, Camera and
`org.plainphone.t5calculator`. Enroll synthetic PIN 111111 through the production
PIN screen, then run `python3 emulator-check.py emulator-5780`.

The check drives the production screens, checks Home order by screen coordinates,
reboots the emulator, tries a wrong PIN and Back, removes Calculator with a fresh
PIN, reads back hidden state, then restores it with the setup command and checks
that an app-private synthetic data marker survived. It restores the original
visible order and app list and never sends or calls. No connectedAndroidTest.

## Recorded emulator result

Author candidate installed in place on private `emulator-5780`; Android readback:
versionCode 45, versionName `0.1.0-preview-45`, TEST_ONLY,
lastUpdateTime `2026-10-13 13:28:58` (emulator clock).
`emulator-proof.txt` records the successful production UI route, actual guest
reboot, wrong-PIN and Back refusal, successful hide/readback, and setup-command
restoration with the synthetic app-private data marker intact. A second in-place install preserved an explicitly nonalphabetical order and the
production Home mapping. Original visible order and starter list restored. No call/send and no spare installation.

Lint is separate from the T5 gates: UI has 0 errors (29 warnings); device has
2 existing MissingPermission errors (AndroidWifi:55, AndroidWifiForget:22);
app has 6 existing errors (five VoicePracticeHost NewApi findings and the
unchanged `runtime.waitingRead.value` composition read in PinAccessActivity).
Those source expressions match the base. No checks were disabled.

The candidate APK exceeds Paperclip's 10 MB attachment limit. Its hash and build
path above identify the local artifact for the shared-workspace driver; source
and screenshots are available on the pushed branch and issue work products.
