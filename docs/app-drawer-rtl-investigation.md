# Arabic / RTL drawer issue reported on version 53

**Status: resolved in the fixed local build, verified on emulator and Samsung
Galaxy A30 (Android 11) on 2026-09-26.** Both the isolated test app and the rebuilt
signed release are installed on Samsung. After an in-place update was blocked by
a signing-key mismatch, the user explicitly requested uninstalling the old app
and installing the fresh release. That replacement succeeded. No Play rollout
is claimed.

Investigated 2026-09-26 against working-tree revision `ddc559c`. Inputs:
[`app-drawer-issue-v-53.txt`](../app-drawer-issue-v-53.txt) and
[`app-drawer-issue-v-53.jpg`](../app-drawer-issue-v-53.jpg).

## Finding

The U-shaped drawer mixes RTL-relative parent alignment with physical,
left-origin graphics translations. A focused Android emulator test reproduces
the selected icon disappearing when layout direction changes from LTR to RTL.
This is a high-confidence explanation for the screenshot's right-edge column.
The reporter's exact device/configuration and previously installed APK were not
available for verification.

## Cause and screenshot match

In `MainActivity.kt`, `AppListOverlay` calls `UshapedAppList` (around line 3713).
`UshapedAppList` creates a full-size `BoxWithConstraints` without an explicit
content alignment (line 4433). Compose's default is `Alignment.TopStart`:
top-left in LTR, top-right in RTL. Confirmed in the locally cached
`foundation-layout-android:1.12.0` source.

`calculateUShapeLayoutParams` calculates physical left/right positions, including
`leftColumnX` and `rightColumnX` (lines 4642–4643). The drawing loop applies these
positions as `graphicsLayer.translationX = x` (line 4541). Each layer is measured
at `largeIconSize` and scaled from its top-left origin. Translation moves it
rightward from the position already assigned by the parent; it does not convert
the position from RTL coordinates or undo the parent's right alignment.

For parent width W and measured layer width L:

```text
LTR rendered left = x
RTL rendered left = (W - L) + x
```

For the screenshot's 720-pixel width, assuming 2× density and a 48-dp icon setting
(consistent with the visible 96-pixel icons), L = (48 + 30) × 2 = 156 pixels.
The unintended shift is 564 pixels:

| Element | Intended left | RTL rendered left |
| --- | ---: | ---: |
| Left column | 36 px | 600 px |
| Selected center icon | 282 px | 846 px |
| Right column | 588 px | 1152 px |

The left column therefore survives at the right edge, matching the screenshot;
the center and right side are outside the viewport. The name label stays centered
because `AppListOverlay` renders it separately in a centered `Row` (around line
3730). The gesture guide is also positioned separately.

The center-icon callback still reports the intended coordinates (line 4528),
so its launch-animation geometry can disagree with the actual drawing in RTL.

## Version history

Commit `fb97dc7086cc50a63c9e67ec33737d230fd5b46c` (2026-09-11,
“fixed app drawer stepping animation”) changed the drawer from `AppIcon(x, y)`
using direction-aware `Modifier.offset` to the physical graphics translation.
The older offset could mirror horizontal positions with RTL; the new layer
translation retains the RTL parent anchor while moving positively right.

This commit predates version 52. Comparing `7f7cdb0` (version 52) with `53f2cc7`
(version 53) shows **no changes** to `MainActivity.kt`, the manifest, or the
Compose version catalog. Thus the source supports an existing RTL defect reported
after the version-53 update, not a newly introduced drawer change between those
two release commits. Establishing why version 52 appeared correct requires the
actual previous APK and configuration; the user's observation alone does not
establish that boundary.

## Reproduction performed

Used the isolated `com.jeerovan.comfer.notificationtest` package on
`emulator-5554`, Android 7.0 / API 24, 720×1280, density 320. The physical phone
and production package were not used for the test.

