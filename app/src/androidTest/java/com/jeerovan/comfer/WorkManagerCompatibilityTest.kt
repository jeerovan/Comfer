package com.jeerovan.comfer

import android.app.job.JobScheduler
import android.os.Build
import android.os.SystemClock
import android.content.Intent
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Run through scripts/test_workmanager_compatibility.py in a fresh isolated package. */
class WorkManagerCompatibilityTest {
    @Test fun startupAndActivitySurviveWithExpectedScheduling() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val mode = InstrumentationRegistry.getArguments().getString("namespaceMode")
        assumeTrue("Requires the isolated compatibility harness", mode in setOf("healthy", "missing"))
        assertTrue(context.packageName.endsWith(".notificationtest"))
        assertTrue(Build.VERSION.SDK_INT >= 34)
        val missing = mode == "missing"

        fun await(message: String, condition: () -> Boolean) {
            val deadline = SystemClock.elapsedRealtime() + 30_000
            while (!condition() && SystemClock.elapsedRealtime() < deadline) Thread.sleep(100)
            assertTrue(message, condition())
        }

        fun assertScheduling() {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            if (missing) {
                assertTrue("WorkManager must remain uninitialized after the compatibility return",
                    runCatching { WorkManager.getInstance(context) }.exceptionOrNull() is IllegalStateException)
                assertTrue("Unsupported startup must not enqueue any jobs",
                    scheduler.pendingJobsInAllNamespaces.values.flatten().isEmpty())
            } else {
                await("Background WorkManager initialization must finish") {
                    runCatching { WorkManager.getInstance(context) }.isSuccess
                }
                await("Background setup must enqueue its unique wallpaper work") {
                    WorkManager.getInstance(context).getWorkInfosForUniqueWork("ImageWorker")
                        .get(5, TimeUnit.SECONDS).isNotEmpty()
                }
                val work = WorkManager.getInstance(context)
                    .getWorkInfosForUniqueWork("ImageWorker").get(30, TimeUnit.SECONDS)
                assertEquals("Normal startup must retain one periodic wallpaper request", 1, work.size)
                assertFalse(work.single().state.isFinished)
                await("Normal startup must schedule its wallpaper job") {
                    scheduler.pendingJobsInAllNamespaces.values.flatten().any {
                        it.service.className == "androidx.work.impl.background.systemjob.SystemJobService"
                    }
                }
            }
        }

        // Application.onCreate has already executed before instrumentation reaches this method.
        await("Application data initialization must complete") { StartupCoordinator.isReady }
        assertScheduling()
        // Launcher guidance can animate continuously. ActivityScenario's global
        // idle wait can hang even while the window is responsive; observe the
        // actual resumed window with a bounded wait, as ModuleLocaleTest does.
        fun focusedActivity(): MainActivity? {
            var result: MainActivity? = null
            instrumentation.runOnMainSync {
                result = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                    .filterIsInstance<MainActivity>().firstOrNull {
                        it.hasWindowFocus() && !it.isFinishing && it.window.decorView.width > 0
                    }
            }
            return result
        }
        try {
            instrumentation.runOnMainSync {
                context.startActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            await("MainActivity must acquire window focus") { focusedActivity() != null }
            val original = requireNotNull(focusedActivity())
            instrumentation.runOnMainSync { original.recreate() }
            await("The replacement MainActivity must acquire window focus") {
                focusedActivity()?.let { it !== original } == true
            }
            assertScheduling()
        } finally {
            instrumentation.runOnMainSync {
                listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
                    .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                    .filterIsInstance<MainActivity>().distinct().forEach { it.finish() }
            }
        }
    }
}
