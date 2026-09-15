# Development progress

Current feature and release status, updated 2026-09-15. Maintain this document as
a concise reference: what is available, restrictions, attempted alternatives and
remaining work. Keep implementation discussions and detailed test output out of
this file.

## Journals — first emulator test build (15 September 2026)

English first-test implementation is available under `journals/`: separate Room storage, durable drafts, dated feed, managed JPEG/PNG images, in-place editing, Trash/Undo, dictation protocol and Android adapter, optional device-credential gate and portable Journal backup section. Search long-press now opens Journal by explicit interim approval; ordinary tap remains Search. The full four-module home folder depends on Notes and is not shipped yet.

See [Plan-Journals.md](Plan-Journals.md) for phase gates and [implementation/validation](docs/journals-implementation.md) for actual attempts and limitations. Journal acceptance is not complete. Debug build installed on the API-24 emulator with normal app data preserved. Final JVM suite: 166 passed; targeted Journal instrumentation: 28 passed, plus subsequent cold-start, protected fresh-install transfer and interrupted-restore checks. Real speech-provider and device-credential acceptance remain open. Samsung now has the signed Journal release test build installed fresh following user approval; backup restoration and hands-on acceptance remain pending. Comfer source version is 50 / 50.0.

Journal UI follow-up: date navigation is above the composer, with horizontal day swipes, centered Today/date picker and a trailing Settings/Archive menu. Header and entry actions are simplified; edit Cancel is a cross icon; successful creation scrolls to the latest entry. Archive retains deleted entries for seven days with recovered-edit safeguards. JVM suite passed (166); emulator storage/Activity checks passed and all five UI tests passed after correcting the menu label. See the Journal validation record for the failed attempt and artifact identity.

Journal opening refinement: fresh openings align the newest entry at the bottom, including short feeds. Empty input displays a fresh non-repeating prompt directly; rotation preserves it and existing draft content remains intact. Journal settings’ protection switch is 70% size. See the Journal validation record for regression evidence.

Journal date/time refinement: small centered date separators sit outside content cards; localized time sits at the card’s trailing bottom edge, without “Written”. Users can edit a saved timestamp with check/cross controls. The new-entry clock control has been removed; any previously selected composer time override is cleared on opening. Draft timestamps survive recovery/backup through Room schema 4’s non-destructive migration. Defaults remain current date/time; historical composer days use the current local time until explicitly changed.

Journal image preview: image taps now open a secure full-screen viewer with pinch zoom in/out and bounded drag pan; double taps do not zoom or reset. An X icon, Change text button and delete icon are at the bottom of the preview; the image bottom sheet is removed. Saved-image changes retain edit cancellation and image-only deletion safeguards.

Journal delete-conflict fix: reproduced a swipe callback retaining the entry’s pre-edit revision. Swipes now use the current row; exact repeat deletion requests are harmless. Revision protection remains enforced for newer edits, restored entries and backup replacement. Reproduction and regression outcomes are recorded in the Journal validation log.

Journal feed anchoring: reverse layout with newest-first items makes the newest entry the bottom origin, while older entries remain visually above newer ones. Archive and Journal navigation no longer share a list position. Removed the startup scroll-to-last workaround; date headings and paging controls follow the reversed index order. An Archive-return regression reproduced the old failure before the fix.

Samsung release update attempt (15 September): signed release 50.0 built and verified (48 manifest components), but Android rejected in-place installation because its certificate differs from Samsung’s currently installed Play build. Existing app/data remain intact; no uninstall performed. APK and mapping preserved under `app/release/50/samsung-ac20a72/`. Following explicit user approval and backup confirmation, the existing app was uninstalled and this release installed fresh successfully. Version 50 / 50.0 and non-debug flags verified; cold launch returned Status: ok. User will restore the backup.

Journal theme correction: translucent root surface now explicitly supplies onSurface foreground, matching Inbox/Tasks. Replaced fixed-light native date/time and security-setup dialogs with Material Compose dialogs; compact input supports short/landscape windows. Theme propagation and picker cancellation regressions are recorded in the Journal validation log. Samsung is not updated by this follow-up.

## Current state

Version **49 / 49.0** includes follow-up fixes for the Notification Inbox's app
language and the Notifications subtitle's theme color. The subtitle now says
“home screen” in English and all 34 supported translations. Samsung's installed
debug build includes these fixes and bounded wallpaper decoding; user acceptance
is pending. Version code is unchanged.

