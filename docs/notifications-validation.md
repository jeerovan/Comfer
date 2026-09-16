# Notification Inbox verification

This is the current test map and execution guide. It does not certify a release or record historical implementation runs. Expected behavior is in [Notification Inbox](../Notification-Features.md) and the [scenario matrix](../Notification-Test-Scenarios.md).

## Test map

| Scope | Test sources |
|---|---|
| Live identity, grouping, revisions, eligibility, reach | `NotificationModelTest` |
| Literal rules, scope/precedence, saved filtering | `NotificationRulesTest` |
| Configuration compatibility | `NotificationConfigurationMigrationTest` |
| Manual backup formats, portable settings, rollback/recovery, clean-data restore | `BackupRestoreManagerTest`, `NotificationBackupRestoreTest`, `NotificationBackupColdStartTest` |
| Saved ordering/grouping | `SavedNotificationRowsTest` |
| Local-time, overnight, timezone/DST boundaries | `QuietScheduleTest` |
| Cross-app listener/actions, history-on-open, snooze, recovery | `NotificationListenerIntegrationTest`, `NotificationListenerRecoveryTest` |
| Layout, selection, navigation, access controls | `NotificationInboxLayoutTest`, `NotificationInboxRotationTest`, `NotificationNavigationGestureTest`, `NotificationSettingsNavigationTest`, `NotificationAccessControlsTest`, `NotificationActionBarSpaceTest`, `NotificationViewChoicesTest` |
| Preview, swipe completion, small icons and contrast | `NotificationBodyPreviewTest`, `NotificationSwipeAnimationTest`, `NotificationSmallIconTest`, `NotificationIconContrastTest` |
| Saved interactions and storage/encryption | `NotificationSavedTabTest`, `NotificationHistoryStoreTest`, `NotificationHistoryCipherTest` |
| Shared app actions and rule editor/cards | `NotificationAppActionsTest`, `NotificationRuleEditorTest`, `NotificationRuleCardTest` |
| DND ownership, timer delivery, overlap, actual reboot | `NotificationQuietScenariosTest`, phased `NotificationQuietRebootTest` |

[JVM test sources](../app/src/test/java/com/jeerovan/comfer/) and [instrumentation sources](../app/src/androidTest/java/com/jeerovan/comfer/) define each test's setup, supported API range, assertions, and cleanup.

## Build and baseline fixture runner

Run with a configured Android SDK and project-compatible JDK. On this macOS workspace, Android Studio's bundled runtime is available through:

```sh
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
python3 scripts/test_notifications.py emulator-5554
```

Select the intended serial explicitly. The [runner](../scripts/test_notifications.py) installs app/test APKs and two synthetic fixture apps, grants fixture posting access when needed, builds/tests/lints, and writes logs plus a screenshot under `validation-artifacts/notifications/<serial>/`. Use `--skip-build` only with verified current artifacts.

The runner's fixed device suite contains ListenerIntegration, InboxLayout, InboxRotation, NavigationGesture, SwipeAnimation, and HistoryCipher. It does **not** run every notification instrumentation class in the table.

For an explicitly selected physical device on API 27+:

```sh
python3 scripts/test_notifications.py DEVICE_SERIAL --physical
```

Physical mode uses the separate `com.jeerovan.comfer.notificationtest` application and test package, with separate data and no Home role/Firebase initializer. It preserves the installed production launcher. Tests can temporarily change listener/policy grants and DND state; review the selected test's setup and cleanup. Fixture/test APK installation is not automatically undone by the runner. See [fixture controls](../notification-fixtures/README.md).

## Focused instrumentation

Build the appropriate variant and install its current app/test APKs plus any required fixtures before invoking a class. For the isolated variant:

```sh
./gradlew -PcomferTestBuildType=notificationTest \
  :app:assembleNotificationTest :app:assembleNotificationTestAndroidTest
adb -s DEVICE_SERIAL shell am instrument -w -r \
  -e class com.jeerovan.comfer.NotificationSavedTabTest \
  com.jeerovan.comfer.notificationtest.test/androidx.test.runner.AndroidJUnitRunner
```

Change the class to the intended suite. Some tests are emulator-only or have version-specific prerequisites; a class appearing in the test map does not imply physical-device compatibility.

`NotificationQuietRebootTest` must run as a sequence: `prepareFocusOnlyReboot` or `prepareScheduleAndFocusReboot`, actual emulator reboot, then `verifyAfterActualRebootAndRestore`. Use `restoreInterruptedRebootFixture` for interrupted cleanup. Do not run the whole class as a normal suite.

## Reporting checks

Record the source revision, build variant, device/API, exact methods, actual pass/fail/skip results, and restored state for any new validation. Inspect the generated report rather than assuming a requested filter ran every class.

