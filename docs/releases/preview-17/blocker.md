# Preview 17: missing wait-start route

This is an integration checkpoint, not release notes for a deliverable candidate.
Build 16 remains the latest installed build recorded by the assignment.

## Reproduced scope gap

The requested parents are preview 16 `3454f0f4` and device wiring 1–4
`5e5810f9`. Merging those exact commits produces the expected conflicts in
`MainActivity.kt`, `boot/PolicyRuntime.kt`, and `docs/mvp/lanes.md`.
The initial diagnostic merge was aborted. After the Lead narrowed the route to
a real PIN-checked friction change, the merge was resolved and committed as
`ae71cca3`, preserving the restriction-free preview plan. The new enrollment
adapter is tested but not yet exposed by the app.

Neither parent supplies the requested trusted-person tap route to start a wait:

- Preview `ShellNavigator.onMore` maps `MoreEvent.OpenTrustedPerson` to
  `ShellRoute.NotInBuild`. `ShellViewModel.prepare` constructs this navigator
  with `testBuild = true`.
- The preview's `PreviewPhone.sample()` names are display fixtures, not saved
  credentials in `JointAuthorityStore`.
- At the pinned device commit, `MainActivity.Home` handles Phone and the notice
  only. Its comment explicitly leaves apps, Messages, and More to later wiring.
- At that commit, `PolicyRuntime` constructs the authority reader, reconciler,
  and `WaitingCheckpoints`. Its authority consumers reject every grant. It
  explicitly says nothing starts or skips a wait and nothing writes an authority
  store before trusted people are set up.
- `WaitingCheckpoints` supports checkpoint, period, notice, and cancel only.
  There is no app host construction of `WaitingChangeController` or a turn-off
  start controller in the pinned main or debug sources.

Consequently the merge alone cannot produce truthful instructions of the form
More → trusted person → PIN → turn off → start wait. An injected waiting record
would exercise a fixture, not this requested user route. No fixture was written
and no credentials were seeded.

## Verification

On the preview 16 base, using JDK 17 from `app/`:

```sh
./gradlew :plainphone:testDebugUnitTest \
  --tests org.plainphone.shell.TestBuildNavigatorTest
```

Result on 2026-10-12: **7 tests, 0 failures, 0 errors, 0 skipped**.
`everyUnwiredMoreRowSaysNotInThisTestBuild` confirms the missing destination.
This passing baseline test is evidence of the gap, not build 17 acceptance.

Static inspection of the exact pinned app sources found no construction or
call of the Update adapter. Update remains unwired. Its existing defects are
not resolved by this finding.

The runtime conflict also needs intentional integration: the preview uses
`Preview2Device.plan` and a fixed starter list with no restrictions, while the
device branch uses `LivePolicyInputAssembler` and `PolicyEngine`. Taking the
device side wholesale would not preserve the preview's plan. Any resumed merge
must preserve the starter list, recovery path, snapshot ownership/publication,
package skip checks, and fresh waiting authority/checkpoints together.

## Handoff

The Lead owns the scope decision on the existing build issue: select a usable
preview slice with an available entry point, or explicitly include the missing
authenticated wait-start host route. A synthetic emulator-only demonstration
can be scoped separately from claims that the spare tester can start a change.
No additional approval chain is proposed.

Built: **no build 17 APK**. Installed: **none**. Exercised: **baseline JVM
navigation only**. No emulator or physical-device commands, emergency call,
version bump, new signer, or build 17 acceptance claim occurred.

## Resumed scope: PIN setup plus a friction change

The Lead replaced turn-off with a looser self-managed friction change. Turn-off,
people/mode starts, and Update remain unavailable. No synthetic waiting operation
or account is permitted. The remaining prerequisite is now precise:

1. `FrictionSettingsPatch.create` requires self-managed credentials whose
   `recoveryProfile.friction` supplies the existing limits.
2. `RecoveryProfile` requires a nonempty mapping of holder IDs to account IDs;
   `CredentialState` requires exactly the active credential holders as keys.
3. The account IDs are passed to reset-protection reconciliation. A fake value is
   not harmless preview metadata. PIN-only enrollment has no genuine account ID.
4. Therefore the real friction route needs either genuine profile enrollment or
   a reviewed model/storage change separating friction settings from account
   metadata. The Lead owns choosing that bounded scope on the existing issue.

`PinEnrollment` is an independent completed foundation: it uses
`PinVault.prepareCredential`, then the reviewed joint-store `initialize` for
first enrollment. `CredentialStore.compareAndSet` cannot create a missing record;
normal PIN authentication uses it afterwards through the vault. Enrollment
creates no recovery profile, returns no grant, clears the supplied PIN array,
and refuses existing/damaged authority. Failed durability never reports success.
The recovery-code UI remains out of scope; no code is exposed or logged.

Tests were written first: the narrow enrollment suite failed to compile on the
missing adapter, then all six tests passed after implementation. They cover
persistence/reopen, single-use PIN authority, re-enrollment refusal, corruption,
invalid PINs, durability failure, and the inability of PIN-only setup to prepare
a friction wait. The last case makes the remaining domain prerequisite explicit.

The merged app debug unit suite also passed. Full requested build/test checks
are recorded in the checkpoint verification file when they finish. The version
has not been bumped and the locally assembled checkpoint must not be installed
as build 17. No ready candidate is claimed.

Read-only emulator preflight confirmed retained `pla363-policy-a35`,
`emulator-5880`, API 35, still on version 16. No data, app install, or device
configuration was changed. The physical spare was not accessed.
