package com.jeerovan.comfer

import android.appwidget.AppWidgetHostView
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.RemoteViews
import android.widget.TextView
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.espresso.Espresso
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.data.ComferRepository
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class WidgetMeasurementRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            instrumentation.uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    // A broken provider adapter, not a synthetic exception: Android's real recycler
    // indexes beyond its declared view-type count while measuring wrap-content rows.
    private fun brokenAdapter() = object : BaseAdapter() {
        override fun getCount() = 3
        override fun getItem(position: Int) = position
        override fun getItemId(position: Int) = position.toLong()
        override fun getViewTypeCount() = 2
        override fun getItemViewType(position: Int) = 2
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View =
            TextView(parent.context).apply { text = "Provider row $position"; minHeight = 40 }
    }

    private fun measure(view: View) {
        view.measure(View.MeasureSpec.makeMeasureSpec(400, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(300, View.MeasureSpec.EXACTLY))
        view.layout(0, 0, 400, 300)
    }

    @Test fun productionHostsContainRecyclerFailureAndAcceptHealthyUpdates() {
        check(context.packageName.endsWith(".notificationtest"))
        val manager = (context as ComferApp).widgetHostManager
        val component = ComponentName(context, AnrFixtureWidgetProvider::class.java)
        val provider = manager.appWidgetManager.installedProviders.single { it.provider == component }
        shell("appwidget grantbind --package ${context.packageName} --user ${android.os.Process.myUserHandle().hashCode()}")
        val fixtureLayout = context.resources.getIdentifier("widget_recycler_fixture", "layout", context.packageName)
        assertNotEquals(0, fixtureLayout)
        for (host in listOf(manager.mainHost, manager.leftHost, manager.rightHost)) {
            val id = host.allocateAppWidgetId()
            try {
                assertTrue(manager.appWidgetManager.bindAppWidgetIdIfAllowed(id, component))
                instrumentation.runOnMainSync {
                    val view = host.createView(context, id, provider)
                    for (direction in listOf(View.LAYOUT_DIRECTION_LTR, View.LAYOUT_DIRECTION_RTL)) {
                        view.layoutDirection = direction
                        view.updateAppWidget(RemoteViews(context.packageName, fixtureLayout))
                        view.findViewById<ListView>(android.R.id.list).adapter = brokenAdapter()
                        measure(view)
                        assertNull("Broken collection must be replaced", view.findViewById<View>(android.R.id.list))
                        assertEquals(id, view.appWidgetId)
                        assertEquals(400, view.measuredWidth)
                        assertEquals(300, view.measuredHeight)
                        val error = view.getChildAt(0) as TextView
                        assertTrue("Fallback must explain failure", error.text.isNotBlank())
                        assertTrue(error.width > 0 && error.height > 0)
                        // A provider update must recover the same bound host view without re-adding it.
                        view.updateAppWidget(RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
                            setTextViewText(android.R.id.text1, "Recovered widget")
                        })
                        measure(view)
                        assertEquals("Recovered widget", view.findViewById<TextView>(android.R.id.text1).text.toString())
                    }
                }
                assertNotNull(manager.appWidgetManager.getAppWidgetInfo(id))
            } finally { host.deleteAppWidgetId(id) }
        }
    }

    @Test fun unrelatedMeasurementFailureIsNotSuppressed() {
        val manager = (context as ComferApp).widgetHostManager
        val provider = manager.appWidgetManager.installedProviders.single {
            it.provider == ComponentName(context, AnrFixtureWidgetProvider::class.java)
        }
        instrumentation.runOnMainSync {
            // No service binding is needed for this negative control.
            val view = manager.mainHost.createView(context, -1, provider)
            val expected = ArrayIndexOutOfBoundsException("unrelated local measurement defect")
            view.addView(object : View(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) { throw expected }
            })
            try {
                measure(view)
                fail("An unrelated exception must propagate")
            } catch (actual: ArrayIndexOutOfBoundsException) { assertSame(expected, actual) }
        }
    }

    private fun descendants(view: View): Sequence<View> = sequence {
        yield(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) yieldAll(descendants(view.getChildAt(i)))
    }

    @Test fun normalHomeRetainsPlacementAndRecoversOnProviderUpdateAndRecreation() {
        check(context.packageName.endsWith(".notificationtest"))
        val manager = (context as ComferApp).widgetHostManager
        val host = manager.mainHost
        val component = ComponentName(context, AnrFixtureWidgetProvider::class.java)
        shell("appwidget grantbind --package ${context.packageName} --user ${android.os.Process.myUserHandle().hashCode()}")
        val id = host.allocateAppWidgetId()
        val slot = "widgets_center"
        val original = runBlocking { ComferRepository.getWidgetPlacement(context, slot)?.widgetsJson }
        val keys = HomeGuideStep.entries.map { it.preferenceKey } +
            listOf(PreferenceManager.FEEDBACK_DIALOG, PreferenceManager.CUSTOM_WIDGETS)
        val preferences = keys.associateWith {
            if (PreferenceManager.hasKey(context, it)) PreferenceManager.getBoolean(context, it, false) else null
        }
        try {
            assertTrue(manager.appWidgetManager.bindAppWidgetIdIfAllowed(id, component))
            val placement = Json.encodeToString(listOf(PersistableBoundWidget(
                id, component.packageName, component.className, 0, 0, 3, 3)))
            runBlocking { ComferRepository.saveWidgetPlacement(context, slot, placement) }
            keys.forEach { PreferenceManager.setBoolean(context, it, true) }
            val fixture = context.resources.getIdentifier("widget_recycler_fixture", "layout", context.packageName)
            manager.appWidgetManager.updateAppWidget(id, RemoteViews(context.packageName, fixture))
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                var view: AppWidgetHostView? = null
                fun findHost() = scenario.onActivity { activity ->
                    view = descendants(activity.window.decorView).filterIsInstance<AppWidgetHostView>()
                        .firstOrNull { it.appWidgetId == id && it.isShown }
                }
                compose.waitUntil(30_000) {
                    findHost()
                    var found = false
                    scenario.onActivity { found = view?.findViewById<ListView>(android.R.id.list) != null }
                    found
                }
                scenario.onActivity {
                    view!!.findViewById<ListView>(android.R.id.list).adapter = brokenAdapter()
                    // Normal attached-view traversal supplies measure/layout, not the test.
                }
                compose.waitUntil(15_000) {
                    var fallback = false
                    scenario.onActivity {
                        fallback = view!!.findViewById<ListView>(android.R.id.list) == null &&
                            (view!!.getChildAt(0) as? TextView)?.text?.isNotBlank() == true
                    }
                    fallback
                }
                assertEquals(placement, runBlocking { ComferRepository.getWidgetPlacement(context, slot)!!.widgetsJson })
                assertNotNull(manager.appWidgetManager.getAppWidgetInfo(id))
                compose.onNodeWithContentDescription("Workspace").performClick()
                compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
                Espresso.pressBack()

                // Queue a real provider update while stopped. Default onStart/host
                // registration must deliver it; no direct updateAppWidget on the view.
                scenario.moveToState(Lifecycle.State.CREATED)
                manager.appWidgetManager.updateAppWidget(id,
                    RemoteViews(context.packageName, android.R.layout.simple_list_item_1).apply {
                        setTextViewText(android.R.id.text1, "Recovered after resume")
                    })
                scenario.moveToState(Lifecycle.State.RESUMED)
                fun awaitRecovered() = compose.waitUntil(30_000) {
                    findHost()
                    var recovered = false
                    scenario.onActivity {
                        recovered = view?.findViewById<TextView>(android.R.id.text1)?.text?.toString() == "Recovered after resume"
                    }
                    recovered
                }
                awaitRecovered()
                scenario.recreate()
                awaitRecovered()
                assertEquals(placement, runBlocking { ComferRepository.getWidgetPlacement(context, slot)!!.widgetsJson })
                assertNotNull(manager.appWidgetManager.getAppWidgetInfo(id))
            }
        } finally {
            host.deleteAppWidgetId(id)
            runBlocking { ComferRepository.saveWidgetPlacement(context, slot, original ?: "[]") }
            preferences.forEach { (key, value) ->
                if (value == null) PreferenceManager.clear(context, key)
                else PreferenceManager.setBoolean(context, key, value)
            }
        }
    }
}
