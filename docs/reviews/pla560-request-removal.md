# PLA-560 request removal verification

Base: `718bb845` (`pla-copy-3/fixes`). Branch: `pla-requests/not-now`.
Tests were committed first in `0862d6e6`; the policy compile failed on the missing `remove` API before implementation.

## Implemented

- Not now opens the exact T3 confirmation, with Remove and Keep it. Failed or unconfirmed writes retain the request and show the existing save-error wording.
- Removal atomically replaces the saved text with an ID/day-only record. Daily quota and ID replay protection survive restart; late SMS results do nothing.
- Store schema 2 reads existing schema 1 without discarding requests or recipient data. Old binaries cannot read schema 2; downgrades must not be treated as a data migration.
- Production `NeedsYou.kt` and `PinScreenMapper` use a fresh readable saved-request count. Requests follows Waiting and Attention; singular/plural copy uses resources. Zero/unreadable counts disappear.
- Both sides use the same store. Every saved request can be reached, including entries past the former ten-row display cap. No Play Store placeholder, new status, notification, or Home change.
- The production phone-user name remains the existing neutral fallback because this preview has no configured phone-user identity source. Mappers and confirmations preserve a supplied name; synthetic long names appear only in tests.

## Host checks

JDK 17, Android SDK 36. Run from `app/`:

- `./gradlew :policy:test`: 677 passed.
- `./gradlew :device:testDebugUnitTest --tests '*RequestFileStoreTest'`: 5 passed.
- `./gradlew :plainphone:testDebugUnitTest`: 325 passed.
- `./gradlew :ui:verifyPaparazziDebug --tests '*RequestRemovalScreenshotTest' --tests '*RequestsScreenshotTest' --tests '*NeedsYouScreenshotTest'`: 73 passed.
- `./gradlew :plainphone:assembleDebug`: passed. No version bump; inherited version 53 is a local test artifact, not the next delivery number.
- `git diff --check`: passed.

The 16 new goldens cover 1.0, 1.3, 1.5 and 2.0 at 360×640 dp with a 280-character request and long name. The two changed existing history goldens plus all new images were visually inspected. Semantics checks exercise Not now, Remove and Keep it, scroll reachability, touch size and visible glyph bounds. Existing not-set-up/read-failure goldens remain passing. The visible-glyph check excludes alignment offsets and trailing whitespace, which are not clipped glyphs.

## Private emulator

Only `emulator-5586`, AVD `pla525-ui-a35`, started with `-read-only -port 5586 -no-window -no-audio -no-snapshot-save`. Airplane mode enabled; no SMS dispatched, no calls, no physical-device commands. No `connectedAndroidTest`.

Build instrumentation with `./gradlew :plainphone:assembleDebugAndroidTest -PpreviewTestRunner=org.plainphone.requests.RequestRemovalInstrumentation`.
Install both APKs using `adb -s emulator-5586 install -r -t`, set `wm size 360x640` and `wm density 160`, then run:

```
adb -s emulator-5586 shell am instrument -w org.plainphone.test/org.plainphone.requests.RequestRemovalInstrumentation
adb -s emulator-5586 shell am force-stop org.plainphone
adb -s emulator-5586 shell am instrument -w -e restart true org.plainphone.test/org.plainphone.requests.RequestRemovalInstrumentation
```

At font scales 1.0 and 2.0: PASS Keep it leaves the request; Remove clears it; the user list is clear; saved text is absent from the file; quota remains five; callbacks are ignored. Separate process restarts PASS absence and quota read-back. The early attempt encountered a cold-boot System UI ANR and then an offscreen test control; closing the emulator's ANR dialog and adding scrolling to the test driver resolved those test-environment failures. No app check was disabled.

Final debug APK SHA-256: `b62e7b329c324882902dfbd3c6a98529596f0f511b86fd5e20f07559487cc5d1`.

Built: yes. Installed/exercised: private emulator only. Spare installation, production menu taps and the usual emergency gate remain the sole driver's acceptance after focused review. No new emergency-access claim is made by these request tests.

Release note: `docs/releases/pending-requests-not-now.md`; driver incorporates it into `ReleaseNotes.kt` when numbering the build.

## Review correction R1

The focused review on PLA-561 accepted the implementation and requested only sentence-start
capitalization for the real neutral phone-user fallback. Added a dedicated resource:
“Remove this request? The person using this phone won't be told.” Configured names remain unchanged.
The flow copy and instrumentation expectation now match it.

The new Paparazzi assertion reads `current_phone_user` from production resources. It failed at
all four scales before the fix. After the fix, all 20 `RequestRemovalScreenshotTest` cases pass
verification; the four new fallback goldens were visually inspected. `:plainphone:assembleDebug`
and `git diff --check` also pass. Earlier policy/store/app and emulator evidence is retained;
no new device run is claimed for this copy-only delta.

Corrected APK SHA-256: `bc7a158fefc3430cb2096473365b769bb2cc0e213e11f36c56c4a029856e0377`.
The existing pending release-note file is unchanged. Return to PLA-561 for a delta check only;
PLA-562 is the Lead's existing build-56 delivery route after approval.
