package com.jeerovan.comfer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.os.Looper
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicInteger

class ClockReceiverLifetimeTest {
    @get:Rule val compose = createComposeRule()

    private fun context(register: () -> Unit, unregister: () -> Unit): Context =
        object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun registerReceiver(receiver: BroadcastReceiver?, filter: IntentFilter?): Intent? {
                register()
                return null
            }
            override fun unregisterReceiver(receiver: BroadcastReceiver?) { unregister() }
        }

    private fun mount(context: Context, visible: State<Boolean>, analog: Boolean) {
        compose.setContent {
            CompositionLocalProvider(LocalContext provides context) {
                if (visible.value) {
                    if (analog) AnalogClock(100.dp, Color.Black, Color.White, Color.White)
                    else TextClock(SettingsUiState(), Color.White, false)
                }
            }
        }
    }

    @Test fun textClockRegistersAndUnregistersOffMain() = checkThreads(false)
    @Test fun analogClockRegistersAndUnregistersOffMain() = checkThreads(true)

    private fun checkThreads(analog: Boolean) {
        val registered = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val threads = CopyOnWriteArrayList<Boolean>()
        val context = context(
            register = { threads += Looper.myLooper() == Looper.getMainLooper(); registered.countDown() },
            unregister = { threads += Looper.myLooper() == Looper.getMainLooper(); cleaned.countDown() },
        )
        val visible = mutableStateOf(true)
        mount(context, visible, analog)
        assertTrue(registered.await(5, TimeUnit.SECONDS))
        compose.runOnIdle { visible.value = false }
        compose.waitForIdle()
        assertTrue(cleaned.await(5, TimeUnit.SECONDS))
        assertEquals(listOf(false, false), threads.toList())
    }

    @Test fun textClockDisposalDuringRegistrationCannotLeakALateReceiver() = checkLateRegistration(false)
    @Test fun analogClockDisposalDuringRegistrationCannotLeakALateReceiver() = checkLateRegistration(true)

    private fun checkLateRegistration(analog: Boolean) {
        val entered = CountDownLatch(1)
        val release = CountDownLatch(1)
        val cleaned = CountDownLatch(1)
        val context = context(
            register = { entered.countDown(); check(release.await(10, TimeUnit.SECONDS)) },
            unregister = { cleaned.countDown() },
        )
        val visible = mutableStateOf(true)
        try {
            mount(context, visible, analog)
            assertTrue(entered.await(5, TimeUnit.SECONDS))
            compose.runOnIdle { visible.value = false } // Main must stay responsive.
            compose.waitForIdle()
            assertEquals("Cleanup must wait for this exact registration to return", 1L, cleaned.count)
        } finally { release.countDown() }
        assertTrue(cleaned.await(5, TimeUnit.SECONDS))
    }

    @Test fun systemTimeBroadcastsPublishFreshValuesAndKeepAllClockActions() = runBlocking {
        val now = AtomicLong(1000L)
        val values = CopyOnWriteArrayList<Long>()
        lateinit var receiver: BroadcastReceiver
        lateinit var filter: IntentFilter
        val cleaned = AtomicInteger()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun registerReceiver(r: BroadcastReceiver?, f: IntentFilter?): Intent? {
                receiver = requireNotNull(r)
                filter = requireNotNull(f)
                return null
            }
            override fun unregisterReceiver(r: BroadcastReceiver?) {
                assertSame(receiver, r)
                assertNotEquals(Looper.getMainLooper(), Looper.myLooper())
                cleaned.incrementAndGet()
            }
        }
        val job = launch(Dispatchers.Default) { clockTimeUpdates(context, now::get).collect { values += it } }
        try {
            withTimeout(5_000) { while (values.isEmpty()) delay(10) }
            assertEquals(listOf(1000L), values.toList())
            for ((index, action) in listOf(Intent.ACTION_TIME_TICK, Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED).withIndex()) {
                assertTrue(filter.hasAction(action))
                val expected = (index + 2L) * 1000L
                InstrumentationRegistry.getInstrumentation().runOnMainSync {
                    now.set(expected)
                    receiver.onReceive(context, Intent(action))
                }
                withTimeout(5_000) { while (values.last() != expected) delay(10) }
            }
            assertEquals(listOf(1000L, 2000L, 3000L, 4000L), values.toList())
        } finally { job.cancelAndJoin() }
        assertEquals(1, cleaned.get())
    }

    @Test fun failedRegistrationHasNoCleanupObligationAndNextSubscriptionRecovers() = runBlocking {
        var failing = true
        val cleaned = AtomicInteger()
        val context = context(
            register = { if (failing) throw SecurityException("clock service unavailable") },
            unregister = { cleaned.incrementAndGet() },
        )
        assertTrue(withTimeout(5_000) { clockTimeUpdates(context) { 1000L }.toList() }.isEmpty())
        assertEquals(0, cleaned.get())
        failing = false
        assertEquals(2000L, withTimeout(5_000) { clockTimeUpdates(context) { 2000L }.first() })
        assertEquals(1, cleaned.get())
    }
}
