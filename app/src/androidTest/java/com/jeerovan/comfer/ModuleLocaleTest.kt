package com.jeerovan.comfer

import android.content.Intent
import android.content.res.Configuration
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import android.os.SystemClock
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.journals.JournalActivity
import com.jeerovan.comfer.notes.NotesActivity
import com.jeerovan.comfer.tasks.TasksActivity
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class ModuleLocaleTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun resources(tag: String) = instrumentation.targetContext.let { context ->
        val config = Configuration(context.resources.configuration)
        config.setLocale(Locale.forLanguageTag(tag))
        context.createConfigurationContext(config).resources
    }

    @Test fun everySupportedLocaleHasModuleAndSettingsTranslations() {
        val english = resources("en")
        val tags = "ar az bn de el es fa fr hi id it he ja km ko lo mn mr my nl pt ro ru ta te tg th tk tr uk ur uz vi zh-CN".split(" ")
        val formatted = listOf(
            R.string.restore_backup_summary to arrayOf<Any>("Sample label", "Sample label"),
            R.string.journal_written_at to arrayOf<Any>("Sample label"),
            R.string.journal_writing_for to arrayOf<Any>("Sample label"),
            R.string.module_recovered_drafts_1_d to arrayOf<Any>(7),
            R.string.module_select_label_1_s to arrayOf<Any>("Sample label"),
            R.string.module_permanently_delete_1_d_notes to arrayOf<Any>(7),
            R.string.module_text_color_1_s to arrayOf<Any>("Sample label"),
            R.string.module_uncheck_item_1_d_2_s to arrayOf<Any>(7, "Sample label"),
            R.string.module_check_item_1_d_2_s to arrayOf<Any>(7, "Sample label"),
            R.string.module_dictation_device_error to arrayOf<Any>(7),
            R.string.module_dictation_provider_error to arrayOf<Any>(7),
            R.string.module_dictation_error to arrayOf<Any>(7),
            R.string.tasks_backup_complete to arrayOf<Any>(7, 7),
            R.string.tasks_snoozed_until to arrayOf<Any>("Sample label"),
            R.string.tasks_backup_counts to arrayOf<Any>(7, 7),
            R.string.tasks_move_count to arrayOf<Any>("Sample label", 7),
            R.string.tasks_restore to arrayOf<Any>(7, 7),
            R.string.tasks_review_move to arrayOf<Any>("Sample label"),
            R.string.tasks_review_delete to arrayOf<Any>("Sample label"),
            R.string.tasks_error to arrayOf<Any>("Sample label"),
            R.string.tasks_children to arrayOf<Any>(7, 7),
            R.string.tasks_next to arrayOf<Any>("Sample label"),
            R.string.tasks_done_at to arrayOf<Any>("Sample label"),
            R.string.tasks_snooze_minutes to arrayOf<Any>(7),
            R.string.tasks_due_count to arrayOf<Any>(7),
            R.string.tasks_counts to arrayOf<Any>(7, 7),
            R.string.tasks_moved to arrayOf<Any>("Sample label"),
            R.string.tasks_every_days to arrayOf<Any>(7),
            R.string.tasks_every_weeks to arrayOf<Any>(7),
            R.string.tasks_every_months to arrayOf<Any>(7),
            R.string.tasks_every_years to arrayOf<Any>(7),
        )
        for (tag in tags) {
            val local = resources(tag)
            for (id in listOf(R.string.module_new_note, R.string.tasks_empty_starred,
                R.string.journal_empty, R.string.protection_timeout,
                R.string.local_spatial_title, R.string.local_spatial_download_description,
                R.string.local_spatial_retry, R.string.local_spatial_model_failed)) {
                assertNotEquals("English fallback: $tag / ${local.getResourceEntryName(id)}", english.getString(id), local.getString(id))
            }
            for ((id, args) in formatted) {
                val rendered = runCatching { local.getString(id, *args) }
                assertTrue("Formatting failed: $tag / ${local.getResourceEntryName(id)}: ${rendered.exceptionOrNull()}", rendered.isSuccess)
                assertTrue(rendered.getOrThrow().isNotBlank())
            }
            val label = "My label — محفوظ"
            assertTrue(local.getString(R.string.module_select_label_1_s, label).contains(label))
            assertEquals(local.getString(R.string.module_choose_a_future_snooze_time), localizedModuleMessage(local, "Choose a future snooze time"))
            assertEquals(local.getString(R.string.module_dictation_error, 7), localizedModuleMessage(local, "Speech recognition stopped (provider error 7)"))
            val alreadyLocalized = local.getString(R.string.module_could_not_copy_this_draft_please_retry)
            assertEquals(alreadyLocalized, localizedModuleMessage(local, alreadyLocalized))
            assertEquals(local.getString(R.string.module_could_not_complete_this_action_please_try_again), localizedModuleMessage(local, null))
            assertEquals(local.getString(R.string.module_could_not_complete_this_action_please_try_again), localizedModuleMessage(local, "/private/unknown-error"))
        }
    }

    @Test fun moduleActivitiesFollowSelectedLanguageAcrossReopening() {
        // ActivityScenario's global-idle launch wait is unreliable for a launcher
        // with continuously updating previews. Observe the actual resumed Activity
        // with a bounded wait, without waiting for every window to become idle.
        launch(GuideActivity::class.java)
        var previous = ""
        instrumentation.runOnMainSync { previous = AppCompatDelegate.getApplicationLocales().toLanguageTags() }
        try {
            for (tag in listOf("de", "ar")) {
                instrumentation.runOnMainSync { AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag)) }
                for ((activityClass, label) in listOf(
                    NotesActivity::class.java to R.string.module_new_note,
                    TasksActivity::class.java to R.string.tasks_empty_starred,
                    JournalActivity::class.java to R.string.journal_empty,
                    SettingsActivity::class.java to R.string.protection_timeout,
                )) {
                    repeat(2) {
                        val activity = launch(activityClass)
                        instrumentation.runOnMainSync {
                            assertEquals(tag, activity.resources.configuration.locales[0].language)
                            assertEquals(resources(tag).getString(label), activity.getString(label))
                            assertEquals(if (tag == "ar") 1 else 0, activity.resources.configuration.layoutDirection)
                            activity.finish()
                        }
                        awaitState("${activityClass.simpleName} destroyed") { activity.isDestroyed }
                    }
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(previous))
                listOf(Stage.CREATED, Stage.STARTED, Stage.RESUMED, Stage.PAUSED, Stage.STOPPED)
                    .flatMap { ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(it) }
                    .distinct().filterIsInstance<AppCompatActivity>().forEach { it.finish() }
            }
        }
    }

    private fun launch(type: Class<out AppCompatActivity>): AppCompatActivity {
        instrumentation.runOnMainSync {
            instrumentation.targetContext.startActivity(Intent(instrumentation.targetContext, type)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
        var resumed: AppCompatActivity? = null
        awaitState("${type.simpleName} resumed") {
            resumed = ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED)
                .firstOrNull { type.isInstance(it) } as? AppCompatActivity
            resumed != null
        }
        return requireNotNull(resumed)
    }

    private fun awaitState(description: String, condition: () -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + 15000
        do {
            var ready = false
            instrumentation.runOnMainSync { ready = condition() }
            if (ready) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("Timed out waiting for $description")
    }
}
