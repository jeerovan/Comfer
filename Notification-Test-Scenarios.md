# Notification Inbox behavior scenarios

These scenarios describe the current behavior to verify. They are not a record of executed tests or a delivery plan. See [features](Notification-Features.md) and [test execution](docs/notifications-validation.md).

| Area | Scenario | Expected behavior |
|---|---|---|
| Entry | Setup completed, no live records; access later revoked | Inbox entry remains available; absent access is shown distinctly from an empty connected inbox. |
| Grouping | Children plus summary; same package in two profiles | Duplicate summary is suppressed; profiles remain separate. Summary-only records remain visible. |
| Ordering | Toggle grouped/chronological and pin apps | Grouped ordering honors pins; chronological uses original post time. Collapse affects presentation only. |
| Preview | Tap a truncated body, then tap again | First tap expands; the next opens. A short preview opens directly. |
| Selection | Long press, select across groups, collapse, toggle group title | Selection includes collapsed children; other groups retain selection; caret only expands/collapses. |
| Revision | Update/remove a selected record or reconnect | Stale actions/confirmation are invalidated; unchanged records retain their revision. Reselecting clears the obsolete warning. |
| Open | Valid intent, absent/cancelled intent, failed launch | Open intent or current-profile app fallback; successful eligible opening requests dismissal; failed launch does not cancel. |
| Protected open | Ongoing/critical, user-protected, summary, non-clearable | Opening does not trigger ordinary post-open dismissal. |
| Save before open | Enabled/disabled history, eligible/ineligible source, failed persistence | Eligible capture completes before launch; required save failure stops opening; unavailable/excluded content is not saved. |
| Swipe | Left/right completion, short swipe, cancel, rejected removal | Complete off-screen animation precedes dismissal; incomplete/cancelled gestures restore; failed/unconfirmed removal recovers. |
| Batch | Eligible and ineligible selections | Bulk dismissal requires confirmation and eligibility of every selected item; revalidate each action. |
| Snooze | API 24–25 versus API 26+ | Unavailable on older versions; eligible selected records use native 15-minute snooze on supported versions. |
| Navigation | More actions → Android settings → return; rule editor → Back/save | Restore source list/valid anchor; Back follows nesting; saving returns to the source list. |
| Layout | Short and tall portrait screens, different densities, landscape, rotation, large text, keyboard | Inbox and launcher settings use a fixed 360 dp portrait reach area from the bottom safe edge, capped by available safe height. Full-height scrolling, no landscape starting padding, fixed bottom controls, wrapping options, usable input/navigation. |
| History consent | Enable, post/update, refresh, disable | Capture eligible future events; no synchronization backfill; turning off preserves existing copies. |
| History exclusion | Locked/secret/protected/incomplete/redacted records | Skip capture; protection/exclusion prunes saved copies; unlock does not reconstruct missed content. |
| Active/Saved | Copy still live, filtered/collapsed, removed, reposted | Hide matching live identity; removal reveals retained copy; new post time has separate identity. |
| Saved actions | Search, expand, open app, select, delete one/many, swipe | Search title/text/package; no original intent; multi-delete confirms; local deletion never cancels Android notifications. |
| Saved rules | One saved selection → More → Create rule | Seed app/profile and editable phrase; separate saved preview; no recovered channel/intent; auto-dismiss still needs consent. |
| Retention | 1/7/30 days, over 500 records, app exclusion | Prune expired/oldest/excluded copies; delete affects queued captures as well as files. |
| Encryption | Restart, altered envelope/ID, missing key, failed write | Read valid encrypted copies; fail closed and preserve files on error; explicit deletion resets storage. |
| Rules | ANY/ALL, exceptions, field/scope/profile/channel, Unicode/case | Literal normalized matching; insufficient selected fields never match; exceptions apply per rule. |
| Rule lifecycle | Preview/edit, test-only, enable, reorder, delete, pause/resume | Preview required for unchanged draft; automatic mode separately confirms; first matching automatic rule wins; no backlog cancellation. |
| Rule race | Content/config changes before cancellation | Recheck current generation and source; skip obsolete action. |
| Quiet access | Missing/revoked policy access or Android-disabled rule | Expose status; no silent takeover of global DND or another rule; explicit enable can restore owned rule. |
| Focus | 15/30/60 minutes, End focus, wall-clock change, reboot | Elapsed-time duration; manual focus ends after reboot; ending focus preserves an active recurring schedule. |
| Schedule | Same-day, overnight, weekdays, timezone and DST | Local boundaries; overnight belongs to start day; UI rejects empty weekdays/equal times. |
| DND overlap | Focus plus schedule plus independent active rule | Comfer stays active while its own conditions apply; ending its contribution leaves independent DND intact. |
| Timing | Exact access granted/absent/revoked; normal/deep idle | Exact alarm when permitted, separate inexact fallback; absent access discloses potential delay. |
| Pause/reset | Pause with live/history activity; reset with cleanup failure | Pause affects rules/quiet only; reset waits for owned-rule cleanup and leaves automation paused; saved deletion is separate. |

The schedule calculation helper accepts equal start/end as a full local day for stored/test inputs; the user-facing editor does not allow applying equal times.

Actual reboot tests require explicit prepare, reboot, verify, and restore phases. Do not run the entire reboot test class as an ordinary suite. Device-only effects such as audible DND exceptions, long-idle quotas, OEM restrictions, and cross-profile routing need their own observed evidence; a model test does not establish them.
