# Spatial wallpapers

Settings → Wallpapers → Own wallpapers uses the existing folder picker and rotation.
Only while Own wallpapers is enabled, a “3D Effect” switch appears. It defaults off.
Its subtitle explains the first model download, reports downloading/failure, and
reads “Spatial effect on wallpaper” once the checksum-verified model is available.
Opting in starts the download without leaving Settings. Every selected local image
uses its cached depth or is prepared automatically, without a preview or extra picker.
Home shows a small 3D icon in a circular progress indicator until ready; failures
leave the original image usable and expose a retry action.

Cloud wallpapers follow Wallpaper motion automatically and never invoke ML.
The wallpaper API may supply an optional `depthUrl` HTTPS URL alongside `id` and
`imageUrl`. This static UTF-8 asset contains `columns rows` followed by exactly
`(columns + 1) * (rows + 1)` normalized float depths (0 far, 1 near), matching the
full image orientation and aspect ratio. Grids are bounded to 128 × 128 and downloads
to 1 MiB. A 48 × 96 grid is recommended. Use versioned URLs for changed depth assets.
Missing metadata preserves ordinary wallpaper motion. Invalid/unreachable assets
preserve the original image and show retry. Server publishing is separate: existing
cloud API responses without spatial asset metadata cannot display a depth effect yet.

For multiple layers, supply `spatialSceneUrl` (HTTPS) instead. It takes precedence
over `depthUrl` and points to a versioned JSON manifest (maximum 64 KiB):

```json
{
  "version": 1,
  "layers": [
    {"imageUrl": "https://cdn.example.com/scene/v1/background.png", "depthUrl": "https://cdn.example.com/scene/v1/background.depth"},
    {"imageUrl": "https://cdn.example.com/scene/v1/subject.png", "depthUrl": "https://cdn.example.com/scene/v1/subject.depth"}
  ]
}
```

The manifest accepts one to four layers in back-to-front order. Every image uses
the same full canvas and orientation; the first must be opaque, with reconstructed
background behind the subjects. Foreground assets retain alpha (PNG/WebP). Each
image download is bounded to 25 MiB and decoded within 2048 × 2048. Every layer has
its own depth mesh, using the format above. Use a new manifest URL for any asset
revision. Both sources share the scene cache, validation, mesh renderer and motion
lifecycle. Incomplete scenes are never applied; the original wallpaper remains
visible until all layers are ready.

## Processing

- Photos are copied into private storage, bounded to 25 MiB on folder import, EXIF-oriented by Coil,
  and decoded with a maximum 2048 × 2048 bounding box.
- Only opted-in local photos run ML. Bundled/server assets do not enter this path.
- Depth Anything V2 Small's fixed-shape INT8-weight LiteRT conversion is downloaded
  on demand (~27.7 MB), pinned to a revision and SHA-256. Model URL, shape, RGB
  normalization, and checksum are in `LocalDepthEstimator.kt`. The bundled NOTICE
  records provenance and the Apache-2.0 license. Images are never uploaded.
- Inference uses two CPU threads, once per uncached image. Native resources are
  closed after inference. A single app-scoped preparation job serializes ML work;
  cancelled or superseded work cannot publish into a newer selection.
- ML Kit subject segmentation downloads its own optional model after opt-in.
  When available and a usable subject is found, it produces an opaque reconstructed
  background and one to three foreground layers (two to four total). Eight-connected
  mask components keep attached details together. Components with less than 1% of
  the image area or less than 0.12 mean-depth separation are merged; if more than
  three groups remain, the closest depth groups merge. All retained subject pixels
  remain represented. Connected subjects are never sliced into arbitrary depth bands.
  These are conservative visual-benefit heuristics, not a guarantee for every photo.
  Additional layers reuse the same depth/segmentation result; inference is not repeated.
  Cutout bitmaps are generated and written sequentially to bound working memory.
  A conservative boundary-contrast check rejects cutouts that appear to clip
  attached subject parts or have ambiguous edges, preventing duplicate silhouettes.
  When segmentation is unavailable or unsuitable, the continuous ML depth mesh is
  used as a single layer. The background fill is boundary propagation, not generative
  reconstruction; complex silhouettes and patterned backgrounds remain quality limits.