Tasks background follow-up (14 September) matches Inbox’s 80%-opaque theme
surface over wallpaper. Debug build passed; this follow-up is not installed on
Samsung. Always update the emulator after app changes; update Samsung only on explicit user request.

Tasks is available in the English test build. Its former home Search long-press
entry is temporarily assigned to Journal for testing; the upcoming module menu
will provide a Tasks icon. The latest Tasks debug build is installed on the API-24 emulator and Samsung
Galaxy A30 (SM-A305F), with existing app data preserved. Samsung installation
and Tasks launch succeeded on 13 September; hands-on acceptance remains pending. Version remains
**49 / 49.0**. Tasks release acceptance remains open, and the earlier 34-locale
coverage statement below does not include the new Tasks labels.

The previous signed APK/AAB in `app/release/49/` predate these fixes and are stale.
Refresh signed artifacts and their release checks after acceptance. Nothing has
been uploaded; the user will perform the production release. No issue is marked
production-verified.

Inbox, guides, history/rules, weather, backup/restore, folder controls and
accessibility labels now have complete resource coverage across all 34 non-English
locales. Guides use the actual translated button labels. The language picker
includes the seven previously omitted locales; Hebrew and Indonesian use the
Android-compatible resource folders, and Uzbek follows the existing Latin script.

Shared layout: [thumb-reach behavior](docs/thumb-reach.md) now starts Inbox, launcher Settings, Tasks browsing and Tasks settings at full height; reach space appears only during downward scrolling at the top.

Scroll-to-reach validation (14 September): **155 JVM tests passed**; 21 distinct API-24 layout/gesture/guide cases passed across the initial run and a focused Inbox rerun after updating obsolete initial-position assertions. Debug/test builds passed. Physical gesture comfort remains pending; Samsung unchanged.

## Available features

### Launcher

| Feature | Current behavior |
|---|---|
| Home gestures | Configurable left/right and existing quick-pattern actions. Recognized left/right, circle, four corner patterns and Inbox return each request one system haptic pulse on release, respecting device feedback settings. The shared parent accepts swipes starting over app icons or Search in CircularLayout and FiveColumnLayout; taps and child scrolling keep their own handlers. |
| Inbox gesture | One finger down, then back up, opens Notification Inbox from the home quick list. Fires on release; incomplete deliberate returns cancel without also triggering swipe up/down. |
| Home gesture guides | Swipe up → long-press settings → long-press widgets → double-tap Recents → Inbox down-and-return → tap clock → long-press clock. Inbox is shown even without notification access; the Inbox screen requests access when opened. Clock steps are skipped when no built-in clock is shown. Its text-free hand animation follows a 120 dp path across Search in both quick-app layouts, raised for navigation-bar clearance and repeats until the gesture is completed correctly; completion is remembered. |
| App drawer | U-shaped layout, adjustable scrolling sensitivity, fling interruption, centred-app double-tap launch and folders. Horizontal AppDrawer always uses automatic black/white titles sampled from the local wallpaper region, updating after scrolling settles; system wallpapers use available global color hints. Theme/day-night settings cannot override title contrast; unavailable wallpaper data falls back to white. Contrasting title shadows remain in normal and reorder modes. |
| App search | Search installed apps and contacts; launch apps or select a contact. |
| Appearance | Configurable icon size/shape, icon packs, themed icons and wallpapers. |
| Widgets | Custom widget screens, editing, positioning, resizing and deletion. |
| Wallpaper | Local/network wallpaper support and scheduled automatic wallpaper work. Decoding and color extraction bound both image dimensions and memory use; allocation failures retry at lower resolution. |
| Backup and restore | Configuration backup and restore through Android document pickers. |
| App launch animation | App windows expand from the tapped icon; the drawer's centred-app double tap uses the same effect. |

Automatic drawer-title contrast validation: 160 JVM tests passed, plus 2 API-24 emulator checks for scroll freeze/fallback and actual wallpaper-file cache refresh. Debug/test builds and whitespace checks passed. [Behavior and platform limits](docs/drawer-title-contrast.md). System WallpaperColors callbacks on newer APIs and physical-device appearance remain unverified in this revision.

### Notification Inbox

Version 50 feedback: rule cards now edit on tap. One-time, touch-transparent hand guides teach rule swipe deletion and notification hold-selection/swipe deletion (live and saved). Guide progress is device-local; deletion safeguards remain. Debug/test builds and 14 API-24 guide/rule/saved-card/swipe/layout tests passed. New guide labels currently use English fallback. Samsung installation remains the earlier release until explicitly requested.

