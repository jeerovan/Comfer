# Notification emulator fixtures

This debug-only module builds two separately installed apps (`com.jeerovan.fixtures.mail` and `com.jeerovan.fixtures.chat`). They post actual Android notifications so Comfer's listener is tested across package boundaries. They are not dependencies of the launcher APK and have no release variants.

Run from the repository root with the configured Gradle JDK:

```sh
python3 scripts/test_notifications.py emulator-5554
```

Use `--skip-build` only after a successful current build. Reports and screenshots go to ignored `validation-artifacts/notifications/<serial>/`. Tests restore notification access and Comfer notification configuration after each test. Fixture notification posting permission is granted on API 33+. Synthetic fixture notifications are cleared after tests; Android can retain snoozed items until their snooze expires. Do not install the fixtures as production apps.

Example synthetic notification:

```sh
adb -s emulator-5554 shell am broadcast \
  -n com.jeerovan.fixtures.mail/com.jeerovan.fixtures.FixtureReceiver \
  --ei id 1 --es title Example --es text Preview --es group mail
```

Supported `kind` values: `summary` (with `group`), `message`, `progress`, `ongoing`, `empty`, `expired`, `silent`. `--ei updates 100` exercises rapid updates to the same ID; Android can reject some posts through source rate limiting. Use `--es operation remove` with an ID, or `--es operation clear` to remove this fixture app's notifications. No real messaging account or network connection is required.

Automated coverage includes multiple apps, grouped children, content revisions, individual dismissal, protected records, expired-intent fallback with dismissal after opening, protected opens, optional saved history, payload exclusion from configuration, burst convergence, access revocation, native snooze where supported, empty entry, compact portrait and landscape. Physical sound, vibration, OEM restrictions, work profiles, TalkBack usability and thumb-reach comfort need separate device validation.
