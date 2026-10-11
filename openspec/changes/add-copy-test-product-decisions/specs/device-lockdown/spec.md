## ADDED Requirements

### Requirement: Every unhide is saved at once
Every time LMNOPhone unhides a package, it SHALL apply PLA-22 mitigation B straight after the unhide. This covers:
- session and elevation starts;
- approving an app, including one installed during a Play session;
- the protected-state check and the never-hide guard;
- the emergency floor, including on unknown builds and the locked-boot path;
- turning off.

**How.** LMNOPhone SHALL change the enabled state of one of its own components for real, with `PackageManager.DONT_KILL_APP | PackageManager.SYNCHRONOUS`, check that the call returned and that the new state reads back. Each unhide gets one flip, to the opposite of the state the component reads at that moment; the component is not put back, so consecutive flips alternate. If the component's state can't be read, LMNOPhone SHALL NOT flip blindly and SHALL treat it as a failed flip. If the flip fails, LMNOPhone SHALL retry once, log it and show "LMNOPhone needs attention". It SHALL NOT undo the unhide, because it may be an emergency repair.

**The attention record.** A failed flip, and a flip-failure record that can't be read, SHALL be saved in device-protected storage and kept until a later flip for that package reads back or the trusted person acknowledges it. An unreadable record SHALL count as unknown, which keeps attention showing. Until a save succeeds, LMNOPhone SHALL show attention in the running process and retry the save on every pass. **Known limit:** if every save fails and LMNOPhone's process dies before one succeeds, the record can be lost, because no durable channel was available to remember it. Emergency repair SHALL NOT wait for this record.

**Battery guard.** Discretionary unhides (session and elevation starts, approvals, turning off) SHALL NOT start while the battery is below 5% and not charging. Emergency repairs (the floor, the never-hide guard, the protected-state check) SHALL NEVER be delayed.

**What it promises.** Mitigation B is **best effort, not a guarantee**. It makes a power loss after the flip much less likely to cause the PLA-22 boot loop, and it SHALL be described that way everywhere, including the target-phone checklist. Evidence from the PLA-25 review: 25 of 25 corrected power-loss trials booted on API 35 and 36 across four packages, while plain unhides looped. But Android's flush queues its disk write instead of finishing it before the call returns, and one early API 36 trial looped after the flip. So:
- a power loss shortly after the flip can still, rarely, cause the boot loop;
- a power loss in the gap between the unhide and the flip can too;
- a flip SHALL change the component's state for real, because a same-state call skips the flush and looped in the review;
- each unhide needs its own flip, in the same user; an earlier flip covers nothing later.

The remaining risk SHALL be measured (task 1.3) and disclosed, and it stays on the target-phone checklist (approved plan task 6.3).

**Other policy can be lost too.** PLA-25 also saw a user restriction and the lock-task list lost after a power cut, even though the calls had returned and read back. The flip does not protect them. LMNOPhone SHALL NOT treat a returned call or an in-memory readback as proof that policy will survive a power cut. It SHALL rely on its own journal and the re-apply-and-read-back at locked and unlocked boot that device-lockdown already requires.

#### Scenario: Power lost after the flip
- **WHEN** LMNOPhone approves an app, unhides it, and the flip completes and reads back, then the phone loses power
- **THEN** the phone is expected, but not guaranteed, to boot normally, with the app's unhidden state kept

#### Scenario: Emergency repair at low battery
- **WHEN** at 2% battery, not charging, the floor finds a hidden system emergency-dialer package
- **THEN** LMNOPhone unhides it at once and applies the flip; the battery guard does not apply

#### Scenario: Restriction lost in a power cut
- **WHEN** LMNOPhone adds a user restriction, the call returns and reads back, and the phone then loses power before Android saves it
- **THEN** at the next boot LMNOPhone re-applies the restriction from its journal and reads it back before showing Home

#### Scenario: Flip fails
- **WHEN** the component flip after an unhide does not read back
- **THEN** LMNOPhone retries once, then shows "LMNOPhone needs attention", and the package stays unhidden

## MODIFIED Requirements

### Requirement: Reset protection names the trusted person and a backup person
LMNOPhone SHALL call `setFactoryResetProtectionPolicy` with FRP enabled and a list of Google `userId` values:
- **supervised mode:** each trusted person's (one or two);
- **self-managed mode:** the user's own;
- in either mode, optionally, a backup person's.

The list SHALL never be empty.

It SHALL then send `com.google.android.gms.auth.FRP_CONFIG_CHANGED` explicitly to Google Play services. LMNOPhone SHALL read the policy back and confirm it matches.

**Signed-in accounts.** In supervised mode, no trusted person's or backup person's account SHALL be signed in on the phone. In self-managed mode, the user's own account is signed in on the phone as normal; no backup person's account SHALL be.

**On every change** of trusted people or mode, LMNOPhone SHALL set the whole new list, send the broadcast and read it back **before** the new credentials take effect (trusted-person-controls, "Changing who the trusted people are"). If the readback fails, the change SHALL stay in progress with the current credentials in charge, LMNOPhone SHALL retry on every policy pass, and trusted-person mode SHALL show "Reset protection not updated – call the helper". While a write has not read back, LMNOPhone SHALL NOT claim which accounts a wipe would ask for. A trusted person MAY stop the change, which restores the list for the people in charge (trusted-person-controls). If the operation record is lost, LMNOPhone SHALL set the list to every PIN holder's `userId` in the credential store plus the backup person's, and read it back. There SHALL be no automatic exception after setup.

Whether reset protection honours two or three IDs, a changed list, and a failed update is untested. Q10 SHALL cover all three. Any documented limit on the number of accounts is unknown and SHALL NOT be assumed.

If the policy cannot be set or read back during setup, setup SHALL NOT report Ready. The one exception is an explicit, recorded acceptance by Beaux.

FRP does not reinstall LMNOPhone, and the disclosure SHALL say so. In self-managed mode, the disclosure SHALL also say that, after a wipe, the user's own account passes reset protection (subject to Q10).

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
- **THEN** the outgoing person keeps their PIN, the incoming person's PIN grants nothing, and LMNOPhone retries and asks for the helper
