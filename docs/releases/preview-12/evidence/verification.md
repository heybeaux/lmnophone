# Preview 12 author verification

[PLA-370](/PLA/issues/PLA-370). Base `release/preview-11` at `a9f29f0`.
Author worktree `plainphone-policy-preview12`, branch `release/preview-12`.
The Lead owns the single opposite-model review and physical spare. No physical
phone was accessed by the author.

## Built

Test-first commit `0b1d1dc` precedes implementation `0b48706`. The Kotlin red run
failed on the missing coverage API and pending-save property; two gate-tool tests
failed against the old UI-dump path. Red and green logs are beside this file.

From `app/`, JDK 17 and SDK 36:

```sh
./gradlew :device:test :plainphone:test :plainphone:assembleDebug
python3 -m unittest discover -s tools/preview-12 -v
```

All required checks pass. Device: 647 tests each in debug/release. App: 147 tests
each in debug/release and the four negative-control variants. Gate tool: 9 tests.
No failures, errors or skips. Existing compiler warnings are in unchanged code.
No unrelated baseline failure was encountered; no check was disabled.

VersionCode 12, `0.1.0-preview-12`, application ID `org.plainphone`, testOnly.
APK SHA-256: `b8834d1a678dfa942895c13a27e7fc531b5f87b251d86a67a7f53d463c1a243b`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
This is the same signer as preview 11. [Candidate APK, gzip](/api/attachments/096eb723-64aa-4951-89b7-e49fafa8e6ee/content)
was downloaded after upload, decompressed and hash-verified.

## Covered work and retained side effects

`BootReapply.run` has no trigger-specific branch: every full run performs session
revocation, journal handling, apply/readback, independent final verification,
flip-failure recovery and disabled-floor readback. `ready=true` requires the final
verification and journal clear. Coverage also requires all five full-preview
stages, no failed stage, no attention, and successful Home publication.

Only `boot` and `locked-boot`, observed unlocked on the serialized dispatcher,
may reuse a preceding verified full pass from this process. The live unlocked
state is read before the full pass starts. A locked pass, failure, exception,
incomplete result or failed publication invalidates coverage. Process restart
starts with none. Package events, `updated`, `user-unlocked`, `start`, `alarm`,
`trusted-person` and recovery triggers never use this optimization. The existing
unlock-signal de-duplication and package-event merging are unchanged.

A pending flip-notice save prevents skipping, including a failed save of an empty
record after acknowledgement. `BootReapply.retryPending` exposes this internal
retry requirement; the seam is recorded in `docs/mvp/lanes.md`. Negative-control
builds never use coverage because their post-pass hook deliberately changes state.

On a covered boot trigger, the unchanged publication path still runs recovery,
Home gate/notice/refresh and run notifications. Count-host lifecycle hooks are
unchanged. `BuildControl.afterReapply` still runs. The unchanged receiver schedules
its alarm before dispatch, and `reapply`'s `finally` still invokes the completion
callback, including locked Home startup and `pending.finish()`.

Logs distinguish a real pass (`pass=<id>`, with timings and readback) from a
covered trigger (`skipped=covered-by=<id>`); a skip is not reported as a new readback.

Requirements preserved: device-lockdown order and independent readback,
locked-boot repair, and the essential-calling floor. This is the authorized boot
scheduling fix; it changes no package policy or emergency route.

## Installed

Retained API 35 AVD `pla363-policy-a35`, `emulator-5880`. Build 11 was read back
before `adb -s emulator-5880 install -r -t`. Build 12 installed at
**2026-10-12T00:33:08.205260Z**, without uninstall, wipe or reprovision. Version
readback and the pulled installed APK SHA-256 match the candidate.

The complete before/final safety summaries are exactly equal: Device Owner,
lock-task list/features, active restrictions, count preference, first-install
time and all hidden/disabled/suspended package sets. First-install time remains
`2026-10-11 14:05:10`. Home is LOCKED; adb remains reachable and
`no_debugging_features` is absent. See `installed12.json`, `before-safety.json`
and `final-safety.json`.

## Exercised

The gate rewakes and dismisses the no-PIN keyguard before every UI dump, including
both routes and their intermediate taps. It retains the exact 911 checks, Call
control exclusions and current-call checks. The host tests prove wake/dismiss
ordering and that dismissal failure stops before any UI dump.

This AVD retains the shell-user `/sdcard` EFAULT workaround accepted for preview
9: the host harness restores adb root after reboot. It does not replace any gate
assertion, and the app remains at its ordinary app UID. From repo root:

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-12 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk <evidence-directory>
```

Gate **exit=0**: More → Emergency prefilled exactly `911`; Phone accepted exactly
`911`; both routes reported zero current Telecom calls. Digits cleared, Home
restored, final version/owner/LOCKED readback passed. Both keypad screenshots
were inspected and contain no personal data. No call intent, Call button or SOS
gesture was used.

The reboot log shows **six full passes and two covered triggers**: locked start,
user-unlocked, and four package-event passes. `locked-boot` reused pass 3 and
`boot` reused pass 4. The user-unlocked pass took 663 ms; the final package pass
ended at emulator log time `17:33:48.037`. These are one no-PIN emulator sample,
not a physical unlock measurement or a speedup claim. See `build12-boot.txt`.

The Lead must still review and install on the spare, then record its two unlock
samples, pass count and last-pass end time. Emulator observations do not establish
spare performance. [Known limits](../NOTES.md) are carried forward.

[Release notes](/PLA/cases/PLA-C54) and [emulator gate](/PLA/cases/PLA-C55)
include the logs and visually checked keypad screenshots.
