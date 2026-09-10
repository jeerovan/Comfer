# Notification implementation validation

Checkpoint: 8 September 2026. This is an initial implementation spanning phases 0–4, with an encrypted-storage feasibility boundary for phase 5. No phase or release gate is declared complete. The authoritative remaining work is in [Plan-Notifications.md](../Plan-Notifications.md).

## Passing automated checks

| Check | Result |
|---|---|
| Debug app and instrumentation APK builds | Passed |
| JVM suite, including existing notification/backup regressions | 81 tests, zero failures/errors |
| Android 7 / API 24, Small Phone emulator, 720 × 1280, density 320 | Full-height revision: 17 methods passed |
| API 37, Pixel 10 emulator, 1080 × 2424, density 420 | 15 test methods passed |
| Samsung Galaxy A30 / Android 11 / API 30, 1080 × 2340, density 420 | 15 existing methods + corrected focused swipe test passed |
| Isolated `notificationTest` APK build and lint | Passed |
| `:app:lintDebug` | Passed; warnings and English-fallback translation gate remain |
| `git diff --check` | Passed |

Both emulator runs use actual notifications from two separately installed fixture apps. Tests restore Comfer notification configuration and listener grants after each case. Test-owned DND rules and encryption keys are removed. Native snooze executes on API 37; API 24 exercises the unavailable path and does not run native snooze.

Coverage includes grouped children across apps; content updates and revision invalidation; individual dismissal without removing siblings; protected notifications; content exclusion from configuration; 100-update burst convergence; access revocation/reconnection; rejection of actions carrying an earlier connection identifier; expired-intent app fallback; native snooze; mutually exclusive double-tap selection; empty home entry; portrait configuration; 2× font scale; landscape and navigation preservation through production-activity recreation; focus activation/elapsed expiry; preservation of an overlapping automatic rule; and encryption round-trip, randomized ciphertext, tampering/identity rejection, bounds and lost-key failure.

The overlap fixture is a distinct rule under the test application's ownership domain. It validates that Comfer targets its own stored rule ID rather than globally switching DND off; it does not validate every other app or OEM. Timer expiry is simulated by advancing the stored elapsed deadline; this is not an overnight/Doze/reboot timing measurement. A 1,000-record check runs against the pure ledger, not 1,000 accepted Android notifications. Android can rate-limit source posts before listener delivery.

