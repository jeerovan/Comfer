# Version 53 remediation

Prepared 22 September 2026 on `main`, based on
`7f7cdb042507893fb2a9bac3eafd5b69dd1e2c56`. Version code/name: **53 / 53.0**.
Changes are local; production resolution requires version-53 telemetry after rollout.
The spatial-wallpaper experiment remains on its separate branch.

## 53-01 — Journal schema migration and encrypted-content conversion

Firebase issue **`2c36a2cdc4a142141e1b60fca86436ee`** accounts for
34 version-52 crashes / 21 affected users in import 4. The sampled error is
Room's missing migration 4→5. Source inspection found JournalDatabase at schema
5 with no registered migrations. Its DAO also rejects the plaintext content
stored by schemas 1–4, so adding columns alone would not restore journals.

- Restore/register migrations 1→2→3→4→5 without destructive fallback.
- Mark only upgraded legacy databases for a transactional conversion into the
  existing encrypted format. Preserve entries (including trash), drafts,
  dictation segments, revisions, timestamps, generation and shared image references.
- Encrypt images into new immutable files. Retain original files until successful
  conversion; the existing cleanup workflow checkpoints/vacuums the database and
  removes obsolete media after recovery. Failed encryption rolls back database
  changes and can retry on reopening.
- Continue rejecting unsupported schema-5 content formats. Missing, invalid or
  oversized legacy attachments stop conversion while retaining original data;
  automatic recovery of an already missing attachment is outside this fix.

Changed files: `JournalDatabase.kt`, new `JournalMigrations.kt`,
`JournalDao.kt`, new `JournalMigrationTest.kt`, and `app/build.gradle.kts`.

Validation: the new 4→5 regression failed with the exact missing-migration error
before the fix on Samsung SM-A305F / Android 11. After the fix, all seven migration
tests passed: schemas 1–4, reopen, transactional encryption failure/retry, encrypted
image conversion with shared references, and missing-image recovery. The 196-test
unit suite passed on retry; its first run had an unrelated local HTTP timeout in
`BoundedDownloadTest.rejectsDeclaredOversizeWithoutWaitingForBody`.
These fixture tests do not reproduce the original Android-16 device or prove
production resolution.

The nine existing Journal encryption tests also pass on Samsung, including real
Keystore round trips, tampering, protection changes and failed-rekey rollback.
A broader Journal package run was not clean: the emulator rejected installation
because its test app already existed; Samsung hit the existing
`reopeningShowsNewPromptAndLatestEntryAtBottom` UI test failure and later stalled
in `encryptedPortableRoundTripAndWrongPassword`. That isolated run was stopped.
The full UI/backup suite therefore remains an outstanding release check.
The persistence suite's `tenThousandEntriesAreBoundedAndOrdered` stress test was
also stopped after several minutes of device Keystore work; it is not verified
for this candidate.
The other **11 persistence tests pass** in a separate Samsung run excluding that
one stress test. In total, **27 focused device tests pass** (7 migration,
9 encryption, 11 persistence).

Signed release APK and AAB builds, including release vital lint, pass.
Local candidate artifact SHA-256 values (not evidence of a Play upload):

| Artifact | Bytes | SHA-256 |
| --- | ---: | --- |
| `app/build/outputs/apk/release/app-release.apk` | 8597497 | `0d0a84110f7f8784f4286595b44c187af52450a0ce3417696dca70cf14df29ed` |
| `app/build/outputs/bundle/release/app-release.aab` | 12484002 | `6b728f452e9caacb60146f2a0f9168f5238492cf44c18d8081dc79141fd2766d` |

## Other version-52 investigations

No speculative platform workaround is included in this urgent release:

- `87073ca8fd3c019e5e5bb6172c9d96e1`, `0aeaa43763a36f5ba69451942c902f67`,
  `a0a49e222e9a6533a72abea121ca7b19`, `b7313afe656f494ee046862d3a0d2b30`,
  `ecdb3c221473dea4b125a2b3eba81cfa`: eight crashes from missing framework
  methods in AndroidX/Compose. All selected samples report Pixel 8 Pro / Android
  14. Reproduce that runtime before dependency bytecode changes or accessibility
  workarounds; API level alone does not explain absent API-34 methods.
- `1ea5988c748d88947b0a46bb05ff104d`: three widget startListening Binder ANRs.
  Moving the entire operation off Main risks reintroducing the view hierarchy
  corruption addressed in attempt 51-02. Await a safe targeted reproduction.
- `8d782ca9e751c6476994a4d4f4ad0075`: selected stack concerns Google certificate
  validation/measurement, despite the CLOSE_SYSTEM_DIALOGS group title. No
  broadcast-permission workaround is supported by that stack.
- `973a7a1429c335323bfe72f30dad1df7`: DeadSystemException has no established
  app-side fix. Remaining native/system ANRs retain their investigation status.

Firebase issue states, historical import counts and previous attempt notes are
preserved. See [the v52 evidence](release-52-issues.md) for the full inventory.

Samsung SM-A305F was updated in place with the signed APK; Package Manager
confirms 53.0 (53), and MainActivity launches. App data was not cleared. This
startup check does not replace the outstanding full UI/backup acceptance checks.