The diagnostic rendered the real `UshapedAppList` with 50 fixture apps, 48-dp
icons, a 360×640-dp host constrained by the window, and zero scroll offset. It
explicitly supplied `LocalLayoutDirection.Ltr`, asserted the selected icon was
displayed, switched to `LayoutDirection.Rtl`, and repeated the assertion.

**Result:** the LTR assertion passed; the RTL assertion failed at diagnostic line
47: `RTL probe 49 ... is not displayed`. One test ran, with one expected failure.
This isolates layout direction without changing the emulator's system language;
it is not a full Arabic-locale or version-52 APK reproduction.

Gradle compiled the diagnostic successfully but its connected-test installer
reported `INSTALL_FAILED_ALREADY_EXISTS` despite a successful build status. The
isolated app and test APKs were then updated using `adb install -r -t`, and the
test was run directly with `am instrument`. The instrumentation output, rather
than the Gradle exit status, establishes the result.

The temporary diagnostic was removed from the normal test source set afterward.
Its source and logs are retained locally under
[`validation-artifacts/rtl-drawer-investigation/`](../validation-artifacts/rtl-drawer-investigation/).

Existing `UShapeCenterSelectionTest` does not explicitly supply RTL and its
position checks compare deltas, which would not detect a constant origin shift.
`UShapedAppListLayoutTest` checks geometry calculations without Compose parent
placement. Neither directly asserts the absolute RTL viewport placement.

## Correction applied and validation

The coordinate canvas now has an explicit physical origin:

```kotlin
BoxWithConstraints(
    modifier = Modifier.fillMaxSize(),
    contentAlignment = AbsoluteAlignment.TopLeft,
) {
    // Existing physical x/y calculations and subpixel graphics translations.
}
```

This preserves the existing physical drawer path and scrolling smoothness while
allowing descendants to retain Arabic/RTL text direction. If mirroring the app
order is a product requirement, mirror the complete geometry deliberately and
keep gesture mapping and center-icon callbacks consistent with it.

Added `UShapeLayoutDirectionTest`, parameterized over LTR and RTL. It checks
selected-icon visibility, absolute bounds against the center-icon callback,
centering at rest, and actual tap delivery. Each direction covers 48-dp and
32-dp icons, normal and 260-dp-high viewports, and scroll offsets 0, 0.03, 8, 12,
-12, 20, and -20. Arabic app labels are used in both cases.

Before the fix, this new test passed in LTR and failed in RTL at the visibility
assertion. After the fix, both cases passed on the same Android 7.0 / API-24
emulator, using the isolated `notificationTest` application.

Post-fix results:

- **228 unit tests passed**, zero failures/errors/skips, via
  `:app:testNotificationTestUnitTest`.
- **16 emulator instrumentation tests passed**: `UShapeLayoutDirectionTest` (2),
  `UShapeCenterSelectionTest` (2), `UShapeScrollGestureTest` (10), and
  `ModuleLocaleTest` (2).
- The locale regressions check supported translations and reopen Notes, Tasks,
  Journal, and Settings with actual German/LTR and Arabic/RTL app locales.
- `git diff --check` passed.

The initial emulator runs covered explicit drawer layout directions and
app-locale regressions. The system-language and physical-device checks were
subsequently completed below. A full release-suite run was not performed for this
fix. The short viewport exercises constrained geometry without physically
rotating the emulator.

Root `AGENTS.md` now requires both RTL and LTR UI testing alongside relevant
project regressions, including actual locales for language-sensitive UI and
verification of instrumentation results.

Before/after logs and the fixed build log are retained locally in
`validation-artifacts/rtl-drawer-investigation/`. The permanent regression is
`app/src/androidTest/java/com/jeerovan/comfer/UShapeLayoutDirectionTest.kt`.

## Samsung system-language and in-app-language verification

Device: Samsung Galaxy A30 / SM-A305F, Android 11 / API 30, 1080×2340.
Installed fixed build: `com.jeerovan.comfer.notificationtest`, code 53,
version `53.0-notification-test`.

