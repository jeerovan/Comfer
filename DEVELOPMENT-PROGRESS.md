# Development progress

Current feature and release status, updated 2026-09-12. Maintain this document as
a concise reference: what is available, restrictions, attempted alternatives and
remaining work. Keep implementation discussions and detailed test output out of
this file.

## Current state

Version **49 / 49.0** is prepared for production upload, including the final
Inbox gesture guide. Local device and artifact gates pass with documented limits. The user confirmed the
Inbox gesture, launch animation and feature set are ready on Samsung.

Signed [AAB](app/release/49/Comfer-49.aab), [APK](app/release/49/Comfer-49.apk),
release notes, mapping and native symbols are in `app/release/49/` (ignored).
Nothing has been uploaded. Play-generated artifact inspection and staged-release
telemetry remain pending; no issue is marked production-verified. Installed
Samsung/emulator debug builds are version 49 and include the guide.

## Available features

### Launcher

| Feature | Current behavior |
|---|---|
| Home gestures | Configurable left/right and existing quick-pattern actions. |
| Inbox gesture | One finger down, then back up, opens Notification Inbox from the home quick list. Fires on release; incomplete deliberate returns cancel without also triggering swipe up/down. |
| Home gesture guides | Swipe up → long-press settings → long-press widgets → double-tap Recents → tap clock → long-press clock → Inbox down-and-return. Clock steps are skipped when no built-in clock is shown. Inbox is last and requires notification access. Its text-free hand animation follows a 120 dp path across Search in both quick-app layouts, raised for navigation-bar clearance and repeats until the gesture is completed correctly; completion is remembered. |
| App drawer | U-shaped layout, adjustable scrolling sensitivity, fling interruption, centred-app double-tap launch and folders. |
| App search | Search installed apps and contacts; launch apps or select a contact. |
| Appearance | Configurable icon size/shape, icon packs, themed icons and wallpapers. |
| Widgets | Custom widget screens, editing, positioning, resizing and deletion. |
| Wallpaper | Local/network wallpaper support and scheduled automatic wallpaper work on compatible devices. |
| Backup and restore | Configuration backup and restore through Android document pickers. |
| App launch animation | App windows expand from the tapped icon; the drawer's centred-app double tap uses the same effect. |

### Notification Inbox

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

Final source checks: **120 JVM tests**, **22 reporting-script tests**, debug lint
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
and [latest artifact checks/hashes](validation-artifacts/device-checkpoint/2026-09-12-inbox-guide/final-artifacts.json).
[Inbox guide results](validation-artifacts/device-checkpoint/2026-09-12-inbox-guide/guide-results.json) retain final checks and interrupted attempts.

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

Local preparation steps 1–2 below are complete for version 49; Play inspection
and staged telemetry remain pending.

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

Published evidence covers versions 46 and 48; version 49 is prepared locally and has not
shipped. Keep Play and Crashlytics counts separate. Prioritize Honor
startup, WorkManager startup, widget hierarchy failures, icon memory pressure,
document pickers and package-registration ANRs during release monitoring.

The consolidated database is the ignored `play_reporting.db`; preserve it and
all ignored SDK, Firebase, signing and reporting credentials. Reporting/import
commands are in [the reporting guide](scripts/README-play-reporting.md).
