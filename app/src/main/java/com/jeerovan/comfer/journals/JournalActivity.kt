@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.jeerovan.comfer.journals

import androidx.room.withTransaction
import android.app.ActivityOptions
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.combinedClickable
import androidx.compose.ui.semantics.*
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.scale
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.R
import com.jeerovan.comfer.ui.rememberThumbReach
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class JournalActivity : AppCompatActivity() {
    private val model: JournalViewModel by viewModels()
    private var unlocked by mutableStateOf(false)
    private var authenticating = false
    private var credentialVerified = false
    private var lockDialogShowing = false
    private val authenticate = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        authenticating = false
        unlocked = result.resultCode == RESULT_OK
        credentialVerified = unlocked
        if (!unlocked) finish() else JournalProtection.authorize()
    }
    private fun unlock() {
        if (!JournalProtection.enabled(this)) { unlocked = true; return }
        val intent = getSystemService(android.app.KeyguardManager::class.java).createConfirmDeviceCredentialIntent("Unlock Journal", "Use your device credentials to open Journal")
        if (intent == null) {
            if (!lockDialogShowing) {
                lockDialogShowing = true
                androidx.appcompat.app.AlertDialog.Builder(this)
                    .setTitle(R.string.journal_lock_unavailable)
                    .setMessage(R.string.journal_lock_setup)
                    .setPositiveButton(R.string.journal_device_security) { _, _ -> startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }
                    .setNegativeButton(android.R.string.cancel) { _, _ -> finish() }
                    .setOnCancelListener { finish() }
                    .setOnDismissListener { lockDialogShowing = false }
                    .show()
            }
            return
        }
        authenticating = true; authenticate.launch(intent)
    }
    override fun onResume() {
        super.onResume()
        if (JournalProtection.enabled(this) && !credentialVerified) unlocked = false
        if (!unlocked && !authenticating) unlock()
    }
    override fun onStop() {
        model.speech.interrupt()
        if (!authenticating) {
            credentialVerified = false
            if (JournalProtection.enabled(this)) unlocked = false
        }
        super.onStop()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        setContent { ComferTheme { if (unlocked) JournalScreen(model, ::finish) else Surface(Modifier.fillMaxSize()) { } } }
    }
    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, JournalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ActivityOptions.makeCustomAnimation(context, R.anim.notification_inbox_enter, R.anim.notification_inbox_underlay).toBundle())
        }
    }
}

