# Preview 9 preparation

Prepared for [PLA-363](/PLA/issues/PLA-363), under [PLA-362](/PLA/issues/PLA-362).
Author verification only. The Lead owns the single opposite-model review; the
Android Platform Engineer owns the spare install. No physical device was accessed.

## Built

Base: `origin/release/preview-8` at `fc99d37`. No `mvp/device` or D1 wiring.
Timing test red `d28be97` → implementation `d9d5f6c`; encoder test red `e0343c8`
→ safe cut `79b178e`; version/tools/notes `f99ed65` is the built code head.
Both reds failed compilation on the new API before implementation; logs are retained.

The existing `PlainphoneBoot` reapply line now includes elapsed milliseconds for
input assembly, live package facts and floor identities, fallback `osFloor`,
session revocation, each stage write/readback, final verification, flip-failure
storage, disabled-state reads, and `afterReapply`. `ms` retains the old boot-run
meaning; `totalMs` also includes the hook. No extra log lines or package lists.
Nested timings are inclusive; repeated stages accumulate; absent labels did not
run. The optional diagnostics parameters are recorded in `docs/mvp/lanes.md`.

The only work cut is per-byte `Formatter` construction when encoding certificate
SHA-256 digests. Two hex-digit lookups produce exactly the same bytes. Tests
compare every byte value with the prior formatter and pin two known SHA-256
vectors. No package query, digest operation, floor calculation, plan, write,
readback, recovery, Keyguard-first condition, or dispatcher ordering changes.
The full live inventory stays: the floor uses its live system identity evidence.
Locked passes still cannot publish Home.

VersionCode 9 (`0.1.0-preview-9`), `org.plainphone`, testOnly.
APK SHA-256: `0a8f5e48e0a031cb0286862c372ffbf75da17d6b0c43966845871d0d398db7f6`.
Signer SHA-256: `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`,
identical to build 8.
[Candidate APK, gzip](/api/attachments/2667633e-26cc-4d06-aaaa-59166479e8ea/content).
Downloaded attachment was gunzipped and independently hash-checked. The attachment
and artifact work product were confirmed through API readback after the upload
helper stalled following its successful upload; no duplicate upload was made.

From `app/`, JDK 17:

```sh
./gradlew :device:testDebugUnitTest :policy:test :plainphone:testDebugUnitTest :plainphone:assembleDebug :ui:lint :device:lint :plainphone:lint --continue
python3 -m unittest discover -s tools/preview-9 -v
```

| Suite | Tests | Failures / errors / skipped |
|---|---:|---|
| Device | 637 | 0 / 0 / 0 |
| Policy | 636 | 0 / 0 / 0 |
| App | 137 | 0 / 0 / 0 |
| Upgrade tool | 7 | 0 / 0 / 0 |

Gradle: **1,410 tests**, successful build. Lint: zero errors/fatals; UI 23,
device 3, app 7 warnings, unchanged from preview 8. No baseline test failures,
disabled checks, skipped tests, new dependencies, or recorded golden images.
The copied upgrade tools differ only in version identifiers; the shell entry
point remains mode `100755`.

## Installed

Fresh owned API 35 AVD `pla363-policy-a35`, target `emulator-5880`. Provisioned
with the recorded build-8 APK (`eca34961…`), then upgraded in place to a build-8
instrumentation-only candidate and finally build 9. No uninstall, reset, or
reprovision during either upgrade. Instrumentation-only build 8 still uses the
original certificate formatter, allowing per-stage before/after comparison.

Build 9 installed at **2026-10-11 21:09:25 UTC**. Package readback confirms
version 9; the installed APK pulled from the emulator matches the candidate
hash. First-install time remains `2026-10-11 14:05:10` in the AVD's local zone.

Safety summaries retain the same Device Owner, nine lock-task packages, features
53, LOCKED state, active restriction sections, 13 hidden / zero suspended /
three disabled packages across 244 records, and absent count-preference file
(the default remains off). No new restrictions or hidden/suspended/disabled
packages; `no_debugging_features` absent and adb reachable.

## Exercised

[Stage timings](timings.md) and the selected system/app logs preserve each boot.
Original build 8 unlock → LOCKED Home: **2041 ms**; instrumented build 8:
**1945 ms**; build 9: **1156 ms**. Locked start pass: original 8 **897 ms**,
instrumented 8 **1656 ms**, build 9 **783 ms**. User-unlocked pass: **1604 / 1438 /
1037 ms**, respectively. The first build-9 attempt did not deliver the test PIN,
so it has no unlock timing and is retained as an unsuccessful harness attempt.
The valid rerun waited for PIN entry to be visible. These are single cold-boot
observations on a shared host, not a controlled speedup estimate or a spare result.

The API 35 emulator's non-root shell cannot write `/sdcard`: both UIAutomator
and a plain shell write report `EFAULT` (UIAutomator misleadingly also says it
wrote the file). The ordinary gate therefore stops at UI-dump readback. Root
can write; reboot resets adb to shell. A small host-only harness restores adb
root after the gate's normal reboot. It invokes the same gate `main`, `restart`
and route assertions unchanged; no assertion is skipped or relaxed. The app
still runs as its ordinary app UID. Both initial failed gate logs are retained.
This is an emulator-driver accommodation, not an app or shipped-tool change.

Gate with the disclosed host-driver accommodation: **exit 0**. Home appears
only after full readback. More → Emergency prefills exactly `911`; Home → Phone
accepts exactly `911` via keypad taps. Both routes report zero current Telecom
calls. Digits were cleared; final owner/version/testOnly/LOCKED and complete
safety comparison pass. No Call button, call intent, or SOS gesture was used.
The screenshots show only the emulator dialer and were inspected before sharing.

The preview-8 limits are carried forward unchanged in [notes](../NOTES.md).
This candidate does not claim full D1 acceptance or a spare install. Lead:
review this delta once and route the same attached APK bytes to the sole spare
driver. The added timing line is ready for measurement on that device.

Reproduce the emulator gate from the repository root, after the in-place install:

```sh
SKIP_INSTALL=1 python3 docs/releases/preview-9/evidence/gate_on_root_avd.py app/tools/preview-9 emulator-5880 app/plainphone/build/outputs/apk/debug/plainphone-debug.apk "$PAPERCLIP_SCRATCH_DIR/gate"
```

`profile.py <label>` reboots the owned AVD, enters the throwaway test PIN from
`PLAINPHONE_TEST_PIN` after its field is visible, and records only selected boot
and policy timing logs. Set the same throwaway PIN on that AVD before using it.
The shared script contains no PIN. The test PIN was cleared before the gate.

Durable records: [candidate notes](/PLA/cases/PLA-C48) and
[emulator gate](/PLA/cases/PLA-C49).
