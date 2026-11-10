package com.jeerovan.comfer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.os.Bundle
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.journals.JournalActivity
import com.jeerovan.comfer.notes.NotesActivity
import com.jeerovan.comfer.notifications.NotificationInboxActivity
import com.jeerovan.comfer.tasks.TasksActivity
import org.junit.Assert.*
import org.junit.Test

/** Dispatch fault injection; default Home wiring is exercised separately. */
class InternalActivityLaunchTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private class RecordingContext(base: Context) : ContextWrapper(base) {
        var failure: RuntimeException? = null
        var captured: Intent? = null
        var options: Bundle? = null
        override fun startActivity(intent: Intent) = startActivity(intent, null)
        override fun startActivity(intent: Intent, options: Bundle?) {
            captured = Intent(intent)
            this.options = options
            failure?.let { throw it }
        }
    }
    private data class Route(val activity: Class<*>, val flags: Int, val animated: Boolean, val open: (Context) -> Unit)
    private val routes = listOf(
        Route(SettingsActivity::class.java, 0, false) { SettingsActivity.open(it) },
        Route(NotificationInboxActivity::class.java, 0, true) { NotificationInboxActivity.open(it, "example.app", true) },
        Route(TasksActivity::class.java, Intent.FLAG_ACTIVITY_SINGLE_TOP, true) { TasksActivity.open(it, "test-task") },
        Route(NotesActivity::class.java, Intent.FLAG_ACTIVITY_SINGLE_TOP, true) { NotesActivity.open(it) },
        Route(JournalActivity::class.java, Intent.FLAG_ACTIVITY_SINGLE_TOP, true) { JournalActivity.open(it) },
    )
    private fun onMain(block: () -> Unit): Throwable? {
        var failure: Throwable? = null
        instrumentation.runOnMainSync { failure = runCatching(block).exceptionOrNull() }
        return failure
    }
    @Test fun rejectedLaunchesRemainRetryableAndPreserveIntent() {
        for (route in routes) for (rejection in listOf(ActivityNotFoundException("test missing activity"), SecurityException("test launch restriction"))) {
            val context = RecordingContext(instrumentation.targetContext)
            context.failure = rejection
            val escaped = onMain { route.open(context) }
            if (escaped != null) throw AssertionError("${route.activity.simpleName} must contain ${rejection.javaClass.simpleName}", escaped)
            context.failure = null
            onMain { route.open(context) }?.let { throw it }
            val intent = requireNotNull(context.captured)
            assertEquals(route.activity.name, intent.component?.className)
            assertEquals(context.packageName, intent.component?.packageName)
            assertEquals("Internal navigation keeps its original task flags", route.flags, intent.flags)
            assertEquals(route.animated, context.options != null)
            if (route.activity == TasksActivity::class.java) assertEquals("test-task", intent.getStringExtra("task"))
            if (route.activity == NotificationInboxActivity::class.java) {
                assertEquals("example.app", intent.getStringExtra("app"))
                assertTrue(intent.getBooleanExtra("controls", false))
            }
        }
    }
    @Test fun unrelatedProgrammingFailuresAreNotSwallowed() {
        for (route in routes) {
            val context = RecordingContext(instrumentation.targetContext)
            val failure = IllegalStateException("test unrelated failure")
            context.failure = failure
            assertSame(failure, onMain { route.open(context) })
        }
    }
}