| Feature | Current behavior |
|---|---|
| Entry | Tap the home notification row or use the down-and-return gesture. |
| Opening animation | Slides from bottom to top over a stationary home screen in 300 ms. |
| Live view | Grouped or chronological notifications, expandable previews and pinned apps. |
| Notification actions | Open, swipe to dismiss, selection, confirmed bulk dismissal and 15-minute snooze where supported. |
| Saved history | Opt-in capture, search, grouped/chronological views, per-app exclusions and deletion. Retention choices: 24 hours, 7 days or 30 days; maximum 500 copies. |
| Content rules | Test-only previews and confirmed automatic dismissal with app/channel scope, phrase matching and exceptions. |
| App controls | Pin apps, protect them from dismissal, and open Android notification/sound settings. |
| Quiet hours | Recurring weekday schedule and 15/30/60-minute focus timers. |
| Connection controls | Connection status, refresh/recovery, pause automation and reset. |

See [Notification Inbox behavior](Notification-Features.md) and
[storage/privacy](docs/notifications-storage.md) for the full feature reference.

### Tasks and reminders — test build

| Feature | Current behavior |
|---|---|
| Entry and layout | Long-press home Search in both layouts; normal tap/folder Close preserved. Browsing and Tasks settings open at full height with content at the top; pull down at the top to reveal the 360 dp bottom-reach area, then scroll up to remove it. Opens with Inbox’s 300 ms bottom-up slide over a stationary underlay. No home Tasks panel or text-guide/Got it prompt. One-time visual list-name tap, swipe and hold-drag hints teach list editing, task deletion and reordering. |
| Tasks and lists | Local capture/edit, stars, cross-list search, views, wrapped list/sort sheets and shared add/rename sheet. Guard final list; deletion offers Deleted/Undo for five seconds. |
| Cards and gestures | Incomplete and Completed each use one enclosing card. Completed is hidden when the current list/Starred/search has no completed tasks. Both support matching drag previews in My Order, animated neighbors and cancellation. Swipe deletes; taps open details without a double-tap delay; explicit Star buttons handle starring; stationary hold does nothing. |
| Details | Title/description, due date/time and frequency summary. Schedule/Star/Move/Cancel/Save icons; Move opens a destination sheet and Save commits. No three-dot, completion or Delete button. |
| Settings | Notification/exact-alarm status, date-only reminder time and privacy. Switch visuals are 70% with full touch targets. No Manual order, panel options or usage guide; Notification settings is not duplicated on Reminder Date. |
| Reminders | Check-in-ring notification icon (including summaries/private versions), calendar recurrence, versioned notification actions, snooze, exact/inexact alarm fallback and reboot/permission reconciliation. OEM/permission delivery limits remain. |
| Persistence and backup | Private Tasks Room schema 4; manual archive format 3 includes Tasks and notification configuration with rollback/recovery. Notification configuration remains SharedPreferences, not Room. System/cloud backup stays disabled. |

[Tasks specification and UI](Tasks-Features.md), [trackable plan](Plan-Tasks.md),
[storage/backup design](docs/tasks-storage.md), and [validation evidence](docs/tasks-validation.md)
are the consolidated references. Subtask UI remains deferred, with existing data
preserved. Settings list ordering and the home panel are removed scope.

## Restrictions and unavailable features

| Area | Restriction |
|---|---|
| Circular app expansion | Not supported for another app's live window through the public launch APIs available to Comfer. The current expansion remains rectangular. |
| Reverse app-to-icon animation | Not implemented. Controlling another app's return window requires privileged/system Recents integration; ordinary Comfer installations leave this to Android. |
| Animation appearance | System animation settings, destination apps and OEM behavior can alter the result. |
| Samsung restore | Locate the ZIP, **long-press to select**, tap **Select**, then confirm **Restore**. Tapping the file opens the archive instead. |
| Notification access | Live Inbox requires notification listener access. Quiet hours requires separate DND access. |
| Notification content | Android-hidden/redacted content cannot be reconstructed. History cannot recover notifications dismissed before capture was enabled and may have capture gaps. |
| Protected notifications | Calls, alarms, media, navigation, ongoing/non-clearable notifications and protected apps have restricted dismissal/history actions. |
| Saved notifications | Saved copies open the source app, not the original notification action. History is excluded from configuration backups and hidden while locked. |
| Screenshots/recordings | Secure Inbox/history rendering can appear black in captures. |
| Notification controls | Pinning does not change Android importance or sound. Comfer cannot directly mute another app or guarantee prevention of the original alert sound through dismissal rules. |
| Android compatibility | Native snooze requires API 26+. Precise quiet-hour boundaries depend on alarm access; fallback scheduling may be delayed. Cross-profile app/settings fallback is unavailable. |
| WorkManager compatibility | Periodic wallpaper scheduling is disabled when the required API-34+ framework method is missing; launcher startup remains available. |
| OEM widgets | Known unsafe Honor/Vivo providers are blocked. Other supported widgets remain available. |
| Honor verification | Samsung/emulator results cannot verify the affected Honor firmware behavior. |

