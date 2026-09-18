package com.jeerovan.comfer.notes

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.semantics.SemanticsActions
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.BackupRestoreManager
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import java.io.File

class NotesEditorTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val context get()=InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var previous:NotesLocalSnapshot
    private var activity:ActivityScenario<NotesActivity>?=null
    @Before fun before()=runBlocking {
        check(context.packageName.endsWith(".notificationtest"));NotesSession.lock()
        previous=NotesBackup.local(context)
        NotesStore(NotesDatabase.get(context)).replace(NotesSnapshot(notes=listOf(Note(id="editor",content=NoteContent("Title","Alpha beta")))))
    }
    @After fun after()=runBlocking {activity?.close();NotesSession.lock();NotesBackup.restoreLocal(context,previous);Unit}
    private fun launch() {
        activity=ActivityScenario.launch(NotesActivity::class.java)
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("note-editor").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
    }
    private fun saved()=runBlocking{NotesBackup.snapshot(context).notes.single{it.id=="editor"}.content}
    private fun select(start:Int,end:Int)=compose.onNodeWithTag("notes-editor").performSemanticsAction(SemanticsActions.SetSelection){it(start,end,false)}
    private fun capture(name:String) {
        InstrumentationRegistry.getInstrumentation().uiAutomation.waitForIdle(100,3000)
        val bitmap=InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
        File(context.cacheDir,"notes-editor-$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
    }
    private fun format()=compose.onNodeWithContentDescription("Text formatting").performScrollTo().performClick()
    @Test fun toolbarScrollKeepsBackFixedAndHasNoOptionsMenu() {
        launch()
        compose.onNodeWithContentDescription("Notes options").assertDoesNotExist()
        val back=compose.onNodeWithContentDescription("All notes").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Add image").performScrollTo().assertIsDisplayed()
        capture("toolbar")
        val after=compose.onNodeWithContentDescription("All notes").fetchSemanticsNode().boundsInRoot
        assertEquals(back.left,after.left,0f);assertEquals(back.right,after.right,0f)
        compose.onNodeWithContentDescription("Numbered list").assertExists()
        compose.onNodeWithContentDescription("Manage labels").performScrollTo().performClick()
        compose.onNodeWithText("Labels",substring=false).assertExists()
    }
    @Test fun selectedFormattingSurvivesSaveReopenAndUndo() {
        launch();select(6,11);format()
        listOf("Bold","Italic","Underline","Strikethrough","Text color blue").forEach{compose.onNodeWithContentDescription(it).performClick()}
        compose.onNodeWithText("Heading",substring=false).performClick()
        capture("formatting")
        androidx.test.espresso.Espresso.pressBack()
        compose.waitUntil(10000){saved().marks.size==6}
        val content=saved()
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("notes-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue("Bold must render immediately, not only persist",layouts.last().layoutInput.text.spanStyles.any{it.start==6&&it.end==11&&it.item.fontWeight==androidx.compose.ui.text.font.FontWeight.Bold})
        assertTrue("Text color must render immediately",layouts.last().layoutInput.text.spanStyles.any{it.start==6&&it.end==11&&it.item.color==noteTextColor("blue")})
        capture("formatted-text")
        assertTrue(content.marks.any{it.kind=="bold"&&it.start==6&&it.end==11})
        assertTrue(content.marks.any{it.kind=="paragraph"&&it.value=="heading"&&it.end==16})
        compose.onNodeWithContentDescription("Undo edit").performScrollTo().performClick()
        compose.waitUntil(10000){saved().marks.none{it.kind=="paragraph"}}
        compose.onNodeWithContentDescription("Redo edit").performClick()
        compose.waitUntil(10000){saved().marks==content.marks}
        compose.onNodeWithContentDescription("All notes").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("note-editor").fetchSemanticsNodes().isNotEmpty()}
        compose.onNodeWithTag("note-editor").performClick()
        compose.waitUntil(10000){compose.onAllNodesWithTag("notes-editor").fetchSemanticsNodes().isNotEmpty()}
        assertEquals(content,saved())
    }
    @Test fun linkRejectsUnsafeSchemeAndPersistsValidUrl() {
        launch();select(6,11);format();compose.onNodeWithContentDescription("URL").performClick()
        compose.onNode(hasSetTextAction() and hasText("URL")).performTextInput("javascript:alert(1)")
        compose.onNodeWithContentDescription("Save link").performClick()
        compose.onNodeWithText("Enter a valid http or https URL").assertExists()
        compose.onNode(hasSetTextAction() and hasText("URL")).performTextReplacement("https://example.com")
        compose.onNodeWithContentDescription("Save link").performClick()
        compose.waitUntil(10000){saved().marks.any{it.kind=="url"&&it.value=="https://example.com"}}
        assertEquals("Alpha beta",saved().text)
        compose.onNodeWithContentDescription("Undo edit").performScrollTo().performClick()
        compose.waitUntil(10000){saved().marks.none{it.kind=="url"}}
    }
    @Test fun circularChecklistAndNumberingCoexistWithProse() {
        launch();select(6,6)
        compose.onNodeWithContentDescription("Insert checklist").performScrollTo().performClick()
        compose.waitUntil(10000){saved().text=="[ ] Alpha beta"}
        val layouts=mutableListOf<androidx.compose.ui.text.TextLayoutResult>()
        compose.onNodeWithTag("notes-editor").performSemanticsAction(SemanticsActions.GetTextLayoutResult){it(layouts)}
        assertTrue(layouts.last().layoutInput.text.text.contains("○   Alpha"))
        val box=layouts.last().getBoundingBox(6)
        compose.onNodeWithTag("notes-editor").performTouchInput{click(box.center)}
        compose.waitUntil(10000){saved().text=="[x] Alpha beta"}
        compose.onNodeWithTag("notes-editor").performTextReplacement("Title\nOne\nTwo")
        select(6,13)
        compose.onNodeWithContentDescription("Numbered list").performScrollTo().performClick()
        compose.waitUntil(10000){saved().text=="1. One\n2. Two"}
    }
    @Test fun collapsedFormattingAppliesToNextTypedText() {
        launch();select(16,16);format()
        compose.onNodeWithContentDescription("Bold").performClick()
        androidx.test.espresso.Espresso.pressBack()
        compose.onNodeWithTag("notes-editor").performTextInput(" bold")
        compose.waitUntil(10000){saved().text.endsWith(" bold")&&saved().marks.any{it.kind=="bold"&&it.start==16&&it.end==21}}
    }
    @Test fun corruptImageLeavesSavedNoteIntactAndImportRecovers() {
        launch()
        val source=File(context.cacheDir,"notes-broken-image.png").apply{writeText("not an image")}
        try {
            val before=saved()
            activity!!.onActivity{ViewModelProvider(it)[NotesViewModel::class.java].queueImage(Uri.fromFile(source))}
            compose.waitUntil(10000){compose.onAllNodesWithText("Image is damaged or too large").fetchSemanticsNodes().isNotEmpty()}
            assertEquals(before,saved())
            compose.onNodeWithContentDescription("Add image").performScrollTo().assertIsEnabled()
        }finally{source.delete()}
    }
    @Test fun importedImageIsCopiedEncryptedAndIncludedInRealBackup() {
        launch();select(6,11);format();compose.onNodeWithContentDescription("Bold").performClick();androidx.test.espresso.Espresso.pressBack()
        val source=File(context.cacheDir,"notes-source.png")
        val archive=File(context.cacheDir,"notes-rich-backup.zip")
        val bitmap=Bitmap.createBitmap(200,100,Bitmap.Config.ARGB_8888).apply{eraseColor(android.graphics.Color.BLUE)}
        source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        try {
            activity!!.onActivity{ViewModelProvider(it)[NotesViewModel::class.java].queueImage(Uri.fromFile(source))}
            compose.waitUntil(15000){saved().images.size==1}
            source.delete()
            compose.onNodeWithTag("notes-editor-after-image-1").performScrollTo().performClick().performTextInput("Below the image")
            compose.waitUntil(10000){saved().text.endsWith("Below the image")}
            compose.onNodeWithTag("notes-editor-after-image-1").performSemanticsAction(SemanticsActions.SetSelection){it(0,5,false)}
            format();compose.onNodeWithContentDescription("Italic").performClick();androidx.test.espresso.Espresso.pressBack()
            compose.waitUntil(10000){saved().marks.any{it.kind=="italic"&&it.start==saved().images.single().offset}}
            compose.onNodeWithContentDescription("All notes").performClick()
            compose.onNodeWithTag("note-editor").performClick()
            compose.onNodeWithTag("notes-editor-after-image-1").performScrollTo().assertTextContains("Below the image")
            val rich=saved();assertNotNull(NotesImages.decode(rich.images.single()))
            androidx.test.espresso.Espresso.closeSoftKeyboard()
            capture("image")
            activity!!.onActivity{ViewModelProvider(it)[NotesViewModel::class.java].removeImage(rich.images.single().id)}
            compose.waitUntil(10000){saved().images.isEmpty()}
            compose.onNodeWithContentDescription("Undo edit").performScrollTo().performClick()
            compose.waitUntil(10000){saved()==rich}
            val row=runBlocking{NotesDatabase.get(context).dao().note("editor")!!}
            assertFalse(row.payload.toString(Charsets.UTF_8).contains(rich.images.single().jpeg.take(30)))
            runBlocking {
                BackupRestoreManager.createBackup(context,Uri.fromFile(archive),"en")
                val store=NotesStore(NotesDatabase.get(context));val old=store.note("editor")!!
                store.mutate(old.id,old.revision){it.copy(content=NoteContent("Changed","after backup"))}
                BackupRestoreManager.restoreBackup(context,Uri.fromFile(archive))
                assertTrue(NotesBackup.snapshot(context).notes.any{it.content==rich})
            }
        } finally {source.delete();archive.delete()}
    }
    @Test fun olderImagesGainWritingAreasAndTypingBetweenImagesKeepsTheirOrder() {
        val source=File(context.cacheDir,"notes-legacy-image.png")
        val bitmap=Bitmap.createBitmap(100,50,Bitmap.Config.ARGB_8888)
        source.outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
        try {
            val image=NotesImages.read(context,Uri.fromFile(source))
            runBlocking {
                val store=NotesStore(NotesDatabase.get(context));val note=store.note("editor")!!
                store.mutate(note.id,note.revision){it.copy(content=it.content.copy(images=listOf(image,image.copy(id="second-image"))))}
            }
            launch()
            compose.onNodeWithTag("notes-editor-after-image-1").performScrollTo().performClick().performTextInput("Between")
            compose.onNodeWithTag("notes-editor-after-image-2").performScrollTo().performClick().performTextInput("After")
            compose.waitUntil(10000){saved().text.endsWith("After")}
            val content=saved();val canvas=NoteFormatting.canvas(content)
            assertEquals("Between\n\n",canvas.substring(content.images[0].offset!!,content.images[1].offset!!))
            assertEquals("After",canvas.substring(content.images[1].offset!!))
            compose.onNodeWithTag("notes-canvas").performScrollToIndex(0)
            compose.onNodeWithTag("notes-editor").performTextReplacement("")
            compose.waitUntil(10000){saved().title.isEmpty()}
            assertTrue(saved().text.contains("Between"));assertTrue(saved().text.endsWith("After"))
        }finally{source.delete()}
    }
}
