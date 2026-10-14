## ADDED Requirements

### Requirement: Truthful runtime identity and results
The installed application SHALL show configured names only, use neutral wording when no identity exists, and SHALL NOT seed request history or claim an unsent request succeeded. Family entries SHALL NOT establish a trusted person. Saved contacts, drafts, credentials and app order SHALL remain unchanged by this cleanup.

#### Scenario: No trusted person configured
- **WHEN** the person opens More or Ask for an app without a configured request recipient
- **THEN** PIN settings remain reachable, requests explain what is missing, and no request is sent or shown as sent

#### Scenario: A saved person happens to share a fixture name
- **WHEN** Family contains a real user-entered person named Sam
- **THEN** that saved name remains visible and the entry is not changed or chosen as a trusted person

### Requirement: Installed release history
About SHALL show actual installed version name and code, current shipped changes, and browsable older release notes. At application startup, the app SHALL durably retain current and previous observed version codes without inferring installation times.

#### Scenario: First observation
- **WHEN** no prior observation exists
- **THEN** About shows latest-release changes without a since-last-version claim

#### Scenario: Skipped upgrades and restart
- **WHEN** startup observes version 49 after an observed 46 and restarts again on 49
- **THEN** About keeps 46 as previous and lists releases 49, 48 and 47 newest first

#### Scenario: Unknown or newer previous version
- **WHEN** the previous version is unknown or newer than current
- **THEN** About shows latest-release changes without an upgrade claim

#### Scenario: New build without notes
- **WHEN** the configured application version code has no checked-in release entry
- **THEN** the host release-note test fails
