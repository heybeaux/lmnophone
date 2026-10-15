# Hide after uninstall and reinstall — cause and fix (PLA-624)

Run: 2026-10-15, emulators only, both `-read-only` on private 59xx ports, identity read back
with `emu avd name` before each step: `pla104-a36` (API 36 Play image, user build) and
`pla113-a35` (API 35 google_apis userdebug). Nothing ran on the spare. No Google sign-in.

## Cause

From API 34 the device policy engine stores an admin's `setApplicationHidden` value as a local
policy per package. The stored value **survives the uninstall** of the package. After uninstalling
a hidden package, `dumpsys device_policy` still shows:

```text
PackagePolicyKey{mPolicyKey= applicationHidden; mPackageName= org.plainphone.playcandidate}
Per-admin Policy: ... BooleanPolicyValue { mValue= true }
Resolved Policy (MostRestrictive): BooleanPolicyValue { mValue= true }
```

When the same package is installed again it is visible (`hidden=false`), but a new
`setApplicationHidden(true)` is the same stored value, so the engine enforces nothing. The call
returns `false` and `isApplicationHidden` reads back `false`, every time. Writing `false` and then
`true` changes the stored value, the engine enforces it, and the package is hidden and reads back.
That also explains the earlier "a reboot cleared it" observation: the engine re-applies stored
policies at boot.

The PackageSetting does not survive. After `pm uninstall` of the hidden package, `dumpsys package`
has no entry at all and the reinstall gets new data inodes on API 35 (on API 36 the inode numbers
repeated, but the package was gone in between). The uninstall was for all users, not only user 0.

## Probe (`-e phase reinstall-hide`, `ReinstallHideCheck`, test APK as testOnly Device Owner)

User 0 lines from `dumpsys package` (inodes left out), raw `setApplicationHidden` return, readback.
API 36 and API 35 gave the same results:

| Step | return | readback | User 0 |
|---|---|---|---|
| first install | — | false | installed=true hidden=false suspended=false enabled=0 |
| hide | true | true | installed=true hidden=true |
| `pm uninstall` (hidden) | Success | — | no entry; stored policy still true |
| install again | Success | false | installed=true hidden=false |
| hide | **false** | **false** | installed=true hidden=false |
| hide, second call | **false** | **false** | installed=true hidden=false |
| fixed `DpmPort.setHidden(true)` | — | **true** | installed=true hidden=true |
| control: show before uninstall, reinstall, hide | true | true | installed=true hidden=true |
| update: hide, `pm install -r` over it | Success | true | installed=true hidden=true (stays hidden) |
| hide after the update | true | true | installed=true hidden=true |

No pending or uninstall state is shown. Note: once the package is gone, `isApplicationHidden` returns
true (it reads the stored value). `DpmPort.packageHidden` returns null for a package that is not installed, so
the applier never sees that value.

## Fix

`HiddenPolicyWrite` (device layer), used by `DpmPort.setHidden`: if a hide returns false and the
package still reads installed and visible, it writes false and then true, once. A show is never
repeated. A hidden or absent package is not touched. Nothing is reported from the write: the HIDES
readback still decides. If the platform still refuses, the readback fails and the existing
fail-closed path (no handback) remains. The unhide in the middle is a no-op on a package that is
already visible.

Host tests (`HiddenPolicyWriteTest`, red then green): a fake of the engine (stored value kept,
only a change enforced) first proves that it reproduces the failure. Then it shows that the reset
fires exactly once (`[true, false, true]`) and hides the package. First install, update-over, an
absent package and a show stay at one write. A refused hide is tried once more and stays visible
for the readback. Red against a single-write stub: 2 of 7 failed. Green: `:device:testDebugUnitTest`,
1218 tests, 0 failures.

## Runtime proof on `pla104-a36` (`PlaySessionProofInstrumentation -e phase install`)

App debug 63 with the fix, provisioned by `tools/preview-2/setup.sh`. Runs in order:

| Case | Before | Result | After close |
|---|---|---|---|
| First install | fixture absent | PASS: candidate recorded with signer, hidden, listed | LOCKED, lock-task list = baseline, journal empty, Play hidden |
| Reinstall: `adb uninstall`, stored `applicationHidden=true` confirmed, install again | absent + stale policy | PASS: hidden, listed, handback ready | LOCKED, baseline, journal empty |
| Update over hidden (`pm install -r`, no uninstall) | installed hidden | PASS: hidden, listed, handback ready | LOCKED, baseline, journal empty |

Baseline lock-task list before and after each run (during the opening only `com.android.vending` was added):
`com.android.camera2, com.android.phone, com.android.server.telecom, com.google.android.apps.maps,
com.google.android.apps.messaging, com.google.android.apps.safetyhub,
com.google.android.cellbroadcastreceiver, com.google.android.dialer, org.plainphone`.

Artifacts (SHA-256): plainphone-debug.apk `61841b4a604099e03322b6ef7b3c741fe9ed660f338cffa15115e3ef456c379b`;
plainphone-debug-androidTest.apk `c7841058d3d1f54913e275de0e4ff92e0f2d939682672b089c9ac20b3736532d`;
device-debug-androidTest.apk `c1ca36de44e3afedebabd9abfd12c687d213b5094a1923f0c1e7594c94823bce`;
fixture `d372874fb720530fa596ad4806902a2c32cb2ea98a833256df40d6ecda5596b8`.

## Not covered (residual)

- A stored `true` for a package that the plan later wants **visible** is not cleared: the reinstalled
  package is already visible, so `show()` writes nothing. At the next boot the engine may hide it again.
  This is a hypothesis and was not tested here. It is not on the Play path, because new candidates
  stay hidden until they are added.
- Actual Play downloads with a signed-in account are still not verified, and runtime reinstall was proven on API 36 only (the
  probe covered API 35 at the port level).
