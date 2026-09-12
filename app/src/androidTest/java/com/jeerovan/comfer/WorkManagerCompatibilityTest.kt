package com.jeerovan.comfer

import android.app.job.JobScheduler
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
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
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun awaitFocus() = await("MainActivity must acquire window focus") {
                var focused = false
                scenario.onActivity { focused = it.hasWindowFocus() && !it.isFinishing }
                focused
            }
            awaitFocus()
            scenario.recreate()
            awaitFocus()
            assertScheduling()
        }
    }
}
