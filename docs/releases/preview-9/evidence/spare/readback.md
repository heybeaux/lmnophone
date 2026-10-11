# Preview 9 on the spare — install, readback, unlock timing (PLA-364)

Driver: the Lead, who took over as sole spare driver for this install (the driver's run was queued behind other work). Device: `<spare>` (moto g power 5G 2024, XT2415-1, Android 15). It has no working SIM. Call was never pressed.

## Artifact
- Gunzipped attachment `2667633e…`: APK SHA-256 `0a8f5e48e0a031cb0286862c372ffbf75da17d6b0c43966845871d0d398db7f6`, signer `99edd5a37e76bb3417a04744328a31d174441778697334e6c4e0c2383ac811bd` (same as build 8), versionCode 9, `0.1.0-preview-9`, testOnly. Both digests match.

## Install
- `upgrade-check.sh` (`install -r -t`) installed at 2026-10-11T22:10:57Z (`lastUpdateTime`), and the installer reported `Success`. The gate restarted the phone and ran both 9-1-1 routes with zero current calls: **`exit=0`** ([upgrade-check.txt](upgrade-check.txt), [More → Emergency](more-emergency-911.png), [Phone](phone-911.png)).
- Pre-install readback: versionCode 8, Device Owner `org.plainphone/.PlainphoneAdminReceiver`, LOCKED, `firstInstallTime=2026-10-09 10:26:07`, `show_communication_counts=true`, `font_scale=1.0`.
- After the restart: versionCode 9, Device Owner kept, LOCKED, `firstInstallTime` unchanged.
- Safety comparison ([before](safety-before.json), [after restart](safety-after-restart.json), [spare_safety.py](spare_safety.py) copied unchanged from preview 8): PASS. Every field is identical before and after, and identical to build 8's after-restart record: owner, restriction names and sections, lock-task packages and features, LOCKED, `firstInstallTime`, the count switch, and package flags (hidden 42, suspended 0, disabled 116 of 545 read, same list hashes). Package names are committed only as a count plus a SHA-256 of the sorted list.
- [Home after the restarts](home-after-reboot.png): LOCKED.

## Unlock → Home after a restart (two samples)
"Unlock" is `uc_finish_user_unlocked` in the event log, as for builds 6–8. The spare has no PIN. Times are relative to unlock; the run length is the pass's `totalMs`.

| | Build 9 sample 1 ([log](reboot-1.txt)) | Build 9 sample 2 ([log](reboot-2.txt)) | Build 8 (one sample) |
|---|---|---|---|
| Locked-boot pass (`reason=start`) ends | −0.2 s (3.9 s run) | −0.1 s (4.0 s run) | +4.6 s (8.7 s run) |
| `PACKAGE_CHANGED:com.android.systemui` pass ends | +3.4 s (3.7 s run) | +4.0 s (4.1 s run) | after Home |
| `reapply reason=user-unlocked` ends | +8.3 s (4.8 s run) | +8.4 s (4.4 s run) | +11.6 s (7.0 s run) |
| `home shown lockTask=LOCKED` | **+8.8 s** | **+9.8 s** | +12.8 s |

- The passes themselves are shorter: the locked `start` pass took ~4.0 s in both samples against 8.7 s, and the user-unlocked pass 4.4–4.8 s against 7.0 s. Two samples cannot prove a speedup; build 7 also measured +8.5 / +9.3 s.
- **Where the wait goes now (the new stage lines):** `livePackages` takes 2.8–3.8 s of every pass, about 75–85 % of each. In both samples a `PACKAGE_CHANGED:com.android.systemui` pass also ran between the locked pass and the user-unlocked pass, so the user waited on three passes, each dominated by `livePackages`. Writes, reads and verifies of every stage are each under 0.5 s.
- Next speed slice input: make the live-package read cheaper, or fold queued package-change passes into the pending user-unlocked pass, without changing what any pass applies or reads back.
