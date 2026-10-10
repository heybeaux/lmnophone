## ADDED Requirements

### Requirement: Written setup guide and checking script for a helper
The repo SHALL include `docs/setup-guide.md` and one setup script.

The guide SHALL list, in order:
1. back up the phone;
2. remove its accounts and reset it **through Settings**;
3. take the phone offline:
   - **physical SIM:** remove it;
   - **eSIM:** turn the eSIM off before the reset, under Settings > Network > SIMs. Choose the reset option that **keeps** eSIMs. Turn the eSIM back on in **on-phone setup step 3, after the sweep**, before lock task starts.
   - go through the first-boot wizard, skipping Wi-Fi, every account and every app recommendation;
   - if the wizard will not finish offline, stop: this model is unsupported, and the guide SHALL say so plainly;
4. enable USB debugging;
5. run the script;
6. finish setup on the phone.

The script SHALL refuse to continue unless all of these hold:
- the phone model and build are on the approved list;
- `ro.boot.verifiedbootstate` is green;
- the bootloader reports locked;
- no accounts are present;
- no network is active on any transport (every network that `dumpsys connectivity` reports is checked, not only Wi-Fi and mobile data);
- a build inventory exists for this model and build fingerprint.

It SHALL then install LMNOPhone and run `dpm set-device-owner`.

Timing SHALL be measured from the first script command to the green handover check, on a phone that is already backed up and reset, with Wi-Fi available. Setup SHALL take 30 minutes or less on that measure. The dry run SHALL also report the total helper time separately, including backup, reset and the wizard.

#### Scenario: Helper follows the guide
- **WHEN** a helper who did not write the guide follows it on a freshly reset phone of an approved model
- **THEN** LMNOPhone becomes Device Owner, and the on-phone setup starts with no steps outside the guide

#### Scenario: Unlocked bootloader
- **WHEN** the script runs on a phone whose bootloader is unlocked or whose verified boot is not green
- **THEN** it stops, explains why, and does not install LMNOPhone

### Requirement: Preinstalled and pushed apps are swept before any network
While the phone has no active network, LMNOPhone SHALL hide every package that is not in the build inventory and not on the allowlist. This includes manufacturer preinstall services, for example Digital Turbine `com.dti.motorola`, and preloaded apps such as Facebook.

The inventory's Play Store, keyboard, setup and account components SHALL stay usable.

Before the sweep, LMNOPhone SHALL check the emergency-safe baseline **locally**: the dialer, emergency dialer and Messages resolve; the floor is computed; the lock-task UI list is correct. This check SHALL NOT be reported as an end-to-end call or SMS test. Real outgoing and incoming call and SMS checks SHALL be done after connectivity returns, and before handover.

Only after the sweep SHALL the guide have the helper insert the physical SIM or turn the eSIM back on, then join Wi-Fi. After that, the package-added watcher SHALL catch anything pushed later.

#### Scenario: Preinstall service pushes games
- **WHEN** the phone joins Wi-Fi after the sweep, and a preinstall service installs games
- **THEN** none of them can be opened, and the handover check lists them as hidden

#### Scenario: Network already active
- **WHEN** the script finds Wi-Fi or mobile data active before provisioning
- **THEN** it stops and asks the helper to disconnect, and does not provision

### Requirement: On-phone setup is a helper-custody phase with authenticated resume
After provisioning, LMNOPhone SHALL take the helper through these steps:
1. name the trusted person and their phone number;
2. set the PIN, and confirm the trusted person holds the recovery code;
3. join Wi-Fi, sign in with the user's own Google account, and turn on Play auto-update;
4. enter the reset-protection `userId` for the trusted person and, optionally, a backup person (see the next requirement);
5. choose the approved apps;
6. read "What LMNOPhone can't stop";
7. turn debugging off.

Once the PIN exists, resuming setup SHALL require it. LMNOPhone SHALL NOT enter lock task until steps 1–6 are complete. After step 7, full lockdown SHALL apply. After handover, setup SHALL NOT be re-enterable.

#### Scenario: Setup interrupted after the PIN is set
- **WHEN** the phone restarts after step 2, and someone other than the helper picks it up
- **THEN** setup asks for the PIN before it continues

### Requirement: Reset-protection account IDs are collected without secrets, from the right account
The guide SHALL walk the trusted person through these steps, on **their own** device:
1. Open Google's People API `people.get` "Try it" page.
2. Pick the exact intended account in Google's account picker. A display name alone is not enough.
3. Authenticate the request with `resourceName=people/me` and `personFields=names`.
4. Copy the digits after `people/` as text.

LMNOPhone SHALL show the digits back for confirmation. No email, token, password or other account data SHALL be collected or logged.

The helper SHALL record that the trusted person confirmed both of these:
- they can sign in to that account from a device other than either LMNOPhone;
- their second factor is not only on an LMNOPhone.

**New accounts.** An account under 72 hours old SHALL be entered, then re-tested after 72 hours. A matching readback is not proof that reset protection is ready. Q10 on the target phone gates handover.

**Backup person.** If the two initial users are each other's trusted person, the guide SHALL recommend adding a backup person.

The guide SHALL be dry-run, before release, by someone who did not write it, on a computer signed in to several accounts with the same display name.

#### Scenario: Same-name accounts
- **WHEN** the trusted person's computer has two Google accounts with the same display name
- **THEN** the guide's account-picker step makes them choose by account address, and the dry run confirms the right ID was copied

### Requirement: Handover check gates "Ready"
At the end of setup, LMNOPhone SHALL run a self-check. For each item it SHALL show green, or a plain-language failure with the fix:
- Device Owner active;
- every restriction applied and read back;
- adb and wireless debugging off, read back;
- key attestation shows the bootloader locked and verified boot green;
- reset-protection policy matches the entered `userId` values;
- the browser role is held;
- the never-hide list is intact, including manufacturer emergency components;
- the emergency self-test passes;
- Play auto-update confirmed;
- the preinstall sweep is complete;
- an end-to-end test text reached the trusted person, who confirmed it.

LMNOPhone SHALL NOT say "Ready" while any item fails. The one exception is reset protection, which may pass with Beaux's explicit recorded acceptance.

"Ready" covers setup only. Handing the phone over for daily use also requires the real-device emergency checks in essential-calling-and-texting.

#### Scenario: Self-check failure
- **WHEN** any self-check item fails
- **THEN** LMNOPhone shows which item failed and what the helper should do, and does not say "Ready"
