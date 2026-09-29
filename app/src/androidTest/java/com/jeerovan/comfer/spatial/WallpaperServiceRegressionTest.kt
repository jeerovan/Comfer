package com.jeerovan.comfer.spatial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WallpaperServiceRegressionTest {
    @get:Rule val compose = createComposeRule()

    @Test fun disabledTiltNeverAcquiresSensorService() {
        val queries = AtomicInteger()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                if (name == Context.SENSOR_SERVICE) queries.incrementAndGet()
                return super.getSystemService(name)
            }
        }
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                rememberSpatialTilt(enabled = false, scene = "disabled")
            }
        }
        compose.runOnIdle { assertEquals("Disabled motion must not initialize sensors", 0, queries.get()) }
    }

    @Test fun wallpaperReceiverSetupAndCleanupNeverRunOnMain() {
        val registrationThreads = CopyOnWriteArrayList<Boolean>()
        val cleanupThreads = CopyOnWriteArrayList<Boolean>()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
                registrationThreads.add(Looper.myLooper() == Looper.getMainLooper())
                return null
            }
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?): Intent? =
                registerReceiver(receiver, filter)
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?, flags: Int): Intent? =
                registerReceiver(receiver, filter)
            override fun unregisterReceiver(receiver: BroadcastReceiver?) {
                cleanupThreads.add(Looper.myLooper() == Looper.getMainLooper())
            }
        }
        var visible by mutableStateOf(true)
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                if (visible) rememberWallpaperActive()
            }
        }
        compose.waitUntil(5_000) { registrationThreads.isNotEmpty() }
        compose.runOnIdle { visible = false }
        compose.waitUntil(5_000) { cleanupThreads.isNotEmpty() }
        assertEquals(listOf(false), registrationThreads.toList())
        assertEquals(listOf(false), cleanupThreads.toList())
    }

    @Test fun activeTiltLookupCannotBlockMainAndDisabledTiltDoesNotRetryIt() {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val queries = CopyOnWriteArrayList<Boolean>()
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val context = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSystemService(name: String): Any? {
                if (name != Context.SENSOR_SERVICE) return super.getSystemService(name)
                queries.add(Looper.myLooper() == Looper.getMainLooper())
                entered.countDown()
                try { check(release.await(10, TimeUnit.SECONDS)) }
                finally { finished.countDown() }
                return null // A device without a rotation sensor remains usable.
            }
        }
        var enabled by mutableStateOf(true)
        var visible by mutableStateOf(true)
        try {
            compose.setContent {
                val owner = remember {
                    object : LifecycleOwner {
                        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
                    }
                }
                val view = remember { object : android.view.View(context) {
                    override fun hasWindowFocus() = true
                } }
                CompositionLocalProvider(LocalContext provides context, LocalView provides view,
                    LocalLifecycleOwner provides owner) {
                    if (visible) rememberSpatialTilt(enabled, "slow-sensor")
                }
            }
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            // runOnIdle would hang if lookup still occupied Main.
            compose.runOnIdle { enabled = false }
            compose.runOnIdle { visible = false }
            assertEquals(listOf(false), queries.toList())
        } finally {
            release.countDown()
            assertTrue(finished.await(5, TimeUnit.SECONDS))
        }
        compose.runOnIdle { visible = true } // Remount with motion disabled.
        compose.runOnIdle { assertEquals(1, queries.size) }
    }

    @Test fun cancellationDuringReceiverSetupReleasesTheLateRegistration() = runBlocking {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val cleanupThreads = CopyOnWriteArrayList<Boolean>()
        val context = receiverContext(
            register = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) },
            unregister = {
                cleanupThreads.add(Looper.myLooper() == Looper.getMainLooper())
                cleaned.countDown()
            },
        )
        val job = launch(Dispatchers.Default) { wallpaperEnvironment(context).collect() }
        try {
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            job.cancel()
            val responsive = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { responsive.countDown() }
            assertTrue(responsive.await(2, TimeUnit.SECONDS))
            assertEquals(1L, cleaned.count) // Cleanup cannot overtake registration.
        } finally { release.countDown(); withTimeout(5_000) { job.join() } }
        assertTrue(cleaned.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(false), cleanupThreads.toList())
    }

    @Test fun receiverFailureDisablesMotionAndNextSubscriptionCanRecover() = runBlocking {
        val registrations = AtomicInteger()
        val cleanups = AtomicInteger()
        val context = receiverContext(register = {
            if (registrations.incrementAndGet() == 1) throw SecurityException("Injected service rejection")
        }, unregister = { cleanups.incrementAndGet() })
        assertEquals(WallpaperEnvironment(false, false), withTimeout(5_000) { wallpaperEnvironment(context).first() })
        assertEquals(0, cleanups.get()) // Registration never succeeded.
        withTimeout(5_000) { wallpaperEnvironment(context).first() }
        assertEquals(2, registrations.get())
        assertEquals(1, cleanups.get())
    }

    @Test fun motionGateRefreshesAfterPauseAndReducedMotionChangesInBothDirections() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val resolver = instrumentation.targetContext.contentResolver
        val key = Settings.Global.ANIMATOR_DURATION_SCALE
        val original = Settings.Global.getString(resolver, key)
        var locale by mutableStateOf("en")
        var active = false
        var direction: LayoutDirection? = null
        lateinit var lifecycle: LifecycleRegistry
        instrumentation.uiAutomation.adoptShellPermissionIdentity(android.Manifest.permission.WRITE_SECURE_SETTINGS)
        try {
            assertTrue(Settings.Global.putFloat(resolver, key, 1f))
            compose.setContent {
                val owner = remember {
                    object : LifecycleOwner {
                        override val lifecycle = LifecycleRegistry(this).apply { currentState = Lifecycle.State.RESUMED }
                    }.also { lifecycle = it.lifecycle }
                }
                SpatialTestLocale(locale) {
                    CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                        val current = rememberWallpaperActive()
                        val currentDirection = LocalLayoutDirection.current
                        SideEffect { active = current; direction = currentDirection }
                    }
                }
            }
            for (tag in listOf("en", "ar")) {
                compose.runOnIdle { locale = tag }
                compose.waitUntil(5_000) { active }
                compose.runOnIdle {
                    assertEquals(if (tag == "ar") LayoutDirection.Rtl else LayoutDirection.Ltr, direction)
                    lifecycle.currentState = Lifecycle.State.STARTED
                }
                compose.waitUntil(5_000) { !active }
                assertTrue(Settings.Global.putFloat(resolver, key, 0f))
                compose.runOnIdle { lifecycle.currentState = Lifecycle.State.RESUMED }
                compose.runOnIdle { assertFalse(active) }
                assertTrue(Settings.Global.putFloat(resolver, key, 1f))
                compose.waitUntil(5_000) { active }
                assertTrue(Settings.Global.putFloat(resolver, key, 0f))
                compose.waitUntil(5_000) { !active }
                assertTrue(Settings.Global.putFloat(resolver, key, 1f))
                compose.waitUntil(5_000) { active }
            }
        } finally {
            Settings.Global.putString(resolver, key, original)
            instrumentation.uiAutomation.dropShellPermissionIdentity()
        }
    }

    private fun receiverContext(register: () -> Unit, unregister: () -> Unit): Context =
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
                register()
                return null
            }
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?): Intent? =
                registerReceiver(receiver, filter)
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?, permission: String?, scheduler: Handler?, flags: Int): Intent? =
                registerReceiver(receiver, filter)
            override fun unregisterReceiver(receiver: BroadcastReceiver?) { unregister() }
        }
}
