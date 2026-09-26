#!/usr/bin/env python3
"""Resumable CSV → static Comfer spatial scenes. See docs/cloud-spatial-assets.md."""
import argparse
import csv
import fcntl
import hashlib
import io
import json
import logging
from pathlib import Path
import re
import shutil
import sqlite3
import sys
import time
from urllib.parse import urlparse

PIPELINE_VERSION = 2
LOG = logging.getLogger('spatial')


def digest(path):
    with open(path, 'rb') as stream:
        return hashlib.file_digest(stream, 'sha256').hexdigest()


def valid_url(value, https=False):
    parsed = urlparse(value)
    if parsed.scheme not in (('https',) if https else ('http', 'https')) or not parsed.hostname or parsed.username or parsed.password:
        raise ValueError('Expected an absolute HTTP(S) URL without credentials')
    return value


def read_csv(path):
    rows = {}
    with open(path, newline='', encoding='utf-8-sig') as stream:
        reader = csv.DictReader(stream)
        if not reader.fieldnames or not {'id', 'url'} <= set(reader.fieldnames):
            raise ValueError('CSV must have id,url headers')
        for number, row in enumerate(reader, 2):
            if not any(row.values()):
                continue
            identifier, url = (row.get('id') or '').strip(), (row.get('url') or '').strip()
            if not re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_-]{0,127}', identifier):
                raise ValueError(f'CSV row {number}: unsafe or empty id')
            try:
                valid_url(url)
            except ValueError as error:
                raise ValueError(f'CSV row {number}: {error}') from error
            if identifier in rows and rows[identifier] != url:
                raise ValueError(f'CSV row {number}: conflicting URLs for id {identifier}')
            rows[identifier] = url
    if not rows:
        raise ValueError('CSV contains no wallpapers')
    return list(rows.items())


def download(url, destination, limit=50*1024*1024):
    import requests
    valid_url(url)
    destination = Path(destination)
    destination.parent.mkdir(parents=True, exist_ok=True)
    temporary = destination.with_suffix(destination.suffix + '.part')
    for attempt in range(3):
        try:
            started = time.monotonic()
            with requests.Session() as session:
                session.max_redirects = 5
                with session.get(url, stream=True, timeout=(15, 45)) as response:
                    response.raise_for_status()
                    valid_url(response.url)
                    length = response.headers.get('Content-Length')
                    if length and int(length) > limit:
                        raise ValueError('Download exceeds byte limit')
                    size = 0
                    with temporary.open('wb') as stream:
                        for block in response.iter_content(1024*256):
                            size += len(block)
                            if size > limit or time.monotonic()-started > 600:
                                raise ValueError('Download exceeds byte/time limit')
                            stream.write(block)
            temporary.replace(destination)
            return
        except (requests.RequestException, OSError):
            if attempt == 2:
                raise
            time.sleep(2**attempt)
        finally:
            temporary.unlink(missing_ok=True)


def load_image(path, maximum):
    from PIL import Image, ImageCms, ImageOps
    Image.MAX_IMAGE_PIXELS = 64_000_000
    with Image.open(path) as original:
        if original.width * original.height > Image.MAX_IMAGE_PIXELS:
            raise ValueError('Image exceeds 64 megapixels')
        if getattr(original, 'n_frames', 1) != 1:
            raise ValueError('Animated wallpapers are not supported')
        image = ImageOps.exif_transpose(original)
        # Wallpaper canvas must be opaque, including transparent source PNGs.
        rgba = image.convert('RGBA')
        flattened = Image.new('RGBA', rgba.size, (0, 0, 0, 255))
        flattened.alpha_composite(rgba)
        image = flattened.convert('RGB')
        icc = original.info.get('icc_profile')
        if icc:
            image = ImageCms.profileToProfile(image, ImageCms.ImageCmsProfile(io.BytesIO(icc)),
                                              ImageCms.createProfile('sRGB'), outputMode='RGB')
        image.thumbnail((maximum, maximum), Image.Resampling.LANCZOS)
        if min(image.size) < 32:
            raise ValueError('Wallpaper dimensions must be at least 32 pixels')
        return image.copy()


