# Give-back inventory on the preview-61 line

The entry point consumes a readable TURN_OFF/COMMITTING operation. It does not
start or skip a wait. `WaitingCheckpoints` retains `turnOffAvailable = !testBuild`.
Only the instrumentation APK can create the synthetic operation used in proof.

| Actual writer | Restore and live readback |
| --- | --- |
| `DpmPort`, `ReadbackPolicyApplier`, boot and session effects | Every current admin user restriction cleared and read from `getUserRestrictions`; every hidden installed app unhidden, flushed synchronously, checked visible/enabled; suspended apps unsuspended and checked. |
| `DpmPermissionGrantPort`, `PermissionGrants`, `DeviceApprovalEffects`, `PreviewPermissionAddition`; communication counts, Wi-Fi/Bluetooth, voice/MMS permission helpers | Enumerate requested dangerous permissions, reset only non-default DPM grant states after visibility; read each state back. Clear uninstall blocks and read them back. Permission policy becomes PROMPT/default. |
| `DpmPort` permitted-service writes | Input methods/accessibility lists become null; both lists read back as null. |
| `DpmPort` Home and lock-task writes; `MainActivity` root lock task | Registered root activities stop lock task on the main thread; empty package/features lists are read back along with ActivityManager NONE. Persistent preferred activities for LMNOPhone are cleared; HOME must stop resolving to LMNOPhone, and a visible other launcher must resolve. Removing package membership also ends surviving locked tasks from a prior process. |
| `DpmResetProtectionPort` | Clear policy and send the existing FRP configuration broadcast; read null. Unsupported devices never accepted an FRP write and have no FRP item. A different exception is not evidence of absence. |
| `SystemUpdatePostpone` | Clear the update policy and read null. The committing route runs before postponement, so later passes cannot restore it. |
| Play application restrictions | Clear and read an empty Bundle when Play is installed. |
| Keyguard feature flags | Read NONE; clear and read back if nonzero. |

The preview line has **no writes** to browser role, default SMS, default dialer,
status-bar-disabled or keyguard-disabled flags. Give-back leaves those unchanged;
it does not guess a previous role holder or reset somebody's lock credential.
It checks the system dialer's enabled/resolving state before removing ownership.
There are also no camera, password, certificate or delegated-scope writes here.
Brightness, text size, volume, ringtone, Wi-Fi/Bluetooth and location are the
person's settings and remain as set. Photos, messages, contacts and application
data are never cleared. This inventory must be extended alongside future writers.

The journal is an optional versioned tail in the existing waiting section of
`JointAuthorityStore` (device-protected, two copies and acknowledgement fences).
Old waiting bytes stay identical when there is no journal. Inventory is saved
before the first effect, and DONE/FAILED is saved after each item's readback.
FAILED entries remain until Try again; PENDING entries resume automatically.
A final scan detects new obligations; every earlier success is re-read before
ownership removal. A missing/unreadable operation selects the existing normal
lockdown path; no new restore authority is inferred.

Disabled hidden apps are attempted with the existing best-effort enable and are
counted as failed if still disabled. No hidden-state success masks that failure.
Every uncertain unhide flush retries once and keeps ownership; it never re-hides.

Android contracts used:
- [DevicePolicyManager](https://developer.android.com/reference/android/app/admin/DevicePolicyManager): grant DEFAULT, permitted lists, persistent preferences, restrictions and ownership removal.
- [Lock task](https://developer.android.com/work/dpc/dedicated-devices/lock-task-mode#stop_lock_task_mode): removing a package from the lock-task list stops that package's locked tasks; only the activity that started lock task can stop it through `stopLockTask`.

Host tests prove sequencing and persistence logic. Emulator evidence is separate
under `evidence/emulator/`. No spare-device give-back or live emergency call is authorized by this slice.
