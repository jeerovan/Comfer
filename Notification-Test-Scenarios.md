# Notification quiet-hours, schedule and timer tests

Test environment: Android 7 / API 24 emulator (`emulator-5554`). Samsung is not used. Tests preserve and restore the emulator's notification configuration, existing Comfer rule/runtime, listener access and DND access grant. Isolation recreates the existing rule definition afterward, so its opaque Android rule ID may change. No wall-clock/time-zone changes or reboot are performed on the emulator.

## Automated scenario checklist

| ID | Scenario | Expected result | Method |
|---|---|---|---|
| Q01 | Focus duration 0 or over 24 hours | Reject; no timer created | Emulator |
| Q02 | Start focus without DND access | Explain access requirement; no timer created | Emulator |
| Q03 | Start 15-minute focus, replace with 30 minutes, end manually | Android DND activates; deadline changes; own contribution ends | Emulator |
| Q04 | Let a real one-minute timer expire | Android alarm/receiver clears own DND contribution without manual reconciliation | Emulator; waits up to 100 seconds |
| Q05 | Active full-day schedule overlaps focus | Ending focus leaves scheduled quiet active | Emulator |
| Q06 | Pause/resume an active schedule, then disable it | Pause removes own contribution; resume restores it; disabling ends it | Emulator |
| Q07 | Upgrade a retired visibility-only schedule | Disable the obsolete schedule without activating DND; retain genuine DND schedules | JVM migration test (replaces retired visibility test) |
| Q08 | Disable Comfer's automatic rule in Android | Reconciliation reports disabled and does not silently re-enable; explicit enable works | Emulator |
| Q09 | Wall-clock deadline becomes misleading | Elapsed-time deadline still bounds focus duration | Emulator runtime injection, no system clock change |
| Q10 | Runtime belongs to a previous boot | Manual focus ends | Emulator boot-marker injection, no actual reboot |
| Q11 | Same-day schedule start/end | Start inclusive, end exclusive; next boundary correct | JVM |
| Q12 | Overnight schedule on selected weekday | Early morning belongs to previous starting weekday | JVM |
| Q13 | Equal start/end times | Full local day, ending at the next day's boundary | JVM |
| Q14 | Disabled schedule / no selected weekdays | Never active, no boundary scheduled | JVM |
| Q15 | Same instant in UTC and Asia/Kolkata | Recompute using local schedule time | JVM |
| Q16 | Spring-forward and fall-back transitions | Local end boundary remains correct | JVM |
| Q17 | Schedule begins ten minutes in the future | Reports Scheduled with future boundary; Android DND remains unchanged | Emulator |
| Q18 | Focus overlaps an independent active DND rule | Ending focus preserves that rule and Android remains quiet | Emulator; second rule, separate condition |

## Additional device coverage

The API 24 run exercises the legacy condition-provider path. API 29+ uses `setAutomaticZenRuleState`; the API 37 follow-up below exercises that path. Alarm delivery during prolonged Doze, OEM battery restrictions, isolated process-death recovery, audible alarm/call priority exceptions, and interaction with a rule owned by another package require additional device scenarios. Q18 covers an independent condition using the same provider, not cross-package ownership. A positive quiet status alone is insufficient: device tests also check Android's interruption filter where applicable.

## Results

- Build and six JVM schedule tests passed, covering Q11–Q16.
- Full emulator run: nine of ten test methods passed. Q08 initially failed while reading Android rule state immediately after an update.
- Q08 passed in isolation after adding an explicit wait for Android to report the rule disabled. The three-method focus/external-disable/future-schedule sequence also passed after the synchronization change. All ten distinct emulator methods have now passed; the original full run was not entirely green.
- Q04 passed twice using a real one-minute timer and no forced reconciliation.
- Q09/Q10 are runtime simulations; they do not establish actual reboot or system-time-change broadcast behavior.
- Initial harness failures were corrected: API 24 grants policy access through notification-listener access; rule isolation must prevent production orphan-rule recovery from adopting a pre-existing rule.
- No product code has been changed by this testing task. Android rule-state timing remains an observation to monitor; one passing retry alone does not prove the earlier failure cannot recur.

Evidence logs (local): `/private/tmp/comfer-quiet-scenarios-final.log`, `/private/tmp/comfer-quiet-disable-retry.log`, `/private/tmp/comfer-quiet-sequence-retry.log`. JVM report: `app/build/test-results/testDebugUnitTest/TEST-com.jeerovan.comfer.QuietScheduleTest.xml`.

## Reproduction

Build with the project Android Studio JBR: `./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --tests com.jeerovan.comfer.QuietScheduleTest`.
Install the main and test debug APKs on an emulator, then run `adb -s emulator-5554 shell am instrument -w -r -e class com.jeerovan.comfer.NotificationQuietScenariosTest com.jeerovan.comfer.test/androidx.test.runner.AndroidJUnitRunner`.

This suite temporarily controls emulator DND/listener grants and Comfer’s automatic rule. Do not run it on a physical/personal device. Android 7 grants are manipulated through secure settings; newer Android grant behavior needs verification before reusing this fixture there.