def open_db(path):
    path.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(path)
    db.row_factory = sqlite3.Row
    version = db.execute('PRAGMA user_version').fetchone()[0]
    if version not in (0, 1):
        raise ValueError(f'Unsupported database version: {version}')
    db.executescript('''
        PRAGMA journal_mode=WAL;
        CREATE TABLE IF NOT EXISTS wallpapers (
          id TEXT PRIMARY KEY, url TEXT NOT NULL, status TEXT NOT NULL,
          source_path TEXT, source_sha256 TEXT, config_hash TEXT,
          manifest_path TEXT, manifest_url TEXT, width INTEGER, height INTEGER,
          layer_count INTEGER, quality_flags TEXT, provenance TEXT,
          assets TEXT, last_error TEXT, updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP);
        CREATE TABLE IF NOT EXISTS attempts (
          attempt_id INTEGER PRIMARY KEY, wallpaper_id TEXT NOT NULL,
          started_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP, finished_at TEXT,
          status TEXT NOT NULL, stage TEXT NOT NULL, error TEXT);
        PRAGMA user_version=1;
    ''')
    # Only called after holding the exclusive process lock.
    db.execute("UPDATE attempts SET status='interrupted', finished_at=CURRENT_TIMESTAMP WHERE status='processing'")
    db.execute("UPDATE wallpapers SET status='interrupted' WHERE status='processing'")
    db.commit()
    return db


def assets_valid(row, root):
    if not row or not row['assets'] or not row['manifest_path']:
        return False
    try:
        assets = json.loads(row['assets'])
        if not assets or row['manifest_path'] not in assets:
            return False
        return all((root / name).is_file() and digest(root / name) == checksum for name, checksum in assets.items())
    except (ValueError, OSError):
        return False


def export_csv(db, rows, destination):
    temporary = destination.with_suffix('.csv.part')
    with temporary.open('w', newline='', encoding='utf-8') as stream:
        writer = csv.writer(stream)
        writer.writerow(['id', 'url', 'scenePath'])
        for identifier, url in rows:
            row = db.execute("SELECT manifest_path FROM wallpapers WHERE id=? AND url=? AND status='ready'", (identifier, url)).fetchone()
            if row:
                writer.writerow([identifier, url, row[0]])
    temporary.replace(destination)


