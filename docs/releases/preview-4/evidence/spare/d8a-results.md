# preview-4 D8(a) driver checks on the spare (PLA-303)

Driver: Android Platform Engineer (sole spare driver). Device: [REDACTED_SPARE_SERIAL] (moto g power 5G 2024,
Android 15). Date: 2026-10-11, 08:39–08:59 UTC (device clock PDT, UTC−7). No calls or texts were
made, and Call was never pressed. Logs below are counts/flags only: the counts code logs
`<source> <outcome>` and never a number, a name or content.

## Result: all D8(a) checks pass

| Check | Result |
|---|---|
| Upgrade gate (`upgrade-check.sh`, counts off) | PASS, `exit=0` ([upgrade-check.txt](upgrade-check.txt)) |
| DIALER / SMS role holders | `com.google.android.dialer` / `com.google.android.apps.messaging` |
| App versions | Phone 241.0.991235506 (20129558); Messages messages.android_20261002_00_RC00.phone_dynamic (324451063) |
| Working SIM | **No.** SIM slot 1 `ABSENT`, slot 2 `LOADED` but `OUT_OF_SERVICE` (lock screen: "No service"); airplane mode off |
| RCS (chat features) before | **On** ("Turn on RCS chats" switched on), status "Not supported: RCS chats have been disabled by your carrier" |
| RCS after (D8 b step 0) | **Off** (switch read back `checked=false`) |
| Restriction flags after install | both `granted=false`, `RESTRICTION_INSTALLER_EXEMPT` |
| Restriction flags after switch on | both `granted=true`, `POLICY_FIXED`, `RESTRICTION_INSTALLER_EXEMPT` |
| Switch off: no numbers, same PID | PASS: PID 2564 before on, after on, after off; no number on Home; no count read logged after off |
| Restart: no number before unlock, fresh read after | PASS (see below) |
| Screenshots 100 % and 200 % | [d8a/](d8a/): reviewed, no names, numbers or account data, so no redaction needed |
| Finish with switch off | PASS: off at 08:59 UTC, PID 2563 unchanged, no reads after off |

## Details

### Permission flags (`dumpsys package org.plainphone`)

After the in-place install (08:41:46 UTC, switch off):

```
android.permission.READ_SMS: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED|RESTRICTION_INSTALLER_EXEMPT]
android.permission.READ_CALL_LOG: granted=false, flags=[ USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED|RESTRICTION_INSTALLER_EXEMPT]
```

After turning the switch on in More > Phone settings (08:54 UTC), and unchanged after it was turned
off and after the restart:

```
android.permission.READ_SMS: granted=true, flags=[ POLICY_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED|RESTRICTION_INSTALLER_EXEMPT]
android.permission.READ_CALL_LOG: granted=true, flags=[ POLICY_FIXED|USER_SENSITIVE_WHEN_GRANTED|USER_SENSITIVE_WHEN_DENIED|RESTRICTION_INSTALLER_EXEMPT]
```

The permission stays granted after the switch is off, as the Settings disclosure says.

### Switch on/off (PID 2564, 100 %)

```
08:53:24Z pid=2564 (switch off, Home: no number)
01:54:07.573 I/CommunicationCounts( 2564): calls available
01:54:07.601 I/CommunicationCounts( 2564): messages available
08:54:13Z pid=2564 (switch on, Home: no number; both counts are zero, and zero is hidden)
08:55:03Z pid=2564 (switch off, Home: no number; no CommunicationCounts line after off)
```

The spare's counts are zero. Both reads succeeded (`available`) and Home shows no number, because
zero is hidden. With no working SIM, and no calls or texts from agents, a positive number could not
be produced on the spare. The positive on/off case is the tester's step 7. The emulator evidence
covers positive numbers at 100 % and 200 %.

### 200 %

`font_scale` 1.0 → 2.0 → 1.0 (restored). Home and Phone settings, with the switch off and on, are
captured. The settings content fits without scrolling, the switch reads clearly, and no text is
clipped. The PID stayed 2564.

### Restart with the switch on (08:57 UTC)

```
01:57:37.903 ActivityManager: User 0 state changed from RUNNING_UNLOCKING to RUNNING_UNLOCKED
01:57:54.588 PlainphoneBoot( 2563): home shown lockTask=1   (keyguard still showing)
01:57:58.663 CommunicationCounts( 2563): calls available
01:57:58.728 CommunicationCounts( 2563): messages available
01:58:08.245 CommunicationCounts( 2563): calls available
01:58:20.290 KeyguardViewMediator: keyguardGoingAway requested for userId: 0
01:58:20.406 CommunicationCounts( 2563): calls available
01:58:20.453 CommunicationCounts( 2563): messages available
```

Design D4 defines "unlock" as `UserManager.isUserUnlocked()`. The spare has no lock credential, so
Android unlocks user 0 during boot (01:57:37.9), before LMNOPhone's Home starts. Every count read
came after that, in a new process (PID 2563; counts are never stored), so each is a fresh read.
While the keyguard showed, Home was covered and no number was visible
([keyguard-before-unlock-switch-on-100.png](d8a/keyguard-before-unlock-switch-on-100.png)). The
keyguard left at 01:58:20.29 and fresh reads followed at 01:58:20.4.

**Not covered on the spare:** with no PIN there is no locked Direct Boot window, so the "user not
unlocked → hidden" path was not exercised here. The emulator gate (PLA-294) covers Direct Boot.

### Messages app state changed by the driver

- Google Messages was on its first-run "Welcome" screen. To reach its settings, the driver chose
  **"Use Messages without an account"**. No account was added or read. The tester can still sign in
  from Messages.
- RCS chats: turned **off** (it was on, and not supported by the carrier).

### Screenshots ([d8a/](d8a/))

100 %: `home-off-100`, `settings-off-100`, `settings-on-100`, `home-on-100`, `settings-off-after-100`,
`home-off-after-100`, `keyguard-before-unlock-switch-on-100`, `settings-final-off-100`,
`home-final-off-100`. 200 %: `home-off-200`, `settings-off-200`, `settings-on-200`, `home-on-200`.
Every frame was reviewed before commit. They show only LMNOPhone's own UI, the clock and the
status bar, with no names, numbers or account data. The Messages RCS screen, which shows the SIM's
phone number, was **not** kept.