- Relative depth is normalized, bilinearly sampled onto a 48 × 96 grid, and slope
  limited to avoid foldovers. Foreground/background depths occupy separate ranges.
- Ready markers and atomic directory renames prevent partial caches from appearing.
  The cache key includes the source digest and pipeline version. At most three
  prepared scenes are retained. Model files and generated scenes are excluded from
  Android backup via `noBackupFilesDir`.

## Motion and lifecycle

The existing 60-second orbit and 8% travel remain. Orbit is enabled by the user's
existing setting; spatial opt-in does not disable it. Rendering uses display frame
callbacks without an artificial 30 fps cap. A shared resumed/focused/interactive
gate pauses orbit and progress animation; the Android animation preference is
respected. Sensor listeners also observe lifecycle and window focus directly, so
they unregister even if Compose has already paused. Returning home resumes the
orbit's phase without advancing it by time spent away. Processing can finish while
the home screen is inactive, but it does not keep wallpaper frames/sensors running.

## Verification

Run JVM tests with `:app:testDebugUnitTest`. Build isolated device tests using
`:app:assembleNotificationTest :app:assembleNotificationTestAndroidTest
-PcomferTestBuildType=notificationTest` (separately from debug unit tests).

`LocalSpatialWallpaperTest` checks opt-out, cloud fallback, cached application, accessible progress/retry, and
orbit pause/resume. `SpatialDepthTest` checks normalization, flat/invalid depth,
extreme-tilt foldovers, background fill and inactive motion gates. Existing spatial
tests verify edge coverage and bundled-wallpaper compatibility. `SpatialLayersTest`
checks grouping, ordering, tiny fragments, layer limits and manifest validation.
`SpatialSceneTest` compares locally generated four-layer scenes with the same assets
loaded through the cloud pipeline at neutral/extreme tilt, and rejects partial downloads.

`LocalDepthModelTest` is optional in the general suite: seed the checksum-verified
model at `no_backup/spatial-models/<DEPTH_MODEL_SHA256>.tflite` in the isolated app,
then run that class explicitly. It exercises actual inference, generated asset
loading and cache reuse, and exports neutral/tilted render PNGs to external files.
Compose screenshot tests need API 26+; other local tests also run on API 24.

Physical-device thermal/battery profiling, broader photo-quality evaluation, and
non-Google Android distribution validation remain release checks. The switch uses the existing automatic wallpaper-directory rotation.
New strings currently use English fallback pending the localization pass.


### Validation of the integrated flow (2026-09-24)

- Debug build and 210 JVM tests passed.
- Samsung Android 11: six UI/lifecycle tests passed, covering the new settings row,
  cloud fallback without ML, cached apply/opt-out, accessible progress/retry and orbit
  pause/resume. The real depth inference/cache test also passed.
- The cloud fallback test initially timed out waiting for continuous orbit to become
  idle; controlling its animation clock fixed the test, without changing production.
- Live server depth delivery and physical battery/thermal measurements remain untested.

### Adaptive layer validation (2026-09-25)

- Debug build and 216 JVM tests passed.
- Eight Samsung tests passed: four-layer local/cloud asset and render equivalence,
  incomplete cloud download rejection, six existing UI/lifecycle checks, and actual
  depth inference/cache reuse. Cloud fetching used deterministic asset fixtures;
  live CDN delivery is not exercised by this suite.
- The equivalence regression exposed unnecessary texture upscaling in Coil. Inexact
  decoding now keeps small textures at their original size; layer pixels and renders
  match exactly. Inspected the exported four-layer maximum-tilt image.
- Four layers are a supported maximum, not a minimum or a measured battery guarantee.
  Real-photo layer selection and battery/thermal profiling need broader validation.
