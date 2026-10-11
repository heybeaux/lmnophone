# Preview 10 verification

Prepared for [PLA-366](/PLA/issues/PLA-366). Author checks only; Lead owns the
single opposite-model review and Android Platform Engineer owns the spare.
No physical device was accessed.

## Built

Base `release/preview-9` at `7b2dd12`. Timing test red `27009de`, implementation
`c60b3bb`; light-reader and preview-input tests red `bbd3597`, implementation
`6ace5ce`; version/tools built at `e610c53`. Both red commits contain tests only
and fail compilation on the new API. Required device/app suites: **642 + 138
passing tests**, zero failures/errors/skips. Upgrade-tool suite: **7 passing**.
No tests disabled, dependencies changed, golden images recorded, or D1 wiring.

Run from `app/`, JDK 17 and Android SDK 36:

```sh
./gradlew :device:testDebugUnitTest :plainphone:testDebugUnitTest :plainphone:assembleDebug
python3 -m unittest discover -s tools/preview-10 -v
```

The two snapshots retain hidden/disabled package flags and the installed-user
filter. The new typed projection omits signer data and probes. Floor queries
still run between snapshots; the same package variants, order, and disabled
merge reach both preview plans. Tests compare 625 snapshot pairs, duplicates,
missing entries, signer-only changes, enabled transitions, floor repair inputs,
and locked/unlocked plans. Full `read()` retains its data and call order for
links/approval. Only diagnostic timing is added there. The new seam is recorded
in `docs/mvp/lanes.md`. Optional dispatcher coalescing is deliberately excluded.

VersionCode 10 (`0.1.0-preview-10`), `org.plainphone`, testOnly.
APK SHA-256: `5639b6387961d8b5dfaa319261cdce620091faf55635ad54746fb6f03d4f8910`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
[Candidate APK, gzip](/api/attachments/5c56da8e-5835-4764-86c4-a6afd4188519/content).
The attachment was downloaded, decompressed, and hash-checked independently.
The three copied gate files differ only in version identifiers.

## Installed on the owned emulator

Retained API 35 AVD `pla363-policy-a35`, target `emulator-5880`. Build 9 was
read back before work. Upgraded in place first to a build-9 substage-timing
candidate, then build 10 at **2026-10-11 22:41:49 UTC**. No uninstall, reset,
or reprovision. Version readback confirms 10 and the pulled installed APK
matches the candidate bytes. First-install time remains `2026-10-11 14:05:10`.

Safety comparison: same Device Owner, lock-task set/features, active restrictions,
count preference, and package hidden/disabled/suspended state. LOCKED, adb
reachable, `no_debugging_features` absent. Full summaries are attached here.

## Measured

One successful PIN-unlock sample per candidate on a shared host:

| Observation | Build 9 + substage timings | Build 10 |
|---|---:|---:|
| Unlock broadcast → first LOCKED Home | 1356 ms | 1905 ms |
| Locked start, whole pass | 824 ms | 284 ms |
| Locked start, livePackages | 552 ms | 192 ms |
| User-unlocked, whole pass | 1137 ms | 1534 ms |
| User-unlocked, livePackages | 852 ms | 493 ms |
| User-unlocked, snapshot 1 / snapshot 2 | 439 / 329 ms | 124 / 76 ms |
| User-unlocked, probes | 33 ms | omitted |

The snapshot work fell in these samples, but **end-to-end unlock was slower**
in the build-10 sample. Other stage costs rose (including 349 ms for never-hide
readback), and a boot pass also queued after user-unlocked. These are individual
observations, not a controlled speedup estimate or spare evidence. The spare
must measure whether this bounded cut reduces its 8.8–9.8 s delay. No queue or
apply-order changes were made to improve a benchmark.

The first baseline attempt failed to expose the PIN field and yielded no unlock
sample. Its failure log is kept. The profile driver now checks the PIN field and
retries the swipe before entering the throwaway PIN, supplied only via the
environment. The PIN was cleared after measurement. No PIN is in shared files.
Use `profile.py <label>` with `PLAINPHONE_TEST_PIN` matching the owned AVD.

## No-call gate

Gate **exit 0**. More → Emergency prefills exactly `911`; Phone keypad accepts
exactly `911`. Both routes report zero current Telecom calls. Digits cleared,
Home restored, and final safety readback passes. Screenshots were visually
checked: emulator keypad only, no personal data. Logs and both images are saved.
The existing preview-9 host harness restores adb root after the gate reboot,
because this AVD's shell-user writes to `/sdcard` return EFAULT. It runs the
unchanged preview-10 gate assertions; the app remains its ordinary app UID.
No call intent, Call button or SOS gesture is used.

Known preview limits are carried forward in [notes](../NOTES.md).

Durable records: [release notes](/PLA/cases/PLA-C50) and [emulator gate](/PLA/cases/PLA-C51).
