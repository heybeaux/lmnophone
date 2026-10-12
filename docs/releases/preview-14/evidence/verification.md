# Preview 14 author verification

[PLA-379](/PLA/issues/PLA-379). Owned branch `release/preview-14` from preview 13
`35d89cf`. Test-first commit `52e13ec`, implementation `86a2fbd`.
The Lead owns the single opposite-model review and physical spare. The author
used only retained API 35 AVD `pla363-policy-a35`, `emulator-5880`.

## Built

`:device:test :plainphone:test :plainphone:assembleDebug` passed: 655 device tests
per debug/release variant; 156 app tests per debug/release and four negative-control
variants. No failures, errors, skipped tests or unrelated baseline failures.
Existing compiler warnings remain unchanged. All nine copied gate-tool tests pass.
The gate tool differs from preview 13 only in version strings.

The initial test run failed on the missing snapshot API, before implementation.
Committed tests cover publication before the queued capture, cancellation after
another pass (including a covered boot trigger) or explicit invalidation, unavailable
coverage, atomic snapshot availability and events received before capture completes.
See `deferred-red.log`, `deferred-green.log`, `full-checks.log`, `unit-results.json`.

## Changed behavior and preserved checks

`BootPassCoverage` offers capture only after the full pass has published and passed
its existing eligibility checks: unlocked, full verified apply, ready publication,
no attention and no retry. Negative controls remain ineligible. The queued task
checks both the generation and pass identity before reading. An intervening pass
or invalidation discards it. Inputs belong to that pass, not a later mutable plan.

`PackageChangeReadback` publishes one immutable volatile snapshot only after all
reads complete; capture/invalidation/targeted checks remain dispatcher-confined.
Broadcast receipt reads only availability. `PackageEventBurst` retains a full-pass
requirement if any coalesced event arrived before capture finished. A snapshot
completed while that event waits cannot make it skippable.

Fresh per-package readback, journal clearing, complete lock-task-list match, live
floor/Home protection, all protected/session exclusions and launcher checks stay
unchanged. There is no bulk package-read optimization in this slice. No policy
permissions, emergency intents, UI/copy, approved apps or recovery paths changed.
Requirements preserved: device-lockdown independent readback, journal recovery,
locked-boot repair and the essential-calling floor.

## Installed

Build **13 → 14**, in place, at **2026-10-12T02:12:56.426855Z**.
VersionCode `14`, version `0.1.0-preview-14`, application ID `org.plainphone`,
testOnly retained. No uninstall, reset or reprovision.
First-install time remains `2026-10-11 14:05:10`.

[Candidate APK, gzip](/api/attachments/9d95645e-828c-42d7-b888-59e5c48e5531/content).
Pulled installed APK and the decompressed uploaded download both match SHA-256:
`40b7e93249fc372c1f804625e51c94eec7bf477efc48983207b840e64632261a`.
Signer SHA-256, unchanged from installed preview 13:
`99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
See `artifact.json` and `installed14.json`.

The final safety summary exactly equals the before summary: owner, active
restrictions, lock-task list/features, LOCKED state, count preference, first-install
time and hidden/disabled/suspended sets. Adb is reachable; no debugging restriction
was added. Package flags were restored after every exercise.

## Exercised

The unchanged gate passed **exit=0**: More → Emergency prefilled exactly `911`;
Phone accepted exactly `911`; both routes had zero current Telecom calls. Digits
were cleared and Home restored. Both saved screenshots were visually inspected
and contain no personal data. No Call button, call intent or SOS gesture was used.

The retained root-host workaround for this AVD's shell-user `/sdcard` EFAULT was
reused unchanged. No assertion is replaced; the app retains its ordinary UID.

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-14 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk \
  docs/releases/preview-14/evidence
python3 docs/releases/preview-14/evidence/package_events.py
```

In `build14-boot.txt`, user-unlocked pass 4 finished at `19:13:17.513` (658 ms).
First Home was shown at **19:13:17.709**. Its separate snapshot completed at
**19:13:18.379**, taking 864 ms. The snapshot is off the Home-publication path.
A later queued package pass 7 runs before pending capture 6; no capture 6 occurs,
and capture 7 completes. This corroborates cancellation on the serial dispatcher.

The sample has **four unlocked full passes**, including Android, Play services
and Partner Setup events. It is not a controlled spare comparison and does not
establish the spare's target of 5–6.5 seconds / at most two full passes. The Lead
still needs the requested two spare unlock samples.

The focused early-event probe uses the existing update receiver for a normal full
pass, then changes only the hidden Contacts launcher's component state. In
`before-capture-component.txt`, the event request is at `19:17:29.614`, during
capture 10 (completed `.810`). Despite that snapshot being complete when the event
runs, **full verified pass 11** finishes at `.963`. No skip occurs. The launcher
component is restored afterward. `before_capture.py` preserves the commands and
its ordering/full-pass assertions passed against the saved log.

After completed capture, disabling/restoring that same component gives two
`skipped=unchanged-since=...` events with no Home refresh. Disabling/restoring the
whole app gives two full verified passes. Contacts remains hidden throughout.
See `package-events.json` and the four corresponding logs.

[Release notes](/PLA/cases/PLA-C61), [emulator gate](/PLA/cases/PLA-C62), and
[known limits](../NOTES.md). This is a test candidate, not full D1 acceptance.
No physical install or exercise is claimed by the author.