## API 37 / Pixel 10 follow-up

Environment: `emulator-5556`, Android 17/API 37 (16 KiB page-size image). Modern grants use `cmd notification allow_dnd/disallow_dnd`; the suite does not write legacy secure policy settings on this version.

| ID | Scenario | Expected result |
|---|---|---|
| Q19 | Force deep Doze while a one-minute focus timer is active | Verified IDLE state; timer contribution expires through AlarmManager/receiver within the test observation window; forced idle and simulated battery state restored |
| Q20 | Reboot with manual focus only | Boot count changes; manual focus does not survive; Android returns to its prior DND filter |
| Q21 | Reboot with manual focus and full-day recurring schedule | Manual focus ends; recurring schedule and its Android DND contribution recover automatically |

Reboot tests use explicit prepare/verify/restore instrumentation phases with a local recovery fixture. Do not run the entire `NotificationQuietRebootTest` class as an ordinary suite: run one prepare method, reboot, then `verifyAfterActualRebootAndRestore`. `restoreInterruptedRebootFixture` cleans up an interrupted sequence.

The forced-Doze case runs with instrumentation attached. It does not prove delivery under every long-idle quota, repeated alarm or OEM restriction. Reboot verification exercises Android boot/provider recovery without explicitly calling reconciliation from the verifier; instrumentation can cause provider rebinding, so it does not establish a precise boot-to-recovery latency.

### API 37 results

- Main suite: **9 passed, 2 failed** (11 methods).
- Both failures concern timer delivery: the normal and forced-Doze one-minute timers still reported ACTIVE and Android priority DND at the **100-second cutoff**. These failures are retained as timing findings, not converted to passes by increasing the original timeout.
- Captured Android alarm state shows `setAndAllowWhileIdle` with an approximately **44.978-second inexact window** after the requested expiry. Evidence: `/private/tmp/comfer-api37-doze-alarms.txt`. This supports OS batching as a cause; it does not prove every possible delay has this cause.
- **Q20 passed:** after a real reboot, Android DND was off before verification and manual focus was zero.
- **Q21 passed:** after a real reboot, Android DND remained active for the recurring schedule, while manual focus was zero.
- The separate extended Doze observation completed: a **60-second timer ended after 105.082 seconds**, about **45.082 seconds late**. Android DND then returned to its baseline. This confirms eventual delivery in this run, but does not resolve either strict timing failure.

Main log: `/private/tmp/comfer-api37-quiet-tests.log`. Reboot logs: `/private/tmp/comfer-api37-reboot-focus-verify.log`, `/private/tmp/comfer-api37-reboot-schedule-verify.log`. Extended observation: `/private/tmp/comfer-api37-doze-observation.log`.

Final cleanup verified: Android `zen_mode=0`, forced idle `false`, quiet runtime empty; reboot fixture removed. Samsung and the API 24 emulator were not changed during this follow-up.

### Open issue: delayed timer expiry on API 37

A user-selected duration currently specifies the requested expiry, not a guaranteed Android delivery time. Schedule boundaries use the same alarm mechanism and may also be delayed; this is an inference from their shared code path, not a measured schedule-boundary failure. Evaluate precise-alarm eligibility/permission and fallback UX before promising timely termination. The test work has not added a new permission or changed production scheduling behavior.

## Timer precision fix validation

Production scheduling now uses elapsed-realtime `setExactAndAllowWhileIdle` when permitted and a separately identified inexact fallback. Android 12+ offers optional Alarms & reminders access in Focus timers and device-quiet schedule settings. Earlier Android versions do not require this additional grant. Both alarm IDs are cancelled on expiry/reset and permission-grant broadcasts reschedule the current boundary.

Measured one-minute timers after the change:

| Environment | Mode | Observed elapsed |
|---|---|---:|
| API 37 Pixel | Precise access, normal | 60.084 s |
| API 37 Pixel | Precise access, forced deep Doze | 60.091 s |
| API 24 emulator | Normal; no extra grant needed | 60.115 s |

- API 37: four checks passed (normal timer, Doze timer, pause/resume with schedule overlap, independent active DND rule).
- API 24: timer and schedule-overlap checks passed after an emulator reboot cleared the stale condition-provider binding seen after APK replacement. The initial two failures remain in `/private/tmp/comfer-precise-api24-tests.log`; successful rerun: `/private/tmp/comfer-precise-api24-retry.log`.
- Revocation verified through Android alarm inspection: the exact alarm disappeared and the separate inexact fallback remained scheduled. This verifies fallback retention, not exact expiry after revocation.
- Grant while focus was active automatically added an exact alarm (`window=0`, `exactAllowReason=permission`) without manually reconciling from the test.
- Build and Android lint passed. Pixel exact-alarm app-op is restored to its prior default; temporary rules, DND grants, forced Doze and test packages are cleaned up.

