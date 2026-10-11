# Communication counts — THROWAWAY

Emulator-only C1–C7 feasibility probe. Never import into the shipping app.
Read [RESULT.md](RESULT.md) for conclusions, qualification of the test-only
installer harness, and links to metadata-only evidence.

Only fake calls/texts. Probe queries project `_id` and log aggregate counts,
permission/record flags, exception class and monotonic timestamps. They never
read content, addresses or message/call dates. Root fixture writers and UI
controls are test machinery, not feature code.

The privacy contract was written first: `:probe:testDebugUnitTest` initially
failed with `Unresolved reference QueryPlan` (commit `3b9aa0d`). The combined
missed-call selection test also failed before its implementation. The final
query contract test and APK build pass.

## Owned emulator setup

Requires the installed API 35/36 Google APIs arm64 images, SDK tools and JDK 17.
Set `ANDROID_HOME` to the SDK and `JAVA_HOME` to JDK 17. Create your own AVDs;
these exact names/ports are reserved by this spike and enforced by `run.py`:

```sh
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  -n pla291-a35 -k 'system-images;android-35;google_apis;arm64-v8a' -d pixel_6
printf 'no\n' | "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager" create avd \
  -n pla291-a36 -k 'system-images;android-36;google_apis;arm64-v8a' -d pixel_6
```

Start each in an owned terminal/session and wait for boot (do not target any
other emulator or a physical phone):

```sh
"$ANDROID_HOME/emulator/emulator" -avd pla291-a35 -port 5572 \
  -no-window -no-audio -no-boot-anim -no-snapshot -memory 2048 -cores 2 -gpu swiftshader_indirect
# Separate terminal, same flags, -avd pla291-a36 -port 5574.
```

Build here: `./gradlew :probe:testDebugUnitTest :probe:assembleDebug`.
Provision each fresh AVD using `python3 run.py emulator-5572 init` and the API 36
serial. Fixture scripts are **stateful experiments**, not a one-command test
suite: follow their prerequisites and the corresponding RESULT section. Calls
and SMS always use fake 5550100/5550101/5550102 data. No emergency call is made.

`probeId` builds the alternate fresh-session app; `withoutCounts=true` builds
an old-version manifest without either count permission. The test instrumentation
adopts only INSTALL_TEST_ONLY_PACKAGE so all DPCs can remain removable test APKs.
Do not copy the hidden flag access, adopted test permission, root fixture writes,
exported controls or synchronous query execution into production.

Cleanup: stop only the two owned AVDs using `adb -s emulator-5572 emu kill` and
`adb -s emulator-5574 emu kill`. No secrets, personal data or APK binaries are
committed. Temporary artifacts belong in `PAPERCLIP_RUN_SCRATCH_DIR`.
