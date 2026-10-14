# PLA-527: one correction batch after the focused review

Base for this delta: `c1a55284`. Tests-first checkpoint: `634b8933`.
Review remains on PLA-536; no second review issue or new acceptance chain.

## Reproduced before changing production code

Four additional host tests compiled and failed on `c1a55284`:

1. An unreadable first clip prevented the next readable clip from playing.
2. Removing/off during pending narration still started the cached clip.
3. Removing/off between clips still started the next cached clip.
4. Refresh retained a removed person's cached Listen queue.

The same reviewer independently requested correction of item 1, including keeping
focus refusal/interruption distinct from an unreadable part.

## Behavior and interfaces checked

- V1/V2/V12a copy, order, symbols, one spoken New message announcement per person,
  production mapper flags, no autoplay, and same-person Record remain unchanged.
- Sender matching still uses current configured family numbers, normalized digits
  or equal E.164; strangers, empty/off lists and nonaudio MMS are excluded. No RCS
  API or new notification is added; Messages remains the fallback.
- Audio parts are checked read-only for readable, nonempty bytes before offering
  them. An unreadable first part cannot hide a later readable part of that message.
  An unsupported/damaged clip at playback is skipped for this visit without being
  marked heard. The next clip plays; all-failed removes Listen; after a successful
  clip, a final failed clip keeps Play again for the last actually heard clip and
  the normal end prompt. Focus refusal and interruptions do not consume the queue.
- Oldest-first, duplicate suppression and latest five per person still apply.
  The adapter no longer discards family replies behind 100 unrelated inbox rows.
- `VoiceReplies.canPlay(personId, clip)` is an additive overload with a default for
  existing implementations. The real book rechecks the current sender/part at each
  start, after narration and at completion, including replay. Number changes,
  removed people, off and missing parts invalidate a cached clip. Refresh never
  starts an idle queue, and clears/stops a removed/off queue.
- Played-state stays opaque and private/no-backup; only completed valid clips are
  marked. Existing erase-on-remove/off/number-change covers drafts, send records and
  played-state. No provider writes, sends, microphone use by listening, emergency
  access changes or lock-task changes are introduced.
- VoicePerson diagnostics now redact configured names, including nested voice-state
  `toString`. No names, phone numbers or message bodies are logged by these paths.
  `docs/mvp/lanes.md` records the additive seam.

## Built and host checked

- `:device:test`: debug **1143**, release **1143**, zero failures/errors.
- `:plainphone:testDebugUnitTest`: **290**, zero failures/errors.
- Final changed-path checks: **58** device tests and **4** production mapper tests,
  zero failures. Seven boundary tests include the four original reproductions,
  removal while playing, final-failed/last-heard replay and diagnostic redaction.
- Debug APK and instrumentation APK build. APK SHA-256:
  `c52c606f30f65016f37dc50cb13db2bd1a89b234e3a1d37d88b9f3b5d4a2fd68`.
- Lint was run, not disabled: two errors in unchanged AndroidWifi/AndroidWifiForget
  permission handling, plus six existing plainphone errors (five API-31 telephony
  calls with minSdk 30 and one PinAccessActivity StateFlow composition read). None
  is in the changed logic. Lint is **not passing**. Kotlin compilation is passing.
- No UI source or goldens change; reuse the reviewer's FamilyScreenshot 150 pass and
  the documented eleven unrelated Branding/PreviewTurnOff failures.

## Installed and exercised: emulator only

Fresh private read-only `pla527-a35`, port 5578 checked free and AVD identity read
back before mutations. Same synthetic two AAC inbox messages as the original proof;
SMS role restored to Google Messages and seeder removed before LMNOPhone runs.

The corrected APK was installed in place; installed base.apk SHA-256 matches the
build above. PASS at font scales 1.0 and 2.0: Sam only New message, Listen with no
autoplay, MediaPlayer started, Play again, Back stops, remove erases both records.
See `delta-steps-1.0.txt`, `delta-steps-2.0.txt`, and the two `delta-screens-*.png`
contact sheets. Every screen was checked: synthetic Sam only, no numbers.

The first cold probe timed out waiting for Home; no app crash was logged. Explicit
shell launch of MainActivity before rerunning the unchanged probe removed that
harness startup dependency. Both final runs passed on the hash above.

Cleanup read back no owners, test APK removed, SMS role Google Messages, font scale
1.0; read-only emulator killed. No physical phone/spare, sends or calls.

## Still not demonstrated

Over-the-air MMS, RCS access, human verification of speech, or emulator incoming-call
and screen-off interruption. Existing host interruption tests pass. No spare install
or public release is claimed. Base stays preview-47; Lead owns current-preview merge,
new build number and truthful release notes.
