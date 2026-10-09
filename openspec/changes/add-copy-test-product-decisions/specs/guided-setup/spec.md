## ADDED Requirements

### Requirement: Setup asks which mode the phone uses
Before any PIN is set, on-phone setup SHALL ask whether the phone is **supervised** (one or two trusted people) or **self-managed** (the user is their own trusted person). It SHALL explain each in plain words:
- supervised: a trusted person approves apps and changes, and turning off waits 24 hours;
- self-managed: the user holds the PIN, and Plainphone adds waits and limits so that distraction takes effort. It can't stop the user, who can remove it by waiting at least 24 hours of the phone being on (longer if the change is cancelled or interrupted) or by wiping the phone.

In supervised mode, setup SHALL offer to add a second trusted person, and SHALL tell both that they are equals. In self-managed mode, setup SHALL show the friction defaults (10-minute countdown, 2 elevations a day) and let the user make them stricter.

During setup (helper custody, before handover), adding trusted people SHALL NOT be a waiting change.

#### Scenario: Self-managed setup
- **WHEN** the helper picks self-managed mode for the user
- **THEN** setup says plainly that Plainphone is friction, not a lock, in this mode, before the user sets their PIN

### Requirement: Privacy steps after Google sign-in
Straight after the user signs in to Google, and before lock task starts, setup and the guide SHALL walk the helper and the user through:
1. pausing Web & App Activity in the Google account;
2. turning Timeline (location history) off;
3. turning ad personalisation off;
4. deleting the phone's advertising ID.

The guide SHALL record the exact screen path of each step for each supported build. The setup dry run SHALL confirm them. Each step SHALL be recorded by the helper as **done** or **skipped**. Plainphone SHALL NOT claim to have checked them. Plainphone SHALL NOT read or record any Google account data while doing this.

#### Scenario: User wants to keep Timeline
- **WHEN** the user chooses to keep Timeline on
- **THEN** the helper records it as skipped, and setup continues

## MODIFIED Requirements

### Requirement: On-phone setup is a helper-custody phase with authenticated resume
After provisioning, Plainphone SHALL take the helper through these steps:
1. choose the mode (supervised or self-managed);
2. in supervised mode, name each trusted person (one or two) and their phone number; in self-managed mode, there is no one to name, and this step is skipped;
3. set each PIN, and confirm that each recovery code is held by its owner (each trusted person, or the user in self-managed mode);
4. join Wi-Fi, sign in with the user's own Google account, turn on Play auto-update, and do the privacy steps;
5. enter the reset-protection `userId` values for the mode (see the next requirement);
6. choose the approved apps;
7. read "What Plainphone can't stop";
8. turn debugging off.

Once a PIN exists, resuming setup SHALL require a PIN set in step 3. Plainphone SHALL NOT enter lock task until steps 1–7 are complete. After step 8, full lockdown SHALL apply. After handover, setup SHALL NOT be re-enterable.

#### Scenario: Setup interrupted after the PIN is set
- **WHEN** the phone restarts after step 3, and someone other than the helper picks it up
- **THEN** setup asks for a PIN before it continues

### Requirement: Reset-protection account IDs are collected without secrets, from the right account
Reset protection SHALL name:
- **supervised mode:** each trusted person's Google `userId`, and optionally a backup person's;
- **self-managed mode:** the user's own Google `userId`, and optionally a backup person's.

The guide SHALL walk each account holder through these steps, on **their own** device:
1. Open Google's People API `people.get` "Try it" page.
2. Pick the exact intended account in Google's account picker. A display name alone is not enough.
3. Authenticate the request with `resourceName=people/me` and `personFields=names`.
4. Copy the digits after `people/` as text.

Plainphone SHALL show the digits back for confirmation. No email, token, password or other account data SHALL be collected or logged.

For **every** account on the list (each trusted person, the backup person, and in self-managed mode the user), the helper SHALL record that its holder confirmed both of these:
- they can sign in to that account from a device other than either Plainphone phone;
- their second factor is not only on a Plainphone phone.

**New accounts.** An account under 72 hours old SHALL be entered, then re-tested after 72 hours. A matching readback is not proof that reset protection is ready. Q10 on the target phone gates handover.

**Backup person.** If Beaux and the trusted person are each other's trusted person, the guide SHALL recommend adding a backup person.

**Self-managed mode.** The guide SHALL say plainly that, because the list names the user's own account, a recovery-mode wipe erases the phone and then lets the user set it up again without Plainphone. After a wipe, someone without a listed account's sign-in can't set the phone up (subject to Q10).

The guide SHALL be dry-run, before release, by someone who did not write it, on a computer signed in to several accounts with the same display name.

#### Scenario: Same-name accounts
- **WHEN** the trusted person's computer has two Google accounts with the same display name
- **THEN** the guide's account-picker step makes them choose by account address, and the dry run confirms the right ID was copied

#### Scenario: Two trusted people
- **WHEN** setup has two trusted people and a backup person
- **THEN** the reset-protection list holds all three `userId` values, each confirmed on screen

### Requirement: Handover check gates "Ready"
At the end of setup, Plainphone SHALL run a self-check. For each item it SHALL show green, or a plain-language failure with the fix:
- Device Owner active;
- every restriction applied and read back;
- adb and wireless debugging off, read back;
- key attestation shows the bootloader locked and verified boot green;
- reset-protection policy matches the entered `userId` values for the mode;
- the browser role is held;
- the never-hide list is intact, including manufacturer emergency components;
- the emergency self-test passes;
- Play auto-update confirmed;
- the preinstall sweep is complete;
- in supervised mode, an end-to-end test text reached **each** trusted person, who confirmed it.

The check SHALL also show the helper's record of the privacy steps (done or skipped for each). That record SHALL NOT gate "Ready".

Plainphone SHALL NOT say "Ready" while any gating item fails. The one exception is reset protection, which may pass with Beaux's explicit recorded acceptance.

"Ready" covers setup only. Handing the phone over for daily use also requires the real-device emergency checks in essential-calling-and-texting.

#### Scenario: Self-check failure
- **WHEN** any gating self-check item fails
- **THEN** Plainphone shows which item failed and what the helper should do, and does not say "Ready"

#### Scenario: Second trusted person's text not confirmed
- **WHEN** the test text reached Beaux but the trusted person hasn't confirmed hers
- **THEN** the check shows the trusted person's text as failing, and Plainphone does not say "Ready"
