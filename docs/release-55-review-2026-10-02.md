# Version 55 issue review — 2 October 2026

Detailed issue inventories, fix attempts, recurrence decisions and validation
history are stored in the local `play_reporting.db`. See the
[query and recovery guide](../scripts/README-play-reporting.md#issue-and-fix-history).

From the repository root:

```sh
python3 scripts/issue_history.py show --source docs/release-55-review-2026-10-02.md
python3 scripts/issue_history.py source --path docs/release-55-review-2026-10-02.md --output /tmp/release-55-review-2026-10-02.md
```

The source export restores the complete original report. Line references in
query results refer to that archived original.

<!-- Historical anchors retained for existing links. -->
<a id="changes-and-limits"></a>
<a id="validation"></a>
<a id="issue-by-issue-assessment"></a>
