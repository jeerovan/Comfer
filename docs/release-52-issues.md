# Version 52 issues and fix-history baseline

Detailed issue inventories, fix attempts, recurrence decisions and validation
history are stored in the local `play_reporting.db`. See the
[query and recovery guide](../scripts/README-play-reporting.md#issue-and-fix-history).

From the repository root:

```sh
python3 scripts/issue_history.py show --source docs/release-52-issues.md
python3 scripts/issue_history.py source --path docs/release-52-issues.md --output /tmp/release-52-issues.md
```

The source export restores the complete original report. Line references in
query results refer to that archived original.

<!-- Historical anchors retained for existing links. -->
<a id="firebase-refresh--22-september-2026"></a>
<a id="investigation-priorities"></a>
<a id="import-validation"></a>
<a id="acceptance-and-release-identity"></a>
<a id="fix-attempts-carried-into-version-52"></a>
<a id="recurrence-review-procedure"></a>
<a id="complete-version-52-inventory--import-4"></a>
