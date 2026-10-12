# Build 32: nearby Bluetooth devices

Candidate for the Lead's one review on [PLA-434](/PLA/issues/PLA-434).
Base: accepted `release/preview-31`, `8e6d63b5`. No D1 integration.

Built: versionCode **32**, versionName **0.1.0-preview-32**, same debug signer as
build 31 (and retained preview signing configuration), TEST_ONLY.
Final APK SHA-256:
`a03b1e022fd072f252b725186cdb13808033e74df5103a24189beba11020f6eb`.
Installed on owned API-35 emulator-5880 in place; final artifact install completed
**2026-10-12 17:34:32Z**. **Not installed on the spare by the author.**

## What changed and how to try it

More → Phone settings → Bluetooth → Pair a new device. The row appears only
when Bluetooth is fully on. Opening it looks for nearby devices. Named,
unpaired devices appear once each, sorted ignoring case, at most 20. When the
search ends, Look again starts a fresh search. Opening a device shows its name
and “Pairing from LMNOPhone isn't ready yet.” There is no Pair action.

New permission: **BLUETOOTH_SCAN**, marked **neverForLocation**. The app grants
it only to itself through its existing Device Owner and independently reads it
back. It also uses its existing BLUETOOTH_CONNECT grant. No new Location
permission or Location use; no radio switch, Settings activity, connect/unpair,
profile operation, network operation or lock-task list change.

## Probe and limits

The throwaway probe found one named private emulator peer with Location **0**,
Device Owner true, and lock task LOCKED. `createBond()` returned true, but the
initiating Android `BluetoothPairingDialog` was rejected with lock-task result
**101**; the peer showed its confirmation. No initiating confirmation appeared.
The peer request was cancelled; BOND_NONE read back, both bonded counts **0 → 0**.
No bond existed to remove. Pairing therefore does not ship. Reproducible probe:
`spikes/pla434-bluetooth/`; output: `probe.txt` beside this file.

The private peer must share the assigned emulator's netsim endpoint; separate
netsim processes are isolated. Only these two owned AVDs were joined, and the
read-only peer was stopped after testing. The discovery-only UI cannot establish
a connection. API 30 reports nearby devices unavailable because this preview's
Location-free scan path requires API 31+. Empty/error pages use the approved U7
copy. Names and radio identities stay in memory, not saved logs or screenshots.

## Evidence

`app/tools/preview-32/evidence/` contains:

- `tests-first.txt`: new controller tests failed before their implementation.
- `host-checks.txt`: 17 Bluetooth JVM tests, 203 app JVM tests and 70 affected
  Paparazzi fixtures pass; all 55 changed/new images inspected at
  1.0/1.3/1.5/1.8/2.0. Three OpenSpec changes validate.
- `upgrade-gate.txt`: initial 31 → 32 upgrade, restart, Home after full readback,
  DO/TEST_ONLY/LOCKED, both exact-911 routes, zero calls.
- `install-readback.txt`: final artifact install/hash/version. The final delta
  avoids cancelling discovery when this adapter owns no receiver; its 17
  Bluetooth tests and build pass. No emergency/recovery code changed.
- `discovery-gate.txt`: final artifact finds one named peer at Location 0 without
  duplicates; no Pair button; discovery stops on leave/pause; Bluetooth off
  clears nearby names and removes the row; SCAN grant reads back.
- `final-emergency-gate.txt`: both exact-911 routes on the final artifact, zero
  calls, DO/TEST_ONLY/LOCKED retained. Dial fields cleared afterward.
- `baseline.txt`, `bond-counts.txt`: both emulator bonded counts remain zero;
  assigned emulator Bluetooth ON, Location restored to its original **3**,
  ringtone hash and saved-network hash unchanged. The spare's Location **0**
  is separate and must stay 0 during its gate.
- `permissions.txt`, `signer.txt`: final permission/signing readbacks.

Baseline lint remains separately reported: five AndroidWifi MissingPermission
errors and one PinAccessActivity StateFlow error. No check was disabled. No
changed Bluetooth-file lint error remains. `usesPermissionFlags` produces an
expected minSdk-30 warning; discovery is unavailable there, not a Location path.

Lead next: single opposite-model review, then in-place install over spare build
31. Open the new page and record nearby **counts only**; pair nothing (no approved
physical peer). Read back Bluetooth on, unchanged bonded count, Location 0 and
ringtone. Update release-notes case `preview-32` with spare install/readback.

## Spare install (Lead, sole driver)

- **Reviewed:** single opposite-model review by the Lead on PLA-434: approved, no blocking findings. Two deferred nits: receiver `RECEIVER_NOT_EXPORTED`; restart the search on resume instead of showing "No devices found".
- **Installed:** exact APK `a03b1e02…20f6eb` in place over spare build 31 at
  **2026-10-12 17:44:32Z** (`lastUpdateTime`). Gate exit 0, 244 PASS (`evidence/spare/gate.log`):
  version 32, Device Owner, TEST_ONLY and LOCKED kept; restart; More › Emergency and
  Phone both typed exactly **911**, **zero calls** (screenshots inspected).
- **Exercised:** More › Phone settings › Bluetooth showed **Pair a new device** (the
  later-test-build note was gone; Paired with: header present). The Pair page showed
  "Looking for nearby devices…" and then Look again after about 11 s. It listed **3** nearby
  rows on the first search and **4** on a second; each row is 160 px (about 64 dp) tall. A device
  screen showed "Pairing from LMNOPhone isn't ready yet." with **no Pair button**, and
  Back returned to the list, then to Bluetooth. Nothing was paired. Counts only;
  no names or radio identities were recorded (`evidence/spare/pair.txt`).
- **Readback** (`before.txt` / `after.txt`): Location **0**, Bluetooth ON, bonded
  count **0 → 0**, ringtone (original Moto URI), ring volume 5 and the saved-network
  count and hash are identical. BLUETOOTH_SCAN and BLUETOOTH_CONNECT are granted (POLICY_FIXED).