## Tried or evaluated alternatives

| Attempt | Outcome |
|---|---|
| Clip-reveal app launch | Replaced by the current icon-origin scale-up animation. |
| Circular live-window expansion | Evaluated; unavailable through the supported public launch APIs. No circular version was built or device-tested. |
| Expanding circle containing only the icon | Possible approximation, but not implemented; it would not display the live app inside the circle. |
| Reverse live-window collapse into icon | Evaluated; unavailable to an ordinary Comfer installation. |
| Missing WorkManager method simulation | Healthy and deliberately missing-method cases passed on the API-37 emulator. This is controlled compatibility coverage, not affected-OEM verification. |

## Validation and remaining work

First-open Tasks list-name guide (14 September): tap pulse, timeout/persistence, tap-through to Rename/Delete and final-list protection verified. 152 JVM tests passed; 24 UI cases passed across final run and selector-correction rerun, plus 3 migration paths and backup. Schema 4 preserves earlier guide progress. Emulator updated; Samsung unchanged.

Contextual Tasks gesture hints (14 September): **152 JVM tests passed**, non-destructive schema 1→3/2→3 migrations passed, and focused guide/layout/backup rerun passed **7 tests** after fixing wallpaper preservation in a test fixture. Final **4 guide UI tests passed**, including actual hand movement, persistence and input transparency. Emulator updated; Samsung unchanged.

Task double-tap starring removed (14 September): **20 API-24 gesture/UI tests passed**, including explicit Star, tap navigation, swipe cancellation and drag ordering. Build passed; emulator updated, Samsung unchanged.

Scheduled Tasks notification check (14 September): **148 JVM tests and 14 API-24 persistence/reminder tests passed**. New regression waits for a real saved date/time alarm, invokes the actual notification Complete action, and verifies persisted completion, notification cancellation and repeated-action safety. Feature was already implemented; only regression coverage/documentation changed. Samsung not updated.

Home haptic feedback (14 September): debug/test builds and **11 API-24 gesture/routing tests passed** (HomeGestureInputTest and InboxHomeGestureRoutingTest). Feedback is requested once in recognized left/right, circle/corner and Inbox callbacks. Incomplete/cancelled paths retain existing no-action behavior. Physical vibration strength/feel was not tested; Samsung was not updated.

Tasks latest check (14 September): empty Completed card regression reproduced
before the fix; all **16 Tasks UI tests** passed afterward on API 24.

Earlier Tasks checkpoints (13 September): settings cleanup passed **14 UI tests**
on API 24; the subsequent thumb-reach correction passed **3 layout tests**.
Debug/test builds passed and the emulator was updated in place. Earlier **148 JVM
tests**, persistence/reminder/backup checks on API 24 and Samsung API 30, and
bounded preview API-37 permission/Doze checks are retained in the Tasks validation
document. These are separate checkpoints, not a final all-device run.

Tasks phase 9 remains open for hands-on TalkBack/focus, final gesture comfort,
broader UI/device coverage, physical performance and release acceptance. Manual
document-provider interaction, long-duration OEM restrictions and actual power
loss during a write are not certified. Subtask UI has its own unchecked later
phase. Refresh signed artifacts only after the final acceptance gate; historical
version-49 release checks below predate Tasks and do not certify this build.

Settings GitHub icon: white circular background with 4 dp inner padding for
contrast in both light and dark themes.

Inbox guide without an access requirement: **127 JVM tests** and **4 Inbox guide tests per device** passed
on Samsung API 30 and emulator API 37. Inbox precedes both clock guides regardless of notification access;
completion flags are preserved. Samsung has the updated build. Evidence:
`validation-artifacts/device-checkpoint/2026-09-12-guide-no-access/`.

