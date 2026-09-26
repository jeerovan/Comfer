# 3D Effect localization and discarded picker cleanup

26 September 2026. English plus all 34 translated locales declared in
`app/src/main/res/xml/locales_config.xml` cover the active 3D Effect setting,
preparation/retry accessibility text, and model-download states.

There are **7 strings per translated locale / 238 translations** in
`strings_spatial.xml`. Android's existing `values-in`, `values-iw` and
`values-zh-rCN` qualifiers are preserved. English uses
`values/strings_local_spatial.xml`, without a MissingTranslation suppression.

## Cleanup

The discarded standalone spatial wallpaper picker has been removed, including
its selection listener, unused bundled-scene renderer, old scene/path constants,
and ten obsolete picker/photo-import strings in English and all translated locales.
The active Settings → Wallpapers → Own wallpapers → 3D Effect flow remains.
Local/cloud rendering, preparation, caching, retry and motion behavior are unchanged.
Shared sensor code now lives in `SpatialTilt.kt`.

Picker persistence and bundled-scene switching tests were removed. Mesh edge
coverage remains in `DepthMeshRenderTest`; its images and depth grids now live in
`src/androidTest/assets/spatial`, alongside the image used by `LocalDepthModelTest`.
`DepthMeshTest` reads those same fixtures. The fixture generator now targets this
test-only directory. APK inspection confirms these assets are absent from the app
APK and present only in the instrumentation APK.

Existing `scene` and `local_path` preferences are no longer read or written;
no migration clears the shared preferences file, preserving the active
`local_spatial` setting.

## Wording and provenance

Translations were drafted by the coding assistant and checked for UI context,
Android XML escaping and source consistency. They preserve the approximate 28 MB
model download plus a subject model, on-device photo processing, and the distinction
between preparation retry and toggling the setting after a model-download failure.
Native-speaker review of all 34 locales has not been performed.

## Validation

- Translation checker: 34 translated locales, 1,032 required resources, zero errors.
- Four translation-checker tests and all 228 app JVM tests pass.
- Isolated `notificationTest` app and instrumentation APK builds pass.
- The retained settings test exercises English/LTR and Arabic/RTL labels and switch
  state. Module locale checks cover translated active labels and errors.

Device regression results for this cleanup are recorded below. Earlier localization
artifacts are historical; their discarded picker screenshots do not describe the
current UI. Full lint was not rerun for this cleanup; the preceding localization
run reported two pre-existing LocalContext-to-Activity casts in JournalUiTest and
NotesThemeTest.

Samsung SM-A305F / Android 11 passed **11 tests** in the cleanup run:
`LocalSpatialWallpaperTest` (6), `DepthMeshRenderTest` (1), `SpatialSceneTest` (1),
`ModuleLocaleTest` (2), and `AppDrawerLocaleSourceTest` (1). This covers active
settings in English/LTR and Arabic/RTL, cache reuse/opt-out, progress/retry, motion
pause/resume, cloud fallback, multilayer rendering, all-locale resources, app-language
switching, and system-default versus app-locale equivalence. Locale tests restore
the previous app setting; the physical system language was not changed.

English and Arabic settings screenshots were inspected: text fits, the switch is
visible, and RTL placement mirrors correctly. Builds and tests used the isolated
application, preserving production data. Logs and screenshots are retained under
`validation-artifacts/spatial-picker-cleanup-20260926/`. The optional real-model
inference test was updated to load its test-only asset but was not rerun; live CDN,
thermal/battery profiling and native-speaker review remain outside this cleanup.
