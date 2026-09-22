# Spatial wallpaper experiment

In Comfer, open **Settings → Wallpapers → Spatial wallpapers**, choose **Ocean
valley** or **Meditating panda**, return Home, and gently tilt the phone. Use
**Wallpaper motion** to stop movement or choose **Off** to restore the regular
wallpaper. This experiment renders inside Comfer; it does not install an Android
live wallpaper or change the lock screen.

The original JPEGs are unchanged. Run `python3 scripts/generate_spatial_meshes.py`
to copy them into the app assets and regenerate their 48 × 96 depth grids.
The grids are hand-authored artistic depth approximations, not inferred metric
geometry. The panda silhouette is raised above its background; valley walls and
foreground rocks are raised above the distant channel. Texture coordinates stay
fixed while depth-weighted mesh vertices move. The original 60-second circular
whole-picture animation (8% travel on each axis) runs underneath sensor parallax.
A 1.24× crop keeps the combined movement inside the image boundaries.
As a single textured surface this
cannot reveal image content hidden behind foreground objects; motion is limited
and depth boundaries feathered to limit stretching.

Rotation vectors are sampled at 50 Hz, calibrated on resume, remapped for display
rotation, smoothed, and clamped. No new Android permission is required. Gyroscope
and accelerometer features are optional. Missing sensors disable tilt but retain
the whole-picture orbit. Disabled system animations, system battery saver,
Comfer battery saver, or disabled wallpaper motion stop both effects. Listeners
and orbit frames stop while the activity is paused or the screen is off; the
orbit retains its phase on resume. The existing automatic wallpaper pipeline
remains independent.

Unit tests cover depth validation, parallax ordering, overscan, triangle
foldovers, nonfinite inputs, tilt clamping, and pose calibration. Before shipping,
check real tilt feel and frame timing on representative hardware, including
landscape, screen off/on, battery saver, and system Remove animations.
