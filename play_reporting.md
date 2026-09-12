# Crash and ANR reporting status

## Inbox home gesture implementation — 2026-09-12

Implemented the one-finger down-and-return gesture in QuickListOverlay's existing
handler. A single release-time classification opens Notification Inbox; it does
not also dispatch up/down. Incomplete deliberate returns cancel. Existing
left/right, L/circle, tap, long-press and child-scroll behaviors passed the focused
regressions. NotificationIconRow remains an entry point.

Validation: 113 JVM tests pass; debug lint and isolated APK builds pass; eight
gesture/navigation tests pass on each of Samsung API 30 and emulator API 37.
See [feature evidence](validation-artifacts/device-checkpoint/2026-09-12-inbox-gesture/RESULTS.md).
Human gesture comfort/false-positive trials and accessibility/orientation review
remain distinct from automated coverage. Guide text follows the existing English
app-guide fallback; translation review remains pending before release.

The earlier device gate describes the pre-feature build. This feature's affected
checks pass, but signed/minified release artifacts must be rebuilt and checked
after feature completion. VersionCode stays 48; no production verification or
upload is implied by these results.

## Controlled namespace compatibility check — 2026-09-12

**PASS: the remaining controlled missing-`forNamespace` check is complete.**
The existing API-37 emulator passed both the healthy control and an isolated
build whose reflective method lookup deliberately fails. The missing-method
run leaves WorkManager uninitialized with no scheduled jobs while application
data initialization, MainActivity focus/recreation, and a subsequent ordinary
cold launch succeed. Both crash buffers are empty, with no fatal/Comfer ANR
signatures and only user-requested exits. The expected fallback diagnostic
appears only in the injected run.

Reproduce using `scripts/test_workmanager_compatibility.py`; see
[controlled-run evidence](validation-artifacts/device-checkpoint/2026-09-12-namespace/RESULTS.md).
Injection occurs only in a disposable copy using the isolated test package;
production application source and the installed user app are unchanged.
The local device gate stays PASS with its documented limits. Actual affected
Honor firmware, the separate post-preflight LinkageError path, and production
telemetry are not verified by this simulation. VersionCode remains 48; signed
release preparation and upload have not started.

## Latest gate update — 2026-09-12 follow-up

**PASS — local device gate, with the documented coverage limitations.**
Samsung restore now passes end to end: locate the ZIP, **long-press to select**
(a 4-second injected press succeeded), tap **Select**, then confirm **Restore**
in Comfer. Tapping the ZIP opens it and is not the selection procedure.

The original 44 focused device/method combinations passed. This follow-up adds
15 passing combinations: 12 Samsung (notification recovery, 4 integration,
4 navigation, 3 folder/transfer/drag) and 3 emulator folder/transfer/drag.
Historical failed/interrupted attempts are retained, not counted as passes.
Three test-only corrections address the reproduced viewport precondition,
Samsung background fixture-broadcast congestion, and a stale notification-open
assertion; application code is unchanged. Instrumentation builds and affected
reruns pass. See [follow-up results](validation-artifacts/device-checkpoint/2026-09-12-followup/RESULTS.md)
for exact runs, failures, reproduction steps, skipped cases and evidence.

Samsung pre-test data was restored and contacts/notification access verified.
After USB reconnection, all seven final evidence captures succeeded. The crash
buffer is empty, all 16 recorded Comfer exits are USER REQUESTED, and all seven
tracked crash signatures have zero matches in main/system logcat. MainActivity
has window focus; JobScheduler retains Comfer work. Evidence and file hashes
are in `RZ8M80E8ZPZ/final-capture-summary.json` under the follow-up directory.
All local gate requirements now have a passing result or explicit skipped-case
disposition. This is a local device-validation pass, not production verification.
Unavailable Honor firmware/work-profile/provider cases and unexecuted DND,
history, real-contact/manual-folder and perceptual smoothness coverage remain
limitations, not verified claims. Honor-specific production verification still
requires affected firmware and meaningful later-release telemetry. VersionCode
remains 48; no production build/upload is prepared by this follow-up.

