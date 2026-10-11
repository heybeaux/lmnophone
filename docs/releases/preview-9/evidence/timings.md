# Boot timings on owned API 35 AVD

| Elapsed ms | Locked start before | Locked start after | User-unlocked before | User-unlocked after |
|---|---:|---:|---:|---:|
| total | 1656 | 783 | 1438 | 1037 |
| input | 1428 | 646 | 816 | 793 |
| livePackages | 1426 | 644 | 779 | 770 |
| floorIdentities | 42 | 21 | 67 | 102 |
| osFloor | — | — | — | — |
| revoke | 3 | 3 | 16 | 55 |
| write.EMERGENCY_FLOOR | 46 | 3 | 42 | 1 |
| read.EMERGENCY_FLOOR | 16 | 1 | 7 | 2 |
| write.NEVER_HIDE_GUARD | 33 | 6 | 27 | 0 |
| read.NEVER_HIDE_GUARD | 6 | 23 | 94 | 2 |
| write.HIDES | — | — | 81 | 7 |
| read.HIDES | — | — | 156 | 11 |
| write.LOCK_TASK | — | — | 26 | 8 |
| read.LOCK_TASK | — | — | 2 | 1 |
| write.HOME | — | — | 27 | 60 |
| read.HOME | — | — | 1 | 0 |
| verify.EMERGENCY_FLOOR | 14 | 17 | 26 | 9 |
| verify.NEVER_HIDE_GUARD | 50 | 5 | 35 | 14 |
| verify.HIDES | — | — | 20 | 36 |
| verify.LOCK_TASK | — | — | 0 | 1 |
| verify.HOME | — | — | 1 | 1 |
| sessionStart | 200 | 117 | 591 | 230 |
| flipRecord | 0 | 2 | 0 | 0 |
| disabledRead | 28 | 18 | 31 | 14 |
| afterReapply | 0 | 0 | 0 | 0 |

Before is instrumentation-only build 8 (`d9d5f6c`), after is build 9 (`f99ed65`). Nested intervals are inclusive, not additive. A dash means the operation did not run; `osFloor` is the plan-independent fallback, not the ordinary floor-identity read. `write` intervals include any safety read required inside a write, such as Keyguard-first. `read` is the stage readback and `verify` is the final policy readback.

Unlock → first `home shown lockTask=LOCKED`, measured from the system `USER_UNLOCKED` broadcast: original build 8 **2041 ms**, instrumented build 8 **1945 ms**, final build 9 **1156 ms**. These are single cold-boot observations under shared-host load, not a controlled benchmark or a spare result. Build 9 waited for visible PIN entry before entering the test PIN; the earlier scripts used a fixed delay. The first build-9 attempt never delivered an unlock and is retained separately, not counted as a performance result.
