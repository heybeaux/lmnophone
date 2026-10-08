## ADDED Requirements

### Requirement: Written setup guide for a helper
The repo SHALL include `docs/setup-guide.md`. It SHALL let a helper with a computer, who is not an Android developer, set up a factory-reset phone in under 30 minutes. It SHALL include a single setup script that installs Plainphone and makes it Device Owner. It SHALL say plainly that setup erases the phone.

#### Scenario: Helper follows the guide
- **WHEN** a helper follows the guide on a freshly reset supported phone
- **THEN** Plainphone is Device Owner and the on-phone setup starts, with no steps outside the guide

### Requirement: On-phone setup in plain words
After provisioning, Plainphone SHALL walk the helper through:
1. naming the trusted person and their phone number
2. setting the PIN and recording the recovery code
3. signing in to Google (for app updates and reset protection)
4. choosing approved apps
5. reading "What Plainphone can't stop"
6. turning debugging off

Plainphone SHALL NOT enter lock task until steps 1–5 are complete. After step 6 it SHALL apply full lockdown.

#### Scenario: Setup interrupted
- **WHEN** the phone restarts in the middle of setup
- **THEN** setup resumes at the step it reached, and debugging stays available until setup completes

### Requirement: Handover check
At the end of setup, Plainphone SHALL run a self-check and show a green list or a plain-language failure for each item:
- Device Owner active
- restrictions applied
- dialer protected
- Emergency button present
- browsers hidden
- debugging off

#### Scenario: Self-check failure
- **WHEN** any self-check item fails
- **THEN** Plainphone shows which item failed and what the helper should do, and does not say "Ready"
