package com.jeerovan.comfer.journals

import android.graphics.Bitmap
import android.graphics.Color
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import com.jeerovan.comfer.BackupRestoreManager
import com.jeerovan.comfer.StartupCoordinator
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File

/** Synthetic export → uninstall isolated target → reinstall → import; never normal Comfer data. */
class JournalTransferTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val password = "portable-synthetic-password"
    @Test fun exportProtectedJournal() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        JournalArchiveMedia.replaceLocal(context, JournalLocalSnapshot(emptyList(), emptyList()))
        val bitmap = Bitmap.createBitmap(40, 24, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        val input = File(context.cacheDir, "transfer-image.png")
        input.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }; bitmap.recycle()
        val image = JournalMedia(context).import(Uri.fromFile(input))
        val store = JournalStore(JournalDatabase.get(context))
        val entry = store.submit(store.saveDraft(store.ensureDraft().copy(text = "Portable Journal fixture", image = image)))
        val speech = store.ensureDraft()
        store.speech(speech, "portable-session", 0, "Dictated", "Unconfirmed", false, true)
        store.saveDraft(store.ensureDraft().copy(text = "Portable draft", day = -123))
        store.saveDraft(store.beginEdit(entry).copy(text = "Portable unsaved edit"))
        // Simulate an already-authenticated export; real credential UI is a separate acceptance check.
        JournalProtection.restoreState(context, true)
        JournalProtection.authorize()
        BackupRestoreManager.createBackup(context, Uri.fromFile(File(context.filesDir, "journal-transfer.zip")), "en", password)
        assertEquals(2, store.dao.exportEntries().size)
    }
    @Test fun importIntoFreshInstallation() = runBlocking {
        check(context.packageName.endsWith(".notificationtest")); StartupCoordinator.awaitReady()
        val dao = JournalDatabase.get(context).dao()
        assertTrue("Requires a fresh isolated installation", dao.exportEntries().isEmpty())
        val archive = Uri.fromFile(File(context.filesDir, "journal-transfer.zip"))
        assertTrue(BackupRestoreManager.inspectBackup(context, archive).journalsEncrypted)
        BackupRestoreManager.restoreBackup(context, archive, password)
        assertTrue(JournalProtection.enabled(context))
        val entries = dao.exportEntries()
        assertEquals(2, entries.size)
        val imageEntry = entries.single { it.text == "Portable Journal fixture" }
        val image = android.graphics.BitmapFactory.decodeFile(JournalMedia(context).file(imageEntry.image!!).path)
        assertEquals(40, image.width); assertEquals(24, image.height)
        assertTrue(Color.blue(image.getPixel(0, 0)) > 200); assertTrue(Color.red(image.getPixel(0, 0)) < 20); image.recycle()
        assertEquals("Portable unsaved edit", dao.draft("edit:${imageEntry.id}")!!.text)
        val review = entries.single { it.needsReview != null }
        assertEquals("Dictated Unconfirmed", review.text)
        assertEquals("Unconfirmed", dao.segments(review.id).single().provisional)
        assertEquals("Portable draft", dao.draft()!!.text); assertEquals(-123L, dao.draft()!!.day)
        assertEquals(android.content.pm.PackageManager.PERMISSION_DENIED, androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO))
        if(!context.getSystemService(android.app.KeyguardManager::class.java).isDeviceSecure) {
            androidx.test.core.app.ActivityScenario.launch(JournalActivity::class.java).use {
                androidx.test.espresso.Espresso.onView(androidx.test.espresso.matcher.ViewMatchers.withText(com.jeerovan.comfer.R.string.journal_lock_unavailable))
                    .check(androidx.test.espresso.assertion.ViewAssertions.matches(androidx.test.espresso.matcher.ViewMatchers.isDisplayed()))
            }
        }
    }
}
