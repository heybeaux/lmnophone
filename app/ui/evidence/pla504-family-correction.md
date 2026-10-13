# Family correction pass (PLA-504)

Base: `2bcaee5b`, branch `pla-voice/family`. Copy source:
`6c250b7d:docs/ux/flows-voice-messages.md` (PLA-501), plus the VT2 strings
quoted in PLA-504 from the accepted PLA-502 review.

- Replaced all family TODO(copy) strings, including the named Messages action,
  its TalkBack label, failed-open words, remove warning/button, save error and
  edit confirmation. V2's spoken prompt now explains Messages.
- Failed or throwing microphone grants show the approved microphone error,
  preserve disabled storage, and clear the error after a successful retry.
  The preview supplies its existing fictional helper, Sam.
- Family V2 orders picture, name, Messages, Back, Record. Back and Record stay
  pinned; Messages scrolls above them. The picture retains Hear name; the
  duplicate visual Hear name link is omitted on family V2.
- Name, spoken-name and number entry events redact `toString()`.
- The opening prompt finishes before the existing Messages callback runs.
  Leaving/stopping or another speech action cancels pending navigation; stale
  completion and timeout callbacks cannot open twice. Unavailable speech falls
  through, with a five-second backstop for a stalled TTS engine.

## Verification

Tests-first commit: `f7ecf270`. Before implementation, all 10 V2 order checks
failed with “Picture and name must precede Back”, the entry-event test exposed
its value, and microphone tests failed to compile because `micFailed` did not
exist. The later speech-completion tests also failed to compile before their
one-shot helper existed.

Commands run from `app/`:

- `./gradlew :ui:verifyPaparazziDebug --tests '*FamilyScreenshotTest*' --tests '*VoicePracticeScreenshotTest*'`: 335 passed.
- `./gradlew :ui:testDebugUnitTest --tests '*FamilyEventTest'`: passed (also run with the initial green family checks).
- `./gradlew :plainphone:testDebugUnitTest --tests '*voice.*' :device:testDebugUnitTest --tests '*voice.*' :plainphone:assembleDebug :plainphone:assembleDebugAndroidTest`: 17 host tests and 92 device-module JVM tests passed; both APKs built.
- `git diff --check`: passed.

Family screenshots retain 360×640 dp with system-bar space, scales
1.0/1.3/1.5/1.8/2.0 × Sam / Sam Jo Alex Sam Jo Alex. Re-recorded only
messagesAction and remove (20 images); added editConfirm and microphoneFailure
(20 images). Inspected all 40 images. The long-name Messages button initially
needs scrolling at 1.8/2.0; semantics assertions check order, button height,
scroll range sufficient to reveal the entire button, viewport separation from
Record, and the fully visible pinned Record target. Unchanged practice goldens
were verified, not re-recorded.

Built only: existing versionCode 43 / 0.1.0-preview-43. Debug APK SHA-256:
`64eccd149899f3fc79b838b3f192264e836aee27c85580610708b3608a389f9e`.
No emulator/physical install or instrumented execution in this correction pass;
no claim of actual TTS playback or Messages launch on a device. The existing
synthetic Messages instrumentation compiles with the new formatted label.

The main-thread `familyStore.read()` finding remains deferred by the Lead.
The sole driver owns the sibling build/install; this branch does not bump or
reuse the installed preview number as a delivery claim. One UX delta check
covers this pass; the author does not approve it.
