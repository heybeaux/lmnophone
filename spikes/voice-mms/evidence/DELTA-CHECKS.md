# PLA-458 correction checks — October 12, 2026

**THROWAWAY. Host and private-emulator checks only.** Original transport/handoff evidence remains unchanged.

- Before the build fix, the host compiler-failure regression failed: expected exit 42, got 43 because d8 ran after the compiler failed. After the fix it passes and d8 is not invoked.
- Before the AVD identity check, the wrong-AVD regression failed with an XML parsing error; afterward it rejects the target before any UI command. Three Python host regression tests pass.
- Java table-driven guard tests pass: goldfish/ranchu accepted, other/null hardware rejected; all 100 fictional 555-01xx recipients accepted; malformed/out-of-fixture recipients, invalid durations and unshared filenames rejected.
- Both APKs build successfully with javac/d8/aapt2/zipalign/apksigner; both signatures verify. Bash syntax, Python AST parsing and `git diff --check` pass. Deprecation notes are not suppressed compilation failures.

## Built / installed / exercised

| Artifact | SHA-256 | Readback |
|---|---|---|
| probe.apk | `ba194745d5115bbc592bd6d29efe38b54b1b34c505d56d1f20ba475c9597f29a` | versionCode 0, minSdk 30, targetSdk 35, installed October 12, 2026 at 15:48:02 PDT |
| seeder.apk | `6840a5d1d2f9762b51a4c4ba51e4b547c85a87b4b6d3b41219580473ce46ffe2` | install succeeded on the same private emulator |

Private target: `pla458-a35`, read-only, `emulator-5984`, API 35, ranchu. Console identity and ADB `ro.boot.qemu.avd_name` both reported pla458-a35; one owned emulator process held both ports 5984/5985. No Device Owner was present and none was added in this delta.

`delta-5984.log` records:

- Probe has SEND_SMS/READ_SMS, Messages is default, and probe holds no SMS role.
- Zero-second encoding, a non-fixture destination, and sharing before encoding fail explicitly, without sending.
- Seeder refuses to write without the SMS role; with the temporary role it writes the synthetic inbox fixture, then Messages is restored.
- Revised playback selects inbox message 1 / audio part 2: 6406 bytes, 4000 ms, `isPlaying=true` with position 1271 ms after 1.5 seconds.
- Valid five-second synthetic encoding remains 8006 bytes, SHA-256 prefix `798b971b35f969d3`, matching original evidence.
- Revoked READ_SMS produces a SecurityException for inbox playback, not success. READ_SMS was restored afterward.
- Correct-AVD UI helper runs successfully. Cleanup verified Messages as role holder and no owners, uninstalled both spike apps, and stopped only the owned read-only emulator process. No MMS send, emergency call, real recipient or physical device was exercised in this delta.

An earlier delta attempt on 5960 was discarded because two emulator processes claimed that serial and console/ADB state was inconsistent. Only the owned pla458 process was stopped; the Lead was notified on PLA-453 for the existing PLA-460 driver's readback/cleanup. No accepted result here comes from that ambiguous attempt.

Seven pinned AOSP sources listed in FINDINGS were re-fetched and byte-compared against the original source copies. The SmsApplication pointer was corrected to `telephony/common`, not `telephony/java`.
