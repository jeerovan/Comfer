package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Resources
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Replays callbacks before recomposition, the window implicated by v55's folder-delete crash. */
class ManageFolderSelectionTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val application = ApplicationProvider.getApplicationContext<ComferApp>()

    @Test fun staleFolderActionsAreSafeForBothLanguageSources() {
        check(application.packageName == "com.jeerovan.comfer.notificationtest")
        runBlocking {
            application.initializeApplicationData()
            StartupCoordinator.awaitReady()
        }
        val systemTag = if (android.os.Build.VERSION.SDK_INT >= 33) {
            application.getSystemService(android.app.LocaleManager::class.java).systemLocales[0].toLanguageTag()
        } else Resources.getSystem().configuration.locales[0].toLanguageTag()
        val systemLanguage = java.util.Locale.forLanguageTag(systemTag).language
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals(it, systemLanguage)
        }
        var oldLocale = ""
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { oldLocale = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        }
        val oldOrder = runBlocking { AppInfoManager.getAppPackageNames(application, LIST) }
        val oldFolders = runBlocking { AppInfoManager.getFolders(application) }
        val oldSort = PreferenceManager.getAlphabeticalOrder(application)
        try {
            PreferenceManager.setAlphabeticalOrder(application, false)
            changeLanguage("")
            val inherited = exercise(systemLanguage)
            changeLanguage(systemTag)
            assertEquals("System and app language preserve geometry", inherited, exercise(systemLanguage))
            val opposite = if (systemLanguage == "ar") "en" else "ar"
            changeLanguage(opposite)
            exercise(opposite)
            changeLanguage("")
            assertEquals("Return to system language preserves geometry", inherited, exercise(systemLanguage))
        } finally {
            runBlocking {
                AppInfoManager.deleteFolder(application, "folder_crash_regression")
                AppInfoManager.deleteFolder(application, "folder_retained_regression")
                AppInfoManager.saveFolders(application, oldFolders)
                oldOrder?.let { AppInfoManager.saveAppPackageNames(application, LIST, it) }
            }
            PreferenceManager.setAlphabeticalOrder(application, oldSort)
            changeLanguage(oldLocale)
        }
    }

    private fun exercise(language: String): List<Float> {
        val target = "folder_crash_regression"
        val retained = "folder_retained_regression"
        runBlocking {
            AppInfoManager.saveFolders(application, mapOf(
                target to FolderData(target, "Delete fixture", emptyList()),
                retained to FolderData(retained, "Retain fixture", emptyList()),
            ))
            AppInfoManager.saveAppPackageNames(application, LIST, listOf(target, retained))
        }
        val store = ViewModelStore()
        lateinit var model: AppInfoViewModel
        lateinit var deleteLabel: String
        lateinit var downLabel: String
        lateinit var upLabel: String
        var bounds = emptyList<Float>()
        try {
            ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
                scenario.onActivity { activity ->
                    assertEquals(language, activity.resources.configuration.locales[0].language)
                    assertEquals(if (language == "ar") 1 else 0, activity.resources.configuration.layoutDirection)
                    deleteLabel = activity.getString(R.string.ui_delete_folder)
                    downLabel = activity.getString(R.string.ui_move_to_folder)
                    upLabel = activity.getString(R.string.ui_move_to_primary)
                    model = ViewModelProvider(store,
                        ViewModelProvider.AndroidViewModelFactory.getInstance(application))[AppInfoViewModel::class.java]
                    activity.setContent { ComferTheme { ManageLayersScreen(model) } }
                }
                compose.waitUntil(60_000) {
                    model.uiState.value.primaryApps.any { it.packageName == target } &&
                        PerformanceTrace.appRefreshStats().active == 0
                }
                val folder = compose.onNodeWithTag("manage-app:$LIST:$target")
                folder.assertIsDisplayed().performClick()
                val deleteNode = compose.onNodeWithContentDescription(deleteLabel).assertIsDisplayed()
                val rectangle = deleteNode.fetchSemanticsNode().boundsInRoot
                assertTrue("Delete control has visible area", rectangle.width > 0 && rectangle.height > 0)
                bounds = listOf(rectangle.left, rectangle.top, rectangle.right, rectangle.bottom)
                val delete = deleteNode.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val moveIn = compose.onNodeWithContentDescription(downLabel).assertIsDisplayed()
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val moveOut = compose.onNodeWithContentDescription(upLabel).assertIsDisplayed()
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val deselect = folder.fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                compose.runOnIdle {
                    deselect() // Remove selection while old button callbacks still exist.
                    moveIn()
                    moveOut()
                    delete()
                }
                compose.runOnIdle { assertTrue(model.uiState.value.folders.containsKey(target)) }
                folder.performClick()
                val confirm = compose.onNodeWithContentDescription(deleteLabel).assertIsDisplayed()
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val moveInAgain = compose.onNodeWithContentDescription(downLabel)
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                val moveOutAgain = compose.onNodeWithContentDescription(upLabel)
                    .fetchSemanticsNode().config[SemanticsActions.OnClick].action!!
                compose.runOnIdle {
                    confirm()
                    confirm() // Repeat before Compose removes the old button.
                    moveInAgain()
                    moveOutAgain()
                }
                compose.waitUntil(10_000) {
                    !model.uiState.value.folders.containsKey(target) &&
                        runBlocking { !AppInfoManager.getFolders(application).containsKey(target) }
                }
                compose.runOnIdle {
                    assertTrue(model.uiState.value.folders.containsKey(retained))
                    assertFalse(model.uiState.value.primaryApps.any { it.packageName == target })
                }
                assertTrue(runBlocking { AppInfoManager.getFolders(application).containsKey(retained) })
                compose.onNodeWithContentDescription(deleteLabel).assertDoesNotExist()
            }
        } finally {
            instrumentation.runOnMainSync { store.clear() }
        }
        return bounds
    }

    private fun changeLanguage(tag: String) {
        application.startActivity(Intent(application, LanguageUpdateActivity::class.java)
            .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val deadline = SystemClock.uptimeMillis() + 10_000
        do {
            var current = ""
            instrumentation.runOnMainSync {
                current = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    application.getSystemService(android.app.LocaleManager::class.java)
                        .applicationLocales.toLanguageTags()
                } else AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
            if (current == tag) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Language did not switch to '$tag'")
    }

    private companion object { const val LIST = AppInfoManager.PRIMARY_APPS_LIST_NAME }
}
