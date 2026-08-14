package com.jeerovan.comfer

import android.app.Application
import android.database.ContentObserver
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class ContactsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ContactsRepository(application)
    private val _contacts = MutableStateFlow<List<Contact>>(emptyList())
    val contacts = _contacts.asStateFlow()
    private var refreshJob: Job? = null

    fun refreshIfNeeded(hasPermission: Boolean) {
        repository.updatePermission(hasPermission)
        if (!hasPermission) {
            refreshJob?.cancel()
            _contacts.value = emptyList()
            return
        }
        if (!repository.isDirty && _contacts.value.isNotEmpty()) return
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            try {
                _contacts.value = repository.loadContacts()
            } catch (e: CancellationException) {
                throw e
            } catch (_: SecurityException) {
                _contacts.value = emptyList()
            }
        }
    }

    override fun onCleared() {
        repository.close()
        super.onCleared()
    }
}

private class ContactsRepository(private val application: Application) {
    private val loadMutex = Mutex()
    private var observerRegistered = false
    @Volatile var isDirty = true
        private set

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            isDirty = true
        }
    }

    @Synchronized
    fun updatePermission(hasPermission: Boolean) {
        if (hasPermission && !observerRegistered) {
            try {
                application.contentResolver.registerContentObserver(
                    ContactsContract.Contacts.CONTENT_URI,
                    true,
                    observer,
                )
                observerRegistered = true
            } catch (_: SecurityException) {
                observerRegistered = false
            }
        } else if (!hasPermission && observerRegistered) {
            application.contentResolver.unregisterContentObserver(observer)
            observerRegistered = false
        }
    }

    suspend fun loadContacts(): List<Contact> = loadMutex.withLock {
        withContext(Dispatchers.IO) {
            val traceCookie = PerformanceTrace.contactQueryStarted()
            PerformanceTrace.beginAsync("contactsQuery", traceCookie)
            val resolver = application.contentResolver
            val cancellationSignal = CancellationSignal()
            val completionHandle = currentCoroutineContext().job.invokeOnCompletion { cause ->
                if (cause is CancellationException) cancellationSignal.cancel()
            }
            try {
                val phoneMap = mutableMapOf<Long, String>()
                resolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ),
                    null,
                    null,
                    null,
                    cancellationSignal,
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID
                    )
                    val numberIndex = cursor.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )
                    if (idIndex >= 0 && numberIndex >= 0) {
                        while (cursor.moveToNext()) {
                            currentCoroutineContext().ensureActive()
                            val id = cursor.getLong(idIndex)
                            if (!phoneMap.containsKey(id)) {
                                cursor.getString(numberIndex)?.let { phoneMap[id] = it }
                            }
                        }
                    }
                }

                val contacts = mutableListOf<Contact>()
                resolver.query(
                    ContactsContract.Contacts.CONTENT_URI,
                    arrayOf(
                        ContactsContract.Contacts._ID,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.PHOTO_URI,
                        ContactsContract.Contacts.HAS_PHONE_NUMBER,
                    ),
                    null,
                    null,
                    null,
                    cancellationSignal,
                )?.use { cursor ->
                    val idIndex = cursor.getColumnIndex(ContactsContract.Contacts._ID)
                    val nameIndex = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                    val photoIndex = cursor.getColumnIndex(ContactsContract.Contacts.PHOTO_URI)
                    val hasPhoneIndex = cursor.getColumnIndex(
                        ContactsContract.Contacts.HAS_PHONE_NUMBER
                    )
                    if (idIndex < 0 || nameIndex < 0 || photoIndex < 0 || hasPhoneIndex < 0) {
                        return@withContext emptyList()
                    }
                    while (cursor.moveToNext()) {
                        currentCoroutineContext().ensureActive()
                        val id = cursor.getLong(idIndex)
                        val name = cursor.getString(nameIndex)
                        val number = if (cursor.getInt(hasPhoneIndex) > 0) phoneMap[id] else null
                        if (!name.isNullOrEmpty() && number != null) {
                            contacts += Contact(
                                id,
                                name,
                                cursor.getString(photoIndex)?.toUri(),
                                number,
                            )
                        }
                    }
                }
                contacts.distinctBy { it.id }.sortedBy { it.name }
                    .also { isDirty = false }
            } finally {
                completionHandle.dispose()
                PerformanceTrace.endAsync("contactsQuery", traceCookie)
                PerformanceTrace.contactQueryFinished()
            }
        }
    }

    @Synchronized
    fun close() {
        if (observerRegistered) {
            application.contentResolver.unregisterContentObserver(observer)
            observerRegistered = false
        }
    }
}
