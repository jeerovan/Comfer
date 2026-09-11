from pathlib import Path
import sys
import unittest


sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import verify_apk_components as verifier


class ManifestComponentTests(unittest.TestCase):
    def test_application_factory_is_verified_with_components(self):
        manifest = """\
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    package="com.example.app">
    <application
        android:name=".ExampleApp"
        android:appComponentFactory=".ExampleFactory">
        <activity android:name=".MainActivity" />
        <service android:name="com.example.jobs.SyncService" />
    </application>
</manifest>
"""

        self.assertEqual(
            verifier.manifest_components(manifest),
            {
                "com.example.app.ExampleApp",
                "com.example.app.ExampleFactory",
                "com.example.app.MainActivity",
                "com.example.jobs.SyncService",
            },
        )


if __name__ == "__main__":
    unittest.main()
