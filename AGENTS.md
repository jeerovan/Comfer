# Project regression testing

- Before choosing tests for an individual change, assess whether it can affect
  layout, geometry, visibility, text direction/alignment, touch targets, or
  interactions that depend on layout direction, screen size, or orientation.
  Run the layout/localization checks below only when that impact warrants them.
  Changes confined to click or sound feedback with no layout impact require
  focused behavior regressions; skip unrelated layout tests.
- As part of that assessment, check whether the change simply reuses an
  established shared component without changing its layout rules, styling, or
  interactions. Additions such as a `SectionHeader` with localized text normally
  need only placement/resource review, translation validation, and compilation;
  do not add tests or run device visual, LTR/RTL, locale, or orientation matrices
  by default. Run additional checks only for a concrete risk introduced by that
  usage, such as constrained text, custom sizing/positioning, or changed touch
  targets, and prefer relevant existing tests. State that risk before expanding
  verification. Once the focused checks pass, stop; do not create a new test
  harness or investigate unrelated failures for this kind of change. Report
  unrelated failures separately. Full release-preparation checks remain mandatory.
- During release preparation, run the full layout/localization checks below
  **before building any release candidate**, even when individual changes did
  not require layout testing. Include portrait and landscape where supported.
- When layout testing is required, test the affected screens and interactions
  in **both LTR and RTL layouts** on an emulator or device. Explicitly exercise
  both layout directions in automated UI regressions; do not rely on the host's
  default.
- For language-sensitive UI, also verify a real RTL locale such as Arabic and
  an LTR locale such as English. Preserve localized text direction when fixing
  physical-coordinate layouts.
- As part of required layout/localization testing, verify both language sources:
  follow the device's system language and select the same language through the
  app's language picker. They must produce the same effective locale, layout
  direction, geometry, and interactions. Exercise
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
- Report the devices, tests run, outcomes, and any untested cases or blockers
  before declaring the change complete. Include locales/layout directions when
  those checks were performed.
- After a user-requested device update, run
  `python3 scripts/prepare_manual_check.py --serial DEVICE --apk APK --control-description LABEL`
  with the intended regular APK and the control's localized accessibility label.
  This inventories installed Comfer variants, verifies the installed APK's
  content, stops test apps without clearing their data, and cold-starts the
  regular app as Home. Preserve production data with in-place updates.
- After every regular-build update on Samsung, recover the launcher before
  handoff: stale Home/Recents tasks have repeatedly left Recents or Settings
  windows visible through Comfer even when APK verification and activity start
  succeeded. Use the helper above to recreate the Home task with
  `am start -S -W -f 0x10008000` (`NEW_TASK | CLEAR_TASK`) and reject a Home
  activity that exposes other apps (`occludesParent=false`). If stale windows
  remain, restart Samsung's Recents process with
  `adb -s DEVICE shell am force-stop com.sec.android.app.launcher`, then rerun
  the helper. Preserve all app data and settings. Confirm normal display and a
  working Home/Recents return before handing the device back. Treat this as
  completing the device update, including when regression tests are skipped;
  keep it focused and do not expand it into layout/locale/orientation matrices.
- Before handing a build back for manual checking, verify the visible app and
  exercise the requested interaction. Check returning from Home/Recents when a
  launcher freeze is reported. A successful build, install, or activity-start
  result alone does not establish that the app is usable.
