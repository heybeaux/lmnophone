# PLA-458 requirement coverage

**THROWAWAY, emulator only.** This checklist is derived from the issue, not product acceptance. It preserves the original run in `evidence/`; later checks are labelled as host or emulator delta checks.

| Requirement | Implementation / evidence | Limit or edge case |
|---|---|---|
| Own assigned worktree and branch, based on preview-35 | `spike/voice-mms`, base `fbb58cd0`, spike-only files | No production-module changes; no sibling worktree writes or fetch |
| Two-hour timebox, report partial results | Original probe and findings completed within the timebox | No new review child or approval chain |
| Private API 35 emulator, read-only 59xx port | `pla458-a35`, original 5960 and correction 5984; cleanup recorded | Helper rejects non-59xx serials and mismatched ADB AVD names; caller must still establish ownership |
| No physical phone, real messages, emergency calls or personal data | Synthetic tone; fictional 555-01xx recipients; loopback MMSC | Apps reject non-goldfish/ranchu hardware; recipient parameters cannot expand scope |
| Probe must not take SMS role | `probe/AndroidManifest.xml`; original `env` readback | Seeder alone temporarily holds role, explicitly checks it, and Messages is restored |
| Google Messages default; no account/new family app | Original `env` log; Messages used without account | No real family recipient was exercised; RCS was not configured |
| A: SEND_SMS + sendMultimediaMessage without role | `Probe.sendA`; broker source; original callback logs | Permission denied throws; app-op/profile rejection may return with no callback |
| A: one audio/amr m-send-req PDU | `VoiceMms.sendReq`; 8099-byte PDU with 8006-byte AMR part at fake MMSC | Minimal multipart/mixed, no SMIL; carrier/interoperability still untested |
| Later Lead priority: AAC `.m4a` / `audio/mp4` and a minimal AOSP `mmslib` subset | Partial, not implemented or exercised; original hand-written AMR encoder retained | No AAC encoding, transport, persistence or playback evidence; bitrate estimates do not satisfy this variant |
| A: licence recorded | Original encoder, Apache-2.0 project source headers | No third-party encoder library |
| A: actual callback and reason on emulator | Disabled MMS: 12; unreachable MMSC: 4; loopback: RESULT_OK | Result 12 depends on platform feature flag; no real carrier result |
| A: sent message persisted, visible in Messages | `a3`, `a4`, `logcat-pla458.log`; `SendRequest.persistIfRequired` | Failed row is transiently SENT then hidden; use callback, not box alone |
| B: SEND and SENDTO with AMR stream/read grant | `Probe.sendB`, ClipData; `b1`, `b2`, `b4`, `b5` | Uses dependency-free FileProvider-style provider, not AndroidX FileProvider |
| B: recipient prefilled / manual Send | Fresh SENDTO stages 5-second audio at fictional recipient; `b2`, `b3` | Address extra on SEND and warm Messages task lost attachment; no guaranteed universal shape |
| B: chooser / share route | `b4` conversation picker; `b5` system chooser | System chooser exposes unrelated apps; never use it as an approved product route |
| B: outcome with READ_SMS observer | Observed SENT row then response status 128 | No reliable failure/cancel callback; SENT alone is not acceptance; timeout is unknown |
| B: stays in lock task | `b6` blocked when not listed; `b7` listed and LOCKED | Floor visibility is not lock-task permission; enabling Messages is a separate policy decision |
| Receive: enumerate incoming MMS with READ_SMS | Seeded inbox m-retrieve-conf; `list`, original observer log | Notification-ind excluded, so no item before Messages downloads it |
| Receive: open/play audio part URI | Original part 6, 6406 bytes, 4000 ms, playback position advances | Revised play scopes to an inbox message, not global newest audio; no inbox/audio or permission loss fails explicitly |
| Receive: seed with role app then restore | `Seeder`, original shell role readback | Role can be absent despite app appearing installed; seeder now refuses writes without role |
| Size: both carrier-config APIs | Original config log: 1048576, MMS disabled, attach audio allowed | Emulator defaults do not establish a SIM carrier's real limit |
| Size: AMR-NB 12.2 / AAC-LC 24 mono at all budgets | Findings includes decimal KB/MB and binary KiB/MiB table and formula | AMR file framing included; AAC is estimate before container/variable encoder overhead |
| Durable findings and evidence, commit/push/comment/done | Original `2e8d9ee5`; correction checkpoint on same branch | Original completed mutations are not replayed; correction is a distinct delta |
| Honest built/installed/exercised distinction | Host tests and build result recorded separately from original emulator evidence | No APK build is claimed to prove carrier delivery, physical device install or iPhone playback |

## Corrections made after checking coverage

- Removed `javac ... || true`: a failing compiler previously advanced to d8; the regression test demonstrates failure before the fix and exit 42 afterward.
- Added emulator/fixture/duration guards and read-only shared-file scope. Exported throwaway activities cannot accidentally send to arbitrary recipients or seed a physical phone.
- Restricted playback to a downloaded inbox message, and made role absence a clear seeding failure. These prevent an outgoing audio part or failed insert from being mistaken for receiving evidence.
- Corrected SENT/timeout conclusions, labelled FileProvider substitution and AAC estimation, made size units explicit, and replaced non-executable reproduction placeholders with permission/role steps.
- Added pinned primary-source pointers and preserved original screenshots and lab results rather than re-recording them to imply a different result.

## Host checks

From the repository root, with `PAPERCLIP_RUN_SCRATCH_DIR` set to the run-owned scratch directory:

```sh
python3 -B -m unittest discover -s spikes/voice-mms/tests -p 'test_*.py'
mkdir -p "$PAPERCLIP_RUN_SCRATCH_DIR/voice-mms-tests"
javac -d "$PAPERCLIP_RUN_SCRATCH_DIR/voice-mms-tests" spikes/voice-mms/common/SpikeSafety.java spikes/voice-mms/tests/SpikeSafetyTest.java
java -cp "$PAPERCLIP_RUN_SCRATCH_DIR/voice-mms-tests" org.plainphone.spike458.SpikeSafetyTest
bash -n spikes/voice-mms/build.sh
spikes/voice-mms/build.sh
git diff --check
```

No production voice flow, recording UI, automatic retry, group MMS support, RCS integration, carrier transport or iPhone playback is claimed. Those are outside this throwaway evidence.