- Last updated: 2026-09-12
- Package: `com.jeerovan.comfer`
- Detailed remediation plan: [`FIX-PLAN.md`](FIX-PLAN.md)
- Database: local, ignored `play_reporting.db`

## Evidence currently stored

The database keeps Play and Crashlytics in separate tables because the two
systems group and sample failures differently. Their event and user counts must
not be added together.

| Provider/version | Crash groups | Crash events | ANR groups | ANR events |
|---|---:|---:|---:|---:|
| Google Play 46 | 11 | 56 | 54 | 140 |
| Google Play 48 | 9 | 10 | 54 | 103 |
| Crashlytics 48 | 30 | 296 | 176 | 886 |

The complete Crashlytics import covers 2026-08-13 through 2026-09-11 and
contains 206 issue groups / 1,182 events. Crashlytics returned no version-46
data, so Play remains the evidence source for that release. `affected_users`
is unique only within an issue group; summing it does not produce a unique
app-wide audience.

## Fix ledger

Status meanings:

- **Production verified**: a later release has meaningful affected-device
  exposure without recurrence, or a directly equivalent production check.
- **Source fixed**: code and local tests exist, but the fix needs telemetry from
  a new version.
- **Mitigated/monitor**: the app contains a safe boundary, but the sampled stack
  does not support calling the whole family resolved.

### Source fixed in the current worktree

| Issue/family | Evidence | Implemented change | Verification still needed |
|---|---|---|---|
| Honor `PowerSaveModeLauncher` class not found | Crashlytics `ca8f7f21...`, 208 events / 7 issue-users; overlaps Play `7eed1be...` | Exact-name `AppComponentFactory` redirect to a no-UI task tombstone; unrelated names still fail normally | A new version on HONOR NIC-LX2/affected firmware |
| WorkManager `JobScheduler.forNamespace` missing | Crashlytics `7c080f7c...`, 14 / 2 | Disable only WorkManager-backed wallpaper scheduling when API-34 firmware lacks the method; remove automatic initializer so the check runs first | Normal API-34 scheduling plus affected/broken firmware startup |
| AndroidView/ViewGroup NPE family | At least 12 Crashlytics groups / 43 events, led by `b9243dd5...` | Cached widget views are reused only after detach and are never stolen from an outgoing parent | Connected overlap/rotation/reopen test and new-version telemetry |
| Launcher-icon OOM/resource pressure | `578a53df...`, 3 / 2; sample requests about 81 MB | Density clamp, two-job limit, cache eviction and per-app OOM containment | Large/vendor icon stress test and telemetry |
| Missing/blocked document picker | `42e44a72...`, 2 / 2 | Backup/restore launcher contains `ActivityNotFoundException` and `SecurityException` | Device without a documents provider/device-policy test |
| LauncherApps callback registration ANRs | Three groups / 5 events | Listener Binder registration/unregistration moved off main; callbacks explicitly delivered on main | Package-install/remove stress test |
| Proven unsafe OEM widget providers | Honor Gallery plus Vivo clock/cleaner one-event groups | Added exact provider-package guards before RemoteViews inflation | Affected OEM/device telemetry |

These rows are intentionally not marked production verified: the fixes have not
shipped under a new version code yet.

### Previously fixed and retained

