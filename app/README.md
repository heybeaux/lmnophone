<!--
  Copyright 2026 The Plainphone Authors
  SPDX-License-Identifier: Apache-2.0
-->
# Plainphone app

The Gradle root for the MVP. The module map, the owner of each module, and the seams between them are in [`docs/mvp/lanes.md`](../docs/mvp/lanes.md).

| Module | Kind |
|---|---|
| `:core`, `:policy`, `:links` | Kotlin/JVM. They never import `android.*`; the build has no Android classes on their path. |
| `:ui` | Android library, Compose |
| `:device` | Android library |
| `:plainphone` | Android application, Device Owner receiver, minSdk 30 |

Toolchain: JDK 17, AGP 8.11.0, Kotlin 2.1.20, compileSdk 36. Versions are in `gradle/libs.versions.toml`.

## Build

Run from this directory. The Android SDK comes from `ANDROID_HOME` or a `local.properties` with `sdk.dir=...` (not committed).

```sh
./gradlew test assembleDebug        # all unit tests, then the debug APK
./gradlew :policy:test              # one module's tests
./gradlew :plainphone:assembleDebug # APK: plainphone/build/outputs/apk/debug/
```

## Emulator only

Make the debug build Device Owner on a fresh emulator (no accounts):

```sh
adb install plainphone/build/outputs/apk/debug/plainphone-debug.apk
adb shell dpm set-device-owner org.plainphone/.PlainphoneAdminReceiver
```

No physical phone without board approval for that device (see `TEAM-POLICY.md`).
