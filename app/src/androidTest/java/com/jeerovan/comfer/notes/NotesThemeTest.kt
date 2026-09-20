package com.jeerovan.comfer.notes

import android.app.Application
import android.graphics.Bitmap
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.ui.theme.ComferTheme
import com.jeerovan.comfer.R
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class NotesThemeTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val dark = mutableStateOf(true)
    private val custom = mutableStateOf(true)
    private val locked = mutableStateOf(false)
    private val viewModels = ViewModelStore()
    private lateinit var model: NotesViewModel
    private lateinit var previous: NotesLocalSnapshot
    private lateinit var scheme: ColorScheme

    @Before fun setup() {
        check(context.packageName.endsWith(".notificationtest"))
        NotesSession.lock()
        runBlocking {
            previous = NotesBackup.local(context)
            NotesStore(NotesDatabase.get(context)).replace(NotesSnapshot(
                notes = listOf(Note(id="theme", content=NoteContent("Theme note", "Searchable body"), pinned=true, tags=setOf("label"))),
                labels = listOf(NoteLabel(Note.INBOX,"Inbox"), NoteLabel(id="label", name="Theme label", tag=true)),
            ))
        }
        compose.runOnUiThread {
            model = ViewModelProvider(viewModels, ViewModelProvider.AndroidViewModelFactory(context.applicationContext as Application))[NotesViewModel::class.java]
            model.start()
        }
        compose.setContent {
            val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
            DisposableEffect(activity) {
                activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                onDispose { }
            }
            ComferTheme(darkTheme=dark.value, dynamicColor=false) {
                val colors = if (!custom.value) MaterialTheme.colorScheme else if (dark.value) darkColorScheme(
                    surface=Color(0xFF101820), onSurface=Color.Cyan, onSurfaceVariant=Color(0xFFAEDBE5),
                    secondaryContainer=Color(0xFF423000), onSecondaryContainer=Color.Yellow,
                    primary=Color(0xFFB5F1AA), outline=Color(0xFFACD8BF),
                ) else lightColorScheme(
                    surface=Color.White, onSurface=Color(0xFF650065), onSurfaceVariant=Color(0xFF604060),
                    secondaryContainer=Color(0xFFFFEDC2), onSecondaryContainer=Color(0xFF563600),
                    primary=Color(0xFF155918), outline=Color(0xFF326546),
                )
                SideEffect { scheme = colors }
                MaterialTheme(colorScheme=colors) { NotesScreen(model, {}, {}, locked.value) }
            }
        }
        compose.waitUntil(10000) { compose.onAllNodesWithTag("note-theme").fetchSemanticsNodes().isNotEmpty() }
    }

    @After fun cleanup() {
        compose.runOnUiThread { viewModels.clear() }
        runBlocking { NotesBackup.restoreLocal(context, previous) }
        NotesSession.lock()
    }

    private fun rendered(node: SemanticsNodeInteraction, expected: Color) {
        compose.waitForIdle()
        compose.waitUntil(5000) {
            val semantics = node.fetchSemanticsNode()
            val bounds = semantics.boundsInWindow.translate(semantics.positionOnScreen-semantics.positionInWindow)
            val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
            var count = 0
            for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
                for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                    val pixel = Color(bitmap.getPixel(x,y))
                    if (abs(pixel.red-expected.red)<.06f && abs(pixel.green-expected.green)<.06f && abs(pixel.blue-expected.blue)<.06f) count++
                }
            }
            bitmap.recycle()
            count > 4
        }
    }

    private fun capture(name: String) {
        // Compose can be idle while the emulator's native activity/window transition is still running.
        android.os.SystemClock.sleep(350)
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,3000)
        val bitmap = requireNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.cacheDir,"notes-theme-$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        bitmap.recycle()
    }

    private fun layout(node: SemanticsNodeInteraction): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        node.performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(results) }
        return results.last()
    }

    private fun theme(night: Boolean) {
        compose.runOnIdle { dark.value=night }
        compose.waitForIdle()
    }

    @Test fun collectionTextIconsCardsAndLabelsFollowLiveThemeChanges() {
        for (night in listOf(true,false,true)) {
            theme(night)
            rendered(compose.onNodeWithText("Notes"), scheme.onSurface)
            rendered(compose.onNodeWithContentDescription("New note"), scheme.onSurface)
            rendered(compose.onNodeWithContentDescription("Notes options"), scheme.onSurfaceVariant)
            rendered(compose.onNodeWithText("Theme note"), scheme.onSurface)
            rendered(compose.onNodeWithText("Theme label"), scheme.onSurfaceVariant)
            rendered(compose.onNodeWithContentDescription("Pinned"), scheme.onSurfaceVariant)
            capture("collection-$night")
            compose.runOnIdle { model.selected=setOf("theme") }
            rendered(compose.onNodeWithText("Theme note"), scheme.onSurface)
            rendered(compose.onNodeWithContentDescription("Pinned"), scheme.onSurfaceVariant)
            rendered(compose.onNodeWithContentDescription("Move selected to Bin"), scheme.onSurface)
            compose.runOnIdle { model.selected=emptySet() }
        }
    }

    @Test fun sheetsLabelsDisabledControlsAndDialogsFollowTheme() {
        for (night in listOf(true,false)) {
            theme(night)
            compose.runOnIdle { model.selected=setOf("theme") }
            compose.onNodeWithContentDescription("Manage labels").performClick()
            rendered(compose.onNodeWithText("Labels"), scheme.onSurface)
            rendered(compose.onNodeWithContentDescription("Select label Theme label"), scheme.onSurface)
            compose.onNodeWithContentDescription("Add label").performClick()
            compose.onNodeWithContentDescription("Save name").assertIsNotEnabled()
            capture("label-edit-$night")
            androidx.test.espresso.Espresso.pressBack()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription("Note color").performClick()
            rendered(compose.onNodeWithText("Note background"), scheme.onSurface)
            for ((_, name) in noteColors) rendered(compose.onNodeWithText(context.getString(name)), scheme.onSurface)
            capture("palette-$night")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithContentDescription("Notes options").performClick()
            rendered(compose.onNodeWithText("Sort"), scheme.onSurface)
            rendered(compose.onNodeWithText("Protect Notes"), scheme.onSurface)
            capture("options-$night")
            androidx.test.espresso.Espresso.pressBack()
            compose.runOnIdle { model.selected=emptySet(); model.error="Theme error" }
            rendered(compose.onNodeWithText(context.getString(R.string.module_could_not_complete_this_action_please_try_again)), scheme.error)
            compose.onNodeWithContentDescription("Dismiss error").performClick()
        }
    }

    @Test fun searchHighlightsUsePairedThemeColorsAndBinRemainsReadable() {
        for (night in listOf(true,false)) {
            theme(night)
            compose.onNodeWithContentDescription("Search notes").performClick()
            compose.onNodeWithTag("notes-search").performTextReplacement("Searchable")
            compose.mainClock.advanceTimeBy(400)
            compose.waitUntil(5000) {
                layout(compose.onNodeWithText("Searchable body",useUnmergedTree=true)).layoutInput.text.spanStyles.isNotEmpty()
            }
            val span=layout(compose.onNodeWithText("Searchable body",useUnmergedTree=true)).layoutInput.text.spanStyles.single().item
            assertEquals(scheme.tertiaryContainer,span.background)
            assertEquals(scheme.onTertiaryContainer,span.color)
            compose.onNodeWithContentDescription("Close search").performClick()
            compose.runOnIdle { model.switchView("Bin") }
            compose.waitUntil(5000) { compose.onAllNodesWithText("Bin is empty").fetchSemanticsNodes().isNotEmpty() }
            rendered(compose.onNodeWithText("Bin"),scheme.onSurface)
            rendered(compose.onNodeWithText("Bin is empty"),scheme.onSurfaceVariant)
            capture("bin-$night")
            compose.runOnIdle { model.switchView("Notes") }
        }
    }

    @Test fun editorDefaultTextToolbarAndFormattingFollowThemeWithoutChangingContent() {
        compose.onNodeWithTag("note-theme").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
        for (night in listOf(true,false)) {
            theme(night)
            assertEquals(scheme.onSurface,layout(compose.onNodeWithTag("notes-editor")).layoutInput.style.color)
            compose.onNodeWithContentDescription("All notes").assertDoesNotExist()
            rendered(compose.onNodeWithContentDescription("Undo edit"),scheme.onSurface)
            compose.onNodeWithContentDescription("Text formatting").performScrollTo()
            rendered(compose.onNodeWithContentDescription("Text formatting"),scheme.onSurface)
            compose.onNodeWithContentDescription("Text formatting").performClick()
            rendered(compose.onNodeWithContentDescription("Bold"),scheme.onSurface)
            rendered(compose.onNodeWithContentDescription(context.getString(R.string.module_text_color_1_s, context.getString(R.string.module_default))),scheme.onSurface)
            capture("formatting-$night")
            androidx.test.espresso.Espresso.pressBack()
        }
        assertEquals("Theme note",model.title)
        assertEquals("Searchable body",model.body.text)
        assertTrue(model.marks.isEmpty())
    }

    @Test fun lockedSurfaceAndEmptyEditorFollowTheme() {
        for (night in listOf(true,false)) {
            theme(night)
            compose.runOnIdle { locked.value=true }
            rendered(compose.onNodeWithText("Notes is locked"),scheme.onSurface)
            rendered(compose.onNodeWithText("Unlock"),scheme.onPrimary)
            compose.runOnIdle { locked.value=false }
        }
        compose.onNodeWithContentDescription("New note").performClick()
        rendered(compose.onNodeWithText("Title"),scheme.onSurfaceVariant)
    }

    @Test fun namedTextColorsStayReadableAndRecomposeWithoutRewritingMarks() {
        compose.runOnIdle { custom.value=false }
        compose.onNodeWithTag("note-theme").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
        val names=listOf("red","orange","green","blue","purple")
        val text="Colors\n"+names.joinToString(" ")+" link\n[ ] Check me"
        compose.onNodeWithTag("notes-editor").performTextReplacement(text)
        for (name in names) {
            val start=text.indexOf(name)
            compose.onNodeWithTag("notes-editor").performSemanticsAction(SemanticsActions.SetSelection) { it(start,start+name.length,false) }
            compose.runOnIdle { model.format("color",name) }
        }
        compose.runOnIdle { model.link(text.indexOf("link"),text.indexOf("link")+4,"https://example.com") }
        val original=model.marks.toList()
        val renderedColors=mutableListOf<List<Color>>()
        for (night in listOf(true,false,true)) {
            theme(night)
            val layout=layout(compose.onNodeWithTag("notes-editor"))
            val colors=names.map { name -> layout.layoutInput.text.spanStyles.single { it.start==text.indexOf(name)&&it.item.color!=Color.Unspecified }.item.color }
            renderedColors+=colors
            // Notes keeps wallpaper transparency: require readable text even over either wallpaper extreme.
            for (color in colors) for (wallpaper in listOf(Color.White,Color.Black)) {
                val background=scheme.surface.copy(alpha=.8f).compositeOver(wallpaper)
                val contrast=(max(color.luminance(),background.luminance())+.05f)/(min(color.luminance(),background.luminance())+.05f)
                assertTrue("$night: $color contrast $contrast",contrast>=4.5f)
            }
            assertTrue(layout.layoutInput.text.spanStyles.any { it.start==text.indexOf("link")&&it.item.color==scheme.primary })
            assertTrue(layout.layoutInput.text.text.contains("○   Check me"))
            assertEquals(original,model.marks)
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            capture("rich-editor-$night")
            compose.onNodeWithContentDescription("Text formatting").performScrollTo().performClick()
            names.forEachIndexed { index, name -> rendered(compose.onNodeWithContentDescription(context.getString(R.string.module_text_color_1_s, name.replaceFirstChar { it.uppercaseChar() })),colors[index]) }
            compose.onNodeWithContentDescription("URL").performClick()
            compose.onNode(hasSetTextAction() and hasText("URL")).performTextReplacement("javascript:alert(1)")
            compose.onNodeWithContentDescription("Save link").performClick()
            rendered(compose.onNodeWithText("Enter a valid http or https URL"),scheme.error)
            capture("invalid-link-$night")
            compose.onNodeWithContentDescription("Cancel link").performClick()
        }
        assertNotEquals(renderedColors[0],renderedColors[1])
        assertEquals(renderedColors[0],renderedColors[2])
        assertEquals(text,model.title+"\n"+model.body.text)
        assertEquals(original,model.marks)
    }

    @Test fun tintedCardsBinPreviewAndDeleteDialogUseThemePairs() {
        for (night in listOf(true,false)) {
            theme(night)
            for ((id,_) in noteColors) {
                compose.runOnIdle { model.batch(model.notes) { it.copy(color=id) } }
                compose.waitUntil(5000) { model.notes.single().color==id }
                rendered(compose.onNodeWithText("Theme note"),scheme.onSurface)
                rendered(compose.onNodeWithText("Searchable body"),scheme.onSurface)
                rendered(compose.onNodeWithText("Theme label"),scheme.onSurfaceVariant)
            }
            compose.runOnIdle { model.batch(model.notes) { it.copy(deletedAt=System.currentTimeMillis()) } }
            compose.waitUntil(5000) { model.notes.single().deletedAt!=null }
            compose.runOnIdle { model.switchView("Bin") }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("note-theme").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("note-theme").performClick()
            // The collection remains under the dialog; target its text in the dialog root.
            rendered(compose.onNode(hasText("Theme note") and hasAnyAncestor(isDialog())),scheme.onSurface)
            rendered(compose.onNodeWithContentDescription("Close"),scheme.primary)
            capture("bin-preview-$night")
            compose.onNodeWithContentDescription("Close").performClick()
            compose.onNodeWithText("Empty").performClick()
            rendered(compose.onNodeWithText("Permanently delete 1 notes?"),scheme.onSurface)
            rendered(compose.onNodeWithContentDescription("Cancel"),scheme.primary)
            capture("delete-dialog-$night")
            compose.onNodeWithContentDescription("Cancel").performClick()
            compose.runOnIdle { model.batch(model.notes) { it.copy(deletedAt=null) }; model.switchView("Notes") }
            compose.waitUntil(5000) { model.notes.single().deletedAt==null }
        }
    }

    @Test fun productionThemeCollectionAndImagePreviewAreReadable() {
        compose.runOnIdle { custom.value=false }
        for (night in listOf(true,false)) {
            theme(night)
            rendered(compose.onNodeWithText("Notes"),scheme.onSurface)
            capture("production-collection-$night")
        }
        compose.onNodeWithTag("note-theme").performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty() }
        val source=File(context.cacheDir,"notes-theme-image.png")
        val bitmap=Bitmap.createBitmap(160,80,Bitmap.Config.ARGB_8888).apply { eraseColor(android.graphics.Color.BLUE) }
        source.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
        try {
            compose.runOnIdle { model.queueImage(android.net.Uri.fromFile(source)) }
            compose.waitUntil(10000) { model.images.size==1 }
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            for (night in listOf(true,false)) {
                theme(night)
                compose.onNodeWithContentDescription("Note image").performScrollTo().performClick()
                rendered(compose.onNodeWithContentDescription("Remove image"),scheme.primary)
                rendered(compose.onNodeWithContentDescription("Close image"),scheme.primary)
                capture("image-preview-$night")
                compose.onNodeWithContentDescription("Close image").performClick()
            }
            assertEquals(1,model.images.size)
        } finally { source.delete() }
    }
}