| Area | Retained fix | Current interpretation |
|---|---|---|
| Historical `SubscriptionActivity` removal | Non-exported, no-history tombstone retained for upgrade/restored-task compatibility | Useful hardening, but it is **not** the current Honor fix; Crashlytics revealed the missing name is the OEM power-save activity |
| Broad media permissions | `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` removed from all merged manifests/artifacts | Resolved the v43 Play policy rejection; keep artifact checks in release verification |
| Manifest/DEX mismatch risk | `scripts/verify_apk_components.py` validates application, component factory, activities, services, receivers and providers against defined DEX classes | Release gate, not evidence that an OEM can never supply a malformed component record |
| Widget geometry | Invalid/inverted bounds and oversized dimensions clamped and tested | No current v48 sample justifies reopening this fix |
| External intents, URIs and framework boundaries | Unsafe launch, URI, `reportFullyDrawn`, sound and malformed-proxy paths guarded | Retain; absence from a finite report window is supporting evidence, not proof |
| Wallpaper/background work | Service failures contained; missing automatic initialization previously had a fallback | The new pre-initialization API check supersedes the older WorkManager fallback for `forNamespace` failures |
| Images/drawables | App-owned bounded bitmaps replace unsafe mutable drawable painting paths | Retained; the new launcher-icon allocation limit is additional protection |
| OEM widgets/weather | Honor/Huawei Calendar, Gallery and weather guards plus Binder/network isolation | Retained and expanded using the v48 provider evidence |
| Search and drawer | Search normalization moved off main; U-shaped drawer occurrence keys and per-event drag animation restored | No current report identifies these paths as a new first-party regression |

### Mitigated or awaiting better evidence

- Seven `nativePollOnce` ANR groups contain 445 events, but representative main
  stacks are commonly idle and do not identify preceding first-party work.
- Launcher two-pane/Minikin text measurement has four groups / 16 events. Log
  locale and displayed text and capture a system trace before changing layout.
- Activity-launch Binder waits have three groups / 4 events. Android activity
  launch belongs on the UI boundary; moving it to a worker would not be a valid
  fix.
- HWUI/GLES/ART/libc, GMS Dynamite, OEM instrumentation and dead-system samples
  remain monitoring items unless a first-party causal frame is captured.

## Local verification

On 2026-09-12, the five focused connected classes passed on both a Google
API-37 emulator and Samsung SM-A305F/API 30. Additional drawer/contact tests and
manual device regressions were executed. Exact results and skipped cases are
in the ignored local
[`device report`](validation-artifacts/device-checkpoint/2026-09-12/RESULTS.md).
These results are local validation only; no fix-ledger row is production verified.

The device decision gate remains **HOLD** for the recorded coverage gaps.
User clarification: Samsung requires locating the backup and **long-pressing
to select it**, then proceeding; tapping opens the archive. The earlier
tap-based outcome is not evidence of a restore defect. This correction documents
the user-provided procedure, not a newly executed restore test. Provider search
separately logged `DeadObjectException`; no deterministic Comfer source defect
was established.
Source and versionCode remain unchanged at 48. The reporting database and
production evidence were not refreshed or modified during device testing.

The resume procedure is in
[`DEVICE-TEST-CHECKPOINT.md`](DEVICE-TEST-CHECKPOINT.md). It contains the exact
emulator/physical-device test order, manual crash regressions, evidence capture,
and release decision gate.

Completed for this fix set:

- `./gradlew :app:testDebugUnitTest`
- `./gradlew :app:assembleNotificationTest`
- `./gradlew :app:assembleDebugAndroidTest`
- `./gradlew lintDebug`
- manifest merge check: custom component factory present, AndroidX Startup
  provider retained, only `WorkManagerInitializer` removed
- signed/minified release validation: v2-signed APK, signed AAB, and all 45
  manifest-declared application/component classes present in DEX

At the 2026-09-11 checkpoint no Android device was connected; the 2026-09-12
executed device results above supersede that instrumentation blocker.
The release artifacts are still version 48 and are validation-only. Increment
to a new version code, rebuild, repeat the checks, and inspect the Play-generated
artifact before publishing.

## Repeatable database checks

```sql
SELECT version_code, type, COUNT(*) AS groups, SUM(events) AS events
FROM issues
WHERE version_code IN (46, 48)
GROUP BY version_code, type;

SELECT version_code, type, COUNT(*) AS groups, SUM(events) AS events
FROM crashlytics_issues
WHERE version_code IN (46, 48)
GROUP BY version_code, type;
```

Operational import instructions are in
[`scripts/README-play-reporting.md`](scripts/README-play-reporting.md).
