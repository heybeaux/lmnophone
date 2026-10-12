# PLA-458 spike: voice messages over carrier MMS without the SMS role

> **THROWAWAY.** Feasibility spike, emulator only. Nothing here ships. Do not import from `spikes/`.

- Emulator: private AVD `pla458-a35`, API 35 `google_apis` arm64 userdebug (`AE3A.240806.043`), run as `emulator-5960` with `-read-only`.
- Default SMS app: Google Messages `messages.android_20240528_00_RC01`, set up with no account. RCS was not set up.
- Probe app `org.plainphone.spike458.probe`: SEND_SMS, READ_SMS and READ_PHONE_STATE only. It does **not** hold the SMS role (`env`: `probeHoldsSmsRole=false defaultSms=com.google.android.apps.messaging`).
- Only fictional numbers were used (555-0100, 555-0101). No real messages were sent. The emulator has no real MMSC, so "sent" means delivered to a loopback fake MMSC (`tools/fake_mmsc.py`). No emergency numbers. No physical phones.
- AOSP citations are from tag `android-15.0.0_r1`.

## Answers

### 1. Send path A: our app sends the MMS itself (`SmsManager.sendMultimediaMessage`). **Works, no role needed.**

- **What is enforced:** `frameworks/base/services/core/java/com/android/server/MmsServiceBroker.java` `sendMessage()` (around l.336–360) checks:
  - `enforceCallingPermission(SEND_SMS)`;
  - `TelephonyPermissions.checkSubscriptionAssociatedWithUser`;
  - `noteOp(OP_SEND_SMS)` == MODE_ALLOWED.
  - There is **no SMS-role or default-app check.**
  - Probe app-ops: SEND_SMS allow, READ_SMS allow, WRITE_SMS ignore.
- **Carrier gate:** `packages/services/Mms/src/com/android/mms/service/MmsService.java` l.258 rejects the send before any network work if the MMS config has `enabledMMS=false`. It returns `MMS_ERROR_MMS_DISABLED_BY_CARRIER` (12). The emulator's carrier config has `enabledMMS=false`.
- **PDU:** a hand-written m-send-req encoder (`common/VoiceMms.java#sendReq`, about 50 lines). It produces one `multipart/mixed` part, `audio/amr`, From `insert-address-token`, To `<n>/TYPE=PLMN`. **No third-party library, so no licence question.** The audio is real AMR-NB 12.2 kbps from MediaCodec on a synthetic tone (no microphone).
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
  - **Failures (msg_box 4/5) are invisible to us** (restricted view, see Q1), so we can only infer "not sent yet" by a timeout. "Sent" can be matched by thread/recipient plus date plus part size.
- **Lock task** (probe as Device Owner on the emulator, `startLockTask`, `mLockTaskModeState=LOCKED`):
  - When Messages is **not** on `setLockTaskPackages`, the handoff is silently blocked: `ActivityTaskManager: Attempted Lock Task Mode violation … LaunchConversationActivity`. `startActivity` threw nothing, and the probe stayed on screen (`b6-…png`).
  - With `[probe, com.google.android.apps.messaging]`, Messages opened inside lock task with the audio staged and no status or navigation bar (`b7-…png`).
  - So **Messages must be on the lock-task UI list** for path B. It is already a floor/never-hide app, but lock task is a separate list (AGENTS.md rule 5). Adding it is a policy decision: inside Messages the user can also reach every other conversation, attachments and settings.
  - Afterwards: `stopLockTask`, `dpm remove-active-admin`.

### 3. Receiving. **Works with READ_SMS only.**

- **Seeding:**
  - The throwaway `seeder/` app briefly held `android.app.role.SMS` (`cmd role add-role-holder`) and inserted one inbox MMS: m_type 132, from 555-0100, one 4 s `audio/amr` part, 6406 bytes, sha `9ceaf2da…`.
  - The role was then restored to Messages and verified with `get-role-holders`.
- **Probe results (no role):**
  - The observer fired on `content://mms/inbox/4`.
  - `list` shows `_id=4 msg_box=1 m_type=132 … part _id=6 ct=audio/amr` with addr types 137 (from) and 151 (to).
  - `play` opened `content://mms/part/6`: 6406 bytes, `#!AMR` header, matching sha.
  - MediaExtractor reports `audio/3gpp 8000 Hz mono durationUs=4000000`.
  - MediaPlayer reports `durationMs=4000`, then `isPlaying=true positionMs=1354` after 1.5 s.
