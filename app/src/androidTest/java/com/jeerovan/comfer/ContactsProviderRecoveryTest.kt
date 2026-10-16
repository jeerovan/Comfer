package com.jeerovan.comfer

import android.app.Application
import android.database.sqlite.SQLiteDatabaseLockedException
import android.database.sqlite.SQLiteDiskIOException
import androidx.lifecycle.ViewModelStore
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.awaitCancellation
import org.junit.After
import org.junit.Assert.*
import org.junit.Test

class ContactsProviderRecoveryTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val store = ViewModelStore()
    private val first = listOf(Contact(1, "Saved contact", null, "5550101"))
    private val updated = listOf(Contact(2, "Updated contact", null, "5550102"))

    private class Source : ContactSource {
        @Volatile override var isDirty = true
        @Volatile var calls = 0
        var query: suspend () -> List<Contact> = { emptyList() }
        override fun updatePermission(hasPermission: Boolean) = Unit
        override suspend fun loadContacts(): List<Contact> {
            calls++
            return query().also { isDirty = false }
        }
        override fun close() = Unit
    }

    @After fun close() { instrumentation.runOnMainSync { store.clear() } }

    private fun model(source: Source): ContactsViewModel {
        lateinit var model: ContactsViewModel
        instrumentation.runOnMainSync {
            model = ContactsViewModel(instrumentation.targetContext.applicationContext as Application, source)
            store.put("contacts", model)
        }
        return model
    }

    private fun refresh(model: ContactsViewModel, permission: Boolean = true) {
        instrumentation.runOnMainSync { model.refreshIfNeeded(permission) }
        instrumentation.waitForIdleSync()
    }

    @Test fun lockedProviderOnFirstLoadCanRecoverOnNextRefresh() {
        val source = Source().apply { query = { throw SQLiteDatabaseLockedException("provider busy") } }
        val model = model(source)
        refresh(model)
        assertTrue(model.contacts.value.isEmpty())
        assertTrue(source.isDirty)
        source.query = { updated }
        refresh(model)
        assertEquals(updated, model.contacts.value)
        assertEquals(2, source.calls)
        refresh(model)
        assertEquals("Successful cache does not query again", 2, source.calls)
    }

    @Test fun ioFailurePreservesLastGoodListAndRetriesWithoutPublishingPartialData() {
        val source = Source().apply { query = { first } }
        val model = model(source)
        refresh(model)
        source.isDirty = true
        source.query = { throw SQLiteDiskIOException("provider I/O failure") }
        refresh(model)
        assertEquals(first, model.contacts.value)
        assertTrue(source.isDirty)
        source.query = { updated }
        refresh(model)
        assertEquals(updated, model.contacts.value)
    }

    @Test fun revokedPermissionClearsCachedContactsAfterProviderFailure() {
        val source = Source().apply { query = { first } }
        val model = model(source)
        refresh(model)
        source.isDirty = true
        source.query = { throw SecurityException("permission revoked during query") }
        refresh(model)
        assertTrue(model.contacts.value.isEmpty())
        source.query = { updated }
        refresh(model)
        assertEquals(updated, model.contacts.value)
        refresh(model, permission = false)
        assertTrue(model.contacts.value.isEmpty())
    }

    @Test fun cancelledQueryCannotPublishOverPermissionRevocationAndLaterRefreshRecovers() {
        val source = Source().apply { query = { first } }
        val model = model(source)
        refresh(model)
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        source.isDirty = true
        source.query = {
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled.complete(Unit) }
        }
        refresh(model)
        assertTrue(started.isCompleted)
        refresh(model, permission = false)
        assertTrue(cancelled.isCompleted)
        assertTrue(model.contacts.value.isEmpty())
        source.query = { updated }
        refresh(model)
        assertEquals(updated, model.contacts.value)
    }
}