The earlier 105.082-second delay is addressed **when precise scheduling is available**. Without that access, batching remains expected and is explained in the UI. Exact idle alarms also remain subject to Android quotas; these measurements are not a hard real-time guarantee.

Evidence: `/private/tmp/comfer-precise-pixel-tests.log`, `/private/tmp/comfer-precise-pixel-alarms.txt`, `/private/tmp/comfer-precise-revoked-alarms.txt`, `/private/tmp/comfer-precise-granted-alarms.txt`, `/private/tmp/comfer-precise-final-build.log`.

### History interaction parity — 2026-09-10

- API 37 emulator: 10 passing Compose tests across saved-tab interactions, swipe completion, body previews, and action-bar size animation (`/private/tmp/comfer-history-parity-emulator.log`).
- Final build: saved-tab suite passed again, 4 tests (`/private/tmp/comfer-history-parity-final-emulator.log`). Covers tab/search navigation, tap/open and long-press selection, swipe deletion, preview expansion before opening, selection taps avoiding app launch, group selection, collapse/expand preserving selection, and automatic exit when deselected.
- Three JVM grouping tests passed: chronological posting-time order, collapsed groups, pinned app order, separate profile groups, and stable row keys. Full JVM suite, debug build, and lint passed (`/private/tmp/comfer-history-parity-verified-build.log`).
- Manual follow-up: on Samsung, select multiple saved copies, verify Delete asks for confirmation and Cancel/Back exits selection; return from Settings with a selection and verify its card/group is in view. Check app launching for installed, removed, and other-profile apps. Bulk persistence failures were not simulated in this UI test run.

### Three-tab inbox and open-then-dismiss — 2026-09-10

- Build, lint and full JVM tests passed; two migration tests verify that obsolete visibility rules are discarded rather than converted into cancellation, old visibility-only schedules do not enable DND, and genuine DND settings/current rules survive reload.
- API 37: successful open removes the live fixture and exposes its saved copy, cancelled-intent fallback opens the app then dismisses, and protected opening leaves the notification active. These three lifecycle cases passed in `/private/tmp/comfer-open-dismiss-api37-lifecycle.log` (the initial failed-launch setup in that run was rejected by Android).
- API 37: failed opening leaves its source notification active, verified with a fixture that disables/restores its own launch activity (`/private/tmp/comfer-open-dismiss-failed-open-verified.log`, 1 passed). Android 17 rejects shell component-state changes, so the earlier shell-based fixture setup was replaced.
- Seven UI checks passed across Saved (4), rule-editor consent (1), contextual settings navigation (1), and App guide (1). Logs: `/private/tmp/comfer-open-dismiss-final-ui.log`; its initial unavailable-launch setup failure was resolved by the separate passing run above.
- API 24: source PendingIntent deliberately performs no cancellation; Comfer still dismisses after launch and retains History. The same test verifies rule creation does not dismiss existing matches and fresh matching posts are dismissed (`/private/tmp/comfer-open-dismiss-api24.log`, 1 passed). This self-posting test is API <33 only because the launcher does not request posting permission; modern coverage uses separately permitted fixture apps.
- No real account notifications were generated. Test configuration/access was restored, temporary fixture/test packages removed, and Samsung received the debug APK in place. Protected/redacted content exclusions and optional History consent remain in effect.

### More actions stale selection notice — 2026-09-10

A changed/removed selection notice persisted after selecting a fresh notification and appeared on its More actions screen. New individual/group selection and entering More actions now clear only this obsolete selection notice; unrelated failures and subsequent real invalidations still appear.

API 37: two tests passed (`/private/tmp/comfer-selection-notice-verified.log`). The new regression opens More actions, updates the selected source, verifies real invalidation, then reselects the updated notification and verifies More actions is usable without the old warning. The group-selection regression now counts children rather than Android-generated summary records. Debug build and lint passed; temporary fixture/test packages were removed after validation.

### Content rules from History — 2026-09-10

API 37: seven tests passed (`/private/tmp/comfer-saved-rules-tests.log`): saved app/profile and title/message seeding, saved-text preview and test-only saving; separate auto-dismiss consent; History → selection → More actions → editor → native Back twice restoring the selected card; plus four existing saved-card/search/grouping/gesture regressions. Build and lint passed (`/private/tmp/comfer-saved-rules-build.log`). The reference uses saved text only, without a historical channel or Android action handle. Temporary fixture/test packages were removed after testing.

### Shared More actions route — 2026-09-10

Active and History now render a single `NotificationAppActions` component through one route, sharing rule creation, app protection and Android settings dispatch. API 37 checks passed for settings-intent channel/app scope, two editor/consent cases, saved-source navigation plus protection/pruning/unprotection without losing app controls, and live selection-warning recovery (five passing checks in `/private/tmp/comfer-shared-actions-tests.log`). The native Android settings return initially failed only because the test recognized `mResumedActivity` but Android 17 emits `topResumedActivity`; both forms are now supported and the focused retry passed (`/private/tmp/comfer-shared-actions-navigation-verified.log`). Build and lint passed. Temporary fixture/test packages were removed after verification.