private fun pickJournalTime(context: Context, value: Long, zone: String, selected: (Long) -> Unit) {
    val initial = Instant.ofEpochMilli(value).atZone(ZoneId.of(zone))
    DatePickerDialog(context, { _, y, m, d ->
        TimePickerDialog(context, { _, h, minute ->
            selected(LocalDate.of(y, m + 1, d).atTime(h, minute).atZone(ZoneId.of(zone)).toInstant().toEpochMilli())
        }, initial.hour, initial.minute, android.text.format.DateFormat.is24HourFormat(context)).show()
    }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

private fun dayLabel(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
internal fun JournalScreen(model: JournalViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val draft by model.draft.collectAsState()
    val error by model.error.collectAsState()
    val busy by model.busy.collectAsState()
    val draftSaving by model.draftSaving.collectAsState()
    val draftSavingLabel = stringResource(R.string.journal_saving_draft)
    val editing by model.editing.collectAsState()
    val editDraft by model.editDraft.collectAsState()
    val captureState by model.captureState.collectAsState()
    val recoveryPending by model.recoveryPending.collectAsState()
    val liveText by model.liveText.collectAsState()
    val provisionalText by model.provisionalText.collectAsState()
    val committedText by model.committedText.collectAsState()
    val liveEntryId by model.liveEntryId.collectAsState()
    val audioLevel by model.audioLevel.collectAsState()
    val capturing = captureState !in setOf(JournalSpeech.State.IDLE, JournalSpeech.State.FINISHED)
    var durationSeconds by remember { mutableLongStateOf(0) }
    LaunchedEffect(capturing) {
        if(capturing) { durationSeconds = 0; while(true) { delay(1000); durationSeconds++ } }
    }
    var speechOptions by remember { mutableStateOf(false) }
    var language by rememberSaveable { mutableStateOf(java.util.Locale.getDefault().toLanguageTag()) }
    var languageMenu by remember { mutableStateOf(false) }
    val speechLanguages = remember {
        (listOf(java.util.Locale.getDefault()) + java.util.Locale.getAvailableLocales().filter { it.language.isNotBlank() && it.country.isEmpty() }).distinctBy { it.toLanguageTag() }.sortedBy { it.displayName }
    }
    var networkConsent by remember { mutableStateOf(false) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) model.startSpeech(language, networkConsent)
        else model.error.value = "Microphone access was denied. Your draft is unchanged."
    }
    val lifecycle = LocalLifecycleOwner.current
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP) model.speech.interrupt() }
        lifecycle.lifecycle.addObserver(observer)
        onDispose { lifecycle.lifecycle.removeObserver(observer); model.speech.interrupt() }
    }
    var navigationSerial by remember { mutableIntStateOf(0) }
    var day by rememberSaveable { mutableLongStateOf(LocalDate.now().toEpochDay()) }
    var followingToday by rememberSaveable { mutableStateOf(day == LocalDate.now().toEpochDay()) }
    var trash by rememberSaveable { mutableStateOf(false) }
    var limit by remember(day, trash) { mutableIntStateOf(50) }
    var windowOffset by remember(day, trash) { mutableIntStateOf(0) }
    val loadedEntries by remember(day, limit, windowOffset) { model.store.dao.observeDay(day, limit + 1, windowOffset) }.collectAsState(emptyList())
    val entries = remember(loadedEntries, limit) { loadedEntries.takeLast(limit) }
    val newestFirst = remember(entries) { entries.asReversed() }
    val hasOlder = loadedEntries.size > limit
    var settings by remember { mutableStateOf(false) }
    var options by remember { mutableStateOf(false) }
    var savedEntryId by remember { mutableStateOf<String?>(null) }
    var protection by remember { mutableStateOf(JournalProtection.enabled(context)) }
    var trashOffset by remember { mutableIntStateOf(0) }
    val loadedArchive by remember(trashOffset) { model.store.dao.trash(limit = 101, offset = trashOffset) }.collectAsState(emptyList())
    val deleted = remember(loadedArchive) { loadedArchive.take(100) }
    val hasOlderArchive = loadedArchive.size > 100
    var emptyArchive by remember { mutableStateOf(false) }
    var undo by remember { mutableStateOf<JournalEntry?>(null) }
    var confirmLeave by remember { mutableStateOf(false) }
    var pendingEdit by remember { mutableStateOf<JournalEntry?>(null) }
    var confirmDelete by remember { mutableStateOf<JournalEntry?>(null) }
    var imageTarget by remember { mutableStateOf<JournalEntry?>(null) }
    var imagePreview by remember { mutableStateOf(false) }
    var localText by remember(draft?.entryId) { mutableStateOf(draft?.text.orEmpty()) }
    var localEdit by remember(editing?.id) { mutableStateOf(editDraft?.text.orEmpty()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            val today = LocalDate.now().toEpochDay()
            if (model.draft.value?.hasContent == false && !model.speech.active && followingToday && day != today) {
                day = today; model.change(day = today)
            }
        }
    }
    LaunchedEffect(draft?.entryId) { localText = draft?.text.orEmpty(); language = draft?.language ?: language }
    LaunchedEffect(editDraft?.entryId) { localEdit = editDraft?.text.orEmpty() }
    LaunchedEffect(undo) { if (undo != null) { delay(5000); undo = null } }
    // Each navigation surface starts at its natural origin; Archive cannot reuse the feed's index.
    // Reverse layout makes Journal's origin the newest entry at the bottom, without startup scrolling.
    val listState = key(trash, day, navigationSerial) { rememberLazyListState() }
    LaunchedEffect(entries, savedEntryId) {
        if (savedEntryId != null && entries.any { it.id == savedEntryId }) {
            listState.animateScrollToItem(0)
            savedEntryId = null
        }
    }
    fun changed() = editing?.let { it.text != localEdit || it.image != editDraft?.image || it.createdAt != (editDraft?.createdAt ?: it.createdAt) || it.day != editDraft?.day } == true
    fun leave() {
        if (capturing) { if(recoveryPending && !model.speech.active) model.error.value = "Retry saving dictation before closing." else model.speech.stop(); return }
        if (editing != null) { if (changed()) confirmLeave = true else model.finishEdit(false) }
        else if (trash) trash = false else model.closeWhenSaved(close)
    }
    BackHandler { leave() }
    fun selectDay(value: Long) { if(model.speech.active || editing != null) return; windowOffset = 0; navigationSerial++; followingToday = value == LocalDate.now().toEpochDay(); day = value; if (draft?.hasContent == false && localText.isBlank()) model.change(day = value) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            val targetId = model.pendingImageTarget
            val targetRevision = model.pendingImageRevision
            model.busy.value = true
            scope.launch {
                try {
                    val id = model.media.import(uri)
                    if (targetId != null) {
                        val target = model.store.dao.entry(targetId) ?: throw JournalConflict()
                        if (target.revision != targetRevision || (model.editing.value != null && model.editing.value?.id != targetId)) throw JournalConflict()
                        if (model.editing.value == null) model.beginEdit(target)
                        model.changeEdit(image = id, changeImage = true)
                    } else model.change(image = id, changeImage = true)
                    android.widget.Toast.makeText(context, R.string.journal_image_copy, android.widget.Toast.LENGTH_SHORT).show()
                } catch (cancelled: kotlinx.coroutines.CancellationException) { throw cancelled }
                catch (e: Exception) { model.error.value = e.message ?: "Could not read image" }
                finally { model.busy.value = false; model.prepareImagePicker(null) }
            }
        } else model.prepareImagePicker(null)
    }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f)) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (trash) R.string.journal_trash else R.string.journal_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if(draftSaving) CircularProgressIndicator(Modifier.size(16.dp).semantics { contentDescription = draftSavingLabel })
            }
            BoxWithConstraints(Modifier.weight(1f)) {
                val reach = rememberThumbReach((maxHeight - 360.dp).coerceAtLeast(0.dp), trash)
                val offset = with(LocalDensity.current) { reach.offset.toDp() }
                LazyColumn(Modifier.fillMaxSize().testTag("journal-feed").nestedScroll(reach).padding(top = offset), state = listState, reverseLayout = !trash, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp, if (trash) Alignment.Top else Alignment.Bottom)) {
                    if (trash) {
                        item { Text(stringResource(R.string.journal_retention)); if(deleted.isNotEmpty()) TextButton(onClick = { emptyArchive = true }) { Text(stringResource(R.string.journal_empty_trash)) } }
                        if (trashOffset > 0) item { TextButton(onClick = { trashOffset = (trashOffset - 100).coerceAtLeast(0) }) { Text(stringResource(R.string.journal_load_newer)) } }
                        if (hasOlderArchive) item { TextButton(onClick = { trashOffset += 100 }) { Text(stringResource(R.string.journal_load_older)) } }
                        items(deleted, key = { it.id }) { entry ->
                            Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(16.dp)) {
                                Text(dayLabel(entry.day)); Text(entry.text)
                                Row { TextButton(onClick = { model.restore(entry) }) { Text(stringResource(R.string.journal_restore)) }
                                    IconButton(onClick = { confirmDelete = entry }) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.journal_delete_forever)) } }
                            } }
                        }
                    } else {
                        // Newer-page action belongs below the newest row in reverse layout.
                        if (windowOffset > 0) item { TextButton(onClick = { windowOffset = (windowOffset - 50).coerceAtLeast(0) }) { Text(stringResource(R.string.journal_load_newer)) } }
                        if (entries.isEmpty()) item { Text(stringResource(R.string.journal_empty)) }
                        itemsIndexed(newestFirst, key = { _, entry -> entry.id }) { index, entry ->
                            Column(Modifier.fillMaxWidth()) {
                            if (index == newestFirst.lastIndex || newestFirst[index + 1].day != entry.day) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(dayLabel(entry.day), style = MaterialTheme.typography.labelSmall)
                            }
                            // The remembered gesture state must read the latest row revision after edits.
                            val currentEntry by rememberUpdatedState(entry)
                            val canDelete by rememberUpdatedState(editing == null && !capturing)
                            val dismiss = rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                                if (value != SwipeToDismissBoxValue.Settled && canDelete) model.delete(currentEntry) { undo = it }
                                false
                            })
                            SwipeToDismissBox(state = dismiss, enableDismissFromStartToEnd = editing == null && !capturing, enableDismissFromEndToStart = editing == null && !capturing,
                                backgroundContent = { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.DeleteOutline, null) } }) {
                                Card(Modifier.fillMaxWidth().semantics {
                                    customActions = listOf(CustomAccessibilityAction(context.getString(R.string.journal_delete)) {
                                        if (editing == null && !capturing) { model.delete(entry) { undo = it }; true } else false
                                    })
                                }) { Column(Modifier.padding(16.dp)) {
                                    if (editing?.id == entry.id) {
                                        OutlinedTextField(value = localEdit, onValueChange = { localEdit = it; model.changeEdit(text = it) }, modifier = Modifier.fillMaxWidth().testTag("journal-edit"), minLines = 2)
                                        Row { IconButton(onClick = { if (localEdit.isBlank() && editDraft?.image == null) confirmDelete = entry else model.finishEdit(true) }) { Icon(Icons.Outlined.Check, stringResource(R.string.journal_save)) }
                                            IconButton(onClick = { model.finishEdit(false) }) { Icon(Icons.Outlined.Close, stringResource(R.string.journal_cancel)) } }
                                    } else if (capturing && entry.id == liveEntryId) {
                                        Text(committedText)
                                        if (provisionalText.isNotBlank()) Text(provisionalText, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    } else Text(entry.text.ifBlank { " " }, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !capturing && !busy) {
                                        if (editing != null && changed()) { pendingEdit = entry; confirmLeave = true }
                                        else { if (editing != null) model.finishEdit(false); model.beginEdit(entry) }
                                    })
                                    val image = if (editing?.id == entry.id) editDraft?.image else entry.image
                                    image?.let { id -> JournalImage(model.media, id, Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable(enabled = !capturing && !busy && (editing == null || editing?.id == entry.id)) { imageTarget = entry; imagePreview = true }) }
                                    entry.needsReview?.let { Text(stringResource(R.string.journal_needs_review), style = MaterialTheme.typography.labelSmall) }
                                    val timestamp = if (editing?.id == entry.id) editDraft?.createdAt ?: entry.createdAt else entry.createdAt
                                    val shownDay = if (editing?.id == entry.id) editDraft?.day ?: entry.day else entry.day
                                    val created = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of(entry.zone))
                                    Text(if (created.toLocalDate().toEpochDay() == shownDay) created.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                                        else created.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.align(Alignment.End).testTag("journal-time-${entry.id}").clickable(enabled = !capturing && !busy && (editing == null || editing?.id == entry.id)) {
                                            pickJournalTime(context, timestamp, entry.zone) { value ->
                                                if (editing == null) model.beginEdit(entry)
                                                model.changeEdit(createdAt = value)
                                            }
                                        }.padding(top = 8.dp, bottom = 4.dp))


                                } }
                            }
                            }
                        }
                        if (hasOlder) item { TextButton(onClick = { if(limit < 150) limit += 50 else windowOffset += 50 }) { Text(stringResource(R.string.journal_load_older)) } }
                    }
                }
            }
            undo?.let { item -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.journal_deleted), Modifier.weight(1f))
                TextButton(onClick = { model.restore(item); undo = null }) { Text(stringResource(R.string.journal_undo)) }
            } }
            if (!trash) {
                val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
                val threshold = with(LocalDensity.current) { 48.dp.toPx() }
                val navigationEnabled = editing == null && !capturing && !busy
                Box(Modifier.fillMaxWidth().testTag("journal-date-row")
                    .pointerInput(day, navigationEnabled, rtl) {
                        var distance = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { distance = 0f },
                            onHorizontalDrag = { change, amount -> if (navigationEnabled) { change.consume(); distance += amount } },
                            onDragCancel = { distance = 0f },
                            onDragEnd = {
                                if (navigationEnabled && kotlin.math.abs(distance) >= threshold) {
                                    val forward = if (rtl) distance > 0 else distance < 0
                                    selectDay(day + if (forward) 1 else -1)
                                }
                            })
                    }) {
                    TextButton(onClick = {
                        val date = LocalDate.ofEpochDay(day)
                        DatePickerDialog(context, { _, y, m, d -> selectDay(LocalDate.of(y, m + 1, d).toEpochDay()) }, date.year, date.monthValue - 1, date.dayOfMonth).show()
                    }, enabled = navigationEnabled, modifier = Modifier.align(Alignment.Center).testTag("journal-date").semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(context.getString(R.string.journal_previous)) { if (navigationEnabled) { selectDay(day - 1); true } else false },
                            CustomAccessibilityAction(context.getString(R.string.journal_next)) { if (navigationEnabled) { selectDay(day + 1); true } else false })
                    }) { Text(if (day == LocalDate.now().toEpochDay()) stringResource(R.string.journal_today) else dayLabel(day)) }
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        IconButton(onClick = { options = true }, enabled = navigationEnabled) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.journal_options)) }
                        DropdownMenu(expanded = options, onDismissRequest = { options = false }) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.journal_settings)) }, onClick = { options = false; settings = true })
                            DropdownMenuItem(text = { Text(stringResource(R.string.journal_trash)) }, onClick = { options = false; trash = true })
                        }
                    }
                }
            }
            if (!trash) Column(Modifier.padding(horizontal = 12.dp)) {
                if (capturing) {
                    Text("${durationSeconds / 60}:${(durationSeconds % 60).toString().padStart(2, '0')}")
                    Text(liveText, maxLines = 5)
                    Text(stringResource(when(captureState) {
                        JournalSpeech.State.STARTING -> R.string.journal_starting
                        JournalSpeech.State.FINALIZING -> R.string.journal_finalizing
                        JournalSpeech.State.PAUSED -> R.string.journal_paused
                        else -> R.string.journal_listening
                    }), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                    audioLevel?.let { LinearProgressIndicator(progress = { ((it + 2f) / 12f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth()) }
                    if (recoveryPending) TextButton(onClick = { model.retryRecovery() }) { Text(stringResource(R.string.journal_retry)) }
                    Row {
                        IconButton(onClick = { if(captureState == JournalSpeech.State.PAUSED) model.speech.resume() else model.speech.stop(true) }, enabled = captureState != JournalSpeech.State.FINALIZING) {
                            Icon(if(captureState == JournalSpeech.State.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, stringResource(if(captureState == JournalSpeech.State.PAUSED) R.string.journal_resume else R.string.journal_pause))
                        }
                        IconButton(onClick = { model.speech.stop() }) { Icon(Icons.Outlined.Stop, stringResource(R.string.journal_stop)) }
                    }
                } else {
                draft?.image?.let { id -> JournalImage(model.media, id, Modifier.height(90.dp).clickable(enabled = editing == null && !busy) { imageTarget = null; imagePreview = true }) }
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(localText, onValueChange = { localText = it; model.change(text = it) }, modifier = Modifier.weight(1f).heightIn(max = 180.dp).testTag("journal-composer").semantics { contentDescription = context.getString(R.string.journal_input) }, enabled = editing == null && !busy && draft != null,
                        placeholder = { Text(stringResource(listOf(R.string.journal_prompt_0, R.string.journal_prompt_1, R.string.journal_prompt_2, R.string.journal_prompt_3)[draft?.prompt ?: 0])) },
                        trailingIcon = { IconButton(onClick = { imageTarget = null; model.prepareImagePicker(null); picker.launch("image/*") }, enabled = editing == null && !busy) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.journal_add_image)) } })
                    val canSubmit = localText.isNotBlank() || draft?.image != null
                    Box(Modifier.size(48.dp).testTag("journal-submit").combinedClickable(enabled = editing == null && !busy && draft != null,
                        onClick = { if (canSubmit) model.submit { entry -> selectDay(entry.day); savedEntryId = entry.id; model.change(day = entry.day) } else model.error.value = "Hold the microphone to start dictation." },
                        onLongClickLabel = stringResource(R.string.journal_start_dictation),
                        onLongClick = { networkConsent = false; speechOptions = true }).semantics {
                            customActions = listOf(CustomAccessibilityAction("Start dictation") { networkConsent = false; speechOptions = true; true })
                        }, contentAlignment = Alignment.Center) {
                        if(busy) CircularProgressIndicator(Modifier.size(24.dp))
                        else Icon(if (canSubmit) Icons.Outlined.Check else Icons.Outlined.Mic, stringResource(if(canSubmit) R.string.journal_save else R.string.journal_start_dictation))
                    }
                }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    if (emptyArchive) AlertDialog(onDismissRequest = { emptyArchive = false }, title = { Text(stringResource(R.string.journal_empty_trash_question)) },
        confirmButton = { TextButton(onClick = {
            emptyArchive = false
            scope.launch {
                try {
                    model.store.db.withTransaction {
                        while(true) {
                            val batch = model.store.dao.trashForPurge()
                            if(batch.isEmpty()) break
                            batch.forEach { model.store.dao.purge(it.id, it.revision) }
                        }
                    }
                    undo = null
                } catch (_: Exception) { model.error.value = "Could not empty Archive. Please try again." }
            }
        }) { Text(stringResource(R.string.journal_delete)) } }, dismissButton = { TextButton(onClick = { emptyArchive = false }) { Text(stringResource(R.string.journal_cancel)) } })
    if (settings) ModalBottomSheet(onDismissRequest = { settings = false }) {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.journal_protect), Modifier.weight(1f))
            Switch(protection, modifier = Modifier.scale(.7f), onCheckedChange = {
                try { JournalProtection.setEnabled(context, it); protection = it }
                catch (e: Exception) { model.error.value = e.message }
            })
        }
        Text(stringResource(R.string.journal_privacy_explanation), Modifier.padding(16.dp))
        TextButton(onClick = { settings = false; trash = true }) { Text(stringResource(R.string.journal_trash)) }
        Spacer(Modifier.height(24.dp))
    }
    if (speechOptions) AlertDialog(onDismissRequest = { speechOptions = false }, title = { Text(stringResource(R.string.journal_start_dictation)) }, text = {
        Column {
            Text(stringResource(R.string.journal_language))
            Box {
                TextButton(onClick = { languageMenu = true }) { Text(java.util.Locale.forLanguageTag(language).displayName) }
                DropdownMenu(expanded = languageMenu, onDismissRequest = { languageMenu = false }, modifier = Modifier.heightIn(max = 300.dp)) {
                    speechLanguages.forEach { locale -> DropdownMenuItem(text = { Text(locale.displayName) }, onClick = { language = locale.toLanguageTag(); languageMenu = false }) }
                }
            }
            Text(stringResource(R.string.journal_language_availability), style = MaterialTheme.typography.bodySmall)
            if (!model.speech.onDeviceAvailable()) {
                Text(stringResource(R.string.journal_network_disclosure))
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.journal_network_allow), Modifier.weight(1f)); Switch(networkConsent, { networkConsent = it }) }
            }
        }
    }, confirmButton = { TextButton(enabled = language.isNotBlank() && (model.speech.onDeviceAvailable() || networkConsent), onClick = { speechOptions = false; permission.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.journal_start)) } }, dismissButton = { TextButton(onClick = { speechOptions = false }) { Text(stringResource(R.string.journal_cancel)) } })
    if (confirmLeave) AlertDialog(onDismissRequest = { confirmLeave = false; pendingEdit = null }, title = { Text(stringResource(R.string.journal_changes)) },
        confirmButton = { TextButton(onClick = { model.finishEdit(true, pendingEdit); confirmLeave = false; pendingEdit = null }) { Text(stringResource(R.string.journal_save)) } },
        dismissButton = { Row { TextButton(onClick = { model.finishEdit(false, pendingEdit); pendingEdit = null; confirmLeave = false }) { Text(stringResource(R.string.journal_discard)) }; TextButton(onClick = { confirmLeave = false; pendingEdit = null }) { Text(stringResource(R.string.journal_keep_editing)) } } })
    confirmDelete?.let { entry -> AlertDialog(onDismissRequest = { confirmDelete = null }, title = { Text(stringResource(if(entry.deletedAt == null) R.string.journal_delete_question else R.string.journal_permanent_question)) },
        confirmButton = { TextButton(onClick = { if (entry.deletedAt == null) { model.finishEdit(false); model.delete(entry) { undo = it } } else scope.launch { try { model.store.dao.purge(entry.id, entry.revision) } catch (_: Exception) { model.error.value = "Could not delete. Your entry is still in Archive." } }; confirmDelete = null }) { Text(stringResource(R.string.journal_delete)) } },
        dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.journal_cancel)) } }) }
    if (imagePreview) {
        val target = imageTarget
        val imageId = if (target == null) draft?.image else if (editing?.id == target.id) editDraft?.image else target.image
        if (imageId != null) JournalImagePreview(model.media, imageId,
            onClose = { imagePreview = false },
            onChange = { imagePreview = false; model.prepareImagePicker(target); picker.launch("image/*") },
            onDelete = {
                if (target == null) model.change(image = null, changeImage = true)
                else if ((if (editing?.id == target.id) localEdit else target.text).isBlank()) confirmDelete = target
                else { if (editing == null) model.beginEdit(target); model.changeEdit(image = null, changeImage = true) }
                imagePreview = false
            })
    }
    error?.let { message -> AlertDialog(onDismissRequest = { model.error.value = null }, title = { Text(message) }, confirmButton = { TextButton(onClick = { model.error.value = null }) { Text(stringResource(R.string.journal_close)) } }) }
}

@Composable
private fun JournalImage(media: JournalMedia, id: String, modifier: Modifier) {
    val bitmap by produceState<android.graphics.Bitmap?>(null, id) { value = media.thumbnail(id) }
    bitmap?.let { Image(it.asImageBitmap(), stringResource(R.string.journal_image), modifier) }
}
