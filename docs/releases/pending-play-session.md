# Pending Play session

Not released or installed on the spare. Runtime/UI integration is implemented;
emulator proof found a blocking reinstall defect. Not ready for the existing focused review or delivery.

Candidate release wording:

> The trusted person can open the Play Store for 10 minutes from PIN settings,
> after a 10-minute wait. New apps stay hidden until added under Add a hidden app.
> Uses the standard 10-minute wait and 2 openings a day; these can't be changed yet.

The emulator opens Play's signed-out welcome screen inside the timed opening.
No Google sign-in was attempted. Downloading apps through a signed-in Play account
is not verified. This build does not open Google account screens or widen access
to Google Play services. Apps can be added afterwards through Add a hidden app;
in-opening approval belongs to the later 8b slice.

Blocking emulator finding: installing a previously hidden app again after an
uninstall can leave that app visible on API 36. The policy readback fails and
LMNOPhone refuses handback. A first installation passes, but that does not cover
reinstallation. This must be corrected before this candidate is delivered.