- Messages shows "555-0100 sent audio", unread (`c1-…png`).
- **Limitation:** the seed was written straight into the provider, not delivered over the air. In real life the item first exists as an m-notification-ind, which is *excluded* from our view, until Messages downloads it (auto-retrieve). Then it appears as m_type 132 in INBOX. If auto-retrieve is off (for example when roaming), we see nothing until the user downloads it in Messages.

### 4. Size limits

- `SmsManager.getCarrierConfigValues()` and `CarrierConfigManager.getConfigForSubId()` both report `maxMessageSize` / `KEY_MMS_MAX_MESSAGE_SIZE_INT` = **1048576** (1 MiB), `enabledMMS=false`, `allowAttachAudio=true`.
- Rates:
  - AMR-NB 12.2 kbps (RFC 4867 storage format): 32 bytes per 20 ms frame = **1600 B/s**, plus a 6-byte header. Measured: 5 s = 8006 B.
  - AAC-LC 24 kbps mono: about **3000 B/s**, plus a few KB of container.
  - m-send-req overhead: about 93 B.

| Limit (KiB = 1024 B) | AMR-NB 12.2 kbps | AAC-LC 24 kbps mono |
|---|---|---|
| 300 KB | ≈ 192 s | ≈ 102 s |
| 600 KB | ≈ 384 s | ≈ 205 s |
| 1 MB | ≈ 655 s | ≈ 349 s |

- Keep about a 10% margin. Read the limit at runtime from `getCarrierConfigValues()`; don't hard-code it.

## Recommendation for PLA-453 (spike opinion, not a decision)

- **Path A** gives LMNOPhone full control: one tap and no Messages UI, which fits a user who can't read. It needs SEND_SMS and our own PDU encoder, and depends on the carrier having MMS enabled.
  - The probe's SEND_SMS and READ_SMS are hard-restricted. They were granted because adb allowlisted them (`RESTRICTION_INSTALLER_EXEMPT`). The production install and updater path must allowlist SEND_SMS the same way the existing READ_SMS is.
- **Path B** needs no new permission, but the user must find and tap a small "Send MMS" icon inside Messages, and Messages must be on the lock-task list.

## Needs a real SIM on a real phone

1. The real carrier `enabledMMS` and `maxMessageSize` values on the test phone (the emulator values are defaults).
2. Real MMSC delivery of path A and path B to an Android recipient and to an **iPhone**. Does the iPhone play `audio/amr`? Some carriers transcode audio or drop non-SMIL `multipart/mixed`, and path A sends no SMIL.
3. A real incoming voice MMS: notification-ind, then auto-download by Messages, then visible to READ_SMS; and the delay before our observer sees it.
4. Whether the path A sentIntent and the persisted row behave the same over a real MMS APN (separate `mms` APN type on the carrier), including the time to `MMS_ERROR_HTTP_FAILURE` with no signal.
5. The path B warm-task draft loss with the production LMNOPhone launcher and lock task.
6. Whether Messages, with RCS off, ever converts these into RCS (it shouldn't); and group MMS (more than one family member) addressing.

## Reproduce

```sh
./build.sh                                  # needs ANDROID_HOME, build-tools 35.0.0, android-35
adb -s emulator-NNNN install -r -t build/probe.apk
P=org.plainphone.spike458.probe/org.plainphone.spike458.Probe
adb -s emulator-NNNN shell am start -n $P --es cmd config        # Q4
adb -s emulator-NNNN shell am start -n $P --es cmd encode --ei seconds 5
adb -s emulator-NNNN shell am start -n $P --es cmd sendA [--ez override true]   # Q1
adb -s emulator-NNNN shell am start -n $P --es cmd sendB --es mode sendto --es to 5550101  # Q2
adb -s emulator-NNNN shell am start -n $P --es cmd list|parts|play            # Q3
```

- Logs use tag `PLA458`.
- Fake MMSC: run `python3 tools/fake_mmsc.py`, then set the APN mmsc with `content update --uri content://telephony/carriers/<id> --bind mmsc:s:http\://10.0.2.2\:59580/mms`.
- Carrier MMS on: `cmd phone cc set-value enabledMMS true` (test override; `cmd phone cc clear-values` undoes it).
- Use a `-read-only` AVD so all of this is thrown away on shutdown.
