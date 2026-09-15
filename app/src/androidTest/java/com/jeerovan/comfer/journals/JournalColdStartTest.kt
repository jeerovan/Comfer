package com.jeerovan.comfer.journals

import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Run seed, force-stop the isolated package, then verify in a second instrumentation process. */
class JournalColdStartTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun seed() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        val previous = JournalArchiveMedia.localSnapshot(context)
        File(context.filesDir, "journal-cold-before.json").writeText(Json.encodeToString(previous))
        JournalArchiveMedia.replaceLocal(context, JournalLocalSnapshot(emptyList(), emptyList()))
        val store = JournalStore(JournalDatabase.get(context))
        val live = store.ensureDraft()
        val entry = store.speech(live, "cold-session", 0, "Finalized", "Provisional", false, false)!!
        store.saveDraft(store.ensureDraft().copy(text = "Recover composer", day = -222))
        store.saveDraft(store.beginEdit(entry).copy(text = "Recover unsaved edit"))
        assertEquals("Finalized Provisional", store.dao.entry(entry.id)!!.text)
    }
    @Test fun verify() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        val file = File(context.filesDir, "journal-cold-before.json")
        check(file.isFile) { "Run seed and force-stop first" }
        try {
            val store = JournalStore(JournalDatabase.get(context))
            val draft = store.ensureDraft()
            assertEquals("Recover composer", draft.text); assertEquals(-222L, draft.day)
            val entry = store.dao.exportEntries().single()
            assertEquals("Finalized Provisional", entry.text)
            assertNotNull(entry.needsReview)
            assertEquals("Recover unsaved edit", store.beginEdit(entry).text)
            assertEquals("Provisional", store.dao.segments(entry.id).single().provisional)
            assertEquals(JournalSpeech.State.IDLE, JournalSpeech().state)
        } finally { JournalArchiveMedia.replaceLocal(context, Json.decodeFromString(file.readText())); file.delete() }
    }
}
