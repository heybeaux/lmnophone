## ADDED Requirements

### Requirement: Every unhide is saved at once
Every time Plainphone unhides a package, it SHALL apply PLA-22 mitigation B straight after the unhide. This covers:
- session and elevation starts;
- approving an app, including one installed during a Play session;
- the protected-state check and the never-hide guard;
- the emergency floor, including on unknown builds and the locked-boot path;
- turning off.

**How.** Plainphone SHALL change the enabled state of one of its own components for real, with `PackageManager.DONT_KILL_APP | PackageManager.SYNCHRONOUS`, check that the call returned and that the new state reads back, then put the component back the same way. If the flip fails, Plainphone SHALL retry once, log it and show "Plainphone needs attention". It SHALL NOT undo the unhide, because it may be an emergency repair.

**Battery guard.** Discretionary unhides (session and elevation starts, approvals, turning off) SHALL NOT start while the battery is below 5% and not charging. Emergency repairs (the floor, the never-hide guard, the protected-state check) SHALL NEVER be delayed.

**What it promises.** After the flip completes and reads back, a power loss is expected not to cause the PLA-22 boot loop. This rests on 3 trials on API 35 so far, and task 1.3 tests it on API 35 and 36 at every call site. A power loss in the gap between the unhide and the flip can still cause the boot loop. That gap SHALL be measured (task 1.3) and disclosed, and it stays on the target-phone checklist (approved plan task 6.3).

#### Scenario: Power lost after the flip
- **WHEN** Plainphone approves an app, unhides it, and the flip completes and reads back, then the phone loses power
- **THEN** the phone is expected to boot normally, with the app's unhidden state kept

#### Scenario: Emergency repair at low battery
- **WHEN** at 2% battery, not charging, the floor finds a hidden system emergency-dialer package
- **THEN** Plainphone unhides it at once and applies the flip; the battery guard does not apply

#### Scenario: Flip fails
- **WHEN** the component flip after an unhide does not read back
- **THEN** Plainphone retries once, then shows "Plainphone needs attention", and the package stays unhidden

## MODIFIED Requirements

### Requirement: Reset protection names the trusted person and a backup person
Plainphone SHALL call `setFactoryResetProtectionPolicy` with FRP enabled and a list of Google `userId` values:
- **supervised mode:** each trusted person's (one or two);
- **self-managed mode:** the user's own;
- in either mode, optionally, a backup person's.

The list SHALL never be empty.

It SHALL then send `com.google.android.gms.auth.FRP_CONFIG_CHANGED` explicitly to Google Play services. Plainphone SHALL read the policy back and confirm it matches.

**Signed-in accounts.** In supervised mode, no trusted person's or backup person's account SHALL be signed in on the phone. In self-managed mode, the user's own account is signed in on the phone as normal; no backup person's account SHALL be.

**On every change** of trusted people or mode, Plainphone SHALL set the whole new list, send the broadcast and read it back **before** the new credentials take effect (trusted-person-controls, "Changing who the trusted people are"). If the readback fails, the change SHALL stay in progress with the current credentials in charge, Plainphone SHALL retry on every policy pass, and trusted-person mode SHALL show "Reset protection not updated – call the helper". While a write has not read back, Plainphone SHALL NOT claim which accounts a wipe would ask for. A trusted person MAY stop the change, which restores the list for the people in charge (trusted-person-controls). If the operation record is lost, Plainphone SHALL set the list to every PIN holder's `userId` in the credential store plus the backup person's, and read it back. There SHALL be no automatic exception after setup.

Whether reset protection honours two or three IDs, a changed list, and a failed update is untested. Q10 SHALL cover all three. Any documented limit on the number of accounts is unknown and SHALL NOT be assumed.

If the policy cannot be set or read back during setup, setup SHALL NOT report Ready. The one exception is an explicit, recorded acceptance by Beaux.

FRP does not reinstall Plainphone, and the disclosure SHALL say so. In self-managed mode, the disclosure SHALL also say that, after a wipe, the user's own account passes reset protection (subject to Q10).

#### Scenario: Reset protection configured
- **WHEN** setup reaches the handover check
- **THEN** the readback lists exactly the entered `userId` values, and the check shows green

#### Scenario: Wipe from recovery mode
- **WHEN** a supervised phone is wiped from recovery mode and someone tries to set it up with the user's own Google account
- **THEN** setup requires a listed account instead. This is test Q10, which needs a separate board-approved procedure on the device being tested.

#### Scenario: List updated after a handover
- **WHEN** a handover completes
- **THEN** the readback lists the incoming person's `userId` and not the outgoing person's, and Q10 confirms the outgoing person's account no longer passes reset protection on the test device

#### Scenario: Update fails during a handover
- **WHEN** a handover matures and the new list does not read back
- **THEN** the outgoing person keeps their PIN, the incoming person's PIN grants nothing, and Plainphone retries and asks for the helper
