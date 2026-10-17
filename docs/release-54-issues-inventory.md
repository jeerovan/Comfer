# Version 54 Firebase issue inventory

Detailed issue inventories, fix attempts, recurrence decisions and validation
history are stored in the local `play_reporting.db`. See the
[query and recovery guide](../scripts/README-play-reporting.md#issue-and-fix-history).

From the repository root:

```sh
python3 scripts/issue_history.py show --source docs/release-54-issues-inventory.md
python3 scripts/issue_history.py source --path docs/release-54-issues-inventory.md --output /tmp/release-54-issues-inventory.md
```

The source export restores the complete original report. Line references in
query results refer to that archived original.
