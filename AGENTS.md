# Project regression testing

- For UI changes, test the affected screens and interactions in **both LTR and
  RTL layouts** on an emulator or device. Explicitly exercise both layout
  directions in automated UI regressions; do not rely on the host's default.
- For language-sensitive UI, also verify a real RTL locale such as Arabic and
  an LTR locale such as English. Preserve localized text direction when fixing
  physical-coordinate layouts.
- Verify both language sources: follow the device's system language and select
  the same language through the app's language picker. They must produce the
  same effective locale, layout direction, geometry, and interactions. Exercise
  switching in both directions and returning to the system default; restore the
  device's original language configuration after testing.
- Check absolute placement and visibility, clipping, text alignment, touch
  targets, gestures, transitions, and relevant size/orientation boundaries.
  Motion-delta assertions alone cannot detect a constant layout-origin error.
- Run these checks **alongside the relevant existing project regressions**
  (unit, integration, and instrumentation tests), not as a replacement for them.
  For bug fixes, reproduce the failure before applying the fix when feasible.
- Use the isolated `notificationTest` application for device tests; preserve
  production app data. Inspect actual instrumentation results: a successful
  Gradle build does not prove tests ran or passed.
- Report the devices, locales/layout directions, tests run, outcomes, and any
  untested cases or blockers before declaring the change complete.
