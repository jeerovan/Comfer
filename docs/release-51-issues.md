# Version 51 recurrence analysis and next-release attempts

Detailed issue inventories, fix attempts, recurrence decisions and validation
history are stored in the local `play_reporting.db`. See the
[query and recovery guide](../scripts/README-play-reporting.md#issue-and-fix-history).

From the repository root:

```sh
python3 scripts/issue_history.py show --source docs/release-51-issues.md
python3 scripts/issue_history.py source --path docs/release-51-issues.md --output /tmp/release-51-issues.md
```

The source export restores the complete original report. Line references in
query results refer to that archived original.

<!-- Historical anchors retained for existing links. -->
<a id="evidence"></a>
<a id="attempts"></a>
<a id="51-01--honor-factory-bypass"></a>
<a id="51-02--widget-updates-on-the-ui-thread-in-lifecycle-order"></a>
<a id="51-03--honor-weather-package-missing-from-the-provider-guard"></a>
<a id="51-04--enforce-wallpaper-size-before-ktor-buffers-the-response"></a>
<a id="51-05--widget-picker-memory-pressure"></a>
<a id="51-06--duplicate-key-follow-up-partial-mitigation-investigation-open"></a>
<a id="51-07--journal-language-name-work-during-composition"></a>
<a id="remaining-investigations-and-follow-up"></a>
<a id="validation"></a>
<a id="complete-import-3-inventory"></a>
