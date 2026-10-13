# PLA-499 continuation: requirements and edge checks

The original transport proof in `EMULATOR.md` is retained. This continuation
adds tests and corrects send-state handling; it does not install on a physical
phone, integrate the family list, or claim recipient receipt/playback.

## Required behavior and evidence

| Behavior / boundary | Check |
|---|---|
| One AAC `.m4a` / `audio/mp4` part, one normalized To address, no text/SMIL | `VoiceMmsPduTest`: encoded-byte round trip, international formatting, invalid/empty recipient, empty audio, maximum header overhead |
| Minimal vendored encoder with attribution | Original Apache headers and modification notes in `voice/mms`; root `NOTICE` |
| App-private PDU, read-only per-attempt URI, non-exported receiver/provider | `VoiceSendPermissionTest`; original emulator handoff proof |
| Default data subscription; no result inferred at handoff | `AndroidMmsPort`; `VoiceSenderTest` persisted-Sending-at-handoff test |
| Sent only for RESULT_OK and a complete OK m-send-conf | `MmsSendResultTest`: all result codes, missing/refused/malformed confirmation, complete optional and application-extension headers |
| Malformed tails, conflicting headers, missing mandatory headers cannot produce Sent | New damaged-confirmation table; no early return after the OK byte |
| No signal, MMS off, too big, missing permission and transport failure keep the draft | `VoiceSenderTest`; original MMS-off/unreachable-MMSC emulator cases |
| A permission read or send permission exception does not crash | New permission-read exception test plus existing handoff SecurityException test |
| Signal preflight never requests or grants Location | Explicit existing-grant checks in `AndroidMmsPort.hasSignal`; unreadable signal returns null; unknown-signal engine test still hands off once |
| Actual recorder bitrate, container/header budget and maximum 120 seconds | `VoiceSendLimitsTest`, controller cap tests, `AndroidVoiceRecorder` settings |
| Unknown, zero, negative or too-small carrier budget | Limit tests; final PDU size check refuses TooBig without truncation; zero is never passed as MediaRecorder's unlimited duration |
| Send only on Review Send or Not sent Try again | Exhaustive state/event table in `VoiceSendFlowTest`; build/person gating tests |
| Duplicate Send while an attempt is pending never makes a second handoff | `VoiceSenderTest.aSecondSendWhileSendingKeepsTheOneAttempt` |
| Process restart / overdue unanswered send is Not confirmed, never Sent | Sender restart/wait-bound tests; restore does not send |
| Reopening an unresolved attempt retains Not confirmed and Try again | New repeat-visit reducer test, even after the outcome was seen |
| A late actual result updates the visible Not confirmed screen | New reducer test covers Sent, NoSignal, repeated identical answer and another person's answer, with no send effect |
| A result arriving during handoff is not overwritten by Sending | New synchronous-result sender test |
| Stale, duplicate, conflicting and other-person callbacks cannot overwrite the latest attempt | Existing sender result-identity tests |
| Confirmed Sent deletes only the matching draft | Digest-based deletion tests, including a newer recording in the same millisecond |
| Restart between persisting Sent and deleting its draft completes cleanup | New persisted-Sent cleanup test; reading never sends |
| Persistence failure prevents handoff; damaged records never imply Sent | Existing store failure and damaged-record tests |
| Phone numbers are absent from records/logs/evidence | Redacted `VoicePerson.toString`, record privacy test, fictional test recipients, original evidence sweep |
| Send is gated by both recipient number and build gate; practice label stays when off | Controller and mapper tests; release gate remains off |
| No emergency-floor, lock-task or Messages/RCS changes | Branch scope; no policy changes; family integration remains slice 2b |

## Corrections and validation

Tests-first commit `de6829af`: 58 targeted tests, six new failing regressions.
After correction the same 58 pass. An additional application-extension regression
test first failed, then passed: complete extension headers before or after the
status are accepted, but a truncated extension is not. Full device tests: 1032 pass; plainphone
tests: 252 pass. Debug assembly and release Kotlin compilation pass.

`device:lintDebug` initially found two new voice errors at the signal read.
The adapter now checks the existing READ_PHONE_STATE/coarse-Location grants,
handles permission revocation, and returns unknown without either grant. The
manifest pairs coarse Location with the already-declared fine Location; this
send path never requests or grants either Location permission.

Two remaining lint errors (AndroidWifi and AndroidWifiForget) and four warnings
are unchanged from `d0d4d14c`. Confirmed by running the same offline lint task
on a clean `git archive` of that commit in the run-owned scratch directory.
No lint suppression, baseline regeneration or unrelated Wi-Fi change is made.

UI code and goldens are unchanged in this continuation. The earlier 11
Branding/PreviewTurnOff screenshot failures remain separately established on
the delivered base. Original emulator evidence is reused, not reported as a
new install or a real carrier test. The same review issue, PLA-508, covers the
updated branch range; this is not another review gate.

Parser reference: AOSP Messaging `android-15.0.0_r1`,
`src/com/android/messaging/mmslib/pdu/PduParser.java`,
`checkMandatoryHeader` (MMS-Version, Transaction-Id and Response-Status).
Signal reference: Android API 36 `TelephonyManager.getServiceState()` contract.
