# THROWAWAY SPIKE (PLA-86): PackageInstaller final-commit boundary

Not MVP code. Answers: what the platform itself enforces at `Session.commit()`, and where
the gap between durable grant use-up and the installer call lies. Run `./run.sh <serial>`
against an emulator you own. APKs are code-free fixtures built with aapt2 and signed
with throwaway keys generated into `out/` (never committed).

## Result on emulator pla113-a35 (Android 15, API 35), 2026-10-09

`evidence/pm-session-rules-api35.txt` is the full output of `./run.sh emulator-5742`.

| # | Question | Observed |
|---|---|---|
| E1 | Does a written session outlive the process that created it? | Yes. A later process committed it: v2 -> v3. |
| E2 | Can bytes be written after commit? | No. `SecurityException: Caller has no access to session` |
| E3 | Can a session be committed twice? | No. Same exception |
| E4 | Is `--pkg` (setAppPackageName) enforced? | Yes. `INSTALL_FAILED_INVALID_APK ... inconsistent with` |
| E5 | Is a downgrade refused? | Yes. `INSTALL_FAILED_VERSION_DOWNGRADE` |
| E6 | Is a same-version reinstall refused? | **No.** `Success`, so "strictly newer" must be our own check |
| E7 | Is another signer refused? | Yes. `INSTALL_FAILED_UPDATE_INCOMPATIBLE` |
| E8 | Can bytes in an open session be replaced before commit? | **Yes.** v2 was written, then v3; v3 installed |
| E9 | Can a session be committed after abandon? | No. `SecurityException` |

`javap` on `platforms/android-36/android.jar`: public `SessionParams` and `Session` have no
expected-digest, required-installed-version or deadline condition on commit, and no seal step.

Not covered (needs an app-level spike): `Session.commit()` from a Device Owner app,
self-update process kill timing, whether `Session.setChecksums` is enforced at commit, and
process death right after `commit()` returns.
