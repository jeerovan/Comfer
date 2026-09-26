# Notes, Tasks, Journal and Settings localization

Prepared for the next release, 20 September 2026.

26 September follow-up: [spatial wallpaper localization](spatial-localization.md)
adds all 7 active 3D Effect strings to the 34 translated locales (238 entries).
Current resource coverage is 1,032 required entries per locale with zero errors.
The separate record documents drafting provenance and locale validation.

## Scope

The app declares English and 34 translated locales in `locales_config.xml`:
Arabic, Azerbaijani, Bengali, German, Greek, Spanish, Persian, French, Hindi,
Indonesian, Italian, Hebrew, Japanese, Khmer, Korean, Lao, Mongolian, Marathi,
Burmese, Dutch, Portuguese, Romanian, Russian, Tamil, Telugu, Tajik, Thai,
Turkmen, Turkish, Ukrainian, Urdu, Uzbek, Vietnamese and Simplified Chinese.
Hebrew and Indonesian retain Android's `values-iw` / `values-in` resource
folders, with modern `he` / `id` language-picker identifiers.

Notes controls, formatting tools, accessibility actions, image states, recovery
messages and protection prompts now resolve Android resources. Existing Tasks
and Journal resources, Workspace labels, backup descriptions and new protection
settings receive locale coverage. Settings backup/authentication failures also
use translated messages.

Domain messages and persisted state identifiers stay stable. `ModuleMessages.kt`
maps known module failures at the presentation boundary, including numbered
speech-provider failures. Unknown failures receive a localized fallback; raw
exception details are not shown. Existing localized callback messages remain
readable instead of being replaced with the fallback.

User-entered text, saved label/list names, note content, archive formats, speech
language tags and internal sort/color IDs are not translated or rewritten.
An existing user-editable label named “Inbox” or list named “Tasks” remains saved
content, including when it originated from the initial defaults.

## Translation provenance

Reuse existing Comfer translations for identical source text. Missing text is
drafted locally with MADLAD400-3B (the `jbochi/madlad400-3b-mt` Q4_K checkpoint),
then checked for resource coverage, XML validity, format arguments, draft
markers, script consistency and contextual terminology. Generation does not
require a runtime service or add a model/dependency to the Android app.

Native-speaker proofreading across all 34 locales has not been performed.
Automated coverage and device tests do not establish linguistic accuracy.
The [review record](module-localization-review.json) records the checkpoint,
resource hashes and corrected keys. There are 461 additional resources per
translated locale (15,674 entries), including 228 newly extracted English
resources. Standard abbreviations and valid identical words are retained.

The contextual review corrected swapped task/list and completed/total counts,
dictation privacy disclosures, ambiguous deletion/restoration actions, foreign
script contamination and copied desktop UI metadata. The stricter metadata
check also found existing Lao/Thai strings, which were cleaned; the Thai
unavailable-control warning now preserves the source's negation.

## Validation record

The initial `ModuleLocaleTest.everySupportedLocaleHasModuleAndSettingsTranslations`
failed on Arabic `module_new_note`, which resolved to English “New note”.
This is the regression the new locale resources must fix.

Validation commands:

```sh
python3 scripts/check_translations.py
python3 -m unittest discover -s scripts/tests -p test_check_translations.py
./gradlew :app:testDebugUnitTest
./gradlew -PcomferTestBuildType=notificationTest :app:assembleNotificationTest :app:assembleNotificationTestAndroidTest
```

`ModuleLocaleTest` checks all translated locales, module/settings labels,
substituted user text, known and unknown failures, numbered speech errors,
31 formatted strings per locale, and repeated Activity launches after
German/Arabic app-language selection. `NotesLocaleUiTest` switches retained
errors and an open formatting sheet between those languages. The existing
Notes theme regression follows translated resource IDs for its palette labels.
Run device checks against the isolated `.notificationtest` package.

Final results: all 1,025 required resources across 34 translated locales pass
coverage/format checks; 196 JVM tests, four translation-checker unit tests and
12 API-24 emulator tests pass. German/Arabic locked-screen and formatting-sheet
screenshots were reviewed for readable wrapping and RTL layout. Full visual
coverage across every locale/device/font scale remains untested.

The theme regression exposed a missing mapping for the persisted `purple`
text-color ID: it incorrectly announced “Default”. A dedicated Purple resource
now exists in every locale, while the stored color ID remains unchanged.
Existing theme expectations were updated for the current transparent label
chips, retained card colors, localized error fallback and accessibility labels.
Activity tests use bounded resumed/destroyed lifecycle checks instead of global
idle waits, which timed out on the emulator even after Settings had rendered.
One unrelated bounded-download JVM test hit its five-second deadline once;
the complete JVM suite passed unchanged on the subsequent run.

No version bump or production rollout is part of this preparation.
