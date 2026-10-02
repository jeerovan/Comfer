#!/usr/bin/env node
// Uses the Firebase CLI's existing login; never reads or prints bearer tokens.
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');
const cliRoot = process.env.FIREBASE_CLI_LIB;
const project = 'comfer-db710';
const output = path.resolve('validation-artifacts/testlab-anr');
const headers = { 'x-goog-user-project': project };

async function main() {
  if (!cliRoot) throw new Error('Set FIREBASE_CLI_LIB to the installed firebase-tools/lib directory.');
  const account = require(path.join(cliRoot, 'auth')).getGlobalDefaultAccount();
  if (!account) throw new Error('Sign in with Firebase CLI first.');
  await require(path.join(cliRoot, 'requireAuth')).requireAuth({ project, ...account });
  const { Client } = require(path.join(cliRoot, 'apiv2'));
  const testing = new Client({ urlPrefix: 'https://testing.googleapis.com', apiVersion: 'v1' });
  const results = new Client({ urlPrefix: 'https://toolresults.googleapis.com', apiVersion: 'toolresults/v1beta3' });
  const storage = new Client({ urlPrefix: 'https://storage.googleapis.com', apiVersion: 'storage/v1' });
  fs.mkdirSync(output, { recursive: true });
  const save = (name, value) => fs.writeFileSync(path.join(output, name), JSON.stringify(value, null, 2) + '\n');
  const [command, id, mode] = process.argv.slice(2);
  if (command === 'preflight') {
    for (const [name, client, url] of [
      ['settings', results, `/projects/${project}/settings`],
      ['histories', results, `/projects/${project}/histories?pageSize=100`],
      ['catalog', testing, `/testEnvironmentCatalog/ANDROID?projectId=${project}`],
    ]) {
      const r = await client.get(url, { headers });
      save(`${name}.json`, r.body);
      console.log(name, name === 'catalog' ? 'saved' : JSON.stringify(r.body));
    }
    return;
  }
  if (command === 'submit') {
    // Prevent simultaneous invocations from both reading the same remaining budget.
    // A stale lock requires inspecting the saved ledger before manual removal.
    const lockPath = path.join(output, 'submission.lock');
    const lock = fs.openSync(lockPath, 'wx');
    try {
    // Explicit allowlist: exactly ONE physical device, one OS, one locale, no shards.
    const deviceVersions = { 'TECNO-BG6': '33', 'Infinix-X6525': '33', 'TECNO-BF6': '31' };
    const version = deviceVersions[id];
    if (!Object.hasOwn(deviceVersions, id)) throw new Error('Choose TECNO-BG6, Infinix-X6525, or TECNO-BF6.');
    const day = new Date().toISOString().slice(0, 10);
    const ledgerFile = path.join(output, `${day}-submissions.json`);
    const ledger = fs.existsSync(ledgerFile) ? JSON.parse(fs.readFileSync(ledgerFile)) : [];
    const verification = mode === '--verify-fix';
    if (mode && !verification) throw new Error('Unknown submit option.');
    // Count cancelled reservations too; keep the fifth physical slot for verification.
    if (ledger.length >= (verification ? 5 : 4)) throw new Error('Daily submission budget reached; preserve the remaining physical quota.');
    const prior = ledger.findLast(r => r.model === id);
    if (!verification && prior) throw new Error('Already submitted/reserved this device today; inspect the ledger, do not blindly retry.');
    if (verification) {
      if (!prior?.matrixId) throw new Error('Verification requires a prior matrix on this model.');
      const previousResult = (await testing.get(`/projects/${project}/testMatrices/${prior.matrixId}`, { headers })).body;
      if (previousResult.state !== 'FINISHED' || previousResult.outcomeSummary !== 'FAILURE') {
        throw new Error('Verification requires a finished failure, not a pending or successful run.');
      }
    }
    const catalog = (await testing.get(`/testEnvironmentCatalog/ANDROID?projectId=${project}`, { headers })).body;
    const model = catalog.androidDeviceCatalog.models.find(m => m.id === id && m.form === 'PHYSICAL');
    if (!model?.supportedVersionIds?.includes(version)) {
      throw new Error(`Requested physical model/API ${version} is no longer available.`);
    }
    const capacity = model.perVersionInfo?.find(v => v.versionId === version)?.deviceCapacity;
    if (!['DEVICE_CAPACITY_LOW', 'DEVICE_CAPACITY_MEDIUM', 'DEVICE_CAPACITY_HIGH'].includes(capacity)) {
      throw new Error(`Do not spend a run on unavailable/unknown device capacity: ${capacity}`);
    }
    const settings = (await results.get(`/projects/${project}/settings`, { headers })).body;
    const bucket = settings.defaultBucket;
    if (!bucket) throw new Error('Test Lab default result bucket is not configured.');
    const runId = `${day}-${id}-${crypto.randomUUID()}`;
    const prefix = `comfer-anr/${runId}`;
    const apks = [
      ['app', 'app/build/outputs/apk/notificationTest/app-notificationTest.apk'],
      ['test', 'app/build/outputs/apk/androidTest/notificationTest/app-notificationTest-androidTest.apk'],
    ];
    const files = {};
    const hashes = {};
    for (const [kind, local] of apks) {
      const body = fs.readFileSync(local);
      hashes[kind] = crypto.createHash('sha256').update(body).digest('hex');
      const previous = ledger.find(r => r.hashes?.[kind] === hashes[kind] && r.matrixId);
      if (previous) {
        const previousRequest = JSON.parse(fs.readFileSync(path.join(output, `${previous.runId}-request.json`)));
        files[kind] = previousRequest.testSpecification.androidInstrumentationTest[
          kind === 'app' ? 'appApk' : 'testApk'
        ].gcsPath;
        console.log(`Reusing identical ${kind} APK, SHA-256 ${hashes[kind]}`);
        continue;
      }
      const name = `${prefix}/${kind}.apk`;
      const upload = new Client({ urlPrefix: 'https://storage.googleapis.com', apiVersion: 'upload/storage/v1' });
      // Test Lab owns this bucket. A user-project billing header would incorrectly
      // charge storage to our Spark project and fail with billing state "absent".
      await upload.post(`/b/${bucket}/o?uploadType=media&name=${encodeURIComponent(name)}&ifGenerationMatch=0`, body,
        { headers: { 'Content-Type': 'application/vnd.android.package-archive' } });
      files[kind] = `gs://${bucket}/${name}`;
      console.log(`Uploaded ${kind} APK, SHA-256 ${hashes[kind]}`);
    }
    if (verification && Object.keys(hashes).every(kind => hashes[kind] === prior.hashes[kind])) {
      throw new Error('APKs unchanged; this would be a duplicate run, not fix verification.');
    }
    const requestId = crypto.randomUUID();
    const request = {
      clientInfo: { name: 'Comfer bundled ANR diagnostics' },
      testSpecification: {
        testTimeout: '1200s',
        disableVideoRecording: false,
        disablePerformanceMetrics: false,
        androidInstrumentationTest: {
          appApk: { gcsPath: files.app },
          testApk: { gcsPath: files.test },
          appPackageId: 'com.jeerovan.comfer.notificationtest',
          testPackageId: 'com.jeerovan.comfer.notificationtest.test',
          testRunnerClass: 'androidx.test.runner.AndroidJUnitRunner',
          testTargets: ['class com.jeerovan.comfer.AnrTestLabSuite'],
          orchestratorOption: 'USE_ORCHESTRATOR',
        },
      },
      environmentMatrix: { androidDeviceList: { androidDevices: [
        { androidModelId: id, androidVersionId: version, locale: 'en', orientation: 'portrait' },
      ] } },
      resultStorage: { googleCloudStorage: { gcsPath: `gs://${bucket}/${prefix}/results` } },
      flakyTestAttempts: 0,
      failFast: true,
    };
    save(`${runId}-request.json`, request);
    // Reserve before the API write. An uncertain response must never trigger a duplicate run.
    const entry = { runId, model: id, version, requestId, hashes, verificationOf: verification ? prior.matrixId : undefined,
      resultPrefix: `${prefix}/results`, bucket, state: 'SUBMITTING' };
    ledger.push(entry);
    fs.writeFileSync(ledgerFile, JSON.stringify(ledger, null, 2));
    const response = (await testing.post(`/projects/${project}/testMatrices?requestId=${requestId}`, request, { headers })).body;
    entry.matrixId = response.testMatrixId;
    entry.state = response.state;
    fs.writeFileSync(ledgerFile, JSON.stringify(ledger, null, 2));
    save(`${response.testMatrixId}.json`, response);
    console.log(JSON.stringify(response, null, 2));
    return;
    } finally {
      fs.closeSync(lock);
      fs.unlinkSync(lockPath);
    }
  }
  if (command === 'status') {
    if (!/^matrix-[a-zA-Z0-9_-]+$/.test(id || '')) throw new Error('Provide a matrix ID.');
    const response = (await testing.get(`/projects/${project}/testMatrices/${id}`, { headers })).body;
    save(`${id}.json`, response);
    console.log(JSON.stringify({
      matrixId: id, state: response.state, outcome: response.outcomeSummary,
      resultsUrl: response.resultStorage?.resultsUrl,
      executions: response.testExecutions?.map(e => ({ state: e.state, details: e.testDetails })),
    }, null, 2));
    return;
  }
  if (command === 'download') {
    if (!/^matrix-[a-zA-Z0-9_-]+$/.test(id || '')) throw new Error('Provide a matrix ID.');
    const matrix = JSON.parse(fs.readFileSync(path.join(output, `${id}.json`)));
    const match = /^gs:\/\/([^/]+)\/(.+)$/.exec(matrix.resultStorage.googleCloudStorage.gcsPath);
    const [, bucket, prefix] = match;
    let pageToken;
    let downloaded = 0;
    do {
      const params = new URLSearchParams({ prefix, ...(pageToken ? { pageToken } : {}) });
      const listing = (await storage.get(`/b/${bucket}/o?${params}`)).body;
      for (const item of listing.items || []) {
        // Keep videos remote; retain diagnostics and machine-readable test outcomes locally.
        if (!/\.(xml|txt|json|log|html)$/.test(item.name) && !/\/(logcat|instrumentation\.results)$/.test(item.name)) continue;
        const relative = item.name.slice(prefix.length).replace(/^\//, '');
        const local = path.resolve(output, id, relative);
        if (!local.startsWith(path.resolve(output, id) + path.sep)) throw new Error('Unexpected object path');
        fs.mkdirSync(path.dirname(local), { recursive: true });
        const r = await storage.get(`/b/${bucket}/o/${encodeURIComponent(item.name)}?alt=media`,
          { responseType: 'stream', resolveOnHTTPError: true });
        if (r.status >= 400) throw new Error(`Download failed: HTTP ${r.status}`);
        await require('node:stream/promises').pipeline(r.body, fs.createWriteStream(local));
        downloaded++;
      }
      pageToken = listing.nextPageToken;
    } while (pageToken);
    console.log(`Downloaded ${downloaded} diagnostic files to ${output}/${id}`);
    return;
  }
  throw new Error('Usage: testlab_anr.cjs preflight | submit MODEL [--verify-fix] | status MATRIX | download MATRIX');
}

main().catch(e => { console.error(e.original?.message || e.message); process.exitCode = 1; });
