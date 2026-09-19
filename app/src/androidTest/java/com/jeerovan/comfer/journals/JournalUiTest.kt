package com.jeerovan.comfer.journals

import android.app.Application
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*

class JournalUiTest {
    @get:Rule val compose = createComposeRule()
    private val themeMode = androidx.compose.runtime.mutableIntStateOf(0)
    private val rtlLarge = androidx.compose.runtime.mutableStateOf(false)
    private lateinit var model: JournalViewModel
    private lateinit var previous: JournalSnapshot
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    @Before fun setup() {
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        check(context.packageName.endsWith(".notificationtest"))
        runBlocking {
            previous = JournalBackup.snapshot(context)
            JournalBackup.replace(context, JournalSnapshot(entries = emptyList(), drafts = emptyList(), media = emptyList()))
        }
        compose.runOnUiThread { model = JournalViewModel(context.applicationContext as Application) }
        compose.setContent {
            val activity = androidx.compose.ui.platform.LocalContext.current as android.app.Activity
            androidx.compose.runtime.DisposableEffect(activity) {
                activity.window.setSoftInputMode(android.view.WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
                onDispose { }
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            androidx.compose.runtime.CompositionLocalProvider(
                androidx.compose.ui.platform.LocalLayoutDirection provides if(rtlLarge.value) androidx.compose.ui.unit.LayoutDirection.Rtl else androidx.compose.ui.unit.LayoutDirection.Ltr,
                androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(density.density, if(rtlLarge.value) 1.5f else density.fontScale),
            ) { MaterialTheme(colorScheme = when (themeMode.intValue) {
                1 -> darkColorScheme(surface = Color.Black, onSurface = Color.Cyan, primary = Color.Yellow)
                2 -> lightColorScheme(surface = Color.White, onSurface = Color.Magenta, primary = Color.Blue)
                else -> lightColorScheme()
            }) { JournalScreen(model, {}) } }
        }
        compose.waitUntil(10000) { model.draft.value != null }
    }
    @After fun cleanup() { compose.runOnUiThread { model.speech.interrupt() }; runBlocking { JournalBackup.replace(context, previous) } }
    @Test fun headerOptionsAndComposerActionsStayOutsideTheInput() {
        val title=compose.onNodeWithText("Journal",substring=false).fetchSemanticsNode().boundsInRoot
        val options=compose.onNodeWithContentDescription("Journal options").fetchSemanticsNode().boundsInRoot
        val search=compose.onNodeWithTag("journal-search-toggle").fetchSemanticsNode().boundsInRoot
        val input=compose.onNodeWithTag("journal-composer").fetchSemanticsNode().boundsInRoot
        val image=compose.onNodeWithContentDescription("Add image").fetchSemanticsNode().boundsInRoot
        val mic=compose.onNodeWithTag("journal-submit").fetchSemanticsNode().boundsInRoot
        assertEquals(title.center.y,options.center.y,1f)
        assertTrue(options.bottom<input.top)
        assertTrue(search.right<=input.left)
        assertTrue(image.left>=input.left && image.right<=input.right)
        assertTrue(input.right<=mic.left)
        compose.onNodeWithTag("journal-search-toggle").performClick()
        compose.onNodeWithTag("journal-composer").assertIsFocused()
        compose.onNodeWithTag("journal-submit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add image").assertDoesNotExist()
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithTag("journal-submit").assertIsDisplayed()
        compose.onNodeWithContentDescription("Add image").assertIsDisplayed()
    }
    @Test fun searchPreservesDraftAndSupportsEntryActionsAcrossDates() {
        val yesterday = java.time.LocalDate.now().minusDays(1).toEpochDay()
        val entry = JournalEntry(day = yesterday, text = "Searchable memory")
        runBlocking { model.store.dao.insert(entry) }
        compose.onNodeWithTag("journal-composer").performTextInput("Unfinished writing")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithTag("journal-search-toggle").performClick()
        compose.onNodeWithText("Search Journal").assertIsDisplayed()
        compose.onNodeWithTag("journal-submit").assertDoesNotExist()
        compose.onNodeWithContentDescription("Add image").assertDoesNotExist()
        compose.onNodeWithTag("journal-date-row").assertDoesNotExist()
        compose.onNodeWithTag("journal-composer").performTextInput("searchable")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Searchable memory").fetchSemanticsNodes().isNotEmpty() }
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithText("Searchable memory").performClick()
        compose.waitUntil(10000) { model.editing.value != null }
        compose.onNodeWithTag("journal-edit").performTextReplacement("Searchable edited")
        androidx.test.espresso.Espresso.closeSoftKeyboard()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { model.editing.value == null }
        compose.onNodeWithText("Searchable edited").assertExists()
        compose.onNodeWithTag("journal-time-${entry.id}").performClick()
        compose.onNodeWithTag("journal-date-dialog").assertIsDisplayed()
        compose.onNode(isSelected() and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("journal-time-dialog").assertIsDisplayed()
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { model.editing.value != null }
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { model.editing.value == null }
        compose.onNodeWithText("Searchable edited").performTouchInput { swipeLeft() }
        compose.waitUntil(10000) { runBlocking { model.store.dao.entry(entry.id)?.deletedAt != null } }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Searchable edited").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Close search").performClick()
        compose.onNodeWithTag("journal-composer").assertTextContains("Unfinished writing")
        assertEquals("Unfinished writing", model.draft.value?.text)
        compose.onNodeWithTag("journal-submit").assertIsDisplayed()
    }

    @Test fun searchDebouncesAndClearingQueryDoesNotShowAllEntries() {
        runBlocking { model.store.dao.insert(JournalEntry(text = "Debounce target")) }
        compose.onNodeWithTag("journal-search-toggle").performClick()
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithTag("journal-composer").performTextInput("Debounce")
        compose.mainClock.advanceTimeBy(100)
        compose.onNodeWithText("Debounce target").assertDoesNotExist()
        compose.onNodeWithTag("journal-composer").performTextReplacement("missing")
        compose.mainClock.advanceTimeBy(350)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithText("Debounce target").assertDoesNotExist()
        compose.onNodeWithTag("journal-composer").performTextReplacement("Debounce")
        compose.waitUntil(10000) { compose.onAllNodesWithText("Debounce target").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("journal-composer").performTextClearance()
        compose.waitUntil(10000) { compose.onAllNodesWithText("Debounce target").fetchSemanticsNodes().isEmpty() }
        compose.onNodeWithText("Search your journal entries").assertIsDisplayed()
    }

    @Test fun searchMatchesLiteralTextExcludesArchiveAndPagesAcrossAllDates() = runBlocking {
        val today = java.time.LocalDate.now().toEpochDay()
        for (index in 0..54) model.store.dao.insert(JournalEntry(id = "search-$index", day = today + index - 25, text = "Memory 100%_$index"))
        model.store.dao.insert(JournalEntry(id = "archived-search", text = "Memory 100%_archived", deletedAt = System.currentTimeMillis()))
        assertEquals(50, model.store.dao.search("MEMORY", 50).first().size)
        assertEquals(5, model.store.dao.search("memory", 50, 50).first().size)
        assertEquals(55, model.store.dao.search("100%_", 100).first().size)
        assertEquals(0, model.store.dao.search("not found", 100).first().size)
        assertTrue(model.store.dao.search("Memory", 100).first().any { it.day > today })
        assertFalse(model.store.dao.search("Memory", 100).first().any { it.id == "archived-search" })
    }

    @Test fun saveEditCancelAndDeleteUndoPreserveText() {
        compose.onNodeWithTag("journal-composer").performTextInput("A synthetic journal")
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        compose.waitForIdle()
        InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()?.let { bitmap ->
            java.io.File(context.filesDir, "journal-ui.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        compose.onNodeWithText("A synthetic journal").performClick()
        compose.waitUntil(10000) { model.editDraft.value != null }
        compose.onNodeWithTag("journal-edit").performTextReplacement("Changed")
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.waitUntil(10000) { model.editing.value == null }
        compose.onNodeWithText("A synthetic journal").assertExists()
        compose.onNodeWithText("Delete").assertDoesNotExist()
        compose.onNodeWithText("A synthetic journal").performTouchInput { swipeLeft() }
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt != null } }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt == null } }
        assertEquals("A synthetic journal", runBlocking { model.store.dao.exportEntries().single().text })
    }
    @Test fun bottomDateSwipeAndOptionsMenu() {
        val date = compose.onNodeWithTag("journal-date")
        val before = date.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        val row = compose.onNodeWithTag("journal-date-row")
        assertTrue(row.fetchSemanticsNode().boundsInRoot.top > compose.onNodeWithText("Journal").fetchSemanticsNode().boundsInRoot.bottom)
        row.performTouchInput { swipeLeft() }
        compose.waitForIdle()
        val after = date.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString()
        assertNotEquals(before, after)
        row.performTouchInput { swipeRight() }
        compose.waitForIdle()
        assertEquals(before, date.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString())
        compose.onNodeWithContentDescription("Journal options").performClick()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Archive").assertIsDisplayed()
        compose.onNodeWithText("Deleted entries stay in Archive for 7 days.", substring = true).assertExists()
    }

    @Test fun imagePreviewZoomPanAndRemovalCanBeCancelled() {
        val id = "${java.util.UUID.randomUUID()}.jpg"
        val bitmap = android.graphics.Bitmap.createBitmap(800, 600, android.graphics.Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.BLUE)
        model.media.file(id).outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 90, it) }
        bitmap.recycle()
        compose.runOnIdle { model.change(text = "Image entry", image = id, changeImage = true) }
        compose.waitUntil(10000) { model.draft.value?.image == id }
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        compose.waitUntil(10000) { compose.onAllNodesWithContentDescription("Journal image").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Journal image").performClick()
        compose.onNodeWithTag("journal-image-preview").assertIsDisplayed()
        try { compose.waitUntil(10000) { compose.onAllNodesWithTag("journal-zoom-image").fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) { println(compose.onRoot().printToString()); throw failure }
        val image = compose.onNodeWithTag("journal-zoom-image")
        val actions = compose.onNodeWithTag("journal-image-actions").fetchSemanticsNode().boundsInRoot
        assertTrue("Actions must be below the image", actions.top >= image.fetchSemanticsNode().boundsInRoot.bottom - 1f)
        compose.onNodeWithContentDescription("Cancel").assertIsDisplayed()
        image.performTouchInput { doubleClick() }
        image.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "100%"))
        image.performTouchInput {
            down(0, center.copy(x = center.x - 40f)); down(1, center.copy(x = center.x + 40f))
            moveTo(0, center.copy(x = center.x - 140f)); moveTo(1, center.copy(x = center.x + 140f))
            up(0); up(1)
        }
        val zoom = image.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].removeSuffix("%").toInt()
        assertTrue("Pinch must zoom the image", zoom > 100)
        image.performTouchInput { swipeLeft() }
        image.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "${zoom}%"))
        image.performTouchInput { doubleClick() }
        image.assert(SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "${zoom}%"))
        image.performTouchInput {
            down(0, center.copy(x = center.x - 140f)); down(1, center.copy(x = center.x + 140f))
            moveTo(0, center.copy(x = center.x - 40f)); moveTo(1, center.copy(x = center.x + 40f))
            up(0); up(1)
        }
        val reduced = image.fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.StateDescription].removeSuffix("%").toInt()
        assertTrue("Pinch inward must reduce zoom", reduced < zoom)

        compose.onNodeWithText("Change").assertIsDisplayed()
        compose.onNodeWithContentDescription("Delete image").performClick()
        compose.onNodeWithTag("journal-image-preview").assertDoesNotExist()
        compose.waitUntil(10000) { model.editing.value != null && model.editDraft.value?.image == null }
        assertEquals(id, runBlocking { model.store.dao.exportEntries().single().image })
        compose.onNodeWithContentDescription("Cancel").performClick()
        compose.waitUntil(10000) { model.editing.value == null }
        assertEquals(id, runBlocking { model.store.dao.exportEntries().single().image })
    }

    @Test fun editedEntryCanBeSwipedWithoutStaleRevisionError() {
        compose.onNodeWithTag("journal-composer").performTextInput("Before edit")
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        compose.onNodeWithText("Before edit").performClick()
        compose.waitUntil(10000) { model.editDraft.value != null }
        compose.onNodeWithTag("journal-edit").performTextReplacement("After edit")
        compose.onNodeWithContentDescription("Save").performClick()
        compose.waitUntil(10000) { model.editing.value == null }
        compose.onNodeWithText("After edit").performTouchInput { swipeLeft() }
        compose.waitUntil(10000) { model.error.value != null || runBlocking { model.store.dao.exportEntries().single().deletedAt != null } }
        assertNull("Normal swipe after editing must not report a stale revision", model.error.value)
        assertNotNull(runBlocking { model.store.dao.exportEntries().single().deletedAt })
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt == null } }
        assertEquals("After edit", runBlocking { model.store.dao.exportEntries().single().text })
    }

    @Test fun archiveReturnStartsAtLatestWithChronologicalVisualOrder() {
        runBlocking {
            repeat(40) { model.store.dao.insert(JournalEntry(id = "archive-return-$it", text = "Return entry $it", createdAt = System.currentTimeMillis() - 40000 + it * 1000)) }
            model.store.dao.insert(JournalEntry(text = "Archived fixture", deletedAt = System.currentTimeMillis()))
        }
        try { compose.waitUntil(10000) { compose.onAllNodesWithText("Return entry 39").fetchSemanticsNodes().isNotEmpty() } }
        catch (failure: Throwable) { println(compose.onRoot().printToString()); throw failure }
        compose.onNodeWithTag("journal-feed").performScrollToNode(hasText("Return entry 0"))
        compose.onNodeWithContentDescription("Journal options").performClick()
        compose.onNodeWithText("Archive").performClick()
        compose.onNodeWithText("Archived fixture").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()
        compose.waitForIdle()
        compose.onNodeWithText("Return entry 39").assertIsDisplayed()
        compose.onNodeWithText("Return entry 0").assertDoesNotExist()
        val older = compose.onNodeWithText("Return entry 38").fetchSemanticsNode().boundsInRoot
        val newest = compose.onNodeWithText("Return entry 39").fetchSemanticsNode().boundsInRoot
        assertTrue("Older entries remain above newer entries", older.bottom < newest.top)
        val bottom = compose.onNodeWithTag("journal-feed").fetchSemanticsNode().boundsInRoot.bottom
        assertTrue("Newest entry stays near the bottom", newest.bottom > bottom - 150f)
    }

    @Test fun rootTextAndIconsFollowThemeAndDatePickerIsThemed() {
        fun assertRenderedColor(node: SemanticsNodeInteraction, expected: Color) {
            compose.waitForIdle()
            // Compose idleness can precede the emulator compositor's displayed frame.
            compose.waitUntil(5000) {
                val bounds = node.fetchSemanticsNode().boundsInWindow
                val bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return@waitUntil false
                var matching = 0
                for (y in bounds.top.toInt().coerceAtLeast(0) until bounds.bottom.toInt().coerceAtMost(bitmap.height)) {
                    for (x in bounds.left.toInt().coerceAtLeast(0) until bounds.right.toInt().coerceAtMost(bitmap.width)) {
                        val pixel = Color(bitmap.getPixel(x, y))
                        if (kotlin.math.abs(pixel.red - expected.red) < .1f && kotlin.math.abs(pixel.green - expected.green) < .1f && kotlin.math.abs(pixel.blue - expected.blue) < .1f) matching++
                    }
                }
                bitmap.recycle()
                matching > 5
            }
        }
        compose.runOnIdle { themeMode.intValue = 1 }
        assertRenderedColor(compose.onNodeWithText("Journal"), Color.Cyan)
        assertRenderedColor(compose.onNodeWithTag("journal-submit"), Color.Cyan)
        assertRenderedColor(compose.onNodeWithTag("journal-date"), Color.Yellow)
        compose.runOnIdle { themeMode.intValue = 2 }
        assertRenderedColor(compose.onNodeWithText("Journal"), Color.Magenta)
        assertRenderedColor(compose.onNodeWithTag("journal-submit"), Color.Magenta)
        assertRenderedColor(compose.onNodeWithTag("journal-date"), Color.Blue)
        compose.onNodeWithTag("journal-date").performClick()
        compose.onNodeWithTag("journal-date-dialog").assertIsDisplayed()
        androidx.test.espresso.Espresso.pressBack()

    }

    @Test fun themedTimestampPickerCancelsWithoutMutatingEntry() {
        compose.runOnIdle { themeMode.intValue = 1 }
        compose.onNodeWithTag("journal-composer").performTextInput("Themed timestamp")
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        val original = runBlocking { model.store.dao.exportEntries().single() }
        compose.onNodeWithTag("journal-time-${original.id}").performClick()
        compose.onNodeWithTag("journal-date-dialog").assertIsDisplayed()
        compose.onNode(isSelected() and hasClickAction()).performScrollTo().performClick()
        compose.onNodeWithTag("journal-time-dialog").assertIsDisplayed()
        compose.onNodeWithText("Cancel").performClick()
        assertNull(model.editing.value)
        assertEquals(original, runBlocking { model.store.dao.entry(original.id) })
    }

    @Test fun longPressSubmitDoesNotAccidentallySave() {
        compose.onNodeWithTag("journal-composer").performTextInput("Keep as draft")
        compose.onNodeWithTag("journal-submit").performTouchInput { longClick() }
        compose.onNodeWithText("Start dictation").assertExists()
        assertTrue(runBlocking { model.store.dao.exportEntries().isEmpty() })
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("journal-composer").assertTextContains("Keep as draft")
    }

    @Test fun partialSwipeKeepsEntryAndFullSwipeOffersUndo() {
        compose.onNodeWithTag("journal-composer").performTextInput("Swipe fixture")
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        compose.onNodeWithText("Swipe fixture").performTouchInput { swipe(center, center.copy(x = center.x - 60f), 300) }
        assertNull(runBlocking { model.store.dao.exportEntries().single().deletedAt })
        compose.runOnIdle { assertNull(model.editing.value) }
        compose.onNodeWithText("Swipe fixture").performTouchInput { swipeLeft() }
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt != null } }
        compose.onNodeWithText("Deleted").assertExists()
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt == null } }
    }

    @Test fun swipeWaitsUntilOffscreenBeforeDeleting() {
        compose.onNodeWithTag("journal-composer").performTextInput("Animated deletion")
        compose.onNodeWithTag("journal-submit").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().size == 1 } }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        try {
            compose.onNodeWithText("Animated deletion").performTouchInput {
                // Pass the dismissal threshold, but leave part of the card visible.
                swipe(centerRight - androidx.compose.ui.geometry.Offset(8f, 0f), centerLeft + androidx.compose.ui.geometry.Offset(width * .3f, 0f), 500)
            }
            compose.mainClock.advanceTimeByFrame()
            assertNull("Deletion must wait for the exit animation", runBlocking { model.store.dao.exportEntries().single().deletedAt })
            compose.mainClock.advanceTimeBy(1200)
        } finally { compose.mainClock.autoAdvance = true }
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt != null } }
        compose.onNodeWithText("Undo").performClick()
        compose.waitUntil(10000) { runBlocking { model.store.dao.exportEntries().single().deletedAt == null } }
        compose.onNodeWithText("Animated deletion").assertIsDisplayed()
    }

    @Test fun rtlAndLargeTextKeepComposerActionsOnTheTrailingSide() {
        compose.runOnIdle { rtlLarge.value = true }
        compose.onNodeWithText("Journal", substring = false).assertIsDisplayed()
        compose.onNodeWithTag("journal-composer").assertIsDisplayed()
        compose.onNodeWithTag("journal-submit").assertHasClickAction().assertIsDisplayed()
        val input = compose.onNodeWithTag("journal-composer").fetchSemanticsNode().boundsInRoot
        val submit = compose.onNodeWithTag("journal-submit").fetchSemanticsNode().boundsInRoot
        assertTrue("RTL trailing action must be to the left of input", submit.right <= input.left + 1f)
        assertEquals("Mic stays vertically centered even with a wrapped prompt", input.center.y, submit.center.y, 1f)
    }
}
