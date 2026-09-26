import csv
from contextlib import closing
import json
from pathlib import Path
import sqlite3
import sys
import tempfile
import unittest
from unittest.mock import patch
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import threading

import numpy as np
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import prepare_cloud_spatial as pipeline
from spatial_assets.render import generate, mesh_text, normalize_depth


class FakeModels:
    provenance = {'test': True}
    calls = 0
    def __init__(self, *args):
        type(self).calls += 1
    def depth(self, image):
        return np.tile(np.linspace(1, 5, image.width, dtype=np.float32), (image.height, 1))
    def mask(self, image):
        mask = np.zeros((image.height, image.width), np.float32)
        mask[12:30, 12:25] = 1
        mask[40:56, 40:56] = 1
        return mask
    def inpaint(self, image, mask):
        return Image.new('RGB', image.size, (50, 70, 90))


class PipelineTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.csv = self.root / 'input.csv'
        self.write_rows([('1', 'https://example.com/one.png')])
        self.args = pipeline.parser().parse_args([str(self.csv), '--output', str(self.root/'output')])
        FakeModels.calls = 0
        self.downloads = 0
    def write_rows(self, rows):
        with self.csv.open('w', newline='') as stream:
            writer = csv.writer(stream)
            writer.writerow(['id', 'url'])
            writer.writerows(rows)
    def download(self, url, destination):
        self.downloads += 1
        destination.parent.mkdir(parents=True, exist_ok=True)
        Image.new('RGB', (64, 80), (120, 150, 180)).save(destination, format='PNG')
    def run_pipeline(self, factory=FakeModels):
        return pipeline.run(self.args, factory, self.download)
    def row(self):
        with closing(sqlite3.connect(self.args.output/'wallpapers.sqlite3')) as db:
            db.row_factory = sqlite3.Row
            return db.execute('SELECT * FROM wallpapers WHERE id="1"').fetchone()
    def test_contract_and_resume_without_model_loading(self):
        self.assertEqual(self.run_pipeline(), 0)
        row = self.row()
        self.assertEqual(row['status'], 'ready')
        manifest = json.loads((self.args.output/row['manifest_path']).read_text())
        self.assertEqual(manifest['version'], 1)
        self.assertEqual(len(manifest['layers']), 3)
        for index, layer in enumerate(manifest['layers']):
            self.assertEqual(layer['imageUrl'], f'layer-{index}.png')
            self.assertEqual(layer['depthUrl'], f'layer-{index}.depth')
            folder = (self.args.output/row['manifest_path']).parent
            with Image.open(folder/f'layer-{index}.png') as image:
                self.assertEqual(image.size, (64, 80))
                if index == 0:
                    self.assertEqual(image.getchannel('A').getextrema(), (255, 255))
            tokens = (folder/f'layer-{index}.depth').read_text().split()
            self.assertEqual(tokens[:2], ['48', '96'])
            self.assertEqual(len(tokens)-2, 49*97)
            self.assertTrue(all(0 <= float(v) <= 1 for v in tokens[2:]))
        self.assertEqual(self.run_pipeline(lambda *args: self.fail('Models loaded on cache hit')), 0)
        self.assertEqual(self.downloads, 1)
        self.assertEqual(FakeModels.calls, 1)
    def test_refresh_same_bytes_reuses_assets(self):
        self.run_pipeline()
        before = self.row()['manifest_path']
        self.args.refresh = True
        self.assertEqual(self.run_pipeline(lambda *args: self.fail('Unchanged image regenerated')), 0)
        self.assertEqual(self.row()['manifest_path'], before)
        self.assertEqual(self.downloads, 2)
    def test_corrupt_asset_is_rebuilt_in_new_folder(self):
        self.run_pipeline()
        before = self.row()
        (self.args.output/before['manifest_path']).write_text('corrupt')
        self.assertEqual(self.run_pipeline(), 0)
        self.assertNotEqual(self.row()['manifest_path'], before['manifest_path'])
    def test_changed_configuration_regenerates(self):
        self.run_pipeline()
        self.args.max_layers = 2
        self.assertEqual(self.run_pipeline(), 0)
        self.assertEqual(self.row()['layer_count'], 2)
    def test_failure_continues_and_retry_succeeds(self):
        self.write_rows([('1', 'https://example.com/one'), ('2', 'https://example.com/two')])
        original = self.download
        def fail_first(url, destination):
            if url.endswith('one'):
                raise OSError('simulated failure')
            original(url, destination)
        self.download = fail_first
        self.assertEqual(self.run_pipeline(), 1)
        self.assertEqual(self.row()['status'], 'failed')
        with (self.args.output/'wallpapers-ready.csv').open() as stream:
            self.assertEqual([r['id'] for r in csv.DictReader(stream)], ['2'])
        self.download = original
        self.assertEqual(self.run_pipeline(), 0)
        self.assertEqual(self.row()['status'], 'ready')
    def test_new_source_failure_cannot_reuse_previous_scene(self):
        self.run_pipeline()
        before = self.row()['manifest_path']
        self.args.refresh = True
        def changed(url, destination):
            Image.new('RGB', (64, 80), (10, 20, 30)).save(destination, format='PNG')
        self.download = changed
        class Broken(FakeModels):
            def depth(self, image):
                raise RuntimeError('inference interrupted')
        self.assertEqual(self.run_pipeline(Broken), 1)
        self.args.refresh = False
        self.assertEqual(self.run_pipeline(), 0)
        self.assertNotEqual(self.row()['manifest_path'], before)
    def test_changed_url_download_failure_does_not_reuse_old_source(self):
        self.run_pipeline()
        self.write_rows([('1', 'https://example.com/new')])
        old_download = self.download
        self.download = lambda *args: (_ for _ in ()).throw(OSError('offline'))
        self.assertEqual(self.run_pipeline(), 1)
        self.download = old_download
        before = self.downloads
        self.assertEqual(self.run_pipeline(), 0)
        self.assertEqual(self.downloads, before+1)
    def test_interruption_is_recorded(self):
        self.download = lambda *args: (_ for _ in ()).throw(KeyboardInterrupt())
        with self.assertRaises(KeyboardInterrupt):
            self.run_pipeline()
        self.assertEqual(self.row()['status'], 'interrupted')
    def test_csv_validation(self):
        for rows in [[('../bad', 'https://example.com/a')], [('1', 'file:///tmp/a')],
                     [('1', 'https://example.com/a'), ('1', 'https://example.com/b')]]:
            self.write_rows(rows)
            with self.assertRaises(ValueError):
                pipeline.read_csv(self.csv)
        self.write_rows([('1', 'https://example.com/a')]*2)
        self.assertEqual(len(pipeline.read_csv(self.csv)), 1)
    def test_export_contains_local_scene_path(self):
        self.assertEqual(self.run_pipeline(), 0)
        with (self.args.output/'wallpapers-ready.csv').open() as stream:
            rows = list(csv.DictReader(stream))
        self.assertEqual(rows[0]['scenePath'], self.row()['manifest_path'])
        self.assertTrue((self.args.output/rows[0]['scenePath']).is_file())
        self.assertNotIn('spatialSceneUrl', rows[0])
        self.assertIsNone(self.row()['manifest_url'])

    def test_flat_and_invalid_depth(self):
        depth = normalize_depth(np.ones((4, 4)), (64, 80))
        self.assertTrue(np.isfinite(depth).all())
        for invalid in [np.zeros((4, 4)), np.full((4, 4), np.nan)]:
            with self.assertRaises(ValueError):
                normalize_depth(invalid, (64, 80))
    def test_depth_orientation_and_grid_slope(self):
        depth = normalize_depth(np.tile(np.array([1., 100.]), (2, 1)), (49, 97))
        self.assertGreater(depth[0, 0], depth[0, -1])
        grid = np.array([float(v) for v in mesh_text(depth).split()[2:]]).reshape(97, 49)
        self.assertLessEqual(np.abs(np.diff(grid, axis=0)).max(), .180001)
        self.assertLessEqual(np.abs(np.diff(grid, axis=1)).max(), .180001)
    def test_unreliable_segmentation_falls_back(self):
        models = FakeModels()
        models.mask = lambda image: np.ones((80, 64), np.float32)
        layers, flags = generate(Image.new('RGB', (64, 80)), models, 4)
        self.assertEqual(len(layers), 1)
        self.assertIn('single_layer_unreliable_segmentation', flags)
    def test_layers_preserve_foreground_alpha_and_outside_pixels(self):
        models = FakeModels()
        original = Image.new('RGB', (64, 80), (120, 150, 180))
        layers, _ = generate(original, models, 4)
        alpha = sum(np.asarray(image)[:, :, 3].astype(float)/255 for image, _ in layers[1:])
        np.testing.assert_allclose(alpha, models.mask(original))
        np.testing.assert_array_equal(np.asarray(layers[0][0])[0, 0, :3], np.asarray(original)[0, 0])
    def test_lock_prevents_concurrent_writer(self):
        self.args.output.mkdir()
        with (self.args.output/'.pipeline.lock').open('w') as lock:
            pipeline.fcntl.flock(lock, pipeline.fcntl.LOCK_EX | pipeline.fcntl.LOCK_NB)
            with self.assertRaisesRegex(ValueError, 'Another spatial pipeline'):
                self.run_pipeline()

    def test_http_download_limit_and_atomic_replacement(self):
        class Handler(BaseHTTPRequestHandler):
            def do_GET(self):
                self.send_response(200)
                self.send_header('Content-Length', '12')
                self.end_headers()
                self.wfile.write(b'hello world!')
            def log_message(self, *args):
                pass
        server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
        thread = threading.Thread(target=server.serve_forever, daemon=True)
        thread.start()
        try:
            url = f'http://127.0.0.1:{server.server_port}/image'
            destination = self.root/'download'
            destination.write_bytes(b'previous')
            with self.assertRaisesRegex(ValueError, 'byte limit'):
                pipeline.download(url, destination, limit=5)
            self.assertEqual(destination.read_bytes(), b'previous')
            self.assertFalse((self.root/'download.part').exists())
            pipeline.download(url, destination, limit=12)
            self.assertEqual(destination.read_bytes(), b'hello world!')
        finally:
            server.shutdown()
            server.server_close()
            thread.join()

    def test_http_retries_are_bounded(self):
        import requests
        with patch('requests.Session.get', side_effect=requests.ConnectionError('offline')) as get, patch('time.sleep'):
            with self.assertRaises(requests.ConnectionError):
                pipeline.download('https://example.com/a', self.root/'download')
            self.assertEqual(get.call_count, 3)
            self.assertFalse((self.root/'download.part').exists())

    def test_image_validation_and_canvas(self):
        path = self.root/'image.png'
        Image.new('RGBA', (200, 100), (255, 0, 0, 0)).save(path)
        image = pipeline.load_image(path, 100)
        self.assertEqual(image.size, (100, 50))
        self.assertEqual(image.mode, 'RGB')
        self.assertEqual(image.getpixel((0, 0)), (0, 0, 0))
        path.write_bytes(b'not an image')
        with self.assertRaises(Exception):
            pipeline.load_image(path, 100)

    def test_abandoned_attempt_is_recovered(self):
        db = pipeline.open_db(self.root/'state.sqlite3')
        db.execute("INSERT INTO wallpapers(id,url,status) VALUES ('1','https://example.com/a','processing')")
        db.execute("INSERT INTO attempts(wallpaper_id,status,stage) VALUES ('1','processing','inference')")
        db.commit()
        db.close()
        db = pipeline.open_db(self.root/'state.sqlite3')
        try:
            self.assertEqual(db.execute('SELECT status FROM wallpapers').fetchone()[0], 'interrupted')
            self.assertEqual(db.execute('SELECT status FROM attempts').fetchone()[0], 'interrupted')
        finally:
            db.close()


if __name__ == '__main__':
    unittest.main()
