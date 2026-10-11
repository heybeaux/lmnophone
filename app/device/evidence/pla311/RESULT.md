# PLA-311 evidence (PLA-307 findings 1–3)

Own AVD `pla242-a35` (emulator-5824), API 35, `org.plainphone` Device Owner, emulator-only lock PIN
1234. Final APK sha256 in `APK.txt` (built from `bfa822d`; `5d79120` and later change only scripts and
evidence). No number dialled, no call placed.

## Finding 1: fallback containment by suspension (stopped at the spike)

The spike hit the issue's stop condition, so no fallback is built. Settings is exempt from suspension
on API 35 (`OP_SYSTEM_EXEMPT_FROM_SUSPENSION`); an explicit intent and a notification tap still open
it, and the permission controller's `MANAGE_APP_PERMISSIONS` opens too. Chrome is contained on all
three routes. Calling and the keyboard are not affected. Full evidence: `spikes/pla311-suspension/`
(`6a5d03d`). Waiting for the board disclosure decision the Lead routes.

## Finding 2: lock task must actually be running

`app/plainphone/check/process-death-race.sh explicit|notification|reapply`. Home `LOCKED` (flags
119), `kill -9` of Home's process, then the stimulus at once; 0.25 s samples for about 25 s, then a
centre tap.

| Run | Unapproved activity top-resumed | Then | Centre tap | Result |
|---|---|---|---|---|
| `race-explicit` | Settings, sample +0.63 s only | `LOCKED`, Home top by the next sample | Home stays | PASS |
| `race-reapply` (second kill 0.15 s into the restart re-apply) | a leftover permission-controller task (Settings START logged but never on top), sample +0.68 s only | `LOCKED`, Home top | Home stays | PASS |
| `notification-attempts/3` | Settings (tap START logged, uid 10207), +0.99 s to +1.45 s | `LOCKED`, Home top | Home stays | PASS |
| `notification-attempts/4` | Settings (tap START logged), +0.61 s only | `LOCKED`, Home top | Home stays | PASS |
| `notification-attempts/1`, `/2`, `race-notification` | the tap landed on SystemUI, not on our notification (the shade reflows when lock task drops); permission screen +0.17 s to +1.11 s | `LOCKED`, Home top | SystemUI Wi-Fi details → Android's lock-task block screen | PASS (contained) |

Window: an unapproved activity stays top-resumed for one to three samples, at most about 1.5 s after
the kill. The logcat files show the restarted runtime's `reapply reason=start ... ready=true`, then
`lock task not running after start: Home brought forward`, then `home shown lockTask=1`, about
0.35–0.75 s apart. After that Settings is not usable: it is behind Home or replaced by Android's block
screen, with lock task `LOCKED`.

## Finding 3: unknown flip state is durable

JVM only: `FlipFailureDurabilityTest`, the Verifier probe `Pla307ReviewTest`. No AVD step.

## First-unlock containment (keyguard first)

`app/plainphone/check/first-unlock-containment.sh`, in `containment/`, on the final APK. Each variant
drops `no_safe_boot`, the KEYGUARD/BLOCK feature bits (features 16) and, unless `-kept`, the whole list,
then reboots.

| Variant | Result |
|---|---|
| `explicit`, `explicit-kept` (Settings queued before unlock) | PASS: `LOCKED` on every sample after unlock, Settings never top |
| `view`, `view-kept` (Chrome VIEW queued) | PASS |
| `notification`, `notification-kept` (tap to Settings before unlock) | PASS |
| `keyguard`, `keyguard-kept` (40 locked samples over 20 s, no PIN) | PASS: keyguard up on all 40, then `LOCKED` after unlock |
| `emergency-kept` (bouncer "Emergency") | PASS: safetyhub `DialPadActivity`, `keyguard=true`, `LOCKED`, `RUNNING_LOCKED`; nothing dialled |

Not on the AVD: a persistent `setLockTaskFeatures` failure (finding 1). That needs the fallback the board
has not decided on yet.
