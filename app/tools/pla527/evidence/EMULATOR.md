# PLA-527 emulator proof: hearing family MMS voice replies

Emulator only. Private read-only AVD `pla527-a35` (API 35 google_apis arm64, hardware `ranchu`),
`-read-only -port 5578`. The port was checked free first, and identity was read back with `emu avd name` and
`ro.boot.qemu.avd_name` before every mutation. Every adb call was pinned with `-s emulator-5578`. No physical
phone, no spare, no `connectedAndroidTest`. Nothing was sent, nobody was called, and no emergency number was used.
Fictional data only: Sam 555-0100 (on the family list) and a stranger 555-0199 (not on it).

## Built

Branch `pla-voice/replies` on `release/preview-47` (b9642d3c). Debug `plainphone-debug.apk` built at 52b9f0a0
(versionName still 0.1.0-preview-47, versionCode 47; the build number is assigned at install), sha256
`206f78335b1d4920c9ed2d9452f2abdd88ae9e341be75583b321d451ddb9ff0f`. The installed `base.apk` read back the same
sha256. Test APK built with
`./gradlew :plainphone:assembleDebugAndroidTest -PpreviewTestRunner=org.plainphone.voice.VoiceRepliesInstrumentation`.

## Seeded

A throwaway copy of the PLA-458 spike seeder (scratch only, not committed) briefly held the SMS role and wrote two
inbox MMS rows straight into the provider, each with one `audio/mp4` part `voice.m4a`:
`_id=1` from 5550100 (Sam) and `_id=2` from 5550199 (stranger), both `msg_box=1`. The SMS role was then given back to
`com.google.android.apps.messaging` (readback) and the seeder was uninstalled. LMNOPhone never holds the SMS role and
never writes to the provider.

## Installed

`app/tools/preview-2/setup.sh emulator-5578 <apk>`: readback Device Owner (`dpm list-owners`: `org.plainphone`,
DeviceOwner), locked, `DISALLOW_DEBUGGING_FEATURES` withheld, `TEST_ONLY`.
READ_SMS before the first read: `granted=false, flags=[RESTRICTION_INSTALLER_EXEMPT]`. After it:
`granted=true, flags=[POLICY_FIXED|...|RESTRICTION_INSTALLER_EXEMPT]`. This is the Device Owner grant in
`AndroidMmsInbox.permitted()`, decided by readback (the same pattern as the SEND_SMS grant in `AndroidMmsPort`).

## Exercised

`am instrument -w -e label <scale> org.plainphone.test/org.plainphone.voice.VoiceRepliesInstrumentation`, at system
text size 1.0 and 2.0 (`settings put system font_scale`). The probe drives the real Home, Family list and person
page through accessibility. It never taps Record, Send or Messages. Both runs: `outcome=PASS`
(`steps-1.0.txt`, `steps-2.0.txt`).

| Step | Check | Screenshot |
|---|---|---|
| Read | `AndroidMmsInbox` returns 2 audio messages (Sam and stranger) | – |
| V1 | Only card: `Sam, new message` (▶ New message); no card for the stranger | `replies-<s>-1-people-new.png` |
| V2 | `▶ Listen to Sam` between `Messages with Sam` and `‹ Back`; nothing playing on open | `replies-<s>-2-person-listen.png` |
| V12a playing | `■ Stop listening`; `AudioPlaybackConfiguration ... MediaPlayer ... state:started usage=USAGE_MEDIA content=CONTENT_TYPE_SPEECH` owned by LMNOPhone's uid | `replies-<s>-3-listening.png` |
| V12a end | `▶ Play again`; played-state holds exactly 1 opaque 32-hex key, no number | `replies-<s>-4-play-again.png` |
| Leave | Play again, then Back while playing: the app player stops; Sam's card is no longer New message | `replies-<s>-5-people-heard.png` |
| Remove | Remove Sam through the production family store: `voice-replies-played/testsam.played` and `voice-send-family/testsam.send` are gone, the played directory is empty, and Home no longer offers Family | `replies-<s>-6-after-remove.png` |

At 1.0 the first audio track reported `channelMask=0x0, sampleRate=0` at the moment of readback (the decoder had not
yet reported its format). At 2.0 it reported `channelMask=0x1, sampleRate=16000`. In both, the state was `started`.

Logcat (37,246 lines, whole session) contains neither 5550100/555-0100 nor 5550199/555-0199. The only `+1555`
lines are the emulator's own SIM line in the system Settings process. Every screenshot was checked: the only name
shown is the fictional Sam, and no number appears.

## Cleanup

`dpm remove-active-admin`, `org.plainphone.test` uninstalled, SMS role read back as Google Messages, then `emu kill`
(read-only AVD, so the seeded rows are discarded).

## Not shown

- An MMS delivered over the air from a real carrier. Rows were seeded straight into the provider, as in PLA-458.
- RCS replies. No public API can read them; they stay in Google Messages ("Messages with {person}").
- The spoken prompts as audio (TalkBack was not on). Their text is covered by `VoiceWords` host tests.
- Incoming-call and screen-off stops on the device. These are covered by host tests of the controller only.
- The spare install. The Lead routes that.
