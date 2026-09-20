package com.jeerovan.comfer

import android.app.Instrumentation
import android.appwidget.AppWidgetHost
import android.content.Context
import android.content.Intent
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.Collections
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CancellationException
import android.graphics.drawable.ColorDrawable
import android.graphics.Color

@RunWith(AndroidJUnit4::class)
class Release51RegressionTest {
    @Test
    fun widgetImagesRecoverAfterAllocationFailureAndRemainBounded() = runBlocking {
        assertNull(loadWidgetImage(128) { throw OutOfMemoryError("injected decode failure") })
        val image = loadWidgetImage(128) {
            object : ColorDrawable(Color.RED) {
                override fun getIntrinsicWidth() = 2048
                override fun getIntrinsicHeight() = 1024
            }
        }
        assertNotNull(image)
        assertEquals(128, image!!.width)
        assertEquals(64, image.height)
        try {
            loadWidgetImage(128) { throw CancellationException("cancelled") }
            fail("Cancellation must propagate")
        } catch (_: CancellationException) { }
    }

    @Test
    fun honorActivityCanBeLoadedWithoutOurComponentFactory() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        // The failing firmware uses the platform loader, not ComferAppComponentFactory.
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val activity = Instrumentation().newActivity(
                context.classLoader,
                "com.hihonor.android.launcher.powersavemode.PowerSaveModeLauncher",
                Intent(),
            )
            assertTrue(activity is HonorPowerSaveCompatibilityActivity)
        }
    }

    @Test
    fun honorWeatherIsBlockedWithoutBlockingSimilarlyNamedPackages() {
        assertTrue(WidgetInflationGuard.isKnownUnsafe(
            "com.hihonor.android.totemweather/.widget.WeatherWidgetProvider"))
        assertFalse(WidgetInflationGuard.isKnownUnsafe(
            "com.hihonor.android.totemweather.safe/.WeatherWidgetProvider"))
    }

    @Test
    fun pendingWidgetUpdatesAndLifecycleCallsAreSerializedOnMain() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val calls = Collections.synchronizedList(mutableListOf<String>())
        val latch = CountDownLatch(6)
        val manager = WidgetHostManager(context)
        fun host(id: Int) = object : AppWidgetHost(context, id) {
            override fun startListening() {
                calls.add("start:$id:${Looper.myLooper() == Looper.getMainLooper()}")
                latch.countDown()
            }
            override fun stopListening() {
                calls.add("stop:$id:${Looper.myLooper() == Looper.getMainLooper()}")
                latch.countDown()
            }
        }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                manager.mainHost = host(1)
                manager.leftHost = host(2)
                manager.rightHost = host(3)
                manager.startListening()
                manager.stopListening()
            }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            assertEquals(listOf("start:1:true", "start:2:true", "start:3:true",
                "stop:1:true", "stop:2:true", "stop:3:true"), calls.toList())
        } finally {
            manager.cleanup()
        }
    }

    @Test
    fun failingWidgetHostDoesNotSkipOtherHostsAndCanRecover() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = WidgetHostManager(context)
        val calls = mutableListOf<Int>()
        var failFirst = true
        fun host(id: Int) = object : AppWidgetHost(context, id) {
            override fun startListening() {
                calls.add(id)
                if (id == 1 && failFirst) throw IllegalStateException("injected service failure")
            }
        }
        try {
            InstrumentationRegistry.getInstrumentation().runOnMainSync {
                manager.mainHost = host(1)
                manager.leftHost = host(2)
                manager.rightHost = host(3)
                manager.startListening()
                failFirst = false
                manager.startListening()
                assertEquals(listOf(1, 2, 3, 1, 2, 3), calls)
            }
        } finally { manager.cleanup() }
    }
}
