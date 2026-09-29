# Cloud spatial assets

Cloud wallpaper asset generation and serving live in the
[comferweb backend](https://github.com/jeerovan/comferweb), located locally at
`/Volumes/JS/Repos/comferweb`.

In that repository:

- `docs/spatial-worker.md` covers installation, configuration, scheduling, the API,
  and testing.
- `scripts/spatial_worker.py` reads wallpaper URLs from PostgreSQL using
  `DATABASE_URL` in `.env` and generates assets every 300 seconds by default.
  Set `SPATIAL_POLL_INTERVAL_SECONDS` to change the interval.
- `docs/cloud-spatial-assets.md` describes the models and optional CSV workflow.
- `GET /api/wallpapers/{id}/spatial` serves the version-1 manifest, with absolute
  HTTPS URLs for versioned image layers and depth meshes. Wallpaper feed and
  detail responses expose `spatialSceneUrl` when the assets are ready.

Keep generation scripts, model dependencies, pipeline tests, and worker deployment
instructions in the backend repository. Comfer retains the Android asset loader,
renderer, cache, on-device processing for personal wallpapers, and Android tests.
See [Spatial wallpapers](local-spatial-wallpapers.md) for the client contract.

`scripts/generate_spatial_meshes.py` remains here solely to rebuild hand-authored
Android test fixtures; it does not run the backend ML pipeline.

## Live client validation (2026-09-29)

- Queried the production feed, `/api/wallpapers/77`, and its `/spatial`
  manifest before changing the client. Both wallpaper responses advertise the
  spatial URL. The version-1 manifest serves two 1152 × 2048 PNG layers and
  two valid 48 × 96 depth grids; all downloads fit the client limits.
- The real scene also passed Android download, opacity/canvas validation,
  cache-format writing and scene loading on Pixel API 37 and Samsung Android 11.
  This is a sampled wallpaper check, not a catalog-wide audit.
- Home now observes metadata changes for the same image path. Older saved
  wallpapers with no spatial URL look up their detail metadata when motion is
  enabled. The existing renderer follows motion on/off and keeps the original
  image as its fallback, without invoking local ML.
- 246 JVM tests passed. All 10 cloud/spatial instrumentation tests passed on
  Samsung SM-A305F (API 30). Pixel API 37 passed the motion/metadata UI regression
  with English and Arabic system locales, following the system and selecting
  languages through the app's language-update flow, switching both ways and
  returning to system default. Pixel's existing spatial regressions also passed.
  Tests check physical left/right placement and edge pixels while toggling motion.
  Pixel locale recreation had transient timing failures during testing; the final
  English and Arabic runs passed. Original device language configuration was restored.
- A temporary control build using the old path-only metadata cache failed the
  new same-file metadata regression; the final implementation passes it.
  Device tests use only `notificationTest`. Physical battery/thermal profiling
  and a full cloud-catalog visual audit remain outside this validation.

`CloudWallpaperIntegrationTest` accepts `-e expectedSystemLanguage en` (or `ar`)
to verify the actual system locale. Its optional live-download test runs when
instrumentation receives `-e cloudSceneUrl https://comfer.jeerovan.com/api/wallpapers/77/spatial`;
without that argument, the live-network test is skipped.
