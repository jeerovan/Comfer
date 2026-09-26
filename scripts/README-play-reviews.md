# Play reviews

Run from the repository root:

```sh
venv/bin/python scripts/sync_play_reviews.py
```

Creates or refreshes `play_reviews.db` using the existing `service-account.json`
and Python environment. Reads only from Google; does not post replies.
Optional flags: `--database`, `--credentials`, `--package`, `--bucket`.

- `reviews`: API reviews keyed by package and review ID, including author,
  stars, text, language, device, version, modification time, developer reply,
  and original JSON. All pages are fetched. Existing older reviews are retained.
- `historical_reviews`: all available monthly CSV rows, including rating-only
  entries, timestamps, review links, replies, and original JSON.
- `exports`: source object, exact downloaded generation, row count, fetch time.
- `sync_runs`: successful imports and source counts.

The [reviews API](https://developers.google.com/android-publisher/reply-to-reviews)
returns written production reviews created or modified within the last week.
The [monthly exports](https://support.google.com/googleplay/android-developer/answer/6135870?hl=en)
provide historical coverage. These sources overlap: **do not add their counts**.
Monthly exports may omit stable review IDs/links and author names, so they are
kept separately without guessing cross-source identity. Rating-only rows have
empty review text and title. This is the available export history, not a guarantee
that every rating ever submitted is exposed by Google.

All fetching and CSV validation finish before database writes. A refresh backs
up an existing database, then applies one transaction. API IDs are upserted;
each fetched export replaces its own rows, including successful empty exports.
Failures preserve existing data. Failed attempts do not create a sync_runs row.
Database files and backups are ignored by Git.

```sql
SELECT star_rating, text, language, version_name FROM reviews
ORDER BY modified_seconds DESC;

SELECT submitted_at, star_rating, title, text, language, version_name
FROM historical_reviews
WHERE coalesce(trim(text), '') <> '' OR coalesce(trim(title), '') <> ''
ORDER BY submitted_at DESC;
```

Tests:

```sh
venv/bin/python -m unittest discover -s scripts/tests -p 'test_sync_play_reviews.py' -v
```
