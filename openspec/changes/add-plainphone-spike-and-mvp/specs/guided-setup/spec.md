## ADDED Requirements

### Requirement: Written setup guide and checking script for a helper
The repo SHALL include `docs/setup-guide.md` and one setup script.

The guide SHALL list, in order:
1. back up the phone;
2. remove its accounts and reset it **through Settings**;
3. in the first-boot wizard, skip Wi-Fi, every account and every app recommendation;
4. enable USB debugging;
5. run the script;
6. finish setup on the phone.

The script SHALL refuse to continue unless all of these hold:
- the phone model and build are on the approved list;
- `ro.boot.verifiedbootstate` is green;
- the bootloader reports locked;
- no accounts are present.

It SHALL then install Plainphone and run `dpm set-device-owner`.

Timing SHALL be measured from the first script command to the green handover check, on a phone that is already backed up and reset, with Wi-Fi available. Setup SHALL take 30 minutes or less on that measure.

#### Scenario: Helper follows the guide
- **WHEN** a helper who did not write the guide follows it on a freshly reset phone of an approved model
- **THEN** Plainphone becomes Device Owner, and the on-phone setup starts with no steps outside the guide

#### Scenario: Unlocked bootloader
- **WHEN** the script runs on a phone whose bootloader is unlocked or whose verified boot is not green
- **THEN** it stops, explains why, and does not install Plainphone

### Requirement: Preinstalled and pushed apps are swept before Wi-Fi
Before the phone joins any network, Plainphone SHALL hide every package that is not on the never-hide list, the essential list, or the allowlist. This includes manufacturer preinstall services, for example Digital Turbine `com.dti.motorola`.

After joining Wi-Fi, the package-added watcher SHALL catch anything pushed later.

#### Scenario: Preinstall service pushes games
- **WHEN** the phone joins Wi-Fi after provisioning, and a preinstall service installs games
- **THEN** none of them can be opened, and the handover check lists them as hidden

### Requirement: On-phone setup is a helper-custody phase with authenticated resume
After provisioning, Plainphone SHALL take the helper through these steps:
1. name the trusted person and their phone number;
2. set the PIN, and confirm the trusted person holds the recovery code;
3. join Wi-Fi, sign in with the user's own Google account, and turn on Play auto-update;
4. enter the reset-protection `userId` for the trusted person and, optionally, a backup person (see the next requirement);
5. choose the approved apps;
6. read "What Plainphone can't stop";
7. turn debugging off.

Once the PIN exists, resuming setup SHALL require it. Plainphone SHALL NOT enter lock task until steps 1–6 are complete. After step 7, full lockdown SHALL apply. After handover, setup SHALL NOT be re-enterable.

#### Scenario: Setup interrupted after the PIN is set
- **WHEN** the phone restarts after step 2, and someone other than the helper picks it up
- **THEN** setup asks for the PIN before it continues

### Requirement: Reset-protection account IDs are collected without secrets
The guide SHALL have the trusted person, on **their own** device, open Google's People API `people.get` "Try it" page with `resourceName=people/me` and `personFields=names`. They SHALL check that the returned name is theirs, and copy only the digits after `people/`.

Plainphone SHALL show the digits back for confirmation. No token, password or other account data SHALL be collected.

The helper SHALL record that the trusted person confirmed both of these:
- they can sign in to that Google account from a device other than either Plainphone phone;
- their second factor does not live only on a Plainphone phone.

The guide SHALL note that new Google accounts can take 72 hours before they work for reset protection. If Beaux and the trusted person are each other's trusted person, the guide SHALL recommend a backup person.

#### Scenario: Wrong account selected
- **WHEN** the returned name is not the trusted person's
- **THEN** the guide tells them to switch accounts and repeat, and setup does not continue until the name matches

### Requirement: Handover check gates "Ready"
At the end of setup, Plainphone SHALL run a self-check. For each item it SHALL show green, or a plain-language failure with the fix:
- Device Owner active;
- every restriction applied and read back;
- adb and wireless debugging off, read back;
- key attestation shows the bootloader locked and verified boot green;
- reset-protection policy matches the entered `userId` values;
- the browser role is held;
- the never-hide list is intact, including manufacturer emergency components;
- the emergency self-test passes;
- Play auto-update confirmed;
- the preinstall sweep is complete.

Plainphone SHALL NOT say "Ready" while any item fails. The one exception is reset protection, which may pass with Beaux's explicit recorded acceptance.

"Ready" covers setup only. Handing the phone over for daily use also requires the real-device emergency checks in essential-calling-and-texting.

#### Scenario: Self-check failure
- **WHEN** any self-check item fails
- **THEN** Plainphone shows which item failed and what the helper should do, and does not say "Ready"
