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

## App-level Device Owner self-update (Lead option (a), PLA-126), 2026-10-09

`app/` is a throwaway Device Owner that updates itself through `PackageInstaller`. Only v1 is
test-only: an app session refuses test-only APKs (`INSTALL_FAILED_TEST_ONLY`, first attempt).
Cleanup uses the app's own `clearDeviceOwnerApp`. Run `./run-app.sh emulator-5742`; the full output
is in `evidence/app-self-update-api35.txt` (emulator pla113-a35, API 35).

| # | Question | Observed |
|---|---|---|
| X1 | Is an installer-provided `TYPE_WHOLE_SHA256` from `setChecksums` (no signature) enforced? | **No.** A deliberately wrong digest installed: `INSTALL_SUCCEEDED`, v1 -> v2 |
| X2 | Silent self-update as Device Owner | Yes. `commit()` returns in about 1-3 ms, the old process is killed, and the new version's process starts about 80-130 ms later. `MY_PACKAGE_REPLACED` and the `INSTALL_SUCCEEDED` status go to the new process |
| X3 | Process killed after write, before `commit()` | Nothing installs. The session survives as `open`, and the next process start lists it in `getMySessions()` and abandons it |
| X4 | Process killed right after `commit()` returns | The install still completes (v2 -> v4). The status goes to the restarted process |
| X5 | Normal self-update | v4 -> v5, same timing as X2 |
| all | Write to the session after `commit()` | `SecurityException` |

Consequences for the protocol:
- G3 (bytes mutable until commit) cannot be closed with `setChecksums`. It stays a code-structure rule: no write after the final `openRead` hash.
- Once `commit()` returns, the install no longer depends on our process. After a restart, classify by the installed version and never commit again.
- The G1 window is the time between the durable use-up and the `commit()` call (here, about a millisecond plus the store write). A crash there spends the turn and nothing installs, because the next start abandons the open session.

## Abandoning a committed session (PLA-140 X6), 2026-10-09

Question from the Lead (PLA-140): does abandoning an own session that is already committed and
still installing cancel the install? If so, startup abandonment would stop late-install recovery.
`run-x6.sh` (phases 1 and 2) uses 40 MB APKs so the install takes about 100 ms. Emulator
`pla140-a35` (API 35, google_apis arm64); full output in `evidence/abandon-committed-api35.txt`.

| # | Action after `commit()` returns | Observed |
|---|---|---|
| X6a/g | Nothing (baseline) | Installs about 100-120 ms after `commit()`. `isCommitted()` reads **false for about 15-16 ms** first (3/3) |
| X6b | Abandon at once (0 ms), all sessions | **Cancelled**: `INSTALL_FAILED_ABORTED: Session was abandoned`, version unchanged (2/2). The session read `committed=false` |
| X6d | Abandon at once, only `isCommitted() == false` | **Cancelled** too (1/1): the filter does not cover the first ~16 ms |
| X6h | Wait until `isCommitted() == true`, then abandon all | **Cancelled**: `INSTALL_FAILED_ABORTED` at progress 0.9, version unchanged (2/2) |
| X6i | Wait until `isCommitted() == true`, then abandon only uncommitted | Committed session kept; **install completes** (2/2) |
| X6c | Abandon after 150 ms | Install already finished; the old process was gone |
| X6e/f | Process killed right after `commit()`, new process started at once | The new process only started after the install (package frozen); it saw no sessions (2/2) |

Consequence for the adapter: abandoning a committed session cancels it, so startup abandons only
sessions with `isCommitted() == false`, and never a session this process has itself committed
(that covers the ~16 ms before `isCommitted()` turns true, for an in-process `UpdateProtocol.onStart`
rerun). Committed sessions are left to finish; startup readback then classifies them.

Update (PLA-210): "never a session this process has itself committed" is now durable. Each session
ID is written to a synced marker file (`CommitMemory`) before `commit()`, so every adapter instance
and every later process skips it during the ~16 ms window, and nothing is abandoned when the markers
cannot be read. `committedSessionPending` reads `getSessionInfo(id)`: a present session never
reads "gone".

## Archive signer read on API 33 and API 35 (R4, PLA-207 / PLA-210), 2026-10-11

Source (from the PLA-207 review): Android 13 r1 `PackageManager.getPackageArchiveInfo` collects and
verifies certificates only when `GET_SIGNATURES` is set
([PackageManager.java#8067](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/java/android/content/pm/PackageManager.java#8067)),
and [PackageParser](https://android.googlesource.com/platform/frameworks/base/+/android-13.0.0_r1/core/java/android/content/pm/PackageParser.java#891)
leaves `signingInfo` null when nothing was collected, so `GET_SIGNING_CERTIFICATES` alone refuses
every valid APK there (fail closed). API 30-32 and 34+ collect for either flag. `ArchiveApkInspector`
now asks for both flags on API 33 only and still reads only `signingInfo.apkContentsSigners`, the
exact current signer set; the legacy `signatures` array is never read.

Run: `app/device/check-fixtures/update-target/run-check.sh <serial> <port>` (the PLA-167 check, with
the PLA-210 adapter) on fresh AVDs with no lock PIN and no accounts:

| AVD | Build | Result | Signer read |
|---|---|---|---|
| pla210-a33 | API 33, `TE1A.240213.009` userdebug | `evidence/update-check-api33.txt`: update and pending2 PASS (44 ok) | tampered APK: no facts; key B APK reads exactly signer B; v2 reads exactly pinned key A |
| pla210-a35 | API 35, `AE3A.240806.043` userdebug | `evidence/update-check-api35.txt`: update and pending2 PASS (44 ok) | same |

Both runs also show the Device Owner install finishing with `STATUS_SUCCESS` and no
`STATUS_PENDING_USER_ACTION` broadcast, and abandonment right after `commit()` (while
`isCommitted()` still read false) keeping the install. `pending1` ends with "Process crashed" by
design (it kills itself after commit). Python `BrokenPipeError` tracebacks from `serve.py` (the
client closing early on refused downloads) were dropped from the saved output; they named
workstation paths. Not run: a control with the old flag on API 33 (the source above is the
evidence for the defect), and OEM or patched API 33 builds.
