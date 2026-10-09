<!-- SPDX-License-Identifier: Apache-2.0 -->
# Core seam

Package: `org.plainphone.core`. Pure JVM value types; no Android imports.

- `PackageName.value`: exact package identity; no normalization.
- `CertDigest.value`: SHA-256 certificate digest, exactly 64 lowercase hex digits. `CertDigest.parse` accepts colon-separated and uppercase tool output. This is the shared type agreed with lane C; `PlainphoneIdentity` remains classifier-specific in `:links`.
- `TrustedPersonId.value`, `Mode`, `WindowKind`: stable identities, no authority.
- `NeverHideList.packages`: immutable snapshot of protected dependencies.
- `BuildInventory(model, fingerprint, revision, neverHide, minimumUi, settingsSessionPackages)`: compiled, reviewed inventory for one exact build. Minimum UI must be in never-hide. Settings packages include reviewed same-UID siblings.
- `RunningClock.checkpoint(): ClockReading?`: the device implementation atomically persists a proven running-time checkpoint before returning it. A failed checkpoint returns null and cannot authorize timed work.
- `ClockReading(proven, boot)`, `RunningTime(epoch, totalMillis)`, `BootTime(bootId, elapsedMillis)`: no wall time. Epoch-qualified readings from another epoch count from current epoch zero; boot-local deadlines expire on reboot.

Do not infer foreground eligibility from never-hide membership. Only the emergency UI subset and reviewed minimum UI enter lock task.

Test-first evidence: `CoreTypesTest` was committed while `:core:compileTestKotlin` failed on the absent types; implementation followed. Seven JVM tests cover identity validation, immutable inventories, minimum UI safety, and epoch readings.

Run from `app/` using the shared Gradle build:

```sh
./gradlew :core:test :core:check
```

The shared `app/settings.gradle.kts` owns the module graph; this module has no standalone build.
