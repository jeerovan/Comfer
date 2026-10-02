# Bundled ANR investigation on Firebase Spark

Use the isolated `com.jeerovan.comfer.notificationtest` app. Version 56 remains
on hold; cloud device tests do not publish a Play release.

## Device catalog: refresh before choosing a device

- [Available Test Lab devices and lookup methods](https://firebase.google.com/docs/test-lab/android/available-testing-devices)
- [Comfer Test Lab console](https://console.firebase.google.com/project/comfer-db710/testlab)
- [Live Android catalog for Comfer](https://testing.googleapis.com/v1/testEnvironmentCatalog/ANDROID?projectId=comfer-db710)
  (authenticated API endpoint; use the runner below with the Firebase CLI login).
- [Device capacity definitions](https://firebase.google.com/docs/test-lab/reference/testing/rest/v1/testEnvironmentCatalog#DeviceCapacity)
- [Latest locally refreshed catalog](../validation-artifacts/testlab-anr/catalog.json)
  (generated local artifact, overwritten by each preflight; may be absent in a fresh checkout).

From the repository root, refresh without submitting a test or consuming a test run:

```sh
FIREBASE_CLI_LIB='/Volumes/JS/Developer/nvm/versions/node/v24.21.0/lib/node_modules/firebase-tools/lib' \
  /Volumes/JS/Developer/nvm/versions/node/v24.21.0/bin/node scripts/testlab_anr.cjs preflight
```

Adjust the Node/Firebase CLI installation paths if they change. Before each new
build test, match the exact model ID and Android version, then inspect that
version's `perVersionInfo.deviceCapacity`. Being listed under
`supportedVersionIds` alone does not establish testing availability.
`DEVICE_CAPACITY_NONE` means the device should not be requested; low capacity
permits a request but may involve queuing. Capacity is not a live queue guarantee.

The September 30, 2026 refresh showed BF6/API 31, X6525/API 33, and RMX3231/API 30
with low capacity, while BG6/API 33 had none. These are historical observations;
refresh before relying on them. BG6 and BG6m, like X6525 and X6525D, are different
models. The runner currently allows only BF6, BG6, and X6525; another model needs
an explicit runner configuration update after catalog and quota checks.

## Quota budget

Spark permits 5 physical and 10 virtual device runs per day, shared by the
project. Many JUnit methods can run within one device execution. Another
device, OS, locale dimension, shard, or retry can add executions.

The initial budget allows at most two diagnostic physical submissions, leaving
three physical runs for investigation and verification. On September 30, BG6
was listed but reported `DEVICE_CAPACITY_NONE`; its still-pending matrix was
cancelled. Infinix X6525/API 33 reports low capacity. The runner now rejects
none/unknown capacity before uploading or submitting. Count the cancelled
submission conservatively until the project's actual quota usage is confirmed.
There are no shards or flaky retries; `failFast` also prevents automatic
infrastructure reattempts. Locale changes happen inside the test, not through
additional matrix dimensions. Do not equate this with testing both system locales.

The runner reserves each submission locally before creating the matrix and uses
an idempotency request ID and a submission lock. It refuses duplicate devices and
more than four total submissions in diagnostic mode per UTC date. The user
authorized a TECNO BF6/API 31 run after the original three submissions;
the fifth slot remains reserved for verification. `--verify-fix` allows up to
five total submissions, only after a finished failure on the
same device and with a changed APK hash. This local ledger is not an authoritative
project-wide remaining-quota counter; check other contributors' executions too.
Do not delete a reservation or resubmit after an uncertain API response. Inspect
the recorded request/matrix first. Fix verification needs a rebuilt APK and a
separate, deliberately budgeted run.

## Build and local preflight

```sh
JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home' \
  ./gradlew :app:assembleNotificationTest :app:assembleNotificationTestAndroidTest \
  :app:testNotificationTestUnitTest -PcomferTestBuildType=notificationTest
adb -s SERIAL install -r app/build/outputs/apk/notificationTest/app-notificationTest.apk
adb -s SERIAL install -r app/build/outputs/apk/androidTest/notificationTest/app-notificationTest-androidTest.apk
adb -s SERIAL shell am instrument -w -r \
  -e class com.jeerovan.comfer.AnrTestLabSuite \
  com.jeerovan.comfer.notificationtest.test/androidx.test.runner.AndroidJUnitRunner
```

After the October 1 Huawei package-boundary regressions, the suite contains
31 tests on API 30+ and 29 on API 24, where two framework
compatibility tests are excluded by their SDK annotations:

- Real widget-service binding, RemoteViews rendering, and 30 pending-update /
  stop-start cycles across three hosts. Each final update must visibly arrive.
- Twelve launcher launches, twelve background/resume cycles, and twelve activity
  recreations, divided between explicit English/LTR and Arabic/RTL app locales.
- Existing app-refresh coalescing, widget failure/recovery, background scheduling,
  broadcast deadline/recovery, unsafe widget filtering, and framework compatibility
  regressions.

The two stress tests sample main-thread dispatch latency and log stack traces for
stalls over one second. Five-second dispatch stalls fail the test. This is a
diagnostic threshold, not Android's ANR classification. The widget provider exists
only in the notificationTest variant. Its widget IDs and bind grant are cleaned up.

Test Lab uses Android Test Orchestrator to invoke tests in separate instrumentation
processes without adding shards. Local preflight runs the suite together; it does
not establish repeated process-cold-start coverage.

## Cloud runner

Use a Node runtime and the installed Firebase CLI's `lib` directory:

```sh
export FIREBASE_CLI_LIB='/Volumes/JS/Developer/nvm/versions/node/v24.21.0/lib/node_modules/firebase-tools/lib'
node scripts/testlab_anr.cjs preflight
node scripts/testlab_anr.cjs submit Infinix-X6525
node scripts/testlab_anr.cjs submit TECNO-BF6
node scripts/testlab_anr.cjs status MATRIX_ID
node scripts/testlab_anr.cjs download MATRIX_ID
# Only when the catalog again reports nonzero capacity, with daily quota left:
node scripts/testlab_anr.cjs submit TECNO-BG6
# After diagnosing a failure, rebuilding, and passing local preflight:
node scripts/testlab_anr.cjs submit Infinix-X6525 --verify-fix
```

The script reuses identical uploaded APKs for the second device, records SHA-256
hashes and exact requests, and leaves video/performance capture enabled. Diagnostic
artifacts and the submission ledger are under `validation-artifacts/testlab-anr/`.
Never print authentication tokens or add service-account credentials to artifacts.

Cloud Tool Results API must be enabled on Comfer. Its requests need Comfer as the
quota project. Storage requests to Google's Test Lab bucket must **omit** the
user-project billing header, otherwise uploads fail on Spark with billing state
`absent`. Do not enable billing to work around that header error.

## Interpretation and limits

The tested source includes the held version-56 compatibility fix. It is an
unminified isolated build, not the exact production v55 APK. A passing run does
not close a production ANR or establish a lower production ANR rate.

The harness removes FirebaseInitProvider and production Google Services resources.
It therefore does not reproduce Firebase Crashlytics/Sessions automatic startup.
That requires a separately configured isolated Firebase startup harness before
claiming coverage. OEM provider behavior, other firmware versions, memory/storage
pressure, and unsupported BG6m/X6525D variants are also not covered by this suite.

Device-filtered Crashlytics reports on September 30 showed 45 of BG6's 60 ANRs and
33 of X6525's 35 ANRs in the unknown-root-cause `nativePollOnce` group. These snapshots
do not identify the blocking work. A matching lab failure and its traces are needed
before attributing that group to a particular code path or claiming a fix.

## September 30 diagnostic result

Local API 24 preflight passed 27 tests; all 246 app unit tests passed. The first
Infinix X6525/API 33 run (`matrix-1sws8m7hmbt6w`) completed 29 tests: 28 passed,
one failed during the test's Arabic locale switch. The test called AppCompat's
locale setter without any active activity delegate. On API 33+, that cannot
obtain LocaleManager and silently does nothing. The harness now invokes the
app's existing LanguageUpdateActivity and checks framework per-app locale state.
Production locale code was not changed.

The first run's real widget-service stress passed with maximum sampled dispatch
latency 44 ms. English launcher lifecycle stress peaked at 1,530 ms, with a
HardwareRenderer stack captured at 1,001 ms. No application ANR record appeared
in the downloaded logcat. Arabic lifecycle coverage did not complete in that
run; do not count the locale timeout as a reproduced production ANR.

After the harness correction, the complete suite passed 29/29 on the local Pixel
10/API 37 emulator, and the two corrected stress tests passed 2/2 again on API 24.
Both exercised English/LTR and Arabic/RTL app locales and restored the original
app locale. Device system language was not changed. The verification submission
is `matrix-9eawt8iym916a`; its app APK is identical to the diagnostic run and only
the instrumentation APK changed. There are three submissions including the
cancelled TECNO request and no virtual Test Lab submissions.

Final Infinix verification: **29 passed, 0 failed, 0 skipped, 0 errors**. Test Lab
reported success with 163 seconds execution time; JUnit recorded 47.003 seconds
of test-method time. Sampled maximum main-thread dispatch latency was 1,062 ms
for English launcher stress, 382 ms for Arabic, and 17 ms for real widget service
stress. The downloaded logcat contains no ANR record for the isolated app. This
does not reproduce or close the production ANR issues; no production ANR fix was
made in this investigation.

- Successful run: https://console.firebase.google.com/project/comfer-db710/testlab/histories/bh.b2465183b7a67b63/matrices/7360429314067308897
- Initial diagnostic: https://console.firebase.google.com/project/comfer-db710/testlab/histories/bh.b2465183b7a67b63/matrices/5436021608365214661
- Cancelled BG6: https://console.firebase.google.com/project/comfer-db710/testlab/histories/bh.b2465183b7a67b63/matrices/6559408351480138401

Three physical executions ran, and one queued request was cancelled without testing.
Counting all four submissions against the
five-run budget leaves one slot reserved for fix verification. Actual cancellation quota refunds were not
verified. No virtual Test Lab quota was used, billing was not enabled, and the
version-56 release remains on hold.

## TECNO BF6 follow-up

On September 30 at 10:02 UTC, submitted `matrix-yvzv4izngsrha` on one physical
TECNO BF6 (POP 7), Android 12/API 31, portrait, English device locale. The live
catalog reported low capacity. The request reuses the exact app/test APK hashes
from the successful Infinix verification and bundles all 29 tests, including
English/LTR and Arabic/RTL app-locale stress, without shards or flaky retries.
The runner now selects API 31 for BF6 and API 33 for BG6/X6525.

The matrix finished **INCONCLUSIVE** with execution state `ERROR`: Test Lab
exhausted three results post-processing attempts. Tool Results explicitly marks
this as an infrastructure failure. The downloaded JUnit XML contains all 29
test cases with zero failures, errors, or skips, and the instrumentation output
independently reports `OK (29 tests)`. Thus the raw tests passed, while the cloud
matrix itself did not receive a successful outcome. No new run was submitted
to work around the post-processing failure.

The downloaded logcat has no `ANR in`, `am_anr`, or `FATAL EXCEPTION` records.
Sampled maximum main-thread dispatch latency was 2,930 ms for English launcher
stress, 649 ms for Arabic, and 24 ms for widget-service stress. English stall
samples at 1,000 and 2,027 ms show initial Compose/theme composition and node
updates. This is a startup responsiveness lead, not a reproduced production ANR
or proof that one sampled function caused the entire delay. The build remains
unminified and isolated, without Firebase automatic initialization. No production
ANR fix or version-56 release resulted from this run. Both app-locale directions
were exercised; the device system locale remained English.

- BF6 result: https://console.firebase.google.com/project/comfer-db710/testlab/histories/bh.b2465183b7a67b63/matrices/8072712427525856226

Sources:

- https://firebase.google.com/docs/test-lab/usage-quotas-pricing
- https://firebase.google.com/docs/test-lab/reference/testing/rest/v1/projects.testMatrices
- https://firebase.google.com/docs/test-lab/android/analyzing-results
