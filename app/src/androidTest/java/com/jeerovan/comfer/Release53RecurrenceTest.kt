package com.jeerovan.comfer

import android.Manifest
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Vibrator
import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.work.WorkManager
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.Collections
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Release53RecurrenceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext

    @Test fun drawerSoundNeverBlocksMainAndDropsStaleQueuedFeedback() {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val recovered = CountDownLatch(1)
        val staleChecked = CountDownLatch(1)
        val now = java.util.concurrent.atomic.AtomicLong(0)
        val plays = java.util.concurrent.atomic.AtomicInteger(0)
        val wasMain = java.util.concurrent.atomic.AtomicBoolean(true)
        val sound = DrawerClickSound(scope, clock = {
            now.get().also {
                if (it == 200L && Looper.myLooper() != Looper.getMainLooper()) staleChecked.countDown()
            }
        }) {
            wasMain.set(Looper.myLooper() == Looper.getMainLooper())
            if (plays.incrementAndGet() == 1) {
                entered.countDown()
                check(release.await(10, TimeUnit.SECONDS))
                finished.countDown()
                throw IllegalStateException("injected audio failure")
            }
            recovered.countDown()
        }
        try {
            instrumentation.runOnMainSync { sound.request() }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            val responsive = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post {
                repeat(1_000) { sound.request() }
                responsive.countDown()
            }
            assertTrue(responsive.await(2, TimeUnit.SECONDS))
            assertFalse(wasMain.get())
            now.set(200) // All queued requests are stale when the service recovers.
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
            assertTrue("The queued stale sound must be consumed", staleChecked.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { sound.request() }
            assertTrue(recovered.await(5, TimeUnit.SECONDS))
            assertEquals("No backlog of 1,000 sounds after a stall", 2, plays.get())
        } finally {
            release.countDown()
            sound.close()
            scope.cancel()
        }
    }

    @Test fun failedWidgetStopIsRetriedWithoutRepeatingSuccessfulHosts() {
        val manager = WidgetHostManager(context)
        val started = CountDownLatch(3)
        val firstStops = CountDownLatch(3)
        val recovered = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<Int>())
        fun host(id: Int) = object : AppWidgetHost(context, id) {
            var stops = 0
            override fun startListening() { started.countDown() }
            override fun stopListening() {
                calls.add(id)
                if (++stops == 1) {
                    firstStops.countDown()
                    if (id == 1) throw IllegalStateException("injected stop failure")
                } else recovered.countDown()
            }
        }
        try {
            instrumentation.runOnMainSync {
                manager.mainHost = host(1); manager.leftHost = host(2); manager.rightHost = host(3)
                manager.startListening()
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { manager.stopListening() }
            assertTrue(firstStops.await(5, TimeUnit.SECONDS))
            instrumentation.runOnMainSync { manager.stopListening() }
            assertTrue(recovered.await(5, TimeUnit.SECONDS))
            assertEquals(listOf(1, 2, 3, 1), calls.toList())
        } finally { instrumentation.runOnMainSync { manager.cleanup() } }
    }

    @Test fun wallpaperSetupLeavesMainResponsiveAndRecoversAfterFailure() {
        val app = context.applicationContext as ComferApp
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val responsive = CountDownLatch(1)
        val recovered = CountDownLatch(1)
        val wasMain = java.util.concurrent.atomic.AtomicBoolean(true)
        try {
            instrumentation.runOnMainSync {
                app.scheduleImageWorker {
                    wasMain.set(Looper.myLooper() == Looper.getMainLooper())
                    entered.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                    throw IllegalStateException("injected optional scheduler failure")
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            Handler(Looper.getMainLooper()).post { responsive.countDown() }
            assertTrue(responsive.await(2, TimeUnit.SECONDS))
            assertFalse(wasMain.get())
        } finally { release.countDown() }
        runBlocking { app.scheduleImageWorker { recovered.countDown() }.join() }
        assertTrue(recovered.await(5, TimeUnit.SECONDS))
    }

    @Test fun realBackgroundSetupKeepsOneUniquePeriodicRequest() = runBlocking {
        val app = context.applicationContext as ComferApp
        app.scheduleImageWorker().join()
        app.scheduleImageWorker().join()
        val work = WorkManager.getInstance(context).getWorkInfosForUniqueWork("ImageWorker")
            .get(15, TimeUnit.SECONDS)
        assertEquals(1, work.size)
        assertFalse(work.single().state.isFinished)
    }

    @Test fun launcherServiceLookupIsDeferredUntilBackgroundInventoryLoad() {
        val lookups = Collections.synchronizedList(mutableListOf<Boolean>())
        val acquired = CountDownLatch(2)
        val application = object : Application() {
            init { attachBaseContext(context.applicationContext) }
            override fun getSystemService(name: String): Any? {
                if (name == Context.LAUNCHER_APPS_SERVICE || name == Context.USER_SERVICE) {
                    lookups.add(Looper.myLooper() == Looper.getMainLooper())
                    acquired.countDown()
                }
                return super.getSystemService(name)
            }
        }
        val store = ViewModelStore()
        try {
            instrumentation.runOnMainSync {
                val model = AppInfoViewModel(application)
                store.put("inventory", model)
                assertFalse("Constructor must not acquire services on Main", lookups.contains(true))
            }
            assertTrue("Inventory must still acquire both services", acquired.await(20, TimeUnit.SECONDS))
            assertFalse(lookups.contains(true))
        } finally { instrumentation.runOnMainSync { store.clear() } }
    }

    @Suppress("DEPRECATION")
    @Test fun vibrationPermissionAllowsDirectProviderFeedback() {
        assertEquals(PackageManager.PERMISSION_GRANTED,
            context.checkSelfPermission(Manifest.permission.VIBRATE))
        (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator).vibrate(1)
    }

    @Test fun blockedWidgetStopDoesNotBlockMainOrAllowRestartToOvertakeIt() {
        val manager = WidgetHostManager(context)
        val started = CountDownLatch(3)
        val stopping = CountDownLatch(1)
        val release = CountDownLatch(1)
        val restarted = CountDownLatch(3)
        val mainResponsive = CountDownLatch(1)
        val calls = Collections.synchronizedList(mutableListOf<String>())
        fun host(id: Int) = object : AppWidgetHost(context, id) {
            var starts = 0
            override fun startListening() {
                calls.add("start:$id:${Looper.myLooper() == Looper.getMainLooper()}")
                if (++starts == 1) started.countDown() else restarted.countDown()
            }
            override fun stopListening() {
                calls.add("stop:$id:${Looper.myLooper() == Looper.getMainLooper()}")
                if (id == 1) {
                    stopping.countDown()
                    check(release.await(10, TimeUnit.SECONDS))
                }
            }
        }
        try {
            instrumentation.runOnMainSync {
                manager.mainHost = host(1)
                manager.leftHost = host(2)
                manager.rightHost = host(3)
                manager.startListening()
            }
            assertTrue(started.await(5, TimeUnit.SECONDS))
            Handler(Looper.getMainLooper()).post { manager.stopListening() }
            assertTrue(stopping.await(5, TimeUnit.SECONDS))
            Handler(Looper.getMainLooper()).post {
                manager.startListening()
                mainResponsive.countDown()
            }
            assertTrue("A stalled stop Binder call must not freeze Main",
                mainResponsive.await(2, TimeUnit.SECONDS))
            assertEquals("Restart must wait for stop to finish", 3L, restarted.count)
            release.countDown()
            assertTrue(restarted.await(5, TimeUnit.SECONDS))
            assertEquals(listOf("start:1:true", "start:2:true", "start:3:true",
                "stop:1:false", "stop:2:false", "stop:3:false",
                "start:1:true", "start:2:true", "start:3:true"), calls.toList())
        } finally {
            release.countDown()
            instrumentation.runOnMainSync { manager.cleanup() }
        }
    }
}