Added `AppDrawerLocaleSourceTest`. It launches the same `LanguageUpdateActivity`
used by the in-app language picker, then renders the real `UshapedAppList` inside
an actual AppCompat activity. No `LocalLayoutDirection`, locale, or resource
configuration is injected. It verifies the activity locale, inherited Compose
direction, centered selection, visible left and right columns, actual taps, and
the coordinates of all visible fixture icons. Each locale change is followed
by reopening the host activity.

The Samsung's actual system language was changed through Settings for the second
pass. Results:

| System language | App language selection | Result |
| --- | --- | --- |
| English (`en-US`) | System default → explicit English → Arabic → system default | Same drawer geometry; correct effective language and LTR/RTL direction |
| Arabic (`ar-AE`) | System default → explicit Arabic → English → system default | Same drawer geometry; correct effective language and RTL/LTR direction |

**17 tests passed** in the English-system run: the new source-equivalence test,
both layout-direction cases, two center-selection tests, ten scroll/gesture
tests, and two module-locale tests. **5 tests passed** in the Arabic-system run:
source equivalence, both layout-direction cases, and both center-selection tests.
No failures were reported in either run.

Saved geometry was also compared across the two runs: all **32 visible fixture
icon bounds** matched exactly between in-app Arabic on an English system and
system-default Arabic; the corresponding English comparison matched exactly too.
The Android resource locale and inherited Compose direction were asserted, so
these checks establish more than a screenshot resemblance.

The actual launcher activity was then opened in the fixed isolated app. Its
drawer was visually checked and captured with real installed app icons under
system Arabic and again after returning to English. Both show the centered arc
and both side columns. The phone's original `en-US,hi-IN` system locale list was
restored and verified. No production app data was cleared and no default-launcher
selection was changed.

`AGENTS.md` now explicitly requires equivalence between system-selected and
in-app-selected languages, switching both ways, returning to system default,
and restoring the original device language configuration.

### Build and installation status

`:app:assembleRelease` succeeded, including generated Room implementation checks.
The signed APK is `app/build/outputs/apk/release/app-release.apk` (53 / 53.0):

```text
SHA-256 ab7179cde1fcf0a47aea1eed0f7e3d2a3ba061bd58332236950df4c7b08ed905
```

The tested isolated APK is
`app/build/outputs/apk/notificationTest/app-notificationTest.apk`:

```text
SHA-256 11c58f858b537d18dfad90564b0fa467723e556238157a86719adc301d321d28
```

The initial attempt to update `com.jeerovan.comfer` with `adb install -r` failed
with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`: its installed signing certificate
differed from the locally signed release. The installation was initially
preserved. The user then explicitly authorized uninstalling the old build and
installing the fresh one. Both commands succeeded, removing the old installation's
local data. Package inspection confirms the signed release **53 / 53.0**, and
`MainActivity` launched successfully. A captured release screenshot shows the
arc and both icon columns. Automated physical-device regressions used the isolated
build; the fresh signed release received this launch/render smoke check.

Samsung logs, per-locale geometry JSON, screenshots, APK hashes, and installation
status are retained under
[`validation-artifacts/rtl-drawer-investigation/samsung/`](../validation-artifacts/rtl-drawer-investigation/samsung/).

## Evidence scope

Graph project `Volumes-JS-Repos-Comfer`, root `/Volumes/JS/Repos/Comfer`, Tier 2;
coverage generation `2026-09-26T07:48:35Z`. Relevant source, tests, manifest,
Gradle build, and version catalog reported matching metadata with no recorded
coverage gaps. Current source reads, Git comparisons, cached Compose source,
and the emulator diagnostic corroborated the graph findings. Graph coverage is
best-effort, not a proof of completeness.

Compose references:
[BoxWithConstraints alignment](https://developer.android.google.cn/reference/kotlin/androidx/compose/foundation/layout/BoxWithConstraints.composable),
[graphics-layer placement behavior](https://developer.android.com/develop/ui/compose/graphics/draw/modifiers).
