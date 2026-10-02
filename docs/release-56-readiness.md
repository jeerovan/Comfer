# Version 56 manual release handoff

Prepared **2 October 2026**: versionCode **56**, versionName **56.0**, package
`com.jeerovan.comfer`. Signed APK/AAB artifacts were rebuilt from `11db594` plus
the retained working-tree changes, including the October 1 and October 2 fixes.
The user will release manually. **No Google Play upload, review submission or
rollout was performed during this preparation.**

The signed release files pass local package validation. Following explicit user
authorization, the exact archived v56 mapping was successfully uploaded to
Firebase Crashlytics on October 2. The upload is bound to this rebuilt candidate,
not the older September 30 mapping.

## Included fixes

- Guard the missing `AccessibilityNodeInfo.setAccessibilityDataSensitive` method
  implicated in v55 issue `c41e409d257c7384774b3aac74a8da94`. The bridge catches
  only `NoSuchMethodError`; working accessibility APIs preserve their behavior.
- Block the exact Huawei weather-widget namespace implicated in
  `0a427c5692949811fcbb46d8e5e16a3f`, using the existing unsafe-provider guard.
  This is a mitigation; the real vendor provider was not available for testing.
- Stop stale folder move/delete actions safely when selection has cleared,
  addressing `46899b52662ccfe38b2ed305819cf25c`.
- Delete the selected folder's saved Room row explicitly, preserving other
  folders. Saving the remaining folder map previously only upserted rows.

No database schema or dependency changes were introduced for these fixes.
The [complete v55 review](release-55-review-2026-10-02.md) covers all 76 groups
in import 9: 7 fatal groups / 37 events and 69 ANR groups / 161 events. Four
fatal groups and all 69 ANR groups remain under investigation. No production
issue is marked resolved and no ANR resolution is claimed.

## Files for manual release

Use the **new** `app/release/56/Comfer-56.aab` for Play upload. Use
[the English release notes](release-56-notes-en-US.txt) for the store text.
The APK is retained for reference; the AAB is the Play upload artifact.
Keep the matching mapping, native symbols and metadata with this exact bundle.

| Artifact | SHA-256 |
| --- | --- |
| `Comfer-56.aab` | `6b38cb38ca85b7a9f0fb5c08ce095889f4d7f4e97d04842ac2d098a5b1a2fcff` |
| `Comfer-56.apk` | `9ecfd72e901d665b70a717570727a55d788abe455b3a5d05660bbc6242126844` |
| `mapping.txt` | `acd4b0a90a6f06089945423ea3a8a23996a7764fe5bf2022bc6b0209b8ce6df2` |
| `native-debug-symbols.zip` | `8964dde2a00a9ed3ed838f74476bbd22881a06763afa4652ee188575f2acf6b4` |

`SHA256SUMS` and `release-metadata.json` are beside the files. The old candidate
is retained only as historical evidence under
`validation-artifacts/release-56-20261002/superseded-release-56-sep30/`.
Do not use its bundle or mapping for this release.

The previous September 30 Play edit expired/deleted after a permissions failure;
it is not a resumable draft. Play acceptance of this newly rebuilt AAB has not
been tested. Upload the new file manually and verify **56 / 56.0** in the Console.
The suggested initial rollout is 10%; review after 24–48 hours and sufficient
usage before increasing it. This is a recommendation, not a configured rollout.
Compare version-specific crash/ANR rates with usage denominators, not raw counts.

## Fresh release-package validation

- `:app:assembleRelease :app:bundleRelease :app:lintRelease` succeeds with R8
  and resource shrinking. Automatic mapping upload was explicitly excluded.
- APK and AAB signatures verify against the v55 upload certificate:
  `52ef555d772b43fbd473a4e4dc03d7211fd146d3b0e06bca14db7a46206431a0`.
- Bundletool 1.18.3 validation passes. Both manifests confirm the production
  package, version 56 / 56.0, min SDK 24, target SDK 37 and no debuggable flag.
- All 54 manifest classes and the dynamic Honor compatibility class are present;
  all four generated Room implementations pass the build guard.
- APK ZIP alignment and LOAD segments in all 17 native libraries meet 16 KB
  alignment. Native symbols are archived alongside the bundle.
- The actual minified Compose accessibility caller invokes the kept bridge.
  The platform node setter in that bridge is inside its `NoSuchMethodError`
  handler; the caller does not invoke the platform setter directly.
- The AAB's embedded R8 mapping matches `mapping.txt` byte for byte. Its map ID
  is `98d1a372d709374857ee57b1128533f156b00337e847203c7276f7daeeec12d8`.
  Both APK and AAB contain Crashlytics mapping resource ID
  `966ac8a6000c43c1b302281c8847b57b`, matching archived `mappingFileId.txt`.
- Lint: **zero errors, 392 warnings**. The existing TypographyEllipsis quick-fix
  generation exception recurs; analysis still completes successfully.
  Jarsigner retains the known self-signed certificate, absent timestamp and
  archive manifest-order warnings; signature and bundle validation pass.
- Source hashes were checked after packaging: the app/build inputs did not
  change during release preparation. `git diff --check` passes.

## Regression evidence and remaining limits

