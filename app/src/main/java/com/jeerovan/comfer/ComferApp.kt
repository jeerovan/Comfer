package com.jeerovan.comfer

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.StrictMode
import android.util.Log
import androidx.work.*
import coil.ImageLoaderFactory
import java.util.concurrent.TimeUnit
import coil.ImageLoader
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

const val saveCrashes = false
const val saveLogs = false
class ComferApp : Application(), ImageLoaderFactory {

    // App-wide background scope for one-time startup work (prefs migration + the
    // DataStore->snapshot load). Running this on the main thread was the #1
    // cold-start ANR source: the first-ever Room build + full migration could
    // exceed the input-dispatch timeout.
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initializationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        enableStrictMode()
        // One-time import of legacy SharedPreferences into Room + DataStore, then
        // load the scalar settings snapshot. Both are suspend and run off the main
        // thread; the in-memory snapshot fills in and the UI updates when done.
        initializeApplicationData()
        if(saveCrashes) {
            Thread.setDefaultUncaughtExceptionHandler(CrashHandler(this))
            LogcatRecorder(this).startLogging()
        }
        setupImageWorker()
    }

    /**
     * Idempotent retry entry point for startup UI/recovery handling.
     * Concurrent calls share the currently active attempt.
     */
    fun initializeApplicationData() {
        if (StartupCoordinator.isReady || initializationJob?.isActive == true) return
        StartupCoordinator.markInitializing()
        initializationJob = appScope.launch {
            PerformanceTrace.beginAsync("startupInitialization", 1)
            try {
                PrefMigrator.runOnce(applicationContext)
                BackupRestoreManager.recoverInterruptedRestore(applicationContext)
                PreferenceManager.reload(applicationContext)
                StartupCoordinator.markReady()
                Log.i("ComferApp", "Application data initialization complete")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                StartupCoordinator.markFailed(e)
                Log.e("ComferApp", "Application data initialization failed", e)
            } finally {
                PerformanceTrace.endAsync("startupInitialization", 1)
            }
        }
    }

    /** Debug-only logging for main-thread I/O and leaked resources. AppCompat may
     *  perform locale-storage reads while attaching an Activity, before app code
     *  can scope an allowance, so killing on every violation makes debug builds
     *  unusable on affected Android versions. */
    private fun enableStrictMode() {
        if ((applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedSqlLiteObjects()
                    .detectLeakedClosableObjects()
                    .penaltyLog()
                    .build()
            )
        }
    }

    private fun setupImageWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val periodicWorkRequest = PeriodicWorkRequestBuilder<ImageWorker>(20, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        val workManager = try {
            WorkManager.getInstance(applicationContext)
        } catch (uninitialized: IllegalStateException) {
            // Some Play-protected/OEM installs omit WorkManagerInitializer even
            // though the normal merged manifest includes it.
            Log.w("ComferApp", "Initializing WorkManager explicitly", uninitialized)
            WorkManager.initialize(
                applicationContext,
                Configuration.Builder().build(),
            )
            WorkManager.getInstance(applicationContext)
        }

        workManager.enqueueUniquePeriodicWork(
            "ImageWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            periodicWorkRequest
        )
    }

    // App-scoped singleton: the 3 AppWidgetHosts + AppWidgetManager are created
    // ONCE per process and reused across Activity recreations, avoiding repeated
    // Binder registration and host leaks.
    val widgetHostManager: WidgetHostManager by lazy {
        WidgetHostManager(applicationContext).apply { initHosts() }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .crossfade(true) // Optional: Add a crossfade animation
            // Add any other global configurations for Coil here
            .build()
    }
}
