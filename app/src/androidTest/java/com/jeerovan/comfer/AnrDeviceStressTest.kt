package com.jeerovan.comfer

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** No production data, OEM provider, network fixture, or additional APK required. */
class AnrDeviceStressTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    private fun isolated() {
        check(context.packageName == "com.jeerovan.comfer.notificationtest")
    }

    @Test(timeout = 240_000)
    fun launcherRecreationAndResumeRemainResponsiveInEnglishAndArabic() {
        isolated()
        await("startup ready") { StartupCoordinator.isReady }
        val keys = HomeGuideStep.entries.map { it.preferenceKey } + PreferenceManager.FEEDBACK_DIALOG
        val previous = keys.associateWith { PreferenceManager.getString(context, it, null) }
        val oldLocale = applicationLocaleTags()
        try {
            keys.forEach { PreferenceManager.setBoolean(context, it, true) }
            for (tag in listOf("en", "ar")) {
                changeLanguage(tag)
                MainThreadProbe("launcher-$tag").use { probe ->
                    repeat(6) { iteration ->
                        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                            fun verifyWindow() {
                                await("launcher window $tag/$iteration") {
                                    var ready = false
                                    scenario.onActivity { activity ->
                                        ready = activity.hasWindowFocus() &&
                                            activity.resources.configuration.locales[0].language == tag &&
                                            activity.resources.configuration.layoutDirection ==
                                            (if (tag == "ar") View.LAYOUT_DIRECTION_RTL else View.LAYOUT_DIRECTION_LTR) &&
                                            activity.window.decorView.width > 0
                                    }
                                    ready
                                }
                            }
                            verifyWindow()
                            scenario.moveToState(Lifecycle.State.CREATED)
                            scenario.moveToState(Lifecycle.State.RESUMED)
                            verifyWindow()
                            scenario.recreate()
                            verifyWindow()
                        }
                    }
                    probe.assertResponsive()
                }
            }
        } finally {
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
            changeLanguage(oldLocale)
        }
    }

    private fun applicationLocaleTags(): String = if (Build.VERSION.SDK_INT >= 33) {
        context.getSystemService(android.app.LocaleManager::class.java).applicationLocales.toLanguageTags()
    } else AppCompatDelegate.getApplicationLocales().toLanguageTags()

    private fun changeLanguage(tag: String) {
        // On API 33+, AppCompat needs a live delegate to find LocaleManager. Calling
        // its setter between closed scenarios silently does nothing. Exercise the
        // actual picker trampoline, then verify the system's per-app locale state.
        context.startActivity(Intent(context, LanguageUpdateActivity::class.java)
            .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        await("language trampoline applies '$tag'") { applicationLocaleTags() == tag }
    }

    @Test(timeout = 240_000)
    fun realWidgetServiceAppliesPendingUpdatesAcrossStopStartBursts() {
        isolated()
        val manager = WidgetHostManager(context)
        val hosts = listOf(20101, 20102, 20103).map { AppWidgetHost(context, it) }
        val ids = mutableListOf<Int>()
        val views = mutableListOf<AppWidgetHostView>()
        val provider = ComponentName(context.packageName, "com.jeerovan.comfer.AnrFixtureWidgetProvider")
        val user = android.os.Process.myUserHandle().hashCode()
        shell("appwidget grantbind --package ${context.packageName} --user $user")
        try {
            instrumentation.runOnMainSync {
                manager.mainHost = hosts[0]; manager.leftHost = hosts[1]; manager.rightHost = hosts[2]
            }
            hosts.forEach { host ->
                val id = host.allocateAppWidgetId().also(ids::add)
                assertTrue("Fixture widget binding must succeed (not silently skipped)",
                    manager.appWidgetManager.bindAppWidgetIdIfAllowed(id, provider))
            }
            ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    val container = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
                    hosts.zip(ids).forEach { (host, id) ->
                        val view = host.createView(activity, id, manager.appWidgetManager.getAppWidgetInfo(id))
                        views += view
                        container.addView(view, LinearLayout.LayoutParams(-1, 160))
                    }
                    activity.setContentView(container)
                    manager.startListening()
                }
                MainThreadProbe("real-widget-service").use { probe ->
                    repeat(30) { iteration ->
                        instrumentation.runOnMainSync { manager.stopListening() }
                        ids.forEach { id ->
                            val updates = RemoteViews("android", android.R.layout.simple_list_item_1)
                            updates.setTextViewText(android.R.id.text1, "widget-$id-update-$iteration")
                            manager.appWidgetManager.updateAppWidget(id, updates)
                        }
                        instrumentation.runOnMainSync {
                            // Stress conflation while retaining a final request to listen.
                            repeat(20) { manager.startListening(); manager.stopListening() }
                            manager.startListening()
                        }
                        await("all three widget hosts render update $iteration") {
                            var applied = false
                            scenario.onActivity {
                                applied = views.zip(ids).all { (view, id) ->
                                    view.findViewById<TextView>(android.R.id.text1)?.text?.toString() ==
                                        "widget-$id-update-$iteration" && view.isShown
                                }
                            }
                            applied
                        }
                    }
                    probe.assertResponsive()
                }
            }
        } finally {
            instrumentation.runOnMainSync { manager.cleanup() }
            hosts.forEach { it.stopListening(); it.deleteHost() }
            shell("appwidget revokebind --package ${context.packageName} --user $user")
        }
    }

    private fun shell(command: String): String = instrumentation.uiAutomation.executeShellCommand(command).use {
        android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().readText()
    }

    private fun await(label: String, condition: () -> Boolean) {
        val deadline = SystemClock.elapsedRealtime() + 30_000
        do {
            if (condition()) return
            SystemClock.sleep(100)
        } while (SystemClock.elapsedRealtime() < deadline)
        fail("Timed out: $label")
    }

    /** A responsiveness signal, not proof that Android classified an actual ANR. */
    private class MainThreadProbe(private val label: String) : AutoCloseable {
        private val running = AtomicBoolean(true)
        private val longest = AtomicLong()
        private val handler = Handler(Looper.getMainLooper())
        private val worker = Thread({
            while (running.get()) {
                val completed = CountDownLatch(1)
                val start = SystemClock.elapsedRealtime()
                val ping = Runnable { completed.countDown() }
                handler.post(ping)
                while (running.get() && !completed.await(1, TimeUnit.SECONDS)) {
                    val elapsed = SystemClock.elapsedRealtime() - start
                    longest.accumulateAndGet(elapsed, ::maxOf)
                    Log.w(TAG, "$label main unresponsive ${elapsed}ms\n" +
                        Looper.getMainLooper().thread.stackTrace.joinToString("\n"))
                }
                longest.accumulateAndGet(SystemClock.elapsedRealtime() - start, ::maxOf)
                handler.removeCallbacks(ping)
                if (running.get()) SystemClock.sleep(100)
            }
        }, "anr-test-main-probe").apply { isDaemon = true; start() }

        fun assertResponsive() {
            Log.i(TAG, "$label maxMainDispatchMs=${longest.get()}")
            assertTrue("$label blocked main >= 5 seconds; inspect AnrDeviceStress logcat",
                longest.get() < 5_000)
        }

        override fun close() { running.set(false); worker.join(2_000) }
        companion object { private const val TAG = "AnrDeviceStress" }
    }
}
