package com.jeerovan.comfer

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.os.Looper
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.espresso.Espresso
import com.jeerovan.comfer.data.ComferRepository
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class WidgetProviderRecoveryTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val context get() = ApplicationProvider.getApplicationContext<Context>()
    private fun shell(command: String) {
        ParcelFileDescriptor.AutoCloseInputStream(
            InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ).use { it.readBytes() }
    }

    @Test fun defaultHomeLoadsRealWidgetPickerAfterLaunchAndRecreation() {
        check(context.packageName.endsWith(".notificationtest"))
        val keys = HomeGuideStep.entries.map { it.preferenceKey } +
            listOf(PreferenceManager.FEEDBACK_DIALOG, PreferenceManager.CUSTOM_WIDGETS)
        val previous = keys.associateWith {
            if (PreferenceManager.hasKey(context, it)) PreferenceManager.getBoolean(context, it, false) else null
        }
        val saved = runBlocking { ComferRepository.getWidgetPlacement(context, "widgets_center")?.widgetsJson }
        val first = AppWidgetManager.getInstance(context).installedProviders.first {
            !WidgetInflationGuard.isKnownUnsafe(it.provider.flattenToShortString())
        }
        val pm = context.packageManager
        val label = pm.getApplicationInfo(first.provider.packageName, 0).loadLabel(pm).toString()
        try {
            keys.forEach { PreferenceManager.setBoolean(context, it, true) }
            runBlocking { ComferRepository.saveWidgetPlacement(context, "widgets_center", "[]") }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                repeat(2) { iteration ->
                    if (iteration > 0) scenario.recreate()
                    val hint = context.getString(R.string.long_press_to_add_edit_widgets)
                    compose.waitUntil(30_000) {
                        compose.onAllNodesWithText(hint).fetchSemanticsNodes(atLeastOneRootRequired = false).isNotEmpty()
                    }
                    compose.onNodeWithText(hint).performTouchInput { longClick() }
                    compose.onNodeWithContentDescription(context.getString(R.string.add_widget))
                        .performTouchInput { click() }
                    compose.waitUntil(15_000) {
                        compose.onAllNodesWithText(label).fetchSemanticsNodes().isNotEmpty()
                    }
                    compose.onAllNodesWithText(label)[0].assertIsDisplayed()
                    compose.onNodeWithContentDescription(context.getString(R.string.close)).performClick()
                    compose.onNodeWithContentDescription("Workspace").performClick()
                    compose.onNodeWithTag("workspace-module-search").assertIsDisplayed()
                    Espresso.pressBack()
                }
            }
        } finally {
            previous.forEach { (key, value) ->
                if (value == null) PreferenceManager.clear(context, key)
                else PreferenceManager.setBoolean(context, key, value)
            }
            runBlocking { ComferRepository.saveWidgetPlacement(context, "widgets_center", saved ?: "[]") }
        }
    }

    @Test fun failedColdAndWarmRefreshPreservePlacementAndResumeRecovers() {
        check(context.packageName.endsWith(".notificationtest"))
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context.packageName, AnrFixtureWidgetProvider::class.java.name)
        val provider = manager.installedProviders.single { it.provider == component }
        val views = CopyOnWriteArrayList<AppWidgetHostView>()
        val host = object : AppWidgetHost(context, 22058) {
            override fun onCreateView(context: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
                AppWidgetHostView(context).also(views::add)
        }
        val slot = "widget-refresh-regression"
        val original = runBlocking { ComferRepository.getWidgetPlacement(context, slot)?.widgetsJson }
        val mode = AtomicInteger(0)
        val calls = AtomicInteger()
        val blocked = CountDownLatch(1)
        val release = CountDownLatch(1)
        val returned = CountDownLatch(1)
        val inventory = WidgetProviderInventory(listOf(provider), listOf(
            WidgetProviderGroup("Fixture", component.packageName, listOf(provider))))
        shell("appwidget grantbind --package ${context.packageName} --user ${android.os.Process.myUserHandle().hashCode()}")
        val id = host.allocateAppWidgetId()
        try {
            assertTrue(manager.bindAppWidgetIdIfAllowed(id, component))
            val saved = Json.encodeToString(listOf(PersistableBoundWidget(
                id, component.packageName, component.className, 0, 0, 2, 2)))
            runBlocking { ComferRepository.saveWidgetPlacement(context, slot, saved) }
            ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
                fun mount() = scenario.onActivity { activity ->
                    activity.setContent {
                        ComferTheme {
                            WidgetHostScreen(Modifier.fillMaxSize(), manager, host, slot, 4, {}, {},
                                providerInventoryLoader = {
                                    calls.incrementAndGet()
                                    when (mode.get()) {
                                        0 -> null
                                        1 -> inventory
                                        2 -> WidgetProviderInventory(emptyList(), emptyList())
                                        else -> withContext(Dispatchers.IO) {
                                            blocked.countDown()
                                            check(release.await(15, TimeUnit.SECONDS))
                                            returned.countDown()
                                            WidgetProviderInventory(emptyList(), emptyList())
                                        }
                                    }
                                })
                        }
                    }
                }
                fun resume(next: Int) {
                    scenario.moveToState(Lifecycle.State.CREATED)
                    mode.set(next)
                    val before = calls.get()
                    scenario.moveToState(Lifecycle.State.RESUMED)
                    compose.waitUntil(15_000) { calls.get() > before }
                    compose.waitForIdle()
                }
                fun assertPreserved() {
                    assertEquals(saved, runBlocking { ComferRepository.getWidgetPlacement(context, slot)!!.widgetsJson })
                    assertNotNull("Failed discovery must not release the widget ID", manager.getAppWidgetInfo(id))
                }
                mount()
                compose.waitUntil(15_000) { calls.get() > 0 }
                assertPreserved()
                resume(1)
                compose.waitUntil(15_000) { views.any { it.isShown } }
                assertPreserved()
                resume(0)
                assertPreserved()
                assertTrue(views.any { it.isShown })

                // A blocked obsolete request must not publish an empty inventory after recovery.
                resume(3)
                assertTrue(blocked.await(5, TimeUnit.SECONDS))
                resume(1)
                release.countDown()
                assertTrue(returned.await(5, TimeUnit.SECONDS))
                compose.waitForIdle()
                assertPreserved()

                mode.set(0)
                scenario.recreate()
                mount()
                compose.waitForIdle()
                assertPreserved()
                resume(1)
                compose.waitUntil(15_000) { views.any { it.isShown } }
                assertPreserved()

                // A successful empty service snapshot really does permit orphan cleanup.
                resume(2)
                compose.waitUntil(15_000) {
                    runBlocking { ComferRepository.getWidgetPlacement(context, slot)?.widgetsJson == "[]" }
                }
                assertNull(manager.getAppWidgetInfo(id))
            }
        } finally {
            release.countDown()
            host.deleteHost()
            runBlocking { ComferRepository.saveWidgetPlacement(context, slot, original ?: "[]") }
        }
    }

    @Test fun previewLabelMemoryFailureRemainsSelectableInBothDirections() {
        val name = "example.widget.provider.with.a.long.package.name"
        val provider = AppWidgetProviderInfo().apply {
            this.provider = ComponentName(name, "$name.MemoryFixture")
        }
        val metadataFailures = AtomicInteger()
        val failingContext = object : ContextWrapper(context) {
            override fun getPackageManager(): PackageManager {
                // Compose also queries PackageManager on Main while constructing the grid.
                // Only fail the background image/label resource boundary under test.
                if (Looper.myLooper() != Looper.getMainLooper()) {
                    metadataFailures.incrementAndGet()
                    throw OutOfMemoryError("preview metadata")
                }
                return super.getPackageManager()
            }
        }
        val selected = AtomicInteger()
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            for (direction in listOf(LayoutDirection.Ltr, LayoutDirection.Rtl)) {
                scenario.onActivity { activity ->
                    activity.setContent {
                        ComferTheme {
                            CompositionLocalProvider(
                                LocalLayoutDirection provides direction, LocalContext provides failingContext,
                            ) {
                                WidgetPickerFullScreen({}, { selected.incrementAndGet() },
                                    listOf(WidgetProviderGroup(name, name, listOf(provider))))
                            }
                        }
                    }
                }
                compose.waitUntil(15_000) {
                    compose.onAllNodesWithText(".MemoryFixture").fetchSemanticsNodes().isNotEmpty()
                }
                compose.onNodeWithText(name).assertIsDisplayed()
                val layouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithText(name).performSemanticsAction(SemanticsActions.GetTextLayoutResult) {
                    it(layouts)
                }
                assertFalse("The package fallback must not clip in $direction", layouts.single().hasVisualOverflow)
                compose.onNodeWithText(".MemoryFixture").assertIsDisplayed().performClick()
            }
            assertEquals(2, selected.get())
            assertTrue(metadataFailures.get() >= 2)
        }
    }
}
