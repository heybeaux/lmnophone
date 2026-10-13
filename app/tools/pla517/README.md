<!-- Copyright 2026 LMNOPhone contributors; SPDX-License-Identifier: Apache-2.0 -->
# T4 preview: Add a hidden app

Author branch `pla-apps/add-hidden`, based on accepted T5 `aa984cec`.
Review: [PLA-519](/PLA/issues/PLA-519), one Product & UX Designer review of
`aa984cec..7747ad8f`. **APPROVE**, with no Blocker/High/Medium findings:
[review verdict](/PLA/issues/PLA-519#comment-7db3973b-8f21-44fa-a9f2-9b69cb0c8e4e).
The reviewer independently passed the targeted host tests and all preview golden
comparisons. Evidence commits after that head contain no app-code changes.

Apps on this phone now ends with Add a hidden app. Eligible installed hidden apps
appear by name with monochrome icons. Empty/unreadable lists have plain messages
and Go back. T4a and T4b disclose unreviewed in-app web access; T4d then asks for a
fresh PIN. The browser classification catalog has no versioned web-leak status
model, so it does not supply a fabricated reviewed-version claim or developer.

Add rechecks live facts and the selected version/signers on the policy dispatcher.
It excludes browsers, catalog stores/assistants, installer capabilities, market
handlers/Play Store, floor, Settings, system UI, Home and LMNOPhone. Missing facts
fail closed. No permission is granted. The existing store lock publishes appended
Home order before starter membership, rolling back order on membership failure.
A process interruption before membership leaves the app hidden. Normal re-apply
and ready readback are required before Done; post-save failure shows attention.
The floor, locked plan, lock-task extras and upgrade preservation are unchanged.

T5 fixes: busy Back returns to a newly read list; Remove is guarded by current
list membership. Add/Remove recovery actions open the unsupported-feature
explanation. App details and confirmation expose Go back, matching the list.

## Scope

Trusted-person wording with the existing synthetic Alex/Sam preview profile and
PIN store. Out: T4c permissions and later permission edits, Play sessions, T7,
versioned web-leak notes, dates and self-managed wording. No on-screen TODOs.
The release driver merges with the latest release/voice Send work, assigns the
release number, and installs on the spare. This author does not operate the spare.

## Host evidence

Tests preceded implementation: `23498840` failed on absent PreviewAddFacts/canAdd;
`5b6331aa` adds screen tests that failed compilation on absent preview APIs. The
old busy-Back assertion was changed before implementation. No failing checks
were disabled.

- Policy: 660 passed.
- Device: 998 passed, including fresh addition PIN, source failure and save-time
  refusal of every excluded class, browser/catalog verdicts, failed save/readback.
- App: 268 passed, including the production PreviewHomeApps mapper's appended
  order, stale order entries, rollback, fresh PIN stages, decline, Back and late
  callback cancellation, wrong PIN and ready-only success.
- UI: 1,146 passed in normal rendering mode.
- Full Paparazzi comparison: 1,146 tests; the same 11 unrelated baseline failures.
  All 45 preview goldens pass at 1.0/1.3/1.5/1.8/2.0, 360x640, with long names.
  All 45 were viewed individually; 40 images changed or were added. Only this
  preview test class was recorded. The 11 baselines are five PreviewTurnOff
  maturityNotice scales and Branding turnOff/attention at 1.3/1.5/1.8.
- Lint remains separate: UI zero errors; device two existing MissingPermission
  errors in AndroidWifi:55 / AndroidWifiForget:22; app five existing VoicePracticeHost
  NewApi errors and the unchanged StateFlow.value expression in PinAccessActivity.

## Artifact and private emulator

Built: debug/testOnly versionCode 45, versionName `0.1.0-preview-45` (the branch's
inherited number, not a new release number).
SHA-256: `dde0d617bebffbb4fdc0a63d3fb615e1382c77342212fdee3d7014b9b277cc1f`.
Artifact: `app/plainphone/build/outputs/apk/debug/plainphone-debug.apk`.
Installed in-place on owned `emulator-5780`; Android lastUpdateTime
`2026-10-13 13:59:19` (guest clock), same version, DEBUGGABLE and TEST_ONLY read back.
No spare installation.

`pla500-ui-a35` runs with `-read-only -no-snapshot -no-window -no-audio` on port
5780. Override this disposable run to 360x640 / 160 dpi. Build the synthetic
Calculator with `../pla514/build-fixture.sh`, install/provision the preview, seed
Phone/Messages/Camera/fixture, and enroll synthetic PIN 111111 through the screen.
Then run `python3 emulator-check.py emulator-5780`. Never use connectedAndroidTest.
The script tests removal/addition, exclusions, wrong PIN/Back, order, private data
and reboot. Shutting down this read-only AVD discards the run's changes.

Exercised: `emulator-proof.txt` records the successful production flow, rejected
wrong add PIN and Back, browser/Play Store/Settings exclusions across the list,
ready-only Done, final order via production Home and the persisted order file,
retained synthetic app-private marker, actual guest reboot, and Phone/Messages
remaining visible. The removal recovery-code explanation and both Go back actions
were also checked. Initial harness attempts needed menu scrolling at 360x640 and
matching the existing Go back label; no app change was needed. Original visible
order was restored, the marker removed, and the read-only AVD was shut down.
No physical-device operation, call or send occurred.


## Review disposition and delivery

The single opposite-model review approves this bounded preview for installation;
no correction or delta check is requested. One Low copy finding is deferred:
T4 uses the same synthetic helper name Sam as existing preview routes. A later
coherent copy/profile change should resolve all helper-name call sites together,
using a stored helper or approved helper-free copy. This is recorded for the Lead
and does not hold this increment. No second review chain is opened.

The Lead routes this reviewed branch to the sole spare driver for integration
onto the latest release, numbering and an in-place install. The APK above was
built and exercised on the private emulator only; no spare installation is claimed.
