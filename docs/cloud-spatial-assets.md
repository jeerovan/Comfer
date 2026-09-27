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
