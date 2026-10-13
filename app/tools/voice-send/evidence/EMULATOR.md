# PLA-499 emulator proof: real MMS send engine

Emulator only. Private read-only AVD `pla458-a35` (API 35 google_apis arm64), `-read-only -port 5986`; port
checked free first, identity read back with `emu avd name` and `ro.boot.qemu.avd_name` before every mutation.
Every adb call was pinned with `-s emulator-5986`. `adb root` was used only on this emulator, for
`cmd phone cc set-value`. The recipient was a fictional 555-01xx number set through the shell-only TestHook
`VOICE_SEND`; it is not written here. No physical phone, no real message, no emergency number.

## Built

Branch `pla-voice/send`, debug `plainphone-debug.apk` (versionName 0.1.0-preview-43), sha256
`2afa928a5ee2b4d69fcd15c08e774897b9eeb2c6fa96804e6af1dc68293b5bad`. Test APK built with
`-PpreviewTestRunner=org.plainphone.voice.VoiceSendInstrumentation`.

## Installed

`install -r -t` (no `-g`), `dpm set-device-owner`. Before the first Send, SEND_SMS read back
`granted=false, flags=[RESTRICTION_INSTALLER_EXEMPT]`; after it, `granted=true, flags=[POLICY_FIXED|...|RESTRICTION_INSTALLER_EXEMPT]`
(the Device Owner grant in `AndroidMmsPort.permitted()`, decided by readback).

## Exercised

`am instrument -w org.plainphone.test/org.plainphone.voice.VoiceSendInstrumentation`: records a ~3 s take for Sam
with the real recorder, then calls the Review screen's Send handler once. Outcome lines are in `case-*.txt`.

| Case | Setup | Screen | Stored | Draft | `content://mms` |
|---|---|---|---|---|---|
| MMS off | stock carrier config (`enabledMMS=false`) | Review (Send offered, no practice band) -> Not sent | `NotSent(MmsOff)`, failures 1 | kept | no row |
| Sent | APN 3492 mmsc -> loopback fake MMSC `http://10.0.2.2:59580/mms`, `cc set-value enabledMMS true` | Sending -> Sent | `Sent` | deleted | `_id=1 msg_box=2 (sent) m_type=128 resp_st=128 creator=org.plainphone`, part `audio/mp4 voice.m4a` |
| Unreachable MMSC | fake MMSC stopped, APN unchanged | Sending -> Not sent (about 20 s) | `NotSent(OtherError)`, failures 1 | kept | still only `_id=1` |

The fake MMSC received one m-send-req of 11670 bytes: MMS 1.2, one part `audio/mp4` `voice.m4a` of 11536 bytes,
the same size as the take; delivery and read reports off.

With MMS off, LMNOPhone reads `enabledMMS=false` from `getCarrierConfigValues()` and answers Not sent before handing
anything to the MMS service, so the platform's result 12 is not reached on this path; `MmsSendResult.map(12)` is
covered by unit tests. The logcat buffer had rolled before the unreachable case's platform code could be read; the
PLA-458 spike recorded 4 for the same setup, which maps to OtherError. The fictional number never appeared in logcat.

## Cleanup (read back, `cleanup.txt`)

`cc clear-values` (`enabledMMS` false), APN 3492 mmsc back to its stock value, VOICE_SEND marker removed,
`remove-active-admin`, both APKs uninstalled, `dpm list-owners`: no owners, then `emu kill` (read-only AVD).

## Not shown

Real carrier MMSC acceptance, delivery or playback on the other phone, and the spare install. Those are the
separate integration step.