## Reproduce and inspect

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  python3 scripts/test_notifications.py emulator-5554
```

Use an explicitly selected emulator; the runner changes notification-access grants during testing. `--skip-build` is for a verified current build. See [fixture instructions](../notification-fixtures/README.md).

For an explicitly connected physical device on API 27+, use:

```sh
env JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  python3 scripts/test_notifications.py DEVICE_SERIAL --physical
```

Physical mode installs `com.jeerovan.comfer.notificationtest`, a separate debug-signed app with separate storage. It does not replace or clear the installed launcher. Its launcher icon opens the inbox directly; it has no Home intent filter and no Firebase initializer. The fixture apps generate synthetic notifications. Tests temporarily change only the test app's listener/policy grants, manipulate its own DND rules, and restore those grants and configuration afterward. DND assertions compare the effective filter before and after cleanup. Test output is streamed to the report file, with a 90-second per-test timeout.

Samsung Galaxy A30 (SM-A305F), Android 11 / API 30, 1080 × 2340, density 420 was connected on 8 September 2026. The initial combined run completed the ten listener/action/DND tests and empty-entry check, then stalled in the configuration layout test and was stopped. All three layout checks passed in a fresh process. The first isolated manifest registered another Home candidate; the original Comfer Home selection was restored and the test manifest corrected to remove Home registration. This interrupted run is retained as `instrumentation-interrupted.txt`; it is not a complete-suite pass.

The full rerun passed all 15 methods in 105.315 seconds, including native snooze, DND ownership/elapsed-expiry/overlap cleanup, portrait settings, 2× font scale, production-activity rotation and authenticated Keystore encryption. The earlier stall did not recur; its cause is unresolved. The Samsung portrait screenshot was visually inspected. After the suite, Comfer 47 remained installed and selected as Home, effective DND was off, and the test listener/policy grants were absent. The separate listener was then explicitly re-enabled and two fixture notifications posted for a hands-on reach check; no DND grant was retained for that check. Human reach comfort, audible/vibration behavior, real idle expiry and reboot remain unverified.

Local artifacts (ignored by Git):

- `validation-artifacts/notifications/build-final.txt`
- `validation-artifacts/notifications/emulator-5554/instrumentation.txt` and `portrait.png`
- `validation-artifacts/notifications/emulator-5556/instrumentation.txt` and `portrait.png`
- `validation-artifacts/notifications/RZ8M80E8ZPZ/instrumentation.txt`, `instrumentation-interrupted.txt` and `portrait.png`

Both portrait screenshots were visually inspected. The compact navigation-label wrapping found in the early prototype was corrected with labeled icon controls. No PDF was generated.

## Open gates

Reach across additional devices and grips, TalkBack usability, sound/vibration, Doze, actual reboot/time changes, work-profile/OEM restrictions, remaining API levels, 24-hour battery/performance, whole-launcher migration/regression and translation review remain unverified. Quiet scheduling uses an inexact idle-capable alarm and discloses that Android can delay boundaries. Manual focus ends on reboot; recurring schedules are recalculated. Missing access or cleanup failure remains visible.

Remaining software acceptance work includes detailed bulk outcomes and lifecycle persistence, richer group/profile presentation, and RTL/keyboard handling before introducing input screens. History capture, saved reminders/Undo, rules/presets, digest and notification-category backup/restore are not implemented. Configuration backups remain specified as unencrypted and freely shareable without credentials; the history cipher does not change that requirement.

## User reach confirmation — 8 September 2026

The user confirmed that notification actions, Settings and Back in the open Samsung Galaxy A30 portrait inbox are reachable with one thumb without shifting grip. This passes that hands-on check for this device and user, not the full DC-05 study (keyboard, other grips/devices and accessibility remain open).

## Gesture follow-up

The updated app passed 81 JVM tests and notification-test lint. On Samsung, the expanded run passed the 15 existing methods; the new swipe method initially failed because its assertion expected “Dismiss” instead of the actual localized “Dismiss notification” label. After correcting the test, the focused swipe method passed (`swipe-reveal.txt`). It verifies left/right action reveal, Cancel, and absence of source dismissal/snoozing from swiping alone. The earlier combined report retains that assertion failure; it is not reported as a full 16-method pass.

The API 24 follow-up initially encountered stale/dead listener bindings after APK installation. Test setup now toggles only Comfer’s already-granted component on API 24, allowing Android’s setting observer to release the dead binding, then invokes the existing Refresh/rebind route. Other listeners are preserved. The failed report is retained as `instrumentation-binding-failure.txt`.

Settings navigation now attempts channel → app notification settings → app details → general settings, stopping at the first successful launch. Cross-profile routing remains explicitly unavailable. OEM rejection of each settings destination has not yet been exercised.

The home-entry test verifies a permanent inbox entry without assuming Android’s own notification list is empty.

The final API 24 rerun passed all 16 methods together, with listener access already granted at the start. No new app code was changed during the binding and label/empty-row test corrections. The Samsung default Home was rechecked as the original Comfer, with effective DND off.

## 9 September 2026 — full-height layout

The portrait surface now fills the safe height. Scrollable top padding places initial content at the calibrated reach boundary; deliberate scrolling moves content into the upper viewport. Navigation and selected actions remain fixed at the bottom. New scopes/settings/menus reset to the reachable start, while rotation and ordinary updates preserve list position. Calibration labels now describe initial reach and content width. Requirements DC-02/DC-03/DC-05 and plan phase 2 reflect this revision.

Debug and isolated notification-test builds passed; notification-test lint passed. All 17 API 24 methods passed, including a new check for full-height bounds, reachable initial heading, heading movement above the initial boundary, stationary Back control and scope-change reset. The instrumentation screenshot captured a leftover fixture window and is not visual evidence for this revision. Direct real-activity screenshots (`fullheight-actual.png`, `fullheight-settings.png`, `fullheight-scrolled.png`) were captured and inspected for initial and scrolled layouts. The Samsung was not connected; its earlier user reach confirmation describes the prior bottom-panel design and does not validate the revised layout. No PDF generated.

## Settings reach and shared background — 9 September 2026

Main Settings now uses the same calibrated, scrollable portrait starting padding as the inbox, a full-height viewport, and a fixed bottom Back button. Landscape has zero starting padding. The inbox now uses ComferTheme and the Settings surface treatment (theme surface at 80% opacity over wallpaper), replacing the opaque elevated panel and scrim. Direct screenshots of both production activities were inspected.

Debug build and lint passed. Six existing Settings-launch/inbox-layout/rotation checks passed; the new Settings reach test passed on focused retry after correcting its uppercase-header assertion. It verifies reachable initial placement, upward content motion, stationary Back and activity closure.

Emulator cleanup removed the isolated notification preview, instrumentation package and both notification fixture apps. The main `com.jeerovan.comfer` installation and its data were retained. Future fixture testing must reinstall its test packages temporarily.

## Native Back only — 9 September 2026

Removed app Back buttons from Settings and the notification inbox. Settings relies on Android’s Back action; the inbox retains its native Back handler for nested navigation and exit. Eight targeted instrumentation methods passed, including injected system Back events that close Settings and return from inbox configuration before closing the inbox. Layout/rotation and repeated Settings launch checks passed, as did debug build and lint. The temporary instrumentation package was removed afterward; only the main Comfer installation remains on the emulator. Earlier visual evidence with an app Back arrow is superseded.

## Settings return transition — 9 September 2026

Settings previously used a dedicated task affinity, singleTask mode and the external-app launch helper’s NEW_TASK flag. It now uses ordinary internal activity navigation, matching the inbox, so Back reveals the existing home activity in the same task. Global/external-app transitions are unchanged. Seven targeted instrumentation methods passed, including the same-task/home-instance regression, Settings reach/native Back, repeated launches, historical activity compatibility and inbox Back/rotation. Debug build and lint passed. The emulator was updated and its temporary test package removed. Physical/OEM animation appearance has not been rechecked.

## Listener recovery — 9 September 2026

Reproduced the reported stuck “Reconnecting” inbox with notification access still granted and dead Android service bindings retained after package updates/instrumentation. Inbox entry previously did not request a reconnect, and Refresh only called requestRebind without timeout/fallback. Earlier shell permission resets masked this product recovery gap.

Home-row initialization, inbox resume and listener disconnection now request a bounded recovery. It first requests a rebind; on API 24–25, after five seconds without a usable snapshot, it refreshes the already-enabled component registration between DEFAULT and ENABLED. The service is never disabled and notification access is never changed. Android 7’s observed package-change delivery takes about ten additional seconds. A further twenty-second deadline covers that delay; exhaustion becomes a recoverable error with Retry and Android access-settings controls. Automatic requests are coalesced and rate-limited; a user Retry is explicit. Other Android versions use the regular rebind and timeout/settings route, without the Android 7 registration workaround.

The inbox no longer labels a disconnected state as “No notifications to view.” Null/failed snapshots cannot become healthy empty snapshots. Old listener instances cannot erase data belonging to a newer connected instance. Recovery logs contain only lifecycle categories, not notification contents.

The regression posts a real synthetic notification, opens the production inbox after an APK update, verifies the connected snapshot contains it, and asserts that the notification-access setting is unchanged. It passed twice (about 15.6 seconds); logs show request_rebind → refresh_enabled_registration → listener_connected. The synthetic notification is cancelled afterward. All 81 JVM tests, debug build and lint passed.

Android’s lifecycle contract requires waiting for onListenerConnected before reading notifications and permits requestRebind while disconnected: [NotificationListenerService reference](https://developer.android.com/reference/android/service/notification/NotificationListenerService). The registration-refresh fallback is based on the reproduced API 24 behavior, not a guarantee for every OEM.

The final cross-app suite passed all 18 methods after the recovery changes. Outside instrumentation, launching the inbox recovered automatically and displayed both existing Android notifications and a fresh Fixture Mail notification; direct screenshots were inspected and retained as `recovered-inbox.png` and `recovered-live-notification.png`. Fixture and instrumentation packages were removed afterward. The main Comfer app remains installed with the same notification-access grant, and the inbox was left connected. No shell permission reset was used to establish this final recovery.

## Group headers and selection gestures (2026-09-09)

Grouped mode now renders stable app headers with child counts and collapse/expand controls. Tap dispatches the source notification action; long press selects, exposes ring/solid-circle indicators, and shows Snooze/Priority/More actions. Multiple selection filters shared operations and requires every selected item to support snooze or bulk dismissal. Horizontal swipe directly dismisses eligible individual items. Android settings round trips retain selection and restore the selected card; inbox, configuration, and More retain separate scroll states.

Validation: 83 JVM tests passed, debug build and lint passed, and the complete API 24 notification suite passed all 20 tests. Two production-activity tests passed on Samsung SM-A305F/API 30 for tap/open, direct swipe dismissal, and returning from real Android channel settings to the selected card and menu. A null selection originally matched header rows whose notification field is null; `notificationAnchorIndex` now explicitly rejects absent keys and has regression coverage. Physical tests preserve the original 12 manual fixture notifications and only remove their temporary IDs. Instrumentation packages and emulator fixture apps were removed after validation; the Samsung fixture apps remain for manual testing.

## Selection presentation refinement (2026-09-09)

Indicators now draw at 14 dp (70% of 20 dp) in an always-reserved 48 dp slot, with no checkbox semantics outside selection mode. Preview wrapping is stable across selection. Selection mode derives from the selected set, including live removals; Cancel is only shown for multiple selections. Group and selected-count labels are removed, and group controls use accessible up/down carets. Batch action labels omit counts; operation-result feedback still reports requests and skipped operations.

Validation: debug build, 83 JVM tests and lint passed; all 20 existing emulator notification tests passed. Three production-activity Samsung tests passed, including fixed text bounds, single/multiple/zero selection, Cancel visibility, and automatic exit after the final selected source notification is removed. Temporary test IDs and instrumentation were removed; the original manual fixture notifications remain.


### Wrapping options and vertically centered selection indicators — 2026-09-09

Selection slots now center vertically against the complete notification card. Bottom actions, connection/reset controls, quiet-hours options and settings choices use wrapping layouts instead of horizontal scrolling. Appearance layout previews, shape choices, social links, widget choices and color-picker buttons follow the same rule.

Validation: debug app/test builds and Android lint passed. API 24 emulator passed the 21-test notification regression suite, including card/indicator center alignment, and the additional narrow-width settings test (all choices visible, multiple lines and successful selection). Updated main debug APK installed on Samsung API 30 with existing data preserved. Samsung instrumentation could not access Compose UI because the device remained locked; those four checks are not counted as passes. Temporary test packages were removed after testing.


### Swipe feedback and settings rings — 2026-09-09

Cards translate with horizontal drag input and animate back on cancellation or incomplete swipe. The quiet-hours checkbox now uses the shared 14 dp ring/dot inside a centered 48 dp slot; its full label row retains checkbox accessibility semantics.

Debug app/test builds and lint passed. Emulator: 21 notification regressions plus 2 settings control tests passed. Samsung API 30: all 5 navigation/gesture/settings tests passed, including visible movement before release, cancelled-swipe restoration without dismissal, ring centering beside a multiline label, and toggling from the full row. Installed the updated main debug APK with existing data preserved; removed temporary test packages and restored the charging wake setting. Samsung fixture apps remain available for manual testing.


### Full off-screen dismissal — 2026-09-09

Corrected premature removal at swipe release. A separate swipe container now completes translation beyond the root viewport before calling Android dismissal. It uses distance/velocity qualification, snapback, protected-item resistance, and recovery after rejected/unconfirmed removal. AOSP reference: https://android.googlesource.com/platform/frameworks/base/+/refs/heads/main/packages/SystemUI/src/com/android/systemui/SwipeHelper.java (animate to the full translation distance before onChildDismissed).

Build and lint passed. Emulator regression runner: 23 tests passed, including the new controlled-clock left/right off-screen completion checks. Samsung API 30: 5 tests passed, covering those checks, actual dismissal/cancellation, source opening, return from Android settings, and selection behavior. The legacy gesture test now waits for Compose animation completion before polling Android; its initial timeout was caused by not advancing the test clock. Installed the corrected main debug app; test packages cleaned up, Samsung fixture apps and existing configuration retained.


### Group title selection and Open cancellation checks — 2026-09-09

Group titles now toggle all current group children in selection mode, including collapsed children; the separate caret only changes expansion. Partial/full/empty state has checkbox semantics. Other groups keep their selections, and deselecting the last group exits selection mode. Updated the app guide.

Superseded validation contract: opening now requests dismissal after successful launch, with optional eligible History capture first. The previous keep-active-on-open regression has been replaced.

Validation: build and lint passed. Emulator: all 25 tests passed, including cross-app/collapsed-group selection and Open preservation. Samsung API 30: all 4 navigation/group-selection tests passed. Updated main debug APK installed with user data preserved. Test packages removed; Samsung manual fixture notifications retained.