The earlier October 2 tests cover the source used for this build; they were not
rerun solely to package the release. **246 app unit tests and 13 build-logic
tests pass**, with zero failures/errors/skips in the retained XML reports.

| Device | October 2 results |
| --- | --- |
| Emulator1 / API 24 | Final folder + app-list tests 11/11 with English system language; focused folder test 1/1 with Arabic. Bundled ANR/compatibility checks 29/29 before the persistence-only follow-up. |
| Pixel 10 AVD / API 37 | App-list tests 10/10 after the persistence fix; final focused folder test 1/1 under each English and Arabic system language. Bundled ANR/compatibility checks 31/31 before the persistence-only follow-up. |

Folder regressions cover LTR/RTL, real English/Arabic system locales, selecting
the same language through the app's picker, switching to the opposite language,
and returning to system default. They check visible control bounds and geometry,
stale actions, repeated deletion, persistent removal and preservation of another
folder. The pre-fix crash and persistence failures were reproduced. Initial test
harness failures are retained and explained in the complete review.

Tests used the isolated `com.jeerovan.comfer.notificationtest` package and
preserved production data. Both AVDs returned to effective English; Emulator1's
persistent locale property now explicitly says `en-US` instead of its earlier
empty value. No devices were changed again during this packaging step and no
Test Lab quota was used. Pixel 10 here is an AVD, not a physical phone.

Device tests use the isolated test variant, not this final signed/minified
production package. Play delivery, production-data upgrades, production Firebase
auto-init, Play-added license handling, the incomplete API-34 framework and the
actual Huawei provider remain unverified. Successful local tests do not establish
production resolution of either crashes or ANRs.

## Additional backup/restore verification — October 2

After the user requested explicit backup/restore confirmation, 35 targeted
instrumentation checks were run on **each** Emulator1/API 24 and Pixel 10 AVD/API 37.
All 35 checks have passing results on each device after correcting two test-harness
assumptions and rerunning the affected nine checks. This is combined evidence,
not a claim that the first 35-test invocation passed cleanly.

- Real archive round trips cover launcher settings/folders, notification settings,
  Tasks, Notes, Journals, images, repeated restore, legacy/missing module sections,
  encryption, wrong passwords, tampering, unsafe ZIP paths, and rollback/recovery
  after injected failures. The previously passing 13 archive unit tests also
  cover supported format versions 1–5 and rejection of a future version.
- A new folder regression verifies that an archive taken before deletion can
  restore that folder. A newer archive excludes the deleted folder; repeated
  restoration preserves that deletion, the retained folder's title/package order,
  and the saved launcher list. This exercises the same persistent operations used
  by the separately verified v56 folder action.
- Initial results: Emulator1 **34/35**, Pixel **33/35**. The notification legacy
  fixture accidentally dropped external Notes attachments while rewriting ZIP
  metadata; it now preserves them. Pixel's unsafe-path test expected only an
  app-thrown `IllegalArgumentException`, but Android correctly rejected the path
  earlier with `ZipException`; the test accepts either rejection and still checks
  that Notes data is unchanged. No application defect or production change was
  needed. Both corrected focused reruns pass **9/9**, with no skips.
- Production source hashes still match the signed v56 build. Only two test files
  changed. The installed isolated app APK is byte-identical to the one used for
  the earlier final v56 folder/LTR/RTL tests. The test APK adds the regression and
  fixture corrections. System locales are unchanged; production app data was
  preserved. No cloud Test Lab runs were used.
- Backup format, database schema, and the backup/restore implementation are
  unchanged by the v56 fixes. No backup/restore regression was found within this
  coverage. These are isolated test-variant checks, not a Play-installed production
  upgrade or restoration of a real user's v55 backup. Those remain untested.

Initial and corrected per-test results, hashes, source identity, and logs are in
`validation-artifacts/release-56-backup-20261002/`. The signed AAB/APK, native
symbols and uploaded Firebase mapping are unchanged; no release rebuild is needed.

## Completed Crashlytics mapping upload

The user explicitly authorized this upload after the initial automatic approval
rejection. `:app:uploadCrashlyticsMappingFileRelease` executed and completed
successfully for Firebase project `comfer-db710`, Android app
`1:141154670700:android:3463921d29d4a97bee2049`; both destination identifiers were
verified from the production Google Services configuration.

The retained
`validation-artifacts/release-56-20261002/upload-archived-mapping.gradle` binds
the task to the archived mapping and checks its SHA-256 and mapping resource ID
before upload. The successful task log confirms resource ID
`966ac8a6000c43c1b302281c8847b57b`. The archived mapping matches the AAB's embedded
mapping byte for byte, and release artifact checksums remain unchanged.
Do not use an unbound upload that might generate a new mapping ID.

Evidence: `validation-artifacts/release-56-20261002/archived-mapping-upload.log`.
The first authorized attempt failed locally because the init script also ran
for buildSrc, which has no `:app` project. Adding that project guard fixed the
script; its initial failed log is retained. No application code was changed.
This completes Firebase symbol preparation; Google Play release remains manual.

Build, package checks, source manifest, retained unit reports, and mapping-upload
preparation evidence: `validation-artifacts/release-56-20261002/`.
Device and full issue-review evidence: `validation-artifacts/firebase-v55-20261002/`.
