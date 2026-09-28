package com.jeerovan.comfer

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test

class WidgetHeightPersistenceTest {
    @Test fun independentHeightsSurviveReloadAndEnterBackups() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        check(context.packageName.endsWith(".notificationtest"))
        StartupCoordinator.awaitReady()
        val keys = listOf("time", "date").map(::widgetHeightPreferenceKey)
        val previous = (keys + "time_font_size").associateWith { PreferenceManager.getString(context, it, null) }
        val stores = mutableListOf<ViewModelStore>()
        fun model(): SettingsViewModel {
            lateinit var result: SettingsViewModel
            instrumentation.runOnMainSync {
                val store = ViewModelStore().also { stores += it }
                result = ViewModelProvider(store, ViewModelProvider.AndroidViewModelFactory(
                    context.applicationContext as Application))[SettingsViewModel::class.java]
            }
            return result
        }
        try {
            PreferenceManager.setFloat(context, keys[0], 1.25f)
            PreferenceManager.setFloat(context, keys[1], .75f)
            // Finish fixture writes before a new model reads the preference snapshot.
            withTimeout(10000) {
                context.settingsDataStore.data.first { prefs ->
                    keys.zip(listOf(1.25f, .75f)).all { (key, value) ->
                        prefs.asMap().entries.find { it.key.name == key }?.value?.toString()?.toFloatOrNull() == value
                    }
                }
            }
            PreferenceManager.reload(context)
            val initial = model()
            withTimeout(10000) { initial.uiState.first { it.timeHeightScale == 1.25f && it.dateHeightScale == .75f } }
            instrumentation.runOnMainSync {
                initial.setWidgetHeightScale("time", 2f)
                initial.setWidgetHeightScale("date", .5f)
            }
            withTimeout(10000) {
                context.settingsDataStore.data.first { prefs ->
                    keys.zip(listOf(2f, .5f)).all { (key, value) ->
                        prefs.asMap().entries.find { it.key.name == key }?.value?.toString()?.toFloatOrNull() == value
                    }
                }
            }
            PreferenceManager.reload(context)
            val restored = model()
            withTimeout(10000) { restored.uiState.first { it.timeHeightScale == 2f && it.dateHeightScale == .5f } }
            val backup = PreferenceManager.snapshotForBackup()
            assertEquals(2f, backup.getValue(keys[0]).toFloat(), 0f)
            assertEquals(.5f, backup.getValue(keys[1]).toFloat(), 0f)
            instrumentation.runOnMainSync { restored.setTimeFontSize(84) }
            withTimeout(10000) { restored.uiState.first { it.timeFontSize == 84 } }
            assertEquals("Font size does not reset stretch", 2f, restored.uiState.value.timeHeightScale, 0f)
        } finally {
            instrumentation.runOnMainSync { stores.forEach { it.clear() } }
            previous.forEach { (key, value) -> PreferenceManager.setString(context, key, value) }
        }
    }
}
