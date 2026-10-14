# Send real app requests

## Why
The preview has no request recipient setup or durable SMS outbox. PLA-525 is
already authorized; the Lead resolved recipient setup in interaction
24613b02-d6fc-4161-8441-54d784a3a00f. This implements the approved app-request
requirement and explicit setup choice after PLA-528, without changing lockdown.

## What Changes
- Fresh-PIN recipient setup in PIN settings, independent of PIN authority.
- Explicit SMS sending, durable text/day/result, five attempts per day.
- Honest multipart Sent / Not sent / Not confirmed and read-only Requests.
- Candidate 50 release notes, full text at large sizes.

## Impact
Policy state/ports, device storage/SMS adapter, application routes, UI, tests.
No emergency, lock-task, app approval or PIN-vault record changes.
