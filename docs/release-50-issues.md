# Version 50 issue-fix ledger

Detailed issue inventories, fix attempts, recurrence decisions and validation
history are stored in the local `play_reporting.db`. See the
[query and recovery guide](../scripts/README-play-reporting.md#issue-and-fix-history).

From the repository root:

```sh
python3 scripts/issue_history.py show --source docs/release-50-issues.md
python3 scripts/issue_history.py source --path docs/release-50-issues.md --output /tmp/release-50-issues.md
```

The source export restores the complete original report. Line references in
query results refer to that archived original.

<!-- Historical anchors retained for existing links. -->
<a id="evidence-and-comparison-rules"></a>
<a id="trackable-phases"></a>
<a id="preserve-and-evaluate-earlier-attempts"></a>
<a id="attempts"></a>
<a id="50-06--user-reported-u-shaped-drawer-precision-release-fling-15-september-2026"></a>
<a id="50-01--64a0bfd0b15b3d38c91b303172f08da1"></a>
<a id="50-02--d1a1d5b193c2d61bbd35024cb27fd42a"></a>
<a id="50-03--a6befacbdc58480db83738fc99edea2c"></a>
<a id="50-04--159e25351be7042db517f3af684684db"></a>
<a id="50-05--8b119648c25bd19c6b17322bbb749438"></a>
<a id="notification-backup-compatibility"></a>
<a id="full-version-49-triage-inventory"></a>
<a id="validation-and-release-outcomes"></a>
