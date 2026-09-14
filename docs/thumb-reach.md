# Shared thumb reach

Updated 14 September 2026. Applies to Notification Inbox and its internal pages, launcher Settings, Tasks browsing (lists, Starred and search), and Tasks settings.

- Open with the full safe viewport available and content at its normal top position. Do not insert initial reach padding or restore a fixed percentage-height viewport.
- A downward drag first scrolls existing content toward its beginning. Only movement left over at the top boundary reveals space above the content, lowering the heading and first items toward the thumb.
- Cap that space so the top content reaches the **360 dp area above the bottom safe edge**, accounting for fixed bottom controls. Use density-independent dimensions and cap reach by available safe height. Regular content margins can remain inside this area.
- Retain the revealed space after release. An upward scroll removes it first, then continues scrolling the content. Horizontal motion, taps and programmatic scrolling do not reveal reach space; unused downward fling motion must not create it.
- Keep bottom navigation/action controls fixed. Content can use the full viewport while scrolling; do not resize the screen to a 360 dp viewport.
- Landscape and safe heights at or below 360 dp need no additional reach space. Geometry changes reset the offset. Newly opened Inbox subpages start without reach space; existing content scroll-position rules remain separate.
- Preserve nested task-card scrolling, hold-drag reorder, swipe deletion, keyboard/system insets and gesture cancellation. Do not trigger the home-screen Inbox gesture from these scrollable screens.

Implementation: [ThumbReach.kt](../app/src/main/java/com/jeerovan/comfer/ui/ThumbReach.kt) uses a shared nested-scroll connection. Downward unconsumed user movement increases bounded top padding; upward movement consumes that padding before the child scrolls. UI-specific code supplies the available geometry and excludes fixed controls from scrolling.

Verification: unit tests cover initial zero offset, capping, consumption, zero-height allowance and ignoring programmatic expansion. Layout checks exercise initial top alignment, pull-down reach, upward removal and fixed bottom controls across the four screen paths. This is not exhaustive physical-device certification.
