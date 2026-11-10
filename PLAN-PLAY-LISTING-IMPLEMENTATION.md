# Google Play listing implementation

| ID | PRD | Work and completion evidence | Status |
| --- | --- | --- | --- |
| PL-01 | Baseline | `play_listing.db`: v001 observed en-US listing, release 58 identity, 26 original assets with bytes/hashes, 104 report generations and raw rows. Integrity, foreign keys and all stored payload hashes verified. | completed |
| PL-02 | Research | Current primary Google guidance, actual Search queries, free-source research and claim review saved in database. Latest complete acquisition context is September 18–October 15; current CTR/benchmark/exposure exports remain uncollected. | completed |
| PL-03 | Candidate | v002: title 24 / short 74 / full 1,889 characters; one feature graphic and 15 genuine phone/tablet screenshots. All 16 upload images validated and visually inspected; editable SVG/font/captures and verified ZIP retained. Existing icon/video retained. | completed |
| PL-04 | Iteration | Live `sync --days 90` succeeded without remote commit; baseline identity preserved. 13 database tests plus 8 existing stats and 3 review tests pass. Unknown activation, incomplete windows and later exposures remain inconclusive. Matching an observed candidate preserves its revision and inherited icon. | completed |
| PL-05 | Skill | Installed `~/.codex/skills/play-store-assets`; skill validation and 6 media-validator regressions pass. Actual v002 manifest passes. Portable skill ZIP retained. | completed |
| PL-06 | Follow-up | Reconcile connected privacy/Data safety disclosures, then review publication scope. Record actual activation/exposure only after verified publication. Evaluate complete 28-day windows; do not attribute the whole refresh to a single element. | pending |

Validation: 3 successful capture sequences on Pixel_10 (API 37), with phone
1080×1920/420 dpi, portrait tablet 1080×1920/280 dpi and landscape tablet
1920×1080/220 dpi; all English/LTR. An additional successful run asserts restored
settings/task content. Original emulator display/hint settings were restored,
the test app exposes neither HOME nor LAUNCHER, and task-owned emulators stopped.
Production app data, connected Samsung and existing reporting databases were
preserved. No RTL media or physical-tablet QA is claimed.

Boundary: v002 is prepared locally and is not activated. The current privacy
policy/Data safety statements conflict with the Firebase setup and need review;
exact runtime data payloads and the Console declaration form were not inspected.
No live listing changes, experiment or recurring schedule were committed. The
database retains detailed evidence/limitations and a verified recovery backup.
Review: `store-listing/private/review.html`; package:
`store-listing/comfer-listing-v002.zip`. Refresh/evaluate with
`venv/bin/python scripts/play_listing.py sync --days 90` and
`venv/bin/python scripts/play_listing.py compare --version 2 --days 28`.
