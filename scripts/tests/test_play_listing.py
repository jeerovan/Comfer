import csv
from datetime import date, timedelta
import io
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import play_listing as listing


class ListingEvidenceTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.conn = listing.connect(Path(self.temp.name)/'listing.db')
        self.copy = [{'language': 'en-US', 'title': 'Comfer Launcher',
                      'shortDescription': 'Swipe to reach your apps', 'fullDescription': 'App search and widgets.',
                      'video': 'https://www.youtube.com/watch?v=example'}]
        self.version = listing.save_version(self.conn, self.copy, [], label='Baseline', state='observed', rationale='Observed only', version=1)

    def tearDown(self):
        self.conn.close()
        self.temp.cleanup()

    def payload(self, rows, dimension='traffic_source', encoding='utf-8'):
        dims = ['Traffic source', 'Search term', 'UTM source', 'UTM campaign'] if dimension == 'traffic_source' else ['Country / region']
        headers = ['Date', 'Package name'] + dims + ['Store listing visitors', 'Store listing acquisitions', 'Store listing conversion rate']
        target = io.StringIO()
        writer = csv.DictWriter(target, headers)
        writer.writeheader()
        for row in rows:
            writer.writerow(dict({'Package name': listing.PACKAGE}, **row))
        return target.getvalue().encode(encoding)

    def report(self, rows, generation='1', dimension='traffic_source', month='202001', encoding='utf-8'):
        name = f'stats/store_performance/store_performance_{listing.PACKAGE}_{month}_{dimension}.csv'
        return listing.import_report(self.conn, {'name': name, 'generation': generation}, self.payload(rows, dimension, encoding))

    def row(self, day='2020-01-01', visitors='100', acquired='10', term='comfer', source='Google Play search'):
        return {'Date': day, 'Traffic source': source, 'Search term': term,
                'Store listing visitors': visitors, 'Store listing acquisitions': acquired}

    def test_copy_video_and_version_history_are_immutable_and_idempotent(self):
        same = listing.save_version(self.conn, self.copy, [], label='Ignored duplicate', state='observed', rationale='', version=1)
        self.assertEqual(same, self.version)
        self.assertEqual(self.conn.execute('SELECT video_url FROM listing_locales').fetchone()[0], self.copy[0]['video'])
        changed = [dict(self.copy[0], title='A different title')]
        with self.assertRaises(ValueError):
            listing.save_version(self.conn, changed, [], label='No overwrite', state='prepared', rationale='', version=1)
        with self.assertRaises(sqlite3.IntegrityError):
            self.conn.execute("UPDATE listing_locales SET title='Lost history'")

    def test_observation_storage_path_does_not_create_new_listing(self):
        asset = {'locale': 'en-US', 'kind': 'phoneScreenshots', 'order': 1, 'sha256': 'content', 'local_path': 'old.png'}
        ident = listing.save_version(self.conn, self.copy, [asset], label='A', state='observed', rationale='')
        same = listing.save_version(self.conn, self.copy, [dict(asset, local_path='new.png')], label='B', state='observed', rationale='')
        self.assertEqual(ident, same)

    def test_snapshot_preserves_asset_bytes_after_source_is_removed(self):
        root = Path(self.temp.name)
        file = root/'baseline.png'
        payload = b'archived image bytes'
        file.write_bytes(payload)
        snapshot = {'listings': {'listings': self.copy}, 'images': {'en-US': {'phoneScreenshots': {'images': [
            {'url': 'https://lh3.googleusercontent.com/example', 'local_path': file.name,
             'download_sha256': listing.sha(payload), 'sha256': 'platform-upload-hash'}]}}}}
        listing.save_snapshot(self.conn, snapshot, root=root)
        file.unlink()
        saved = self.conn.execute('SELECT payload FROM asset_files').fetchone()[0]
        self.assertEqual(saved, payload)

    def test_utf16_blank_is_missing_and_same_generation_is_idempotent(self):
        rows = [self.row(acquired='')]
        self.report(rows, encoding='utf-16')
        self.report(rows, encoding='utf-16')
        self.assertEqual(self.conn.execute('SELECT count(*) FROM report_objects').fetchone()[0], 1)
        stats = listing.summary(self.conn, date(2020,1,1), date(2020,1,2))['traffic_sources']['Google Play search']
        self.assertEqual(stats['missing_rows'], 1)
        self.assertIsNone(stats['acquisition_conversion_rate'])
        self.assertEqual(stats['visitors'], 100)
        self.assertIsNone(stats['acquisitions'])

    def test_new_generation_replaces_whole_object_in_latest_view_and_keeps_raw_history(self):
        self.report([self.row(), self.row(day='2020-01-02')])
        self.report([self.row(visitors='20', acquired='5')], generation='2')
        self.assertEqual(self.conn.execute('SELECT count(*) FROM performance_rows').fetchone()[0], 3)
        latest = self.conn.execute('SELECT * FROM latest_performance').fetchall()
        self.assertEqual(len(latest), 1)
        self.assertEqual(json.loads(latest[0]['metrics_json'])['Store listing visitors'], '20')
        self.assertEqual(self.conn.execute('SELECT count(*) FROM report_objects WHERE length(payload)>0').fetchone()[0], 2)

    def test_invalid_generation_is_atomic_and_preserves_prior_evidence(self):
        self.report([self.row()])
        with self.assertRaises(ValueError):
            self.report([self.row(day='2020-02-01')], generation='2')
        with self.assertRaises(ValueError):
            self.report([self.row(visitors='1', acquired='9')], generation='2')
        with self.assertRaises(ValueError):
            self.report([self.row(acquired='9')], generation='1')
        self.assertEqual(self.conn.execute('SELECT count(*) FROM report_objects').fetchone()[0], 1)

    def test_conversion_uses_matched_counts_and_search_terms_keep_traffic_source(self):
        self.report([self.row(), self.row(day='2020-01-02', visitors='10', acquired='9'),
                     self.row(visitors='50', acquired='1', source='Google Play explore')])
        self.report([{'Date':'2020-01-01', 'Country / region':'IN', 'Store listing visitors':'999', 'Store listing acquisitions':'900'}], dimension='country')
        result = listing.summary(self.conn, date(2020,1,1), date(2020,1,3))
        self.assertAlmostEqual(result['traffic_sources']['Google Play search']['acquisition_conversion_rate'], 19/110)
        self.assertEqual(result['search_terms_search_only']['comfer']['visitors'], 110)
        self.assertEqual(result['countries_separate_axis']['IN']['visitors'], 999)

    def test_unpublished_candidate_has_no_evaluation_window(self):
        result = listing.compare(self.conn, 1, 28)
        self.assertEqual(result['status'], 'not_started')

    def test_comparison_waits_for_every_date_and_excludes_transition_day(self):
        listing.activate(self.conn, 1, '2020-01-15T12:00:00-08:00', 'Verified in Console')
        self.report([self.row(day='2020-01-14'), self.row(day='2020-01-15'), self.row(day='2020-01-16')])
        result = listing.compare(self.conn, 1, 7)
        self.assertEqual(result['status'], 'incomplete')
        self.assertEqual(result['windows'][0]['end_exclusive'], '2020-01-15')
        self.assertEqual(result['windows'][1]['start_inclusive'], '2020-01-16')
        self.assertNotIn('conversion_change_percentage_points', result)

    def test_complete_equal_week_windows_and_later_activation_conflict(self):
        listing.activate(self.conn, 1, '2020-01-15T12:00:00-08:00', 'Verified in Console')
        self.report([self.row(day=str(date(2020,1,8)+timedelta(days=i))) for i in range(15)])
        result = listing.compare(self.conn, 1, 7)
        self.assertEqual(result['status'], 'observational')
        self.assertAlmostEqual(result['conversion_change_percentage_points'], 0)
        listing.save_version(self.conn, [dict(self.copy[0], title='Comfer Comfort Launcher')], [], label='Next', state='prepared', rationale='', version=2)
        listing.activate(self.conn, 2, '2020-01-20T12:00:00-08:00', 'Another listing published')
        self.assertEqual(listing.compare(self.conn, 1, 7)['status'], 'exposure_conflict')

    def test_activation_needs_timezone_and_cannot_be_future(self):
        for timestamp in ['2020-01-01T12:00:00', '2999-01-01T12:00:00Z']:
            with self.assertRaises(ValueError):
                listing.activate(self.conn, 1, timestamp, 'Evidence')

    def test_review_timestamps_keep_utc_instead_of_install_report_timezone(self):
        payload = ('Package Name,Review Submit Date and Time,Star Rating\n' +
                   listing.PACKAGE + ',2020-01-01T00:30:00Z,5\n').encode()
        listing.import_report(self.conn, {'name':f'reviews/reviews_{listing.PACKAGE}_202001.csv', 'generation':'1'}, payload)
        row = self.conn.execute('SELECT timezone,date FROM latest_performance').fetchone()
        self.assertEqual(tuple(row), ('UTC','2020-01-01'))

    def test_deployed_candidate_reuses_revision_with_retained_icon_without_starting_evaluation(self):
        snapshot = {'listings':{'listings':self.copy},'images':{'en-US':{'icon':{'images':[
            {'url':'https://lh3.googleusercontent.com/icon','sha256':'original-icon'}]}}}}
        listing.save_snapshot(self.conn,snapshot,root=Path(self.temp.name))
        root=Path(self.temp.name)
        file=root/'screen.png';file.write_bytes(b'candidate image')
        candidate=dict(self.copy[0],title='Comfer Comfort Launcher')
        manifest={'version':3,'label':'Candidate','hypothesis':'Clearer purpose','listing':dict(candidate,locale='en-US'),
                  'assets':[{'kind':'phoneScreenshots','locale':'en-US','path':file.name,'sha256':listing.sha(file.read_bytes())}],
                  'retained_assets':['icon']}
        manifest['listing'].pop('language')
        path=root/'manifest.json';path.write_text(json.dumps(manifest))
        ident=listing.add_candidate(self.conn,path,root=root)
        snapshot['listings']['listings']=[candidate]
        snapshot['images']['en-US']['phoneScreenshots']={'images':[{'url':'https://lh3.googleusercontent.com/new','sha256':listing.sha(file.read_bytes())}]}
        observed=listing.save_snapshot(self.conn,snapshot,root=root)
        self.assertEqual(ident,observed)
        self.assertEqual({asset['kind'] for asset in listing.effective_assets(self.conn,ident)}, {'icon','phoneScreenshots'})
        self.assertEqual(listing.compare(self.conn,3,28)['status'],'not_started')


if __name__ == '__main__':
    unittest.main()
