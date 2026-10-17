# Comfer 57 release readiness

Prepared on 16 October 2026 for **57.0 (57)**. The current signed bundle is
`app/release/57/Comfer-57.aab`. The matching APK, R8 mapping, native symbols,
source snapshot, validation results and checksums are beside it. The older
candidate is preserved in a `superseded-*` subdirectory. Nothing was uploaded
to Google Play.

Source base: `2b1bf4aababb0d5182243b61aaa232cedcb7050a`, plus the wallpaper TLS fix
and test fixture correction made during this preparation. The release includes
the latest preload, default widget position, Pro Settings sound and immediate
Lock screen wallpaper changes; see [the release notes](release-57-notes-en-US.txt).

| Validation | Result |
| --- | --- |
| App unit tests | 259 passed |
| Build-logic tests | 17 passed |
| Python script tests | 41 passed |
| Translations | 34 translated locales; 1,036 required resources; zero errors |
| Samsung functional selection | 265 cases across 47 classes passed after targeted reruns |
| Samsung final-source follow-ups | 20 passed, including reruns and eight supplementary UI checks |
| API 24 layout/localization matrix | 132 passed across 76 class runs |
| Release lint | Zero errors; 393 existing warnings |
| Signed APK/AAB validation | Passed; matching release certificate and embedded mapping |
| Native compatibility | All 17 libraries aligned for 16 KB pages; AAB requests 16 KB alignment |

The release layout checks completed before building this candidate. They cover
English/LTR and real Arabic/RTL system locales, portrait and landscape, matching
app language selection, switching to the opposite language and returning to the
system default. The widget checks exercise the actual app picker; other fixtures
also use its shared language handler. The picker has no system-default row, so
returning to system language uses that handler. Geometry, visibility, clipping,
touch targets and relevant interactions were checked. Representative Settings,
widget and drawer images were reviewed in all four cells.

Devices: Samsung Galaxy A30 (SM-A305F, Android 11/API 30), using the isolated
`notificationTest` app, and an Android 7/API 24 emulator. Cloud rendering checks
that require API 26 were run on Samsung in all four language/orientation cells.
Some API 24 drawer screenshots contained stale native Settings frames; clear
Samsung drawer screenshots and passing geometry/touch checks supplement them.

The initial API 24 live 3D download failed certificate-chain validation. Spatial
asset downloads now use the app's existing bundled CA trust store, matching photo
downloads while preserving certificate and hostname validation. Live downloads
passed after the fix. Scene bounds/failure checks, all ten preload tests and host
checks passed on the updated source. Unchanged layout results were retained.
The live Samsung preload prepared a night photo and its published 3D assets while
Auto wallpapers remained disabled and no wallpaper was applied.

The first Samsung run passed 263/265 cases. The 10,000-entry Journal test exceeded
the runner's 240-second deadline; the same case passed in 276.64 seconds with a
600-second deadline. The Battery Saver preload fixture wrote preferences directly
without creating the monochrome images normally prepared by Settings. It now
creates missing fixture images and removes only those files afterward. All ten
preload cases passed in the final-source rerun; application behavior for this case
is unchanged. The 20 final-source cases include these reruns.

Original device languages and rotation settings were restored. Temporary mail
and chat fixtures and the temporary notification-listener grant were removed.
Test app data was retained and test apps were stopped. Samsung was returned to its
existing regular Comfer Home, with its APK content and visible interactive UI
verified. Its original installation and production app data were preserved.

The signed build verified all four generated Room implementations. Artifact
checks verified version/package/SDK manifests, components, the minified framework
compatibility guard, APK/AAB signatures, ZIP/native alignment and mapping identity.
The build's Crashlytics mapping upload task completed successfully.

This is a selected release regression run, not every instrumentation class.
Google Play delivery, an in-place production 56-to-57 migration and installation
of this exact new signed candidate on a device were not tested in this run.
Device regressions used the isolated test variant. The AAB was validated locally.

AAB SHA-256: `c812202f09a225b30b7eec694729b0f5a382eb605de00d3a3a21fcdf05b57ccc`.

Detailed evidence is in `validation-artifacts/release-57-final-20261016/`.
The pre-build gate is `pre-candidate-validation.json`; final packaging results are
`validation.json`. Source hashes record the production and test inputs used.
