# Preview 7 preparation

Prepared for [PLA-353](/PLA/issues/PLA-353). The Lead owns one review, then the
assigned driver owns the spare install. This evidence is emulator-only.

## Built

Real merge `b87eca7` joins preview-6 `cfde6c7` and exact reviewed device head
`df307f3`; version/tool checkpoint `f4fdaab`. The one conflict, MainActivity,
keeps preview-6 Home, counts and shell effects, adds `runtime.onUserUnlocked()`
in onResume, and retains the emergency fallback with Boolean launch results.
No UI, count-source or shell files differ from preview-6. Reconciliation remains
unwired device-library code. No new behavior beyond the reviewed source changes.

Version 7 (`0.1.0-preview-7`), `org.plainphone`, testOnly.
APK SHA-256: `cb6aaa42d843badb6e7dfead15675760b1270e37dc9a7aafd0831ebaa2fb4fd3`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[Candidate APK, gzip](/api/attachments/03f0bd9f-9a53-4187-b14f-f6be8b53852d/content).

From `app/`, JDK 17:

```
./gradlew :plainphone:testDebugUnitTest :ui:testDebugUnitTest :ui:verifyPaparazziDebug :device:testDebugUnitTest :policy:test :ui:lint :device:lint :plainphone:lint :plainphone:assembleDebug --continue
python3 -m unittest discover -s tools/preview-7 -v
```

Gradle succeeds: app 130, UI including Paparazzi 191, device 632, policy 636;
1,589 tests, zero failures/errors/skips. Upgrade tools: 7/7 pass. No golden
recording. Lint: zero errors; app 8, UI 23, device 3 warnings. UI warning count
includes current dependency-version notices. No failed checks or disabled checks.
Logs, lint summaries and exact suite counts accompany this record.

## Installed

Owned API 35 AVD `pla353-ui-a35`, target `<emulator>`, fresh baseline provisioning
only. Build 6 came from the assigned archived attachment; SHA-256 verified as
`4806bada7b7bd0f827312972b8ff4a98af361df5c5299d625fa25a4a41cd5ace`.
The preview-2 setup script establishes the existing restriction-free preview
plan. The count toggle was exercised ON then OFF and its durable OFF value saved.

`tools/preview-7/upgrade-check.sh` upgraded in place at **2026-10-11 19:14:30 UTC**.
No uninstall/reset/reprovision during upgrade. Installed bytes were pulled and
match the candidate hash exactly; version 7 and testOnly read back. First install
remains `2026-10-11 12:12:01` (AVD local time UTC−07). Font scale remains 1.0.

Before, after upgrade and after timing samples retain:

- Same Device Owner; LOCKED; same nine lock-task packages; features 53.
- Same active restriction sections, no new restriction, no_debugging_features absent.
- Same 13 hidden, zero suspended, three disabled packages; 244 package records read.
- Same firstInstallTime and durable count preference OFF. adb reachable.

Raw snapshots and parsed safety comparisons are in `emulator/`; `readback.py`
checks all those invariants. No physical device was accessed.

## Exercised

Upgrade gate **exit 0**: full readback precedes Home; More → Emergency prefilled
exactly 911; Phone typed field exactly 911; zero current Telecom calls on both;
digits cleared, final version/owner/LOCKED pass. No Call button or SOS action.
The missing-preferred-dialer fallback is host-tested only, not exercised on the
spare or by the emulator's normal More route.

Matched timing procedure: temporary test lock credential, reboot, wait for
locked-boot reapply and stopped boot animation, wake/swipe/type/submit, then
record system `am_user_state_changed: [0,3]` and first `home shown lockTask=LOCKED`.
The same credential is used in each sample, then cleared before ordinary gates.
`unlock_timing.py` accepts it through `PLAINPHONE_TEST_PIN`. No production state
is changed. One sample per build, not a performance benchmark.

| Sample | Android unlock → first LOCKED Home | Credential submit → Home |
|---|---:|---:|
| Build 6 | 3.219 s | 3.619 s |
| Build 7 | 2.928 s | 3.259 s |

Build 6 got a full package-change pass before its boot pass. Build 7's
`reapply reason=user-unlocked` completes at epoch `1791746198.548`, first Home
at `1791746198.692`, and the boot pass at `1791746200.500`. Thus the unlock pass
and Home precede the boot **pass**; Android had already queued BOOT_COMPLETED.
The observed 0.291 s difference is small; this does not predict the spare's timing.
Raw event/reapply logs and both measurement origins are retained.

Home and More screenshots at 1.0 were inspected, with no personal data. More's
body and Home below its changing clock are pixel-identical to build 6; only
clock/status-bar changes are excluded (rectangles in `screens-comparison.json`).
The four Home/More images and both dialer images were visually checked before
sharing. Host paths and device serials are omitted from new evidence.

## Handoff limits

Notes carry build-6 limits: external-dialer identity, pending fallback containment,
record-I/O in-process-only recovery, More recovery without prefilled digits,
unwired controls and SMS/MMS-only counts. No broader D1 or physical acceptance.
Lead: review the merge/version delta and these results once, then route the
same attached bytes to the sole spare driver under [PLA-352](/PLA/issues/PLA-352).

Durable records: [emulator gate](/PLA/cases/PLA-C40) and
[draft release notes](/PLA/cases/PLA-C41).
