package com.jeerovan.comfer.notes

import android.content.res.Configuration
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.ui.theme.md_theme_dark_onSurface
import com.jeerovan.comfer.ui.theme.md_theme_light_onSurface
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*

/** Exercise the real activity's native theme as well as its Compose content after recreation. */
class NotesActivityThemeTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous:NotesLocalSnapshot
    private var scenario:ActivityScenario<NotesActivity>?=null

    @Before fun setup()=runBlocking {
        check(context.packageName.endsWith(".notificationtest"))
        NotesSession.lock()
        previous=NotesBackup.local(context)
        NotesStore(NotesDatabase.get(context)).replace(NotesSnapshot())
    }
    @After fun cleanup()=runBlocking {
        scenario?.close()
        NotesSession.lock()
        NotesBackup.restoreLocal(context,previous)
        Unit
    }
    @Test fun realActivityNativeTextAndComposeContentFollowNightMode() {
        scenario=ActivityScenario.launch(NotesActivity::class.java)
        for (dark in listOf(true,false)) {
            scenario!!.onActivity { it.delegate.localNightMode=if(dark)AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO }
            compose.waitUntil(10000) {
                var matched=false
                scenario!!.onActivity { matched=(it.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==if(dark)Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO }
                matched && compose.onAllNodesWithContentDescription("New note").fetchSemanticsNodes().isNotEmpty()
            }
            scenario!!.onActivity {
                val attributes=it.obtainStyledAttributes(intArrayOf(android.R.attr.textColorPrimary))
                val nativeText=Color(requireNotNull(attributes.getColorStateList(0)).defaultColor)
                attributes.recycle()
                assertEquals("Native selection menu theme must follow Notes",dark,nativeText.luminance()>.5f)
            }
            // This test emulator is API 24, below dynamic wallpaper-color support.
            if(android.os.Build.VERSION.SDK_INT<31) {
                val layouts=mutableListOf<TextLayoutResult>()
                compose.onNodeWithText("Notes").performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
                assertEquals(if(dark)md_theme_dark_onSurface else md_theme_light_onSurface,layouts.last().layoutInput.style.color)
            }
            compose.onNodeWithContentDescription("New note").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("notes-editor").performTextReplacement("Theme switch\nPreserved text")
            compose.onNodeWithTag("notes-editor").performSemanticsAction(SemanticsActions.SetSelection) { it(0,5,false) }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            android.os.SystemClock.sleep(350)
            val bitmap=requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            java.io.File(context.cacheDir,"notes-theme-native-$dark.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
            bitmap.recycle()
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            androidx.test.espresso.Espresso.pressBack()
        }
    }
}
