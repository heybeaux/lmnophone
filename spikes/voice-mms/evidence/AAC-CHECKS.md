# PLA-458 AAC follow-up (THROWAWAY, emulator only)

This answers the Lead's later priority: one AAC `.m4a` part labelled `audio/mp4`, built with a vendored AOSP `mmslib` subset and sent by an app without the SMS role. The original AMR evidence and `DELTA-CHECKS.md` stay as they were. Only fictional 555-01xx numbers were used. No physical phone, real recipient or emergency number was involved.

## Target

- Private AVD `pla458-a35`, API 35 `google_apis` arm64 userdebug, `-read-only -port 5986` (port checked free first).
- Identity checked before any mutation: `adb -s emulator-5986 emu avd name` and `getprop ro.boot.qemu.avd_name` both reported `pla458-a35`. Every adb call went through a wrapper pinned to `emulator-5986`.
- `adb root` was used on this private emulator only, because `cmd phone cc set-value` returns `Permission denied` from the shell user on this image.

## Built

- `probe.apk` sha256 `bfb33b24c67c4c5f64a32a5bc7ca901cd4d890f1a8256ba758558f996eddc5eb`
- `seeder.apk` sha256 `cd9c57c98d180fd949438fa38d2999a5b2920a214a13f00e2ad683898b08caa1`
- Both are built by `build.sh` (javac, d8, aapt2, debug-signed). The probe now compiles `mmslib/*.java mmslib/pdu/*.java`.

## Installed

- `install -r -t -g build/probe.apk`, then `pm grant` SEND_SMS, READ_SMS and READ_PHONE_STATE. `env`: `probeHoldsSmsRole=false defaultSms=com.google.android.apps.messaging`. `dpm list-owners`: no owners.
- `install -r -t build/seeder.apk`, given the SMS role only for seeding, then the role went back to Messages (`get-role-holders` = `com.google.android.apps.messaging`).

## Exercised (`aac-5986-logcat.log`, `aac-5986-fake-mmsc.log`, `aac-5986-messages-conversation.png`)

| Step | Result |
|---|---|
| `encodeAac --ei seconds 5` | MediaCodec AAC-LC, 16 kHz mono, 24 kbps, MediaMuxer MPEG-4: 18209 bytes, `ftypmp42`, part sha256 `54afb889aacb80d0` |
| `sendAac`, stock carrier config | mmslib PDU 18344 bytes; `sentIntent resultCode=12` (MMS disabled by carrier); `content://mms rows=0` (no row written) |
| `sendAac`, APN 3492 mmsc pointed to loopback fake MMSC, `cmd phone cc set-value enabledMMS true` | `resultCode=-1` (RESULT_OK), `uri=content://mms/sent/1`, `MMS_DATA=byte[33]` (fake m-send-conf), `LAST_CONNECTION_FAILURE_CAUSE_CODE=0` |
| Provider row read back with READ_SMS | `_id=1 msg_box=2 (SENT) m_type=128 resp_st=128 creator=org.plainphone.spike458.probe ct_t=application/vnd.wap.multipart.mixed`, one part `ct=audio/mp4 name=voice.m4a cl=voice.m4a`, addr types 137 (from) and 151 (to) |
| Fake MMSC decode | POST of 18344 bytes; PDU sha256 `2984d90f53b12ffc` (same as the probe's log); headers m-send-req, MMS 1.2, To `5550100/TYPE=PLMN`, class personal, priority normal, delivery and read reports no; Content-Type in general form (25 bytes); 1 part with text content type `audio/mp4`, name and content location `voice.m4a`, content ID `<voice.m4a>`, 18209 bytes, sha256 `54afb889aacb80d0` (same as the encoder output) |
| Google Messages | Conversation list: `555-0100`, "You: Audio clip". Thread: audio attachment with play button, seek bar and `00:04`, status "MMS". After tapping Play the UI dump still showed the play button and `00:04`; playback inside Messages was **not** proved by this dump |
| Seeder `--es format aac --ei seconds 4` | Inbox `m-retrieve-conf` `content://mms/2` with one `audio/mp4` `voice.m4a` part, 15137 bytes, sha256 `cae51c12d692f521` |
| Probe `list` / `play` with READ_SMS only | Lists both rows; `play` opens `content://mms/part/3`: 15137 bytes, sha matches, MediaExtractor `mime=audio/mp4a-latm aac-profile=2 sample-rate=16000 channel-count=1 bitrate=24000 durationUs=3968000`; MediaPlayer `durationMs=3968`, after 1500 ms `isPlaying=true positionMs=1452` |

## Cleanup (read back)

- `cmd phone cc clear-values`: `enabledMMS` back to `false`.
- APN 3492 mmsc restored to its stock value.
- SMS role: `com.google.android.apps.messaging`. `dpm list-owners`: no owners.
- Probe and seeder uninstalled (`pm list packages org.plainphone.spike458` is empty).
- `emu kill` on `emulator-5986` only; loopback MMSC and logcat stopped.

## What this does not show

- Real carrier MMSC acceptance, delivery to Android or iPhone recipients, or recipient playback of `audio/mp4` (AAC in MPEG-4). Carriers and iMessage gateways may transcode or reject it.
- That Messages actually played the outgoing clip (it parsed and showed the 4-second duration).
- A real incoming AAC MMS through the WAP push notification and auto-download path. The seeded inbox row stands in for a downloaded message.
- Whether the PDU's text-form `audio/mp4` content type (the AOSP well-known table has no entry for it) is accepted by every MMSC.
