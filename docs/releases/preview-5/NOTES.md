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

Built and installed on the owned API 35 emulator. Physical-device installation
and exercise are pending the Lead’s review and the assigned driver.

## TODO(spare) — assigned driver

- Record install time, exact APK hash and version readback after an in-place upgrade.
- Confirm first-install time, Device Owner, lock task and count setting are kept.
- Check Home, Phone and restart. Preserve the no-call rule for emergency checks.
- Add the tester’s observations and distinguish installed from exercised.
- Do not reset the phone or clear its data for this preview.
