# Version 52 fix-history baseline

## Acceptance and release identity

On 20 September 2026, the user confirmed manual device testing is complete.
This records user acceptance; it does not claim independent verification of
every OEM scenario or production resolution of the reported issues.

Source baseline: `97f1cbef6c5ca0c0eeb1e5630c2743a2376bd3d2` (`UI changes.`).
The source version was bumped to 52 / 52.0 on 20 September 2026. Version-52 release packaging, final
release commit, signed APK/AAB hashes and rollout date remain to be recorded
when that release is prepared. This document is not evidence of a shipped build.

## Fix attempts carried into version 52

Keep the existing attempt IDs: `51-*` identifies the version whose reports
triggered the investigation, not the release in which the fix shipped.
The [version-51 ledger](release-51-issues.md) contains exact issue IDs, prior
implementations, changed files, regression evidence and limitations for each:

| Attempt | Implementation |
| --- | --- |
| 51-01 | Exact-name Honor power-save compatibility class for factory-bypass startup crashes. |
| 51-02 | Main-thread, ordered widget host lifecycle updates with per-host failure isolation. |
| 51-03 | Block the unsafe Honor weather provider package before binding/preview. |
| 51-04 | Stream wallpaper downloads with declared/actual size limits, cancellation and partial-file cleanup. |
| 51-05 | Load widget icons/previews on demand with serialized loading and bounded bitmap sizes. |
| 51-06 | Capture a stable drawer list snapshot and deduplicate restored folder packages; duplicate-key cause remains under investigation. |
| 51-07 | Load dictation language names off the composition thread, calculate once and cache sorted labels. |

Earlier attempts remain in [the version-50 ledger](release-50-issues.md).
The local `play_reporting.db` holds version-51 issue notes and sampled evidence;
the version-51 ledger preserves the full 236-group inventory in version control.
Do not overwrite old notes or mark issues resolved solely because a mitigation
was implemented or manual testing passed.

Other release changes include [module localization](module-localization.md),
centered Notes search/add and selection actions, and matching Tasks search,
title and description input styling. Localization provenance and test results
are recorded separately; native-speaker proofreading remains pending.

## When version-52 reports arrive

1. Import version-filtered Firebase/Play evidence while preserving prior records.
2. Match issue IDs and compare current exceptions, stacks, devices and exact
   build identity; a retained group title alone does not establish recurrence.
3. Check whether the sampled build actually contains the attempted fix above.
4. Append the observed outcome to the original attempt and record any new
   implementation as a new attempt, including source commit, regression test,
   validation result and intended shipping version. Preserve ineffective attempts.
5. Compare crash/ANR rates using comparable device and release exposure after
   rollout, including 24/48-hour checkpoints. Keep unproven system/native ANRs
   open for investigation rather than claiming all issues are fixed.
