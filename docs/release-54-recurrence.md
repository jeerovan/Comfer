# Version 54 recurrence review — 29 September 2026

Detailed issue inventories, fix attempts, recurrence decisions and validation
history are stored in the local `play_reporting.db`. See the
[query and recovery guide](../scripts/README-play-reporting.md#issue-and-fix-history).

From the repository root:

```sh
python3 scripts/issue_history.py show --source docs/release-54-recurrence.md
python3 scripts/issue_history.py source --path docs/release-54-recurrence.md --output /tmp/release-54-recurrence.md
```

The source export restores the complete original report. Line references in
query results refer to that archived original.

<!-- Historical anchors retained for existing links. -->
<a id="scope-and-recurrence"></a>
<a id="what-earlier-fixes-did-and-what-is-visible-now"></a>
<a id="new-issue-review-and-remaining-limits"></a>
<a id="54-03--gated-asynchronous-tilt-sensor-lifetime"></a>
<a id="54-04--wallpaper-environment-work-off-main-with-ordered-cleanup"></a>
<a id="validation-and-follow-up"></a>
