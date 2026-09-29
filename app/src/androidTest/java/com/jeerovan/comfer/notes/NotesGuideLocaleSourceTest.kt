package com.jeerovan.comfer.notes

import android.content.Intent
import android.content.res.Resources
import android.os.SystemClock
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.v2.createEmptyComposeRule
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.text.layoutDirection
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.GuideActivity
import com.jeerovan.comfer.LanguageUpdateActivity
import androidx.compose.runtime.*
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.Locale

class NotesGuideLocaleSourceTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()

    @Test fun systemAndPickerLanguagesKeepNotesGuidesOnTheirCards() {
        val systemTag = Resources.getSystem().configuration.locales[0].toLanguageTag()
        InstrumentationRegistry.getArguments().getString("expectedSystemLanguage")?.let {
            assertEquals(it, Locale.forLanguageTag(systemTag).language)
        }
        var previous = ""
        ActivityScenario.launch(GuideActivity::class.java).use { it.onActivity {
            previous = AppCompatDelegate.getApplicationLocales().toLanguageTags()
        } }
        compose.mainClock.autoAdvance = false
        try {
            changeLanguage("")
            val inherited = capture(systemTag, "system")
            changeLanguage(systemTag)
            assertEquals(inherited, capture(systemTag, "picker"))
            val opposite = if (Locale.forLanguageTag(systemTag).language == "ar") "en" else "ar"
            changeLanguage(opposite)
            assertEquals(inherited, capture(opposite, "switched"))
            changeLanguage("")
            assertEquals(inherited, capture(systemTag, "restored"))
        } finally { changeLanguage(previous) }
    }

    private fun changeLanguage(tag: String) {
        // Exercise the same trampoline used by the language picker in Settings.
        instrumentation.targetContext.startActivity(
            Intent(instrumentation.targetContext, LanguageUpdateActivity::class.java)
                .putExtra(LanguageUpdateActivity.EXTRA_LOCALE_TAG, tag)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        val deadline = SystemClock.uptimeMillis() + 10000
        do {
            var current = ""
            instrumentation.runOnMainSync {
                current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            }
            if (current == tag) return
            SystemClock.sleep(50)
        } while (SystemClock.uptimeMillis() < deadline)
        fail("LanguageUpdateActivity did not apply '$tag'")
    }

    private fun capture(tag: String, source: String): List<Float> {
        val locale=Locale.forLanguageTag(tag)
        val expectedDirection=if(locale.layoutDirection==1)LayoutDirection.Rtl else LayoutDirection.Ltr
        var direction:LayoutDirection?=null
        var grid by mutableStateOf(false)
        val geometry=mutableListOf<Float>()
        val notes=listOf(Note(id="one",content=NoteContent("First","Body")),Note(id="two",content=NoteContent("Second","Body")))
        ActivityScenario.launch(GuideActivity::class.java).use { scenario ->
            scenario.onActivity { activity ->
                assertEquals(locale.language,activity.resources.configuration.locales[0].language)
                activity.setContent {
                    val currentDirection=LocalLayoutDirection.current
                    SideEffect { direction=currentDirection }
                    MaterialTheme { Box(Modifier.fillMaxSize().padding(top=64.dp)) {
                        key(grid) {
                            NotesReorderGrid(notes,grid,true,{}, {}, {},Modifier.fillMaxWidth().height(380.dp),topPadding=80.dp,
                                guide=NotesGuide.DRAG) { note ->
                                Card(Modifier.fillMaxWidth().height(90.dp)) { Text(note.content.title,Modifier.padding(12.dp)) }
                            }
                        }
                    } }
                }
            }
            for (isGrid in listOf(false,true)) {
                compose.runOnIdle { grid=isGrid }
                compose.mainClock.advanceTimeBy(100)
                compose.waitForIdle()
                assertEquals(expectedDirection,direction)
                val card=compose.onNodeWithTag("note-one").fetchSemanticsNode().boundsInRoot
                val target=compose.onNodeWithTag("note-two").fetchSemanticsNode().boundsInRoot
                val hand=compose.onNodeWithTag("notes-guide-drag").fetchSemanticsNode().boundsInRoot
                assertEquals(card.center.x,hand.center.x,1f)
                assertEquals(card.center.y,hand.center.y,1f)
                assertTrue(hand.left>=card.left&&hand.right<=card.right&&hand.top>=card.top&&hand.bottom<=card.bottom)
                if(isGrid)assertEquals(expectedDirection==LayoutDirection.Rtl,target.center.x<card.center.x)
                captureImage("notes-hold-$tag-$source-$isGrid")
                compose.mainClock.advanceTimeBy(1500)
                val dragged=compose.onNodeWithTag("notes-guide-drag").fetchSemanticsNode().boundsInRoot
                assertEquals(target.center.x,dragged.center.x,2f)
                assertEquals(target.center.y,dragged.center.y,2f)
                captureImage("notes-drag-$tag-$source-$isGrid")
                geometry+=listOf(hand.width,hand.height,hand.center.x-card.center.x,hand.center.y-card.center.y)
            }
        }
        return geometry
    }

    private fun captureImage(name: String) {
        compose.waitForIdle()
        val bitmap = checkNotNull(instrumentation.uiAutomation.takeScreenshot())
        java.io.File(instrumentation.targetContext.getExternalFilesDir(null), "$name.png").outputStream().use {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        bitmap.recycle()
    }
}
