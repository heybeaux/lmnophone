## ADDED Requirements

### Requirement: Unapproved apps cannot be opened
Every installed app that is not on the allowlist SHALL be hidden with `setApplicationHidden`. Packages in the build inventory are exempt; see device-lockdown. That inventory includes the Play Store and every never-hide dependency.

**What is promised.** An unapproved app cannot be **opened**, from the first interactive frame after unlock. It is not promised that the app is never installed, or never runs any code.

**When Own Phone hides apps.** Own Phone SHALL hide new packages when it receives the package-added broadcast. It SHALL also check every package against the allowlist at boot and every 15 minutes. Lock task SHALL stop an unapproved app from opening even if hiding it fails.

**Background code.** If hiding fails, the app may run background code, and no time limit is promised. This SHALL be disclosed.

**Preinstalled apps.** Apps installed at first boot or later by preinstall services (for example `com.dti.motorola`) SHALL be treated like any other unapproved install.

#### Scenario: Unapproved app installed by any path
- **WHEN** a package not on the allowlist is installed, whether pushed from the Play website, by a preinstall service, or preloaded
- **THEN** no tested entry point can open it: deep link, share target, notification, or explicit intent

#### Scenario: Hide call fails
- **WHEN** hiding a new package fails
- **THEN** Own Phone retries, lock task still prevents the app opening, and trusted-person mode shows "Own Phone needs attention"

### Requirement: Approved apps keep receiving updates, and the monthly check cannot silently lapse
Own Phone SHALL keep the Play Store installed, enabled and signed in to the user's own account. Play SHALL stay outside the lock-task UI list except during a Play session. Setup SHALL include a step where the helper confirms Play auto-update is on.

**Per-app record.** Own Phone SHALL keep a record for each approved app: its version and when it was last updated.

**Monthly check:**
- **Stored state.** Own Phone SHALL store the monthly check's due date and the date it was last completed.
- **Reminders.** On the due date it SHALL text the trusted person. If the text fails, it SHALL retry once a day, for up to 7 days, without sending duplicates.
- **Overdue note.** When the check is more than 7 days past its due date, whether or not any text was delivered, the user's home screen SHALL show one calm line: "Ask [trusted person] to do the monthly phone check." Tapping it SHALL only explain what the check is; it SHALL NOT open Play. The line SHALL be removed when completion is recorded, and the next due date set.
- **Completion.** The check counts as completed only when the trusted person, in the Play session opened from the check, confirms with their PIN that they reviewed Play's update list. They SHALL record any app whose update failed. Unresolved failures SHALL stay listed in trusted-person mode.
- **Play services.** If Google Play services has not changed in 30 days, the reminder SHALL say so neutrally. It SHALL NOT call the phone insecure.

Own Phone SHALL NOT claim to know whether a newer version of any app exists.

#### Scenario: Background update of an approved app
- **WHEN** Play is offering a newer version of an approved app and auto-update is on
- **THEN** the app updates without the user opening Play, and stays approved and visible. Q2's unattended run sets the test conditions.

#### Scenario: Reminder text fails
- **WHEN** the monthly text cannot be sent, for example because there is no service or no SMS permission
- **THEN** Own Phone retries daily, and after 7 days the user's home screen shows the "Ask [trusted person]" line until the check is completed

#### Scenario: Reminder delivered but check skipped
- **WHEN** the text arrives but nobody runs the check
- **THEN** the check stays overdue, and the home-screen line appears after 7 days

#### Scenario: Approved app opens a Play Store link
- **WHEN** an approved app opens a `market://` or Play web link, in a new task or the same task
- **THEN** the Play Store does not open

### Requirement: Play sessions add and approve apps, with permissions decided at approval
Adding an app SHALL happen in a Play session, which follows the supervised-session contract in trusted-person-controls.

**Approving.** Approving a newly installed app SHALL need a fresh PIN entry. The approval SHALL be bound to the app's package name and its signing-certificate digest.

**Permissions.** `BLOCK_ACTIVITY_START_IN_TASK` blocks runtime permission prompts. Device test PLA-5 A3 showed "Permission controller is not available right now." So the approval screen SHALL list the app's runtime permissions in plain words. For each permission, the trusted person chooses Allow or Deny. Own Phone SHALL apply those choices with `setPermissionGrantState`.

**Q1 checks** whether Android lets the Device Owner grant sensor permissions (camera, microphone, location) on the build. If it does not, the app's listing SHALL say which features will not work.

**Session end.** Events that arrive after a session has ended SHALL be ignored. An app installed in a session SHALL stay hidden until it is approved.

#### Scenario: Add an app
- **WHEN** the trusted person starts a Play session, installs an app, chooses its permissions, and approves it with a fresh PIN
- **THEN** the app appears on the home screen with those permissions set, no permission prompt appears in use, and Play cannot be reached again

#### Scenario: Trusted person walks away
- **WHEN** the screen turns off, or the deadline passes, during a Play session
- **THEN** the session ends, and any app installed in the session stays hidden until it is approved

### Requirement: Removing an app is immediate and reversible
After a fresh PIN entry, the trusted person SHALL be able to remove an app from the allowlist. Own Phone SHALL hide the app, not uninstall it.

#### Scenario: Remove then re-approve
- **WHEN** the trusted person removes an app and later approves it again
- **THEN** the app comes back with its previous data
