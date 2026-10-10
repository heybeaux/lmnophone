# PLA-182 evidence: scope

`uninstall-block-api35.txt` is the raw API 35 emulator transcript, unedited since it was recorded.

Rotation coverage is early only. The check's rotation ("rotation inside the write is
REPLACED_CONTAINED") installs the v3-rotated build before the first grant write. It does not test a
rotation that lands after the last signer read: while the uninstall block is put back, or before
`write()` returns. That late window is not run on an emulator. It is covered only by the documented
contract ("What DONE means" and "Callers' duty" on `PermissionGrants`) and by two host tests in
`PermissionGrantsContainmentTest` (PLA-214):

- `rotationWhileTheBlockIsRestoredIsOutsideDoneAndTheReReadIsUnknown`
- `rotationAfterTheLastSignerReadWithAnExistingBlockIsOutsideDoneAndTheReReadIsUnknown`