Home swipe routing: swipes beginning on Search/app icons now reach the shared
parent in both quick-app layouts. **14 input/guide tests per device** passed on
Samsung API 30 and emulator API 37; **126 JVM tests** and lint passed (0 errors,
200 warnings). Samsung has the updated debug build. User acceptance is pending;
signed release artifacts remain stale. Evidence:
`validation-artifacts/device-checkpoint/2026-09-12-home-swipe/`.

Latest Inbox fixes: **120 JVM tests**, **4 translation-checker tests**, resource
validation for all **34 locales**, debug builds and lint passed (0 errors,
201 warnings). Samsung API 30 and the API-37 emulator each passed **9 focused
instrumentation tests**. The final isolated actual-activity locale test also passed
on API 24, 30 and 37, switching German/Arabic and reopening twice per language.
On API 24 the same test failed before the fix (German requested, English loaded).
Emulator screenshots confirm translated Inbox rendering; Samsung screenshot capture
returned no image, so its visual theme/layout acceptance remains with the user.

Wallpaper memory fix: an oversized-image regression reproduced a **128 MiB**
allocation failure on API 24's **48 MiB** app heap. Bounded decoding removes the
second resize bitmap and handles allocation failure with smaller retries.
**126 JVM tests**, debug builds and lint passed (0 errors, 200 warnings).
**3 wallpaper instrumentation tests per device** passed on API 24, Samsung API 30
and API 37, including repeated wide/tall images, transparent/small/invalid inputs
and injected allocation failure/recovery. API 24 also passed **3 cold + 3 warm
starts**. Samsung was updated in place.
Power-of-two sampling can reduce resolution below the requested dimensions.
The original downloaded image and exact startup memory state were not retained;
the controlled oversized-image failure is reproduced and fixed locally.
Honor hardware and native-speaker review remain unavailable.
Evidence: `validation-artifacts/device-checkpoint/2026-09-12-wallpaper-memory/`
and `validation-artifacts/device-checkpoint/2026-09-12-inbox-locale-theme/`.

Previous translation gate (before these follow-up fixes):
Translation checks: **564 required resources × 34 locales**, no missing entries
or format errors; **4 checker tests**, **120 JVM tests**, debug builds and lint
passed (0 errors, 203 warnings). Samsung API 30 and the API-37 emulator each passed
**8 focused instrumentation tests**, including resource loading for every locale,
all formatted guide sections and usable German/Arabic/Hindi/Japanese rule controls.
Both devices passed **3 cold + 3 warm starts**. Samsung's larger-font view choices
remain visible in one row. Updates preserved app data and restored screen settings.
Signed APK/AAB verification passed, including signatures, all 45 manifest components
in DEX, unchanged permissions, 16-KB alignment and retained locale resources after
release shrinking. The refreshed package, mapping, symbols and hashes are in
`app/release/49/`; nothing was uploaded.

Two older Samsung native AssetManager crashes at 13:14–13:15 remain in retained
logs. They did not recur in final-build instrumentation or startup checks; their
root cause is unestablished and they are not marked fixed. No new crash or ANR
was observed during those final runs. Native-language proofreading of all locales,
TalkBack and exhaustive layout/font/orientation coverage remain unperformed.

Earlier feature checks: **120 JVM tests**, **22 reporting-script tests**, debug lint
and signed/minified release builds passed. Both Samsung API 30 and the API-37
emulator passed all **19 affected gesture/navigation/drawer tests in clean runs**.
Captured crash buffers were empty, with no Comfer ANR found. The emulator retains
scheduled ImageWorker work. Earlier widget, package-change, picker and controlled
WorkManager results remain applicable to unchanged functionality.

Version-49 artifact checks passed: APK/AAB signatures, all 45 manifest components
in DEX, expected version metadata, unchanged permissions, startup configuration,
APK alignment and 16-KB alignment of all four packaged 64-bit native libraries.
The version change follows the passing device gate; these are local checks.

The revised Inbox guide checks passed: **16 tests on Samsung**, **15 on the emulator**
plus one skipped access-dependent test. Both quick-app layouts verified a path
crossing Search that opens Inbox without opening Search. Incomplete gestures and
waiting leave the hint pending; successful completion survives recreation.
Samsung's final visual check confirmed the text-free guide over the lower Search
button. Updated signed version-49 files replace the earlier release package.

Remaining coverage limits and follow-up:

