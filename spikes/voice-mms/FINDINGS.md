# PLA-458 spike: voice messages over carrier MMS without the SMS role

> **THROWAWAY.** Feasibility spike, emulator only. Nothing here ships. Do not import from `spikes/`.

- Emulator: private AVD `pla458-a35`, API 35 `google_apis` arm64 userdebug (`AE3A.240806.043`), run as `emulator-5960` with `-read-only`.
- Default SMS app: Google Messages `messages.android_20240528_00_RC01`, set up with no account. RCS was not set up.
- Probe app `org.plainphone.spike458.probe`: SEND_SMS, READ_SMS and READ_PHONE_STATE only. It does **not** hold the SMS role (`env`: `probeHoldsSmsRole=false defaultSms=com.google.android.apps.messaging`).
- Only fictional numbers were used (555-0100, 555-0101). No real messages were sent. The emulator has no real MMSC, so "sent" means delivered to a loopback fake MMSC (`tools/fake_mmsc.py`). No emergency numbers. No physical phones.
- AOSP citations are from tag `android-15.0.0_r1`.
- Requirement-by-requirement coverage and limitations: `REQUIREMENTS.md`. These results are a feasibility spike, not product acceptance or evidence of real recipient delivery.
- Correction verification: `evidence/DELTA-CHECKS.md` and `evidence/delta-5984.log`, labelled separately from the original run.
- **AAC follow-up (the Lead's later priority):** `evidence/AAC-CHECKS.md`, run on the same private AVD as `emulator-5986`. A vendored AOSP `mmslib` subset builds an m-send-req with one AAC `.m4a` part labelled `audio/mp4`; the probe (no SMS role) sends it, the system writes it to `content://mms/sent`, Google Messages shows it as an audio attachment, and a seeded incoming AAC part plays with READ_SMS only. Still emulator-only: no real MMSC, no real recipient, and the AAC table below now uses the measured container overhead but remains an estimate of carrier limits.

## Answers

### 1. Send path A: our app sends the MMS itself (`SmsManager.sendMultimediaMessage`). **Works for the AMR and AAC fixtures, no role needed.**

- **What is enforced:** `frameworks/base/services/core/java/com/android/server/MmsServiceBroker.java` `sendMessage()` (around l.336–360) checks:
  - `enforceCallingPermission(SEND_SMS)`;
  - `TelephonyPermissions.checkSubscriptionAssociatedWithUser`;
  - `noteOp(OP_SEND_SMS)` == MODE_ALLOWED.
  - There is **no SMS-role or default-app check.**
  - Permission denial throws; a disallowed SEND_SMS app-op or active subscription/user mismatch returns without calling the send service or its callback. A timeout therefore means **unknown**, not a confirmed transport failure. These denied paths are source-backed, not additional emulator runs.
  - Probe app-ops: SEND_SMS allow, READ_SMS allow, WRITE_SMS ignore.
- **Carrier gate:** `packages/services/Mms/src/com/android/mms/service/MmsService.java` l.258 rejects the send before any network work if the MMS config has `enabledMMS=false`. It returns `MMS_ERROR_MMS_DISABLED_BY_CARRIER` (12). The emulator's carrier config has `enabledMMS=false`.
- **AAC via `mmslib` (`evidence/AAC-CHECKS.md`):** `probe/MmsLibSend.java` builds a `SendReq` with the vendored AOSP `mmslib` (`PduComposer`): multipart/mixed, no SMIL, one `audio/mp4` part `voice.m4a` (5 s synthetic AAC-LC 24 kbps mono, 18209 B). Stock config: `resultCode=12`, no row. With `enabledMMS=true` and the fake MMSC: RESULT_OK, `uri=content://mms/sent/1`, row msg_box 2 SENT, `resp_st=128`, `creator` = probe, part `ct=audio/mp4`. The fake MMSC received an 18344-byte PDU whose part sha256 (`54afb889…`) equals the encoder output. Google Messages lists "You: Audio clip" and shows an audio attachment of 00:04 with a play button; playback inside Messages was not proved.
- **`mmslib` source and licence:** copied from `platform/packages/apps/Messaging` tag `android-15.0.0_r1`, `src/com/android/messaging/mmslib/`. Apache-2.0, original Esmertec AG / The Android Open Source Project headers kept, each file marked as modified. Files: `InvalidHeaderValueException`, `MmsException`, and `pdu/{CharacterSets, EncodedStringValue, GenericPdu, MultimediaMessagePdu, PduBody, PduComposer, PduContentTypes, PduHeaders, PduPart, SendReq}`. Changes: package renamed to `org.plainphone.spike458.mmslib`, `androidx.collection.SimpleArrayMap` replaced by `java.util.HashMap`. `audio/mp4` is not in the AOSP well-known content-type table, so `PduComposer` writes it as a text string. The Lead's note says Klinker android-smsmms was archived in March 2026 (not re-checked here).
- **PDU (AMR, original run):** a hand-written m-send-req encoder (`common/VoiceMms.java#sendReq`, about 50 lines). It produces one `multipart/mixed` part, `audio/amr`, From `insert-address-token`, To `<n>/TYPE=PLMN`. **No third-party library, so no licence question.** The audio is real AMR-NB 12.2 kbps from MediaCodec on a synthetic tone (no microphone).
- **Runs** (`evidence/logcat-pla458.log`, `evidence/logcat-system-mms.log`, `evidence/fake-mmsc.log`):

| Run | Setup | sentIntent | Provider row | Messages UI |
|---|---|---|---|---|
| A1 | as shipped | `resultCode=12` (MMS disabled by carrier), immediately | none | nothing |
| A2 | `configOverrides{enabledMMS=true}`, real emulator APN (MMSC address not reachable) | `resultCode=4` `MMS_ERROR_HTTP_FAILURE` after about 3 min (3 MMS network requests of about 60 s each), `uri=content://mms/sent/1` | written as SENT, then moved to FAILED (msg_box 5) | "You sent audio · Trouble sending" with playable audio (`evidence/a3-…png`) |
| A3 | override on, APN mmsc pointed at the fake MMSC (`10.0.2.2:59580`) | `resultCode=-1` RESULT_OK, `uri=content://mms/sent/2`, `MMS_DATA=byte[33]` (m-send-conf) | msg_box 2 SENT, `resp_st=128`, `creator=org.plainphone.spike458.probe` | "3:17 PM • MMS" with playable audio (`evidence/a4-…png`) |
| A4 | **no override**; carrier config set to `enabledMMS=true` (`cmd phone cc set-value`, test only), fake MMSC | RESULT_OK, `uri=content://mms/sent/5` | SENT | shown |

  - The fake MMSC received our exact 8099-byte PDU. The audio part is 8006 bytes, sha256 `798b971b…`, the same as the encoder output.
- **Persistence:** the system writes the message for us. `SendRequest.persistIfRequired` (`packages/services/Mms/.../SendRequest.java` l.180–250) inserts into `Mms.Sent` and marks it `MESSAGE_BOX_FAILED` on error, whenever `SmsApplication.shouldWriteMessageForPackageAsUser(creator)` is true. That method is `!isDefaultSmsApplication`, so it is true for any non-default app. Google Messages syncs the row into its conversation.
- **Gotcha: our app cannot see its own failures.**
  - A non-default app reads the `pdu_restricted` view (`MmsProvider.java` l.121, `ProviderUtil.isAccessRestricted`). Per `MmsSmsDatabaseHelper.java` l.899–906, that view holds only INBOX or SENT rows and excludes m-notification-ind.
  - So FAILED and OUTBOX rows **vanish** from our queries. Observed: the row for A2 went from visible (SENT) to `rows=0`.
  - **Outcome detection for path A must use the sentIntent result code**, not the provider.
  - Separately, `content://mms/part` is *not* filtered: our app can list parts of hidden messages (`cmd parts` saw part 2 of the FAILED mid 1). That's a side note, not something to rely on.

### 2. Send path B: hand off to Google Messages. **Works, but the user must press Send, and the intent shape matters.**

| Intent | Result | Evidence |
|---|---|---|
| `ACTION_SENDTO smsto:5550101` + `EXTRA_STREAM` (content URI, ClipData, `FLAG_GRANT_READ_URI_PERMISSION`), `setPackage(Messages)` | **Recipient pre-filled and audio staged** (5 s preview, "Remove", "Send MMS" button). **Nothing is sent until the user taps Send.** | `b2-sendto-new-recipient.png`, `b7-…` |
| `ACTION_SEND type=audio/amr` + `EXTRA_STREAM` + extra `address` | Messages opens the existing 555-0100 thread with **nothing staged**: the attachment was dropped and the draft was lost | `b1-…nothing-staged.png` |
| `ACTION_SEND` without an address | "Select conversation" picker; the user must choose | `b4-action-send-no-address.png` |
| `Intent.createChooser` | System share sheet showing Quick Share, Drive, Bluetooth, Gmail. **Not acceptable**: it exposes apps that aren't approved | `b5-chooser.png` |

- **Caveat on staging:** when Messages already had *that* conversation open in a warm task, the SENDTO draft (`DraftMessageTypeConverter: draft switching to xMS`) was replaced and the audio did not show. After `am force-stop` it staged reliably. A product version should clear or route to a fresh Messages task (for example with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK`, not tested) and verify on device.
- **The carrier gate applies here too, with no override possible:** with `enabledMMS=false`, tapping Send shows a toast and logs `checkDraftForActionPreconditions … resultPrecondition: MMS_DISABLED`. After `cmd phone cc set-value enabledMMS true`, the result is `PASSED`, `MmsServiceBroker: sendMessage() by com.google.android.apps.messaging`, and the fake MMSC received Messages' own PDU (SMIL plus our identical AMR bytes, renamed `recording000000.amr`).
- **Learning the outcome:**
  - Our READ_SMS ContentObserver on `content://mms` fired and showed the new row (`creator=com.google.android.apps.messaging`, msg_box 2), then `resp_st=128` once the MMSC accepted it.
  - **A SENT row alone is not completion.** The observed path A failure was briefly visible in SENT before disappearing, and the path B SENT row initially had `resp_st=null`. For this Messages version, `resp_st=128` plus the observed MMSC response supports MMSC acceptance, not delivery to or playback by a recipient.
  - **OUTBOX (4) and FAILED (5) are invisible to us** (restricted view, see Q1). A timeout means **unknown**: the user may have cancelled, MMS may be disabled, transport may have failed, or the caller may have died. Thread/recipient, timestamp and part size are only heuristics; concurrent messages can collide. The share handoff provides no documented send-result callback to this app. Do not label it reliable failure detection or infer an automatic retry.
- **Lock task** (probe as Device Owner on the emulator, `startLockTask`, `mLockTaskModeState=LOCKED`):
  - When Messages is **not** on `setLockTaskPackages`, the handoff is silently blocked: `ActivityTaskManager: Attempted Lock Task Mode violation … LaunchConversationActivity`. `startActivity` threw nothing, and the probe stayed on screen (`b6-…png`).
  - With `[probe, com.google.android.apps.messaging]`, Messages opened inside lock task with the audio staged and no status or navigation bar (`b7-…png`).
  - So **Messages must be on the lock-task UI list** for path B. It is already a floor/never-hide app, but lock task is a separate list (AGENTS.md rule 5). Adding it is a policy decision: inside Messages the user can also reach every other conversation, attachments and settings.
  - Afterwards: `stopLockTask`, `dpm remove-active-admin`.

### 3. Receiving. **Works with READ_SMS only.**

- **AAC follow-up:** a seeded inbox m-retrieve-conf with one `audio/mp4` `voice.m4a` part (15137 B) is listed and played by the probe with READ_SMS only: MediaExtractor reports `audio/mp4a-latm`, AAC-LC, 16 kHz mono, 24 kbps, 3968 ms; MediaPlayer `isPlaying=true` at 1452 ms (`evidence/AAC-CHECKS.md`). The original AMR results follow.

- **Seeding:**
  - The throwaway `seeder/` app briefly held `android.app.role.SMS` (`cmd role add-role-holder`) and inserted one inbox MMS: m_type 132, from 555-0100, one 4 s `audio/amr` part, 6406 bytes, sha `9ceaf2da…`.
  - The role was then restored to Messages and verified with `get-role-holders`.
  - The old seeder log reported `defaultSms=null` even while shell role readback showed the seeder. The revised seeder checks `RoleManager.isRoleHeld(ROLE_SMS)` explicitly before any writes; package visibility is not evidence of role absence.
- **Probe results (no role):**
  - The observer fired on `content://mms/inbox/4`.
  - `list` shows `_id=4 msg_box=1 m_type=132 … part _id=6 ct=audio/amr` with addr types 137 (from) and 151 (to).
  - `play` opened `content://mms/part/6`: 6406 bytes, `#!AMR` header, matching sha.
  - MediaExtractor reports `audio/3gpp 8000 Hz mono durationUs=4000000`.
  - MediaPlayer reports `durationMs=4000`, then `isPlaying=true positionMs=1354` after 1.5 s.
- Messages shows "555-0100 sent audio", unread (`c1-…png`).
- The revised `play` command selects a downloaded INBOX m-retrieve-conf and then that message's audio part, rather than the newest global audio part (which could belong to an outgoing or hidden failed message). Missing inbox/audio, revoked READ_SMS and decoder errors remain explicit unsuccessful probe commands, not successful playback.
- **Limitation:** the seed was written straight into the provider, not delivered over the air. In real life the item first exists as an m-notification-ind, which is *excluded* from our view, until Messages downloads it (auto-retrieve). Then it appears as m_type 132 in INBOX. If auto-retrieve is off (for example when roaming), we see nothing until the user downloads it in Messages.

### 4. Size limits

- `SmsManager.getCarrierConfigValues()` and `CarrierConfigManager.getConfigForSubId()` both report `maxMessageSize` / `KEY_MMS_MAX_MESSAGE_SIZE_INT` = **1048576** (1 MiB), `enabledMMS=false`, `allowAttachAudio=true`.
- Rates:
  - AMR-NB 12.2 kbps (RFC 4867 storage format): 32 bytes per 20 ms frame = **1600 B/s**, plus a 6-byte header. Measured: 5 s = 8006 B.
  - AAC-LC 24 kbps mono: about **3000 B/s**, plus container. Measured with the MediaCodec/MediaMuxer `.m4a`: 5 s = 18209 B and 4 s = 15137 B, i.e. about 3072 B/s plus about 2.85 KB of MPEG-4 container (this encoder's real rate is a little above the nominal 24 kbps).
  - m-send-req overhead: about 93 B.

| Limit | Bytes | AMR-NB 12.2 kbps storage | AAC-LC 24 kbps mono |
|---|---|---|---|
| 300 KB (decimal) | 300000 | ≈ 187.4 s | ≈ 100.0 s |
| 600 KB (decimal) | 600000 | ≈ 374.9 s | ≈ 200.0 s |
| 1 MB (decimal) | 1000000 | ≈ 624.9 s | ≈ 333.3 s |
| 300 KiB | 307200 | ≈ 191.9 s | ≈ 102.4 s |
| 600 KiB | 614400 | ≈ 383.9 s | ≈ 204.8 s |
| 1 MiB (emulator value) | 1048576 | ≈ 655.3 s | ≈ 349.5 s |

- AMR calculation: `floor((budgetBytes - 93 - 6) / 32) * 0.020`. AAC estimate: `(budgetBytes - 93) / 3000`, before container and encoder variation; with the measured 3072 B/s and about 2.85 KB of container the AAC figures are about 2–3% lower (for example about 340 s at 1 MiB). These are approximate upper budgets, not tested carrier duration limits. The previous table used binary units despite its KB/MB labels.

- Keep about a 10% margin. Read the limit at runtime from `getCarrierConfigValues()`; don't hard-code it.

## Recommendation for PLA-453 (spike opinion, not a decision)

- **Path A** gives LMNOPhone full control: one tap and no Messages UI, which fits a user who can't read. It needs SEND_SMS and a PDU encoder (the vendored Apache-2.0 AOSP `mmslib` subset worked on the emulator), and depends on the carrier having MMS enabled.
  - The probe's SEND_SMS and READ_SMS are hard-restricted. They were granted because adb allowlisted them (`RESTRICTION_INSTALLER_EXEMPT`). The production install and updater path must allowlist SEND_SMS the same way the existing READ_SMS is.
- **Path B** needs no new permission, but the user must find and tap a small "Send MMS" icon inside Messages, and Messages must be on the lock-task list.

## Needs a real SIM on a real phone

1. The real carrier `enabledMMS` and `maxMessageSize` values on the test phone (the emulator values are defaults).
2. Real MMSC delivery of path A and path B to an Android recipient and to an **iPhone**. Does the iPhone play `audio/amr`? Some carriers transcode audio or drop non-SMIL `multipart/mixed`, and path A sends no SMIL.
3. A real incoming voice MMS: notification-ind, then auto-download by Messages, then visible to READ_SMS; and the delay before our observer sees it.
4. Whether the path A sentIntent and the persisted row behave the same over a real MMS APN (separate `mms` APN type on the carrier), including the time to `MMS_ERROR_HTTP_FAILURE` with no signal.
5. The path B warm-task draft loss with the production LMNOPhone launcher and lock task.
6. Whether Messages, with RCS off, ever converts these into RCS (it shouldn't); and group MMS (more than one family member) addressing.
7. Real MMSC and recipient handling of the AAC `.m4a` part labelled `audio/mp4`: whether the carrier accepts or transcodes it, and whether Android and iPhone recipients play it.
8. Playback of the outgoing AAC clip inside Google Messages (the emulator UI showed the attachment, but a Play tap was not proved to start playback).

## Reproduce

Use only the private API 35 `pla458-a35` AVD assigned to this spike, launched with `-read-only` on a verified-free 59xx port. The original run used 5960; the correction run used 5984 after a 5960 collision. Check both console identity and the ADB `ro.boot.qemu.avd_name` property before commands; a serial alone does not establish ownership. Do not run these commands against another AVD or any physical phone. The apps additionally reject hardware other than goldfish/ranchu and recipients outside the fictional 555-01xx fixture range. The helper rejects non-59xx serials and a mismatched ADB AVD name. Synthetic clips are bounded to 1–60 seconds for this probe; that is not a carrier duration limit.

This spike uses `probe/Files.java`, a dependency-free, read-only **FileProvider-style stand-in**, not AndroidX FileProvider. URI grants and ClipData were exercised; the exact AndroidX implementation remains untested. Only `voice.amr`, `voice.m4a` and `send.pdu` are shareable, and write modes are rejected.

```sh
cd spikes/voice-mms
./build.sh                                  # ANDROID_HOME, JDK, build-tools 35.0.0, android-35
S=emulator-5984                             # verified private assigned emulator only
adb -s "$S" emu avd name                    # must report pla458-a35
test "$(adb -s "$S" shell getprop ro.boot.qemu.avd_name | tr -d '\r')" = pla458-a35
adb -s "$S" install -r -t -g build/probe.apk
adb -s "$S" shell pm grant org.plainphone.spike458.probe android.permission.SEND_SMS
adb -s "$S" shell pm grant org.plainphone.spike458.probe android.permission.READ_SMS
adb -s "$S" shell pm grant org.plainphone.spike458.probe android.permission.READ_PHONE_STATE
adb -s "$S" shell appops get org.plainphone.spike458.probe SEND_SMS
adb -s "$S" shell appops get org.plainphone.spike458.probe READ_SMS
P=org.plainphone.spike458.probe/org.plainphone.spike458.Probe
adb -s "$S" shell am start -n "$P" --es cmd env
adb -s "$S" shell am start -n "$P" --es cmd config
adb -s "$S" shell am start -n "$P" --es cmd encode --ei seconds 5
adb -s "$S" shell am start -n "$P" --es cmd sendA
adb -s "$S" shell am start -n "$P" --es cmd sendA --ez override true
adb -s "$S" shell am start -n "$P" --es cmd encodeAac --ei seconds 5
adb -s "$S" shell am start -n "$P" --es cmd sendAac     # mmslib PDU, one audio/mp4 part
adb -s "$S" shell am start -n "$P" --es cmd sendB --es mode sendto --es to 5550101
adb -s "$S" shell am start -n "$P" --es cmd list
adb -s "$S" shell am start -n "$P" --es cmd parts

adb -s "$S" install -r -t build/seeder.apk
adb -s "$S" shell cmd role add-role-holder android.app.role.SMS org.plainphone.spike458.seeder 0
adb -s "$S" shell cmd role get-role-holders android.app.role.SMS
adb -s "$S" shell am start -W -n org.plainphone.spike458.seeder/org.plainphone.spike458.Seeder --ei seconds 4
# AAC variant: add --es format aac (audio/mp4, voice.m4a)
adb -s "$S" shell cmd role add-role-holder android.app.role.SMS com.google.android.apps.messaging 0
adb -s "$S" shell cmd role get-role-holders android.app.role.SMS
adb -s "$S" shell am start -n "$P" --es cmd env
adb -s "$S" shell am start -n "$P" --es cmd list
adb -s "$S" shell am start -n "$P" --es cmd play
```

- Logs use tag `PLA458`.
- Fake MMSC: run `python3 tools/fake_mmsc.py`, then set the APN mmsc with `content update --uri content://telephony/carriers/<id> --bind mmsc:s:http\://10.0.2.2\:59580/mms`.
- Carrier MMS on: `cmd phone cc set-value enabledMMS true` (test override; `cmd phone cc clear-values` undoes it). On this image it needs `adb -s "$S" root` first (shell user gets `Permission denied`); use root only on the private emulator. Quote the APN binding for the remote shell: `adb -s "$S" shell "content update --uri content://telephony/carriers/<id> --bind 'mmsc:s:http\://10.0.2.2\:59580/mms'"`.
- Use a `-read-only` AVD so all of this is thrown away on shutdown.
- Restore the Messages role even if seeding fails. Stop lock task and remove the test-only admin before cleanup. The original run verified Messages restored, no owners, carrier overrides cleared, both spike apps uninstalled, then shut down emulator-5960 and stopped the loopback MMSC/logcat.

## Primary-source pointers

Each path below is pinned to `android-15.0.0_r1`; method names are more durable than line numbers. These primary sources support the enforcement and restricted-view claims above, alongside the committed emulator evidence.

- MMS broker: `https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/services/core/java/com/android/server/MmsServiceBroker.java` — `BinderService.sendMessage`.
- MMS carrier gate: `https://android.googlesource.com/platform/packages/services/Mms/+/android-15.0.0_r1/src/com/android/mms/service/MmsService.java` — `sendMessage`.
- System persistence: `https://android.googlesource.com/platform/packages/services/Mms/+/android-15.0.0_r1/src/com/android/mms/service/SendRequest.java` — `persistIfRequired`.
- Default-app check: `https://android.googlesource.com/platform/frameworks/base/+/android-15.0.0_r1/telephony/common/com/android/internal/telephony/SmsApplication.java` — `shouldWriteMessageForPackageAsUser`.
- Restricted reads: `https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/android-15.0.0_r1/src/com/android/providers/telephony/ProviderUtil.java` — `isAccessRestricted`; same tree's `MmsProvider.java` — `getPduTable`; `MmsSmsDatabaseHelper.java` — `createMmsTables`.
- Public API contract: `https://developer.android.com/reference/android/telephony/SmsManager` — `sendMultimediaMessage`, `getCarrierConfigValues`, MMS result constants. The stock API 35 emulator's disabled-MMS result was 12; the source selects this or configuration error according to its feature flag, so do not assume 12 for every API 35 build.
