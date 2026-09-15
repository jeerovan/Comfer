# Planned home module menu

Confirmed 15 September 2026. Future behavior; not implemented yet.

Replace the middle Search icon in `QuickListOverlay` with a three-dot icon at the same position. Tapping it opens a built-in folder-style menu containing four individually tappable module icons: **Journals, Tasks, Search, Notes**. Each icon opens its own module. Search remains Search, rather than searching across private Journal/Notes content.

Use the existing home folder expansion layout, timing and reversal behavior in both circular and five-column quick-list layouts:

- The four module icons expand out from the three-dot control into the normal folder-icon positions.
- Existing quick-list icons shrink into their own centers and disappear while the menu opens.
- The center control becomes Close while expanded. Closing reverses module expansion back into the origin; existing quick-list icons emerge from their own centers and reclaim their positions.
- Reuse current home-folder close timing, interruption/reversal and input handling. Do not substitute the app-drawer convergence behavior for the home layout's own-center disappearance.
- Only one ordinary app folder or module menu is active at once. Android Back closes the open menu before leaving home. Selecting a module closes the menu and opens that destination once; returning to home starts with the menu closed.
- Keep module IDs/navigation actions separate from installed app package IDs. These are internal destinations, not fake installed apps or a user-editable app folder. Preserve ordinary app folder data and backup behavior.
- Give icons visible labels and accessible names, with at least 48 dp targets. Update center-control semantics and relevant gesture guides to describe opening the module menu, not directly opening Search or Tasks.

This is the final shared entry design, superseding the proposed dedicated Search-long-press Journal shortcut when the menu is delivered. Tasks' alternative entry is resolved: its own icon inside this menu. Current app behavior remains until implementation; no new launcher Settings section or extra default gesture is required.

Integration gate: Journals and Notes must have working destinations before presenting this as a complete four-module menu. Do not silently ship dead icons, redirect Notes to Tasks, or invent placeholder behavior. Menu construction can be tested with fake module destinations before those modules are complete. Any interim rollout with fewer modules or an interim gesture needs an explicit integration decision.

Verification: both layouts, all four destinations, center Close/Back, rapid open-close reversal, repeated taps, ordinary folder transitions, module launch/return, RTL/large text/TalkBack, no duplicate launches and no accidental Search action on menu tap.

## Interim Journal test build — approved 15 September 2026

Until Notes is implemented, Search long-press opens Journal; ordinary tap continues to open Search. This is an explicitly approved interim route, not the four-icon menu rollout. No dead Notes icon is displayed.
