# Horizontal drawer title contrast

Automatic app title color is always active for visible titles in the horizontal drawer. There is no toggle, and themed-text/day-night colors cannot override it. Show app titles still controls visibility. AppListOverlay is unchanged.

For Comfer-rendered wallpapers, load a small software image through Coil's cache off the main thread. Retain a bounded pixel map, not a full-screen capture. Sample 45 points behind each label using centered crop geometry and select whichever of opaque black/white has greater contrast against the average color. Keep the existing opposite-color shadow for mixed backgrounds. Animated wallpapers use the movement envelope; this is an approximation rather than per-frame pixel detection.

Freeze the selected color during horizontal scrolling and update after 150 ms of stable positioning. Reordering, viewport changes, image changes and wallpaper cache-version changes update the relevant samples. Normal and reorder title paths share the implementation. No screenshots, extra permissions or image uploads are used.

For externally managed/system wallpapers, use WallpaperColors hints on API 31+, or its primary color on API 27–30. These are global, not region-specific. Subscribe to available wallpaper-color changes. On API 24–26, denied access, missing wallpaper data or decoding failure, use opaque white text with a contrasting shadow, independent of theme/day-night preferences.

There is no preference for this behavior. Any legacy `auto_drawer_title_color` value in stored settings or restored backups is ignored.

Validation: unit tests cover light/dark/midtone choices, distinct local regions, centered crop, offscreen bounds and zero geometry. Emulator coverage and results are recorded in DEVELOPMENT-PROGRESS.md. This is not a guarantee of contrast for every pixel of a moving or patterned wallpaper.
