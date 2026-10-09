## MODIFIED Requirements

### Requirement: Reset protection names the trusted person and a backup person
Plainphone SHALL call `setFactoryResetProtectionPolicy` with FRP enabled and a list of Google `userId` values:
- **supervised mode:** each trusted person's (one or two);
- **self-managed mode:** the user's own;
- in either mode, optionally, a backup person's.

The list SHALL never be empty.

It SHALL then send `com.google.android.gms.auth.FRP_CONFIG_CHANGED` explicitly to Google Play services. Plainphone SHALL read the policy back and confirm it matches. No trusted person's or backup person's account SHALL be signed in on the phone.

**On every change** of trusted people or mode, Plainphone SHALL set the whole new list, send the broadcast and read it back, before it removes the outgoing person's credentials (trusted-person-controls). If the readback fails, trusted-person mode SHALL show "Reset protection not updated – call the helper".

Whether reset protection honours two or three IDs, and a changed list, is untested. Q10 SHALL cover both. Any documented limit on the number of accounts is unknown and SHALL NOT be assumed.

If the policy cannot be set or read back, setup SHALL NOT report Ready. The one exception is an explicit, recorded acceptance by Beaux.

FRP does not reinstall Plainphone, and the disclosure SHALL say so. In self-managed mode, the disclosure SHALL also say that the user's own account passes reset protection.

#### Scenario: Reset protection configured
- **WHEN** setup reaches the handover check
- **THEN** the readback lists exactly the entered `userId` values, and the check shows green

#### Scenario: Wipe from recovery mode
- **WHEN** a supervised phone is wiped from recovery mode and someone tries to set it up with the user's own Google account
- **THEN** setup requires a listed account instead. This is test Q10, which needs a separate board-approved procedure on the device being tested.

#### Scenario: List updated after a handover
- **WHEN** a handover completes
- **THEN** the readback lists the incoming person's `userId` and not the outgoing person's, and Q10 confirms the outgoing person's account no longer passes reset protection on the test device
