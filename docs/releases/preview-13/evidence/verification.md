# Preview 13 author verification

[PLA-375](/PLA/issues/PLA-375). Branch `release/preview-13` from `2c94834`.
Test-first commit `5fb8d8a`; implementation checkpoint `d96ee38`.
The Lead owns the one opposite-model review and the physical spare. The author
used only retained API 35 AVD `pla363-policy-a35`, `emulator-5880`.

## Built

Required `:device:test :plainphone:test :plainphone:assembleDebug` passed.
654 device tests each in debug/release; 152 app tests each in debug/release and
four negative-control variants. Gate tool: 9 tests. No failures, errors, skips,
disabled checks or unrelated baseline failures. Existing warnings are unchanged.
The first red run failed on the missing readback API; its committed tests precede
implementation. See `package-red.log`, `full-checks.log`, `unit-results.json`.
The final artifact build confirms committed source after comment-only edits.

The preview-12 gate was copied unchanged except version strings. Its host test
suite remains green. No UI, calling route, emergency intent or policy permissions
changed. Requirements preserved: device-lockdown independent readback and journal
recovery, locked-boot repair, and the essential-calling floor.

## Why the skip is bounded

Audited `PolicyRuntime.onPackageEvent`, `BootPassCoverage` and `BootReapply.run`.
A full pass revokes session relaxations, handles the journal, applies and verifies
independently, remembers flip failures and checks the disabled floor. Coverage
still requires a full unlocked ready verified pass, all full-preview stages,
no failed stage, no attention, successful publication and no pending flip-record
save. Negative controls cannot populate or reuse package coverage.

A fresh plan contributes only its installed system packages. After a successful
full run, the new read-only snapshot excludes the never-hide guard, emergency UI,
Home, all lock-task packages, live floor/role/Home names and session packages.
The live journal must be empty with no revocation latch. Capture failures remove
eligibility; they do not fail or weaken the completed policy pass.

For a single PACKAGE_CHANGED, a new targeted read must match installed, hidden,
suspended and enabled state, and the complete lock-task list. Live floor and Home
exclusions are checked again, including default dialer and SMS role holders.
Unreadable facts, absent packages and unknown packages force a full pass. A
visible candidate must still have no launcher: component changes can otherwise
create a new hiding obligation without changing app-level flags.

All queued action/name pairs are retained. Mixed names or mixed actions take a
full pass. Added/removed/replaced packages and all non-boot/non-package triggers
retain full passes. Our own package no longer gets the receiver's blanket drop;
it is protected and takes a full pass. Events arriving during a pass queue another
pass. Process restart loses coverage. A locked, failed or incomplete full pass
invalidates it. Skips retain calling-screen recovery and lock-task retry, but do
not bump Home's refresh revision. No writes happen in the targeted proof.

## Installed

Build 12 → 13 at **2026-10-12T01:15:16.412559Z**, using an in-place `install -r -t`.
Version `0.1.0-preview-13`, application ID `org.plainphone`, testOnly retained.
Pulled installed APK and downloadable artifact both match SHA-256:
`03d099128f7acdbf11992362c169eea88796c37d65c7e348ef7d0f670bd5ac12`.
Signer SHA-256, unchanged from build 12:
`99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[Candidate APK, gzip](/api/attachments/0fc45d1f-a754-41c3-b515-550bb082c7b8/content).

No uninstall, reset or reprovision. First-install time remains `2026-10-11 14:05:10`.
The final safety summary exactly equals the before summary: owner, restrictions,
lock-task list/features, LOCKED state, count preference, install time and all
hidden/disabled/suspended sets. Adb is reachable; no debugging restriction was
added. See `installed13.json`, `before-safety.json`, `final-safety.json`.

## Exercised

Gate **exit=0**. More → Emergency prefilled exactly `911`; Phone accepted exactly
`911`; both routes had zero current Telecom calls. Digits cleared and Home
restored. Both saved screenshots were visually inspected and show no personal
data. No Call button, call intent or SOS gesture was used.

The retained root-host workaround for this AVD's shell-user `/sdcard` EFAULT was
reused; it changes no assertions and the application keeps its ordinary UID:

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-13 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk \
  docs/releases/preview-13/evidence
python3 docs/releases/preview-13/evidence/package_events.py
```

The actual hidden Contacts launcher component was disabled then restored to its
original default. Both PACKAGE_CHANGED events logged `skipped=unchanged-since=6`,
and neither logged another `home shown`. The app stayed hidden. Disabling the
whole app then restoring its default enabled setting caused two full verified
passes (`failed=null`), with the app still hidden throughout. All original
package flags were restored. See the four event logs and `package-events.json`.

The reboot log also contains a natural Digital Wellbeing skip and a mixed-burst
full pass. There are three unlocked full passes, one package skip and one covered
boot trigger (plus three locked passes). The final unlocked full pass ended at
emulator log time `18:16:04.212`. This is one emulator sample, not a controlled
spare timing comparison. Coverage snapshots cost 938, 563 and 133 ms in those
three unlocked passes; the first full pass took 1500 ms total. The Lead must
measure the net effect on the spare with the requested two unlock samples.

[Known limits](../NOTES.md) are carried forward. This is a test candidate, not
full D1 acceptance. No physical install or exercise is claimed by the author.

[Release notes](/PLA/cases/PLA-C57) and [emulator gate](/PLA/cases/PLA-C58)
retain the notes, logs and visually checked keypad screenshots.
