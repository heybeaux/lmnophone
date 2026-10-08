## ADDED Requirements

### Requirement: Written setup guide and checking script for a helper
The repo SHALL include `docs/setup-guide.md` and one setup script. The guide SHALL cover, in order:
1. back up the phone
2. remove accounts and factory reset
3. enable adb
4. run the script
5. finish on the phone

The script SHALL refuse to continue unless all of the following hold:
- the phone model is on the approved list;
- `ro.boot.verifiedbootstate` is green;
- the bootloader reports locked;
- no accounts are present.

It SHALL then install Plainphone and run `dpm set-device-owner`.

Setup is considered to take 30 minutes or less. The time runs from the first script command to the green handover check, on a phone that is already backed up and reset, with Wi-Fi available.

#### Scenario: Helper follows the guide
- **WHEN** a helper who did not write the guide follows it on a freshly reset phone of an approved model
- **THEN** Plainphone is Device Owner, the on-phone setup starts, and no steps outside the guide are needed

#### Scenario: Unlocked bootloader
- **WHEN** the script runs on a phone whose bootloader is unlocked, or whose verified boot is not green
- **THEN** it stops and explains the problem, and does not install Plainphone

### Requirement: On-phone setup is a helper-custody phase with authenticated resume
After provisioning, Plainphone SHALL walk the helper through these steps:
1. name the trusted person and their phone number
2. set the PIN, and confirm the trusted person holds the recovery code
3. sign in with the user's own Google account and turn on Play auto-update
4. enter the trusted person's Google `userId` for reset protection
5. choose the approved apps
6. read "What Plainphone can't stop"
7. turn debugging off

Once the PIN exists, resuming setup after an interruption SHALL require the PIN. Plainphone SHALL NOT enter lock task until steps 1–6 are complete. After step 7 it SHALL apply the full lockdown. After handover, setup SHALL NOT be re-enterable.

#### Scenario: Setup interrupted after the PIN is set
- **WHEN** the phone restarts after step 2, and someone other than the helper picks it up
- **THEN** setup asks for the PIN before continuing

#### Scenario: Setup re-entry after handover
- **WHEN** anything tries to open the setup flow after handover
- **THEN** it does not open

### Requirement: Handover check gates "Ready"
At the end of setup, Plainphone SHALL run a self-check and show green, or a plain-language failure with the fix, for each of these:
- Device Owner is active
- every restriction is applied (read back)
- adb is off (read back)
- key attestation reports the bootloader locked and verified boot green
- the reset-protection policy matches the trusted person's `userId`
- the browser role is held by Plainphone
- the never-hide list is intact
- the emergency self-test passes
- Play auto-update was confirmed

Plainphone SHALL NOT say "Ready" while any item fails. The only exception is reset protection, which may pass with an explicit, recorded acceptance by Beaux.

#### Scenario: Self-check failure
- **WHEN** any self-check item fails
- **THEN** Plainphone shows which item failed and what the helper should do, and does not say "Ready"