- User gesture/animation comfort review passed. TalkBack, broader orientation
  coverage remain unreviewed; the Inbox animation has no on-screen text.
- Warm-task launch animation, centre-double-tap visuals, all icon placements,
  split screen, navigation modes and disabled-animation settings are not exhaustive.
- Affected Honor firmware, work profiles and restricted document providers are
  unavailable. Full DND/history/manual interaction coverage is outside the focused gate.
- Inspect Play-generated artifacts after upload, then monitor staged rollout.

Final gate evidence: [decision](validation-artifacts/device-checkpoint/2026-09-12-release-gate/decision.json)
and [latest artifact checks/hashes](validation-artifacts/device-checkpoint/2026-09-12-translations/final-artifacts.json).
[Inbox guide results](validation-artifacts/device-checkpoint/2026-09-12-inbox-guide/guide-results.json) retain final checks and interrupted attempts.
[Translation decision and limits](validation-artifacts/device-checkpoint/2026-09-12-translations/decision.json)
include failed attempts, corrected locale lookup, device identities, reports and logs.

Exact runs, unsuccessful test attempts and skipped cases remain in local reports:
[device pass](validation-artifacts/device-checkpoint/2026-09-12/RESULTS.md),
[follow-up](validation-artifacts/device-checkpoint/2026-09-12-followup/RESULTS.md),
[WorkManager compatibility](validation-artifacts/device-checkpoint/2026-09-12-namespace/RESULTS.md),
[Inbox gesture/animation](validation-artifacts/device-checkpoint/2026-09-12-inbox-gesture/RESULTS.md),
[app expansion](validation-artifacts/device-checkpoint/2026-09-12-app-launch-animation/RESULTS.md).
These ignored artifacts may be unavailable in a fresh checkout.

## Device verification procedure

Run focused tests separately on the emulator and physical device. Keep devices
awake and preserve Samsung data with in-place installs; Gradle connected-test
workflows can uninstall the app. Cover startup/focus, API-34+ wallpaper scheduling,
widget overlap/rotation/resize/deletion, icon loading/package changes, document
pickers, gestures and drawer scrolling. Retain exact reproduction steps, test
reports, device model/manufacturer/API/fingerprint, logcat, process-exit history
and JobScheduler state under `validation-artifacts/device-checkpoint/`.

## Crash remediation and release requirements

Implemented safeguards cover Honor power-save activity compatibility, missing
WorkManager APIs, widget ownership, oversized icon allocations, unavailable
pickers, off-main package callback registration and unsafe OEM widget providers.
These are source fixes or mitigations, not production-verified resolutions.
Native/system ANRs, text-layout stalls and activity-launch Binder waits remain
monitoring items where no deterministic first-party cause has been established.

Historical version-49 device checks and signed-package preparation predate the
latest Tasks changes. The current build still requires its final acceptance gate
and refreshed signed artifacts; Play inspection and staged telemetry remain pending.

Release requirements:

1. Complete planned features and pass affected tests plus the device decision
   gate: no first-party crash/ANR, working scheduling/widgets/icons, and documented skips.
2. Only after the gate passes, assign a new version code and rebuild signed/minified
   APK/AAB; verify signatures, permissions and manifest components against DEX.
3. Inspect Play-generated artifacts before staged rollout.
4. Review Play/Crashlytics at 24 and 48 hours with comparable exposure. Halt rollout
   expansion for recurring Honor startup or WorkManager startup crashes.
5. Require meaningful affected-device exposure before marking fixes production verified.

## Production reporting and issue ledger

Published evidence now includes version 49. It shipped without notification-settings
backup/restore; the local implementation is intended for version 50. Keep Play and Crashlytics counts separate. Prioritize Honor
startup, WorkManager startup, widget hierarchy failures, icon memory pressure,
document pickers and package-registration ANRs during release monitoring.

The consolidated database is the ignored `play_reporting.db`; preserve it and
all ignored SDK, Firebase, signing and reporting credentials. Reporting/import
commands are in [the reporting guide](scripts/README-play-reporting.md).

Version 50 remediation is tracked in the [issue-fix ledger](docs/release-50-issues.md).
Record every attempt with its full issue ID, evidence, changed files/commit, test results,
shipped artifact and later production outcome. Preserve earlier attempts so recurring
issues can be evaluated against what was actually tried. Local tests do not establish
production resolution. The ledger includes all 48 version-49 Crashlytics groups and
the five current first-party mitigations; remaining investigations and release gates
stay unchecked until verified.
