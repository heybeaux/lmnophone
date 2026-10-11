# LMNOPhone preview 5 — draft tester notes

Build 5 uses the LMNOPhone name on the app label, notices, Wi-Fi explanation,
unfinished-feature explanation and shared-link text preview. The calm Home,
centered More control and optional missed-call/unread-text setting are unchanged.
An in-place upgrade keeps the existing setting.

## What's new

- More has no lines between rows.

## Try this

1. Confirm the LMNOPhone name where shown.
2. Open More > Phone settings and check that the optional count setting is kept.
3. Return Home and open Phone.
4. Restart, then check Home and the same count setting again.

No emergency call is part of this test.

## Known limits

- This is an experimental preview, not the complete MVP.
- The recovery fix is **deferred to build 6**. This build does not claim it is fixed.
- Counts remain in testing. They cover supported Phone missed calls and SMS/MMS
  unread counts; RCS and other apps are not supported. Unavailable data is hidden.
- Turning counts off keeps previously granted read permissions. Message contents
  are not read by the count feature.
- Unfinished actions show “Not in this test build yet”; they do not send messages
  or make the requested change. The shared-link text was inspected only.
- Long Home notices retain the existing ellipsis behavior at large text. Task
  bodies scroll. No layout redesign is included.
- Five established device unit-test failures remain; see evidence/verification.md.

## Delivery state

APK SHA-256: `f43b93978c8994bc25b44ec66bc8d40532a79cead43becd69fed87b3e048204c`.
Candidate source: `28506e3e0f077a49a90deb719d9fbae82cc1cfb4`.

Built and installed on the owned API 35 emulator, then installed in place on the
approved spare (moto g power 5G 2024, Android 15) on 2026-10-11. See below.

## Spare phone — in-place upgrade (PLA-337)

Evidence: `evidence/spare/`. Device written as `<spare>`.

**Installed.** `adb install -r -t` at 2026-10-11 16:30:22 UTC through
`app/tools/preview-5/upgrade-check.sh` (sole-driver opt-in). The APK SHA-256 and
signer `99edd5a3…11bd` were verified before install.

| Readback | Before | After install | After 2nd restart |
| --- | --- | --- | --- |
| Version | 4, `0.1.0-preview-4` | 5, `0.1.0-preview-5` | 5 |
| `firstInstallTime` | 2026-10-09 10:26:07 | unchanged | unchanged |
| Device Owner | `org.plainphone/.PlainphoneAdminReceiver` | kept | kept |
| Lock task | `LOCKED` | `LOCKED` | `LOCKED` |
| Count setting | on | on | on |
| `font_scale` | 1.0 | 1.0 | 1.0 (restored after the 2.0 checks) |

**Gate `exit=0`** (`upgrade-check.txt`): restart showed Home 0.43 s after the full
policy read-back. More > Emergency 9-1-1 prefilled exactly `911` and Phone typed
exactly `911`, with zero current Telecom calls each time. Call was never pressed.
The count setting stayed on for both routes.

**Exercised on the spare:**

- More has no lines between rows at text size 1.0 (`more-1.0.png`) or 2.0
  (`more-2.0.png`). At 2.0 every row and the About link fit on one screen; the
  screen does not scroll (after a swipe, `more-2.0-after-scroll.png` differs only in the status-bar clock). A pixel scan
  (`line-scan.txt`) finds only the `‹ Home` and `About this phone` underlines,
  which are kept.
- Home and More > Phone settings at 1.0 and 2.0, with the count setting on.
- A second restart returned to Home (`restart-home.png`), with the count setting
  still on.
- About this phone shows “Not in this test build yet”, as expected.

**Not exercised:**

- Counts. The spare has no working SIM, so no count can appear.
- The “LMNOPhone (preview)” label on the device. The checking screen ran behind
  the lock screen during the restart and was not captured (`restart-strip.png`),
  and no launcher shows the app label under lock task. The label is confirmed
  only in the APK (`aapt2 dump badging`).
- The recovery fix. It is deferred to build 6.

The phone was not reset, and its data was not cleared.
