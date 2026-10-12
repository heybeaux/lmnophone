# Preview 15 author verification

[PLA-382](/PLA/issues/PLA-382). Branch `release/preview-15` from preview 14
`2b50cc2b`. Tests first: `cbee1a2a`. Implementation: `453ce54b`.
Author used only retained API 35 AVD `pla363-policy-a35`, `emulator-5880`.
The Lead owns the single opposite-model review and physical spare.

## Built

Required `:device:test :plainphone:test :plainphone:assembleDebug` passed:
655 device tests per debug/release variant; 158 app tests per debug/release and
four negative-control variants. No failures, errors, skips or unrelated baseline
failures. Existing compiler warnings remain. Nine copied gate-tool tests pass;
the gate differs from preview 14 only in version strings.

Before implementation, the coverage suite ran 17 tests with one expected failure:
`coveredBootSkipPreservesPublishedPassCapture`. See `capture-red.log` and
`capture-green.log`. Both late boot reasons now retain capture. Retry becoming
pending during the covered skip or failed publication discards it. Explicit
invalidation, a new full pass, failed/attention outcomes and a locked full pass
still discard it. The old test's covered-boot cancellation expectation is replaced
by retention; the locked boot that actually runs a full pass still cancels.

`last === pass` preserves capture only when the same pass object survives all
existing re-checks. A full pass creates a new object; invalidation or failed
re-check leaves no current object. Eligibility rules, fresh skip readbacks and
PackageEventBurst receipt-time eligibility are unchanged. No module interface,
policy permission, emergency intent, approved app, recovery path or UI changed.
This corrects scheduling under the existing device-lockdown readback/boot rules.

## Installed

Build **14 → 15**, in place, at **2026-10-12T02:39:50Z** (package-manager update
time converted from the emulator's -07:00 local time). VersionCode `15`,
`0.1.0-preview-15`, application ID `org.plainphone`, testOnly retained.
First-install time remains `2026-10-11 14:05:10`. No uninstall/reset/reprovision.
The installer wrapper's overly strict stdout equality failed after installation;
package readback and a pulled APK confirmed success without repeating the install.

[Candidate APK, gzip](/api/attachments/fca6a83a-057e-4bfe-ab33-95286b2d83f9/content).
Built, pulled installed and decompressed uploaded APKs all match SHA-256:
`9d13b678db2b6c2976f3ee1db21434d2b726ab25a3864de7e3f500e4ffbf128a`.
Unchanged signer SHA-256:
`99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
See `artifact.json` and `installed15.json`. The upload helper did not terminate;
the saved attachment/work product were verified through the API and the download
was hash-checked before stopping the stalled helper. No duplicate upload.

## Exercised

The unchanged upgrade gate passed **exit=0**: More → Emergency prefilled exactly
`911`; Phone accepted exactly `911`; zero current calls on both routes. Digits
cleared and Home restored. Screenshots visually inspected; no personal data.
No call intent, Call button or SOS gesture used.

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py \
  app/tools/preview-15 emulator-5880 \
  app/plainphone/build/outputs/apk/debug/plainphone-debug.apk \
  docs/releases/preview-15/evidence
python3 docs/releases/preview-15/evidence/queued_boot.py
python3 docs/releases/preview-9/evidence/readback.py final emulator-5880
```

The retained root-host workaround for this AVD's shell-user sdcard EFAULT was
reused unchanged. The app keeps its ordinary UID and every gate assertion runs.

Ordinary samples (`boot-gate.txt`, `boot-sample2.txt`) capture their user-unlocked
passes, but their covered skips occur after capture. They do not prove the target
interleaving. They contain three and two full unlocked passes respectively.

The controlled reboot probe (`queued_boot.py`) sends one explicit
`LOCKED_BOOT_COMPLETED` to the existing BootReceiver immediately after observing
the real `USER_UNLOCKED` broadcast, on the root-host emulator. No app code or
policy state is modified for the probe. `queued-boot.txt` records:

- `19:43:37.386`: user-unlocked full verified pass **4** completes.
- `19:43:37.386`: `locked-boot skipped=covered-by=4` runs next.
- `19:43:37.601`: first Home shown with lock task LOCKED.
- `19:43:37.965`: `packageSnapshot pass=4 ms=577 captured=true`.

The old generation check would discard that capture. This log proves capture
survives a covered skip and remains after Home publication. The controlled sample
has two full unlocked passes. It is not a natural spare timing comparison.

Final safety summary **exactly equals** the before summary: owner, active
restrictions, lock-task list/features/state, count preference, first-install time,
and all hidden/disabled/suspended sets. Adb remains reachable. See
`before-safety.json` and `final-safety.json`.

[Release notes](/PLA/cases/PLA-C63), [emulator gate and attachments](/PLA/cases/PLA-C64).
[Known limits](../NOTES.md) are carried forward. No physical installation or
performance acceptance is claimed: the Lead still owns review, spare in-place
install/readback, gate and two natural unlock samples.