Distinguish model tests, mocked/runtime-injected events, and actual device observations. Forced Doze under instrumentation does not establish every long-idle quota; a reboot test does not establish exact recovery latency. Separately inspect screenshots, TalkBack, large fonts, RTL, keyboard navigation, cross-profile/OEM behavior, and audible/vibration effects when relevant. No historical pass count in documentation should substitute for checks against the current build.

## Notification backup / restore checks

Run `BackupRestoreManagerTest` with the normal debug unit-test build. Build device tests with `-PcomferTestBuildType=notificationTest` and run `NotificationBackupRestoreTest` against the isolated `com.jeerovan.comfer.notificationtest` app. It intentionally rejects the production package because restore replaces local stores. Fixtures restore their previous local settings/Room state after each case.

For a fresh-app-data round trip, explicitly run `NotificationBackupColdStartTest#exportFixture` with instrumentation argument `notificationBackupStage=export`. Pull `notification-transfer.zip` from the isolated app's external files directory, clear only that isolated app's data, push the file back, then run `#restoreIntoFreshAppData` with `notificationBackupStage=restore`. Never clear the regular launcher for this test. These staged tests skip normal suite execution.

Verify the archive contains portable preferences and rule/schedule definitions, not notification bodies, collection cursors, runtime IDs, or permission grants. Restore must pause automation, refresh enabled history's cutoff, preserve settings for legacy archives with no section, and recover prior data after failed/interrupted writes. [Notification-settings backup evidence](tasks-validation.md#notification-settings-backup-evidence-retained-from-the-plan) records the executed validation and its limits; [Plan-Tasks.md](../Plan-Tasks.md) tracks phase status.

## Action-completed gesture guides — 16 September 2026

Follow-up: reuse the home-screen `LongPressHint` (pulsing hand and filling circular ring). Place swipe guidance on the first dismissible live card, independently of a protected first card; keep it available after long-press enters selection mode, including Saved cards. Shared thumb-reach state now retains the pulled-down offset when the action toolbar resizes its viewport, updating the pull limit without recreating state. Landscape/compact viewports with no reach allowance still clear it.

Follow-up validation: 8 guide/layout cases passed; the new real-notification case initially failed during listener setup because `cmd notification allow_listener` is unavailable on API 24. Updated that test's setup/cleanup to preserve and restore secure listener settings on older Android. Its first functional run showed a 16 dp viewport adjustment while the toolbar animated, failing an overly strict 2 dp assertion; the final test allows 24 dp of local adjustment and passed selection, deselection, and swipe-guide placement below an ongoing/protected first card. The jump to the top is prevented; exact stationary card geometry is not promised during toolbar animation. All 167 JVM tests passed, including resize preservation and zero-limit reset. Reports: `validation-artifacts/device-checkpoint/2026-09-16-inbox-selection-reach/`. Emulator updated; no Samsung installation or physical-device verification for this follow-up.

Replaced labelled, six-second auto-completing overlays with input-transparent, circular hand animations matching Journal. Live and Saved cards teach long-press selection followed by swipe deletion; rule cards teach swipe deletion. Removed the associated static gesture hints. Partial/rejected swipes, taps, elapsed time and navigation do not complete learning. Rule confirmation cancellation retains the guide; only a successful confirmed swipe deletion completes it. Inapplicable targets (selection mode, unavailable actions, modal confirmation) temporarily hide the animation without marking progress complete. Progress uses device-local `notification_gesture_guides_actions`; legacy flags are ignored because they cannot distinguish learned gestures from expired timers.

Validation: five instrumentation tests passed on API 24 (`NotificationGestureGuideTest`, `NotificationRuleCardTest`, `NotificationSwipeAnimationTest`): ten-second virtual waits, remount persistence, out-of-order actions, input transparency, partial/rejected swipes, confirmed/cancelled rule deletion, and left/right exit animation. All 166 JVM tests passed; debug APK built and installed on emulator. No pre-fix regression run or physical-device validation was performed for this change. Report: `validation-artifacts/device-checkpoint/2026-09-16-notification-guides/ui.txt`.

## Fixed module/page headers — 16 September 2026

Moved the shared Notification inbox title and settings/Saved page headings out of the LazyColumn into a fixed top header. All routes through NotificationInbox retain that module header, including app controls, history and settings subpages. Thumb reach now measures the body viewport below the header and above fixed bottom controls; scrolling and pull-down reach do not move titles. Updated layout checks to assert stationary headers and moving body controls, including compact portrait, large text and navigation across settings/history.

Validation: all 7 NotificationInboxLayoutTest/NotificationSettingsNavigationTest cases passed on API 24, including fixed header bounds in Inbox, Saved, settings root, quiet hours, filters, history and connection/privacy, plus focus/schedule navigation. All 166 JVM tests passed; debug build and whitespace checks passed. Emulator updated; Samsung unchanged. Report: `validation-artifacts/device-checkpoint/2026-09-16-inbox-header/ui.txt`.