def run(args, model_factory=None, downloader=download):
    from spatial_assets.models import Models, PROVENANCE
    from spatial_assets.render import generate, mesh_text
    rows = read_csv(args.csv)
    root = args.output.resolve()
    root.mkdir(parents=True, exist_ok=True)
    database = (args.db or root / 'wallpapers.sqlite3').resolve()
    database.parent.mkdir(parents=True, exist_ok=True)
    config = {'version': PIPELINE_VERSION, 'max_size': args.max_size, 'max_layers': args.max_layers,
              'models': PROVENANCE}
    config_hash = hashlib.sha256(json.dumps(config, sort_keys=True).encode()).hexdigest()
    models = None
    failures = 0
    # Both paths locked: different DBs must not write one output directory concurrently.
    with (root / '.pipeline.lock').open('w') as output_lock, Path(str(database)+'.lock').open('w') as db_lock:
        for lock in (output_lock, db_lock):
            try:
                fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except BlockingIOError as error:
                raise ValueError('Another spatial pipeline owns this output or database') from error
        db = open_db(database)
        try:
            for identifier, url in rows:
                previous = db.execute('SELECT * FROM wallpapers WHERE id=?', (identifier,)).fetchone()
                same = previous and previous['url'] == url and previous['config_hash'] == config_hash
                if same and previous['status'] == 'ready' and not args.refresh and assets_valid(previous, root):
                    LOG.info('%s: cached', identifier)
                    continue
                db.execute('''INSERT INTO wallpapers(id,url,status) VALUES (?,?,'processing')
                    ON CONFLICT(id) DO UPDATE SET
                    source_sha256=CASE WHEN url=excluded.url THEN source_sha256 ELSE NULL END,
                    config_hash=CASE WHEN url=excluded.url THEN config_hash ELSE NULL END,
                    url=excluded.url,status='processing',last_error=NULL,
                    updated_at=CURRENT_TIMESTAMP''', (identifier, url))
                attempt = db.execute("INSERT INTO attempts(wallpaper_id,status,stage) VALUES (?,'processing','download')", (identifier,)).lastrowid
                db.commit()
                stage_dir = root / '.staging' / identifier
                try:
                    source = root / 'sources' / (identifier + '.image')
                    cached_source = previous and previous['url'] == url and source.exists() and previous['source_sha256'] == digest(source)
                    if args.refresh or not cached_source:
                        downloader(url, source)
                    source_hash = digest(source)
                    if not same or previous['source_sha256'] != source_hash:
                        db.execute('UPDATE wallpapers SET config_hash=NULL WHERE id=?', (identifier,))
                    db.execute('UPDATE wallpapers SET source_path=?,source_sha256=? WHERE id=?',
                               (str(source.relative_to(root)), source_hash, identifier))
                    db.execute("UPDATE attempts SET stage='inference' WHERE attempt_id=?", (attempt,))
                    db.commit()
                    if same and previous['source_sha256'] == source_hash and assets_valid(previous, root):
                        db.execute("UPDATE wallpapers SET status='ready' WHERE id=?", (identifier,))
                    else:
                        image = load_image(source, args.max_size)
                        if models is None:
                            LOG.info('Loading models (first run downloads checkpoints)')
                            cache = (args.model_cache or root / 'models').resolve()
                            cache.mkdir(parents=True, exist_ok=True)
                            models = (model_factory or Models)(args.device, cache, downloader)
                        layers, flags = generate(image, models, args.max_layers)
                        shutil.rmtree(stage_dir, ignore_errors=True)
                        stage_dir.mkdir(parents=True)
                        version = hashlib.sha256((source_hash + config_hash).encode()).hexdigest()[:24]
                        relative = Path('assets') / identifier / version
                        entries = []
                        for index, (layer, depth) in enumerate(layers):
                            name = f'layer-{index}'
                            layer.save(stage_dir / (name+'.png'), optimize=True)
                            (stage_dir / (name+'.depth')).write_text(mesh_text(depth), encoding='utf-8')
                            entries.append({'imageUrl': name+'.png', 'depthUrl': name+'.depth'})
                        (stage_dir / 'scene.json').write_text(json.dumps({'version': 1, 'layers': entries}, indent=2)+'\n', encoding='utf-8')
                        # Expose the output folder only after every asset is complete.
                        destination = root / relative
                        destination.parent.mkdir(parents=True, exist_ok=True)
                        if destination.exists():
                            # Retain previous versions and rebuild into a new folder.
                            version += '-' + str(time.time_ns())
                            relative = Path('assets') / identifier / version
                            destination = root / relative
                        stage_dir.rename(destination)
                        assets = {str(path.relative_to(root)): digest(path) for path in destination.iterdir()}
                        db.execute('''UPDATE wallpapers SET status='ready', config_hash=?, manifest_path=?,
                            manifest_url=NULL,width=?,height=?,layer_count=?,quality_flags=?,provenance=?,assets=?,
                            last_error=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?''',
                            (config_hash, str(relative/'scene.json'), *image.size,
                             len(layers), json.dumps(flags), json.dumps(models.provenance), json.dumps(assets), identifier))
                        LOG.info('%s: generated %d layers%s', identifier, len(layers), ' ['+', '.join(flags)+']' if flags else '')
                    db.execute("UPDATE attempts SET status='ready',stage='complete',finished_at=CURRENT_TIMESTAMP WHERE attempt_id=?", (attempt,))
                    db.commit()
                except (Exception, KeyboardInterrupt) as error:
                    status = 'interrupted' if isinstance(error, KeyboardInterrupt) else 'failed'
                    db.execute('UPDATE wallpapers SET status=?,last_error=?,updated_at=CURRENT_TIMESTAMP WHERE id=?', (status, str(error), identifier))
                    db.execute('UPDATE attempts SET status=?,error=?,finished_at=CURRENT_TIMESTAMP WHERE attempt_id=?', (status, str(error), attempt))
                    db.commit()
                    failures += 1
                    LOG.error('%s: %s: %s', identifier, status, error)
                    if isinstance(error, KeyboardInterrupt):
                        raise
                finally:
                    shutil.rmtree(stage_dir, ignore_errors=True)
            return 1 if failures else 0
        finally:
            export_csv(db, rows, root / 'wallpapers-ready.csv')
            db.close()


def parser():
    result = argparse.ArgumentParser(description=__doc__)
    result.add_argument('csv', type=Path, help='UTF-8 CSV with id,url headers')
    result.add_argument('--output', type=Path, default=Path('spatial-output'))
    result.add_argument('--db', type=Path, help='Default: OUTPUT/wallpapers.sqlite3')
    result.add_argument('--model-cache', type=Path)
    result.add_argument('--device', choices=['auto', 'cpu', 'cuda'], default='auto')
    result.add_argument('--max-layers', type=int, choices=[2, 3, 4], default=4)
    result.add_argument('--max-size', type=int, choices=[1024, 1536, 2048], default=2048)
    result.add_argument('--refresh', action='store_true', help='Re-fetch sources, reusing generated assets if content is unchanged')
    return result


def main():
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    args = parser().parse_args()
    try:
        return run(args)
    except KeyboardInterrupt:
        return 130
    except Exception as error:
        LOG.error('%s', error)
        return 1


if __name__ == '__main__':
    sys.exit(main())
