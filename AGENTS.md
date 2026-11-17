# Active tracking: AI SDLC

- Use the configured AI SDLC MCP project `comfer`, canonical root
  `/Volumes/JS/Repos/Comfer`, for active requirements and delivery state.
  Current priority is release-58 issue investigation (`R58-REFRESH-01`,
  `R58-FETCH`), scoped remediation (`R58-FIX-01`–`R58-FIX-09`) and version-59
  release testing (`R59-LOCAL-TESTS`, `R59-PREPARE`).
  The language-delivery follow-up now targets60: `R60-AAB` tracks the verified
  local bundle and `R60-INTERNAL-TEST` tracks focused Play59-to60 checks on both
  phones after owner upload. Preserve `R59-LANGUAGE-PACKAGING` and prior IDs.
  The owner-requested modular Settings enhancement (`R59-SETTINGS`) is also a
  prerequisite for the version-59 release build. Preserve every existing setting
  while validating bottom-reachable categories, Back/recreation and locale layouts.
  The corresponding issue
  history attempt IDs remain `58-R02`–`58-R10`; target-version changes do not
  rename attempts or establish production resolution. Sync remains future work.
  Play listing scope is preserved.
  On resume, verify `list_projects`, `project_status`, then focused
  `get_context` for the active step and `PL-MIGRATION`. Use current opaque versions for
  writes; repository JSON managed by the MCP service is authoritative.
- Migrated scope: all of `PRD-PLAY-LISTING.md` and
  `PLAN-PLAY-LISTING-IMPLEMENTATION.md`, retaining step IDs `PL-01`–`PL-06`.
  Requirements are `PL-REQ-01`–`PL-REQ-06`. Preserve these Markdown documents as
  historical snapshots; do not maintain a competing editable Play listing plan.
  Historical completed rows are not current verification: reuse working code,
  retain the evidence, and verify only the boundary needed for the next action.
- Unmigrated scope: `PRD-SYNC.md`, release-58 PRD/plan (`R58-01`–`R58-04`), and
  earlier release history. Preserve them and their unresolved requirements;
  reconcile the relevant scope into AI SDLC before resuming it. Migration does
  not establish release readiness or production resolution.
- Keep detailed listing observations/artifacts in `play_listing.db` and
  `store-listing/`; keep issue/release history in `play_reporting.db` as below.
  Link evidence from AI SDLC rather than replacing either historical store.
  Record source/test references, dependency gates and fixture limitations.
  Begin verification before checks, retain nonsecret proof, and finish with
  actual observations. Publication, activation and evaluation are distinct;
  prepared v002 does not establish exposure or authorize publication.
- If MCP is unavailable, disclose the failure and keep task-scoped fallback
  updates explicitly pending reconciliation under the existing Markdown rules.
  Never silently restore the archived plan as a competing authority.

# Implementation answers

- Before answering questions about app behavior or implementation, inspect the
  current code, including relevant callers and state transitions as needed. Use
  docs for context and navigation, never as sole evidence of current behavior;
  explicitly state any verification limits.

# Issue and release history

- Current investigation: `R60-INVESTIGATE-01` covers the version-60 Honor Gallery
  click crash and bitmap/clock ANR leads. Preserve `58-R05` and its original
  allocation-cleanup acceptance; a sampled ANR in that path is not proof the
  earlier fix failed. Production causal verification remains separate.
- Firebase MCP event responses may be lossy summaries. Before concluding that
  thread evidence is unavailable, inspect the formatter and retrieve the raw
  selected events through the authenticated API client as described in the
  reporting guide. Preserve all returned threads/frames and link immutable
  evidence IDs; do not replace full-window imports with partial investigations.
- Keep issue evidence, fix attempts, recurrence reviews, validation and production
  follow-ups in `play_reporting.db`; use `scripts/issue_history.py` and the reporting
  guide in `scripts/README-play-reporting.md`. Append history rather than replacing
  earlier decisions. Release docs should link to the database and retain concise
  artifact/acceptance summaries. Historical records require current-code checks
  before making implementation claims; local tests do not prove production fixes.
- Keep one canonical `play_reporting.db`. Use its append-only linked document,
  import-snapshot and issue-state revision tables; do not create full database
  copies for routine updates. Preserve unique historical rows before removing
  duplicate copies. Do not infer production resolution from local test passes.
- Current release target: **61**. `R61-AAB` tracks the owner-requested signed
  internal-testing bundle; `R61-INTERNAL-TEST` remains a separate Play-delivered
  verification step after owner upload. Preserve version60 and earlier evidence
  with its original artifact scope; a metadata bump does not rerun those tests.
  Keep the fix register in `play_reporting.db`
  (`record:release59-fix-register`), appending revisions with target version,
  stable attempt IDs, issue links, source/test references and validation state.
  Preserve existing `58-Rxx` IDs and historical evidence. Planned validation is
  the regular production-package release build delivered through Play Internal
  testing on Redmi, including in-place 58-to-59 upgrade and affected journeys.
  Track local checks, delivered-build validation and production follow-up
  separately; do not mark production resolution from local/internal-test passes.
- Version 60 follows the supported-language delivery defect found in Play59.
  Preserve the existing register and stable IDs; append target-version60 revisions.
  Owner direction: verify the language fix before building60 for owner upload.
  Use focused language packaging, offline picker, fallback and affected navigation
  regressions; retain unchanged earlier release evidence with its original scope.
  After owner upload, test the Play-delivered59-to60 update on both Redmi and
  Samsung, preserving data and running only relevant language/upgrade journeys.
  Local bundle checks do not complete this separate Play-delivery gate.
- Finish the evidence-backed issue fixes/review and required release testing
  before building or uploading the version 59 internal-testing candidate.
  Keep unresolved evidence gaps explicit; development test APKs are separate.

# Project regression testing

- Owner authorization (2026-11-11): Redmi is a dedicated test device. Any
  task related to testing on Redmi is authorized, including ADB interaction
  and changes to test settings or the app language, without renewed approval.
  Record the final state and preserve useful test data; this does not expand
  Samsung permissions or authorize release promotion.

- Current owner direction (2026-11-10): use only the Xiaomi Redmi for device
  testing while fixing release-58 issues; do not test on Samsung or emulators.
  Reconfirm its ADB identity each session (observed Redmi 7, API 29, `71a3063`).
  Preserve regular app data and use the isolated `notificationTest` variant.
  For version-59 Play Internal release testing, the owner explicitly authorized
  both Redmi and Samsung on 2026-11-11. Use Samsung's existing Play version58
  for the in-place upgrade and Redmi for Play installation/restore after its
  debug data is protected. Reconfirm both device identities; regular delivered
  builds are required for these journeys. Preserve data and distinguish upgrade
  evidence from fresh-install/restore evidence.

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
- Verify language delivery with an English-only bundle-generated split APK set.
  When following an unsupported device language, English is the default.
  Supported languages selected through the app picker must use their translations.
  Run `scripts/verify_bundle_languages.py` against its base/master APK and
  exercise a different language through the real app picker while offline.
  A universal/debug APK or a phone with that language split already installed
  does not cover this boundary: Play59 testing exposed missing picker languages.
  Keep this check separate from verification of the actual Play-delivered build.
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
- Keep test builds out of user-facing launchers: the `notificationTest` manifest
  must not expose HOME or LAUNCHER intent filters. Before manual handoff, verify
  that no "Comfer Notification Test" entry is launchable; retain test app data.
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
