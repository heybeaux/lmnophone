# Preview 30 — read-only Bluetooth

Approved slice: [PLA-428](/PLA/issues/PLA-428), based on accepted preview 29 at
`650779b1`. Pairing and Wi-Fi joining remain later work.

## Built / installed / exercised

- Built: version `30` / `0.1.0-preview-30`, implementation `e0d9cca9`.
- Final APK SHA-256: `3a9f12dab2c02e89d0510dd49e03ca81bd0575527ee6d88781e9d83b54e5eca5`.
- Signer matches installed 29 and accepted 23–29 continuity:
  `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd`.
- In-place emulator 29 → 30 at **2026-10-12 15:30:46Z**. An explicit permission
  check and library-manifest declaration correction was installed in place at
  **15:32:36Z**, the final artifact above. No uninstall or reset.
- Initial and final upgrade gates: restart, both routes exactly 911, zero calls,
  Device Owner, TEST_ONLY and LOCKED retained. Final installed APK pulled back
  and byte-compared successfully with the candidate.
- Exercised on the owned API 35 emulator: requested menu order; Bluetooth ON
  with Location mode 0 and zero paired devices; self-grant readback; no scan
  permission; visible off/on refresh; pairing-later copy. Bluetooth state ON,
  bonded count 0, Location mode 3 and saved networks exactly match the original
  baseline at the end. No Bluetooth names or screenshots were retained.
- Spare: **not installed by this author**. The Lead owns review and spare delivery.

## What changed

More → Phone settings → Bluetooth shows paired-device names only while fully
on, sorted case-insensitively, at most ten. Missing names use “Unnamed device”;
no devices shows “No paired devices.” Off and transition states show “Bluetooth
is off.” A failed read shows “Bluetooth status can't be read right now.” with
no state claim or stale list. Every state says pairing comes in a later test
build. The page polls once a second while visible and drops names when hidden.

Only the app's own BLUETOOTH_CONNECT runtime grant is added through Device Owner
(legacy normal BLUETOOTH permission on API 30). No scan permission, discovery,
pairing, unpairing, connection, Bluetooth-state change, Location use or system
Bluetooth/Settings UI. The read-only API and required permission are documented
by [Android BluetoothAdapter.getBondedDevices](https://developer.android.com/reference/android/bluetooth/BluetoothAdapter#getBondedDevices()).

## Checks and limits

Tests-first commit `101a118b` failed compilation on the missing Bluetooth types.
Then seven controller tests and 202 host tests passed. The 68 selected Paparazzi
test methods passed, including all Bluetooth states and Phone settings at
1.0/1.3/1.5/1.8/2.0. All 83 changed goldens were inspected; fixture names are
made up. UI lint and `openspec validate --all` (3 changes) pass.

Separate baseline lint errors remain in files byte-identical to `650779b1`:
five MissingPermission findings in `AndroidWifi.kt`, and one
StateFlowValueCalledInComposition in `PinAccessActivity.kt`. Bluetooth has no
lint errors; no checks were disabled or suppressed. The final build/test tasks
completed before the unrelated lint failures.

Nonempty/unnamed/ten-device, transition, absent-adapter and read-failure states
are controller/golden evidence, not physical-device observations. Pairing is
not implemented. Device names are never recorded in evidence; only counts.

## Handoff and reproduction

APK: `app/plainphone/build/outputs/apk/debug/plainphone-debug.apk` in the assigned
`plainphone-policy-preview28` worktree. Paperclip's attachment endpoint rejected
the 28 MB APK at its 10 MB limit; use the local artifact and check its SHA-256.
Source and evidence are pushed on `release/preview-30`.

From `app/`:

```sh
./gradlew :device:testDebugUnitTest --tests '*BluetoothControllerTest'
./gradlew :plainphone:testDebugUnitTest :plainphone:assembleDebug
./gradlew :ui:verifyPaparazziDebug --tests '*BluetoothSettingsScreenshotTest' --tests '*PhoneSettingsWaitingScreenshotTest' --tests '*BasicSettingsScreenshotTest.moreSettingsCopy*' --tests '*CommunicationCountsScreenshotTest.settings*' --tests '*UserScreensScreenshotTest.more*'
```

From the repo root, the owned-emulator gate is
`app/tools/preview-30/upgrade_check.py`; `bluetooth_check.py before` captures the
baseline in the run scratch directory, and `bluetooth_check.py after` checks the
page with Location off, Bluetooth off/on, then restores and compares settings.
That Bluetooth script is hardcoded to **emulator-5880** only. It never retains
Bluetooth screenshots or names. Evidence: [gate records](../../app/tools/preview-30/evidence/).

The Lead installs the reviewed APK in place over spare build 29, opens the page,
and compares state and bonded count with `dumpsys bluetooth_manager` without
recording names. Bluetooth state, bonded count, Location mode and saved networks
must remain unchanged; BLUETOOTH_CONNECT is the only expected new runtime grant.
Preserve both emergency routes and never place a call.

## Spare install (Lead, sole driver)

Lead review [PLA-428](/PLA/issues/PLA-428): approved (changed product code plus
affected tests). The APK hash above matched the author's local build before install.

- **Installed:** in place over 29 at **2026-10-12 15:54:22Z** with the build-30 gate,
  installer Success, exit 0. The installed APK on the spare reads back byte-identical
  (`3a9f12da…e5eca5`). A second gate run with `SKIP_INSTALL=1` saved the full log:
  restart, More › Emergency and Phone both typed exactly **911**, **zero calls**,
  Device Owner, TEST_ONLY and LOCKED kept, exit 0 (`spare-upgrade-check.txt`).
- **Exercised:** Phone settings rows are Wi-Fi, Bluetooth, Brightness, Sound, Text size,
  Battery. The Bluetooth page reads **Paired with: / No paired devices.** with the
  pairing-later note. The spare has Bluetooth ON, zero bonded devices and Location
  mode 0, so no names existed to show or record (`spare-bluetooth-check.txt`,
  `spare-bluetooth-page.png`).
- **Readback:** Bluetooth state ON and bonded count 0 unchanged, Location mode 0,
  saved networks exactly unchanged, zero calls. BLUETOOTH_CONNECT is granted
  `POLICY_FIXED`. It read back granted after the post-install gate restart, before the
  Bluetooth page was first opened. The trigger for that early grant was not identified.
  It is the one expected new grant.
- **Not exercised on the spare:** named, unnamed or many paired devices; Bluetooth off
  and transition states; read failure. These are covered by controller tests and goldens only.
