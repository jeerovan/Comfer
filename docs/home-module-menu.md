# Home workspace menu

Updated 17 September 2026. Notes now has a working test-build destination; the initial menu approval below was on 16 September. The user approved implementing the four-icon menu now, with Notes marked as coming soon. This supersedes the earlier three-dot design and the requirement to wait for a working Notes module.

The middle Search control in `QuickListOverlay` is now **Workspace** (Material Workspaces icon). Tapping it opens four built-in module icons using ordinary folder slots. In the circular layout: Notes (`Notes`) left, Journal (`MenuBook`) right, Tasks (`TaskAlt`) above and Search (`Search`) below. In the five-column layout, the left-to-right order is **Notes | Tasks | Close | Search | Journal**, keeping Tasks and Search next to the center. RTL mirrors these positions. The center becomes Close; tapping it or Android Back closes the menu.

Both circular and five-column layouts reuse `HomeFolderLayout`'s 320 ms FastOutSlowIn transition and the same icon geometry used for installed-app folders:

- Module icons expand from the center Workspace control. Existing home icons shrink into their own centers and disappear.
- Closing reverses module movement back to the center. Existing home icons emerge from their own centers and regain their positions.
- Keep outgoing icons composed until animation completion. Center Close supports immediate reversal; icon taps are disabled while the transition is running so repeated taps cannot reach a returning home icon.
- Only one app folder or workspace menu can be active. Selecting a module closes the menu and waits for the real transition completion before launching once. Returning home starts closed. Back during a pending close cancels the pending launch.
- Internal `WorkspaceModule` destinations never become `AppInfo` package IDs or persisted user folder entries, and do not affect backup data.
- Use themed icons without visible labels, retaining accessible names; mirror existing folder geometry in RTL. The home Inbox gesture remains anchored to the center control, and guides are hidden while the workspace is active.

Search opens the existing launcher search overlay. Tasks and Journal open their existing activities. Notes opens its main collection; New note starts capture and pending drafts remain accessible (17 September test build); its accessible name is Notes. See [Notes implementation](notes-implementation.md) for validation and remaining release gates. There is no longer a Journal long-press action on the center control.

Validation: 13 focused API-24 emulator tests passed (`WorkspaceMenuTest`, `WorkspaceHomeRoutingTest`, existing `FolderExpansionTest` and two updated Tasks entry cases). Coverage includes both layouts, RTL, own-center shrink/return, expansion/merge, early reversal, ordinary home/drawer folder regression, individually tappable modules, deferred single launch, real Tasks/Journal/Search navigation, Notes status, Back and return home. All 167 JVM tests passed; debug build and whitespace checks passed. Emulator updated; Samsung unchanged. Physical-device and TalkBack acceptance remain open.

The first repeated-tap regression exposed click-through into a returning home folder before the pending module could launch. Disabling icon interaction until the transition settles fixed it while preserving center-button reversal. The first cold Journal return test also sent Back before its screen composed; the test now waits for the composer. Reports are under `validation-artifacts/device-checkpoint/2026-09-16-workspace/`.
