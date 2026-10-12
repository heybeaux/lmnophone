# Build 17 work checkpoint — not a device candidate

The exact device commit `5e5810f9` was merged at `ae71cca3`. Both runtime paths
compile together; the preview plan, starter list, grouped package eligibility,
Home-before-snapshot publication and capture ownership remain in place.
Waiting checkpoints precede skip decisions. An active or uncertain wait forces
a full pass. Runtime notices now reach the preview shell and its PIN-free cancel
and needs-attention destinations.

Fail-closed choice: lost-record reconciliation has no full-policy input on this
preview, so it leaves the record held with attention. It does not substitute the
full engine plan or set reset-protection accounts. Forward turn-off is not hosted.
Back handling retains the pinned wiring's known limitation; wiring 4b was not merged.

The new `PinEnrollment` adapter has six JVM tests, first shown failing on the
missing implementation and then passing. It persists only an actual supplied PIN
through the reviewed vault and joint-store genesis transaction, grants nothing,
refuses existing/damaged state, checks durable readback, and clears the caller's
PIN array. It is not called by a product screen yet. No account or wait was seeded.

On 2026-10-12, from `app/`, JDK 17:

```sh
./gradlew :device:test :plainphone:test :plainphone:assembleDebug
```

**BUILD SUCCESSFUL in 2m 15s.** All variants passed with no failures, errors or
skips; exact totals are in `checkpoint-unit-results.json`. Existing compiler
warnings remain. Imported gate summary tables have pre-existing trailing
whitespace; no new source whitespace errors were found. The original navigation
test still asserts every unavailable More row because the real route is pending.

The local APK still identifies as version 16 and is only a compilation artifact;
it was not uploaded or installed. Build 17's version bump, gate copy, deliverable
APK and release-notes case await the usable route. Read-only preflight confirmed
the retained API 35 AVD `pla363-policy-a35` (`emulator-5880`) still has build 16.
No upgrade, pending/cancel exercise, reboot sample or emergency gate was run.
The physical spare was not accessed.

Remaining engineering dependency: the reviewed friction controller reads active
limits from `RecoveryProfile`, whose account-ID mapping must contain each holder.
PIN-only enrollment intentionally lacks that profile, and the regression test
proves it cannot prepare a friction change. Genuine profile enrollment or an
explicit model/storage separation is required. The Lead was asked to settle that
bounded scope on the existing issue; no synthetic profile is proposed.
