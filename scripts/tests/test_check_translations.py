import importlib.util
from pathlib import Path
import tempfile
import unittest

spec = importlib.util.spec_from_file_location('check_translations', Path(__file__).parents[1] / 'check_translations.py')
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)

class TranslationChecksTest(unittest.TestCase):
    def test_arguments_allow_reordering_but_preserve_index_type_and_count(self):
        self.assertEqual(module.arguments('%1$s: %2$d, 100%%'), module.arguments('%2$d — %1$s'))
        self.assertNotEqual(module.arguments('%1$s: %2$d'), module.arguments('%1$d: %2$s'))
        self.assertNotEqual(module.arguments('%1$s %1$s'), module.arguments('%1$s'))

    def test_missing_translation_and_picker_locale_are_reported(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for d in ('values', 'values-fr', 'xml'):
                (root / d).mkdir()
            (root / 'values/strings.xml').write_text('<resources><string name="label">Save</string><string name="brand" translatable="false">Comfer</string></resources>')
            (root / 'values-fr/strings.xml').write_text('<resources/>')
            (root / 'xml/locales_config.xml').write_text('<locale-config xmlns:android="http://schemas.android.com/apk/res/android"><locale android:name="en"/></locale-config>')
            errors, locales, required = module.check(root)
            self.assertEqual((locales, required), (1, 1))
            self.assertTrue(any('missing label' in e for e in errors))
            self.assertTrue(any('Locale picker mismatch' in e for e in errors))
            self.assertFalse(any('missing brand' in e for e in errors))

    def test_translation_markup_is_rejected(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            for d in ('values', 'values-fr', 'xml'):
                (root / d).mkdir()
            (root / 'values/strings.xml').write_text('<resources><string name="label">Snow</string></resources>')
            (root / 'xml/locales_config.xml').write_text('<locale-config xmlns:android="http://schemas.android.com/apk/res/android"><locale android:name="en"/><locale android:name="fr"/></locale-config>')
            for text in ('&lt;0xE9&gt;', '&amp; # 160;', 'Neige @ info', 'ZXQ1ZXQ'):
                with self.subTest(text=text):
                    (root / 'values-fr/strings.xml').write_text(f'<resources><string name="label">{text}</string></resources>')
                    errors, _, _ = module.check(root)
                    self.assertTrue(any('untranslated marker' in error for error in errors))

    def test_android_legacy_resource_folders_match_modern_picker_names(self):
        for legacy, modern in (('iw', 'he'), ('in', 'id')):
            with self.subTest(locale=modern), tempfile.TemporaryDirectory() as tmp:
                root = Path(tmp)
                for d in ('values', f'values-{legacy}', 'xml'):
                    (root / d).mkdir()
                content = '<resources><string name="label">Example</string></resources>'
                (root / 'values/strings.xml').write_text(content)
                (root / f'values-{legacy}/strings.xml').write_text(content)
                (root / 'xml/locales_config.xml').write_text(f'<locale-config xmlns:android="http://schemas.android.com/apk/res/android"><locale android:name="en"/><locale android:name="{modern}"/></locale-config>')
                self.assertEqual(module.check(root)[0], [])
                (root / f'values-{legacy}').rename(root / f'values-{modern}')
                self.assertTrue(any('Android resource lookup' in error for error in module.check(root)[0]))

if __name__ == '__main__':
    unittest.main()
