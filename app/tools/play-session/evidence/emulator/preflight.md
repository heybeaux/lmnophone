# Play session emulator preflight

This is image readiness evidence only. No timed Play session, containment,
end trigger, reboot recovery, or Google sign-in was exercised.

The read-only `pla104-a36` AVD booted successfully. `emu avd name` returned
`pla104-a36` before package inspection. Its configured system image is
`system-images/android-36/google_apis_playstore/arm64-v8a/`.

- Model: `sdk_gphone64_arm64`.
- Build: `google/sdk_gphone64_arm64/emu64a:16/BE2A.250530.026.D1/13818094:user/release-keys`.
- `com.android.vending` is installed; the active package reports version code
  `85343440`, hidden false, suspended false, enabled 1.
- The initial activity dump reports lock-task state `NONE` and no lock-task
  packages. This is not a provisioned preview or containment proof.

The other suggested AVDs, `pla113-a35` and `pla186-a35`, both point to
`system-images/android-35/google_apis/arm64-v8a/`, rather than the Play Store
image. Use the verified API 36 image for the eventual session proof.

No account data was read. No sign-in action was taken. Sign-in behavior under
lock task remains unknown.
