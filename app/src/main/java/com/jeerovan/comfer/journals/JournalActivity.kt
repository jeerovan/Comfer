@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.jeerovan.comfer.journals

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.ui.draw.clipToBounds
import androidx.room.withTransaction
import android.app.ActivityOptions
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
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.R
import com.jeerovan.comfer.ui.rememberThumbReach
import com.jeerovan.comfer.ui.theme.ComferTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

class JournalActivity : AppCompatActivity() {
    private val model: JournalViewModel by viewModels()
    private var unlocked by mutableStateOf(false)
    private var authenticating = false
    private var credentialVerified = false
    private var lockDialogShowing by mutableStateOf(false)
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
            lockDialogShowing = true
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
        setContent { ComferTheme {
            if (unlocked) JournalScreen(model, ::finish)
            else {
                Surface(Modifier.fillMaxSize()) { }
                if (lockDialogShowing) AlertDialog(onDismissRequest = { finish() },
                    title = { Text(stringResource(R.string.journal_lock_unavailable)) },
                    text = { Text(stringResource(R.string.journal_lock_setup)) },
                    confirmButton = { TextButton(onClick = { lockDialogShowing = false; startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)) }) { Text(stringResource(R.string.journal_device_security)) } },
                    dismissButton = { TextButton(onClick = { finish() }) { Text(stringResource(R.string.journal_cancel)) } })
            }
        } }
    }
    companion object {
        fun open(context: Context) {
            context.startActivity(Intent(context, JournalActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                ActivityOptions.makeCustomAnimation(context, R.anim.notification_inbox_enter, R.anim.notification_inbox_underlay).toBundle())
        }
    }
}

private data class JournalDateRequest(val timestamp: Long, val zone: String, val entry: JournalEntry? = null)

private fun dayLabel(day: Long) = LocalDate.ofEpochDay(day).format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))

@Composable
internal fun JournalScreen(model: JournalViewModel, close: () -> Unit) {
    val context = LocalContext.current
    val resources = LocalResources.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val inputView = LocalView.current
    val mutedGray = Color(0xFF9E9E9E)
    val cardText = MaterialTheme.colorScheme.onSurface
    val journalCardColors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f), contentColor = cardText)
    val composerColors = OutlinedTextFieldDefaults.colors(
        focusedPlaceholderColor = mutedGray, unfocusedPlaceholderColor = mutedGray,
        disabledPlaceholderColor = mutedGray.copy(alpha = .6f),
        focusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .55f), unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .55f),
        disabledBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = .25f)
    )
    val scope = rememberCoroutineScope()
    val guides = remember(context) { JournalGuideProgress(context) }
    var guideEditRequest by remember { mutableStateOf<String?>(null) }
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
    var searchMode by rememberSaveable { mutableStateOf(false) }
    var searchText by rememberSaveable { mutableStateOf("") }
    var searchQuery by remember { mutableStateOf("") }
    LaunchedEffect(searchMode, searchText) {
        if (!searchMode || searchText.isBlank()) searchQuery = ""
        else { delay(300); searchQuery = searchText.trim() }
    }
    var limit by remember(day, trash, searchMode, searchQuery) { mutableIntStateOf(50) }
    var windowOffset by remember(day, trash, searchMode, searchQuery) { mutableIntStateOf(0) }
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
    var dateRequest by remember { mutableStateOf<JournalDateRequest?>(null) }
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
    LaunchedEffect(editing?.id) {
        if (editing != null && editing?.id == guideEditRequest) {
            guides.performed(JournalGuide.EDIT)
            guideEditRequest = null
        }
    }
    fun changed() = editing?.let { it.text != localEdit || it.image != editDraft?.image || it.createdAt != (editDraft?.createdAt ?: it.createdAt) || it.day != editDraft?.day } == true
    fun closeSearch() {
        keyboard?.hide()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(inputView.windowToken,0)
        focusManager.clearFocus(force=true)
        searchMode=false;searchText="";searchQuery=""
    }
    fun leave() {
        if (capturing) { if(recoveryPending && !model.speech.active) model.error.value = "Retry saving dictation before closing." else model.speech.stop(); return }
        if (editing != null) { if (changed()) confirmLeave = true else model.finishEdit(false) }
        else if (trash) trash = false else if (searchMode) closeSearch() else model.closeWhenSaved(close)
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
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface.copy(alpha = .8f), contentColor = MaterialTheme.colorScheme.onSurface, tonalElevation = 0.dp) {
        Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(if (trash) R.string.journal_trash else if (searchMode) R.string.journal_search_title else R.string.journal_title), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                if(draftSaving) CircularProgressIndicator(Modifier.size(16.dp).semantics { contentDescription = draftSavingLabel })
                if (!trash) Box {
                    IconButton(onClick = { options = true }, enabled = editing == null && !capturing && !busy) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.journal_options), tint = cardText) }
                    DropdownMenu(expanded = options, onDismissRequest = { options = false }, containerColor = MaterialTheme.colorScheme.surfaceContainer, tonalElevation = 0.dp) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.journal_settings), color = MaterialTheme.colorScheme.onSurfaceVariant ) }, onClick = { options = false; settings = true })
                        DropdownMenuItem(text = { Text(stringResource(R.string.journal_trash), color = MaterialTheme.colorScheme.onSurfaceVariant) }, onClick = { options = false; trash = true })
                    }
                }
            }
            val rtlLayout = LocalLayoutDirection.current == LayoutDirection.Rtl
            AnimatedContent(targetState = day, modifier = Modifier.weight(1f).clipToBounds(),
                transitionSpec = {
                    val direction = if ((targetState > initialState) != rtlLayout) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                    (slideIntoContainer(direction, tween(320)) + fadeIn(tween(240))) togetherWith
                        (slideOutOfContainer(direction, tween(320)) + fadeOut(tween(240)))
                }, label = "journal-day-content") { pageDay ->
            val loadedEntries by key(searchMode, searchQuery) {
                remember(pageDay, searchMode, searchQuery, limit, windowOffset) {
                    if (!searchMode) model.store.dao.observeDay(pageDay, limit + 1, windowOffset)
                    else if (searchQuery.isEmpty()) kotlinx.coroutines.flow.flowOf(emptyList<JournalEntry>())
                    else model.store.dao.search(searchQuery, limit + 1, windowOffset)
                }.collectAsState(emptyList())
            }
            val entries = remember(loadedEntries, limit) { loadedEntries.takeLast(limit) }
            val newestFirst = remember(entries) { entries.asReversed() }
            val hasOlder = loadedEntries.size > limit
            LaunchedEffect(entries.isNotEmpty()) {
                if (entries.isNotEmpty()) guides.activate()
            }
            val pageNavigationSerial = remember { navigationSerial }
            val listState = key(trash, pageDay, pageNavigationSerial, searchMode, searchQuery) { rememberLazyListState() }
            LaunchedEffect(entries, savedEntryId) {
                if (pageDay == day && savedEntryId != null && entries.any { it.id == savedEntryId }) {
                    listState.animateScrollToItem(0)
                    savedEntryId = null
                }
            }
            val guideEntryId by remember(newestFirst, listState) { derivedStateOf {
                val ids = newestFirst.map { it.id }.toSet()
                listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key in ids }?.key
                    ?: newestFirst.firstOrNull()?.id
            } }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val reach = rememberThumbReach((maxHeight - 360.dp).coerceAtLeast(0.dp), trash to searchMode)
                val offset = with(LocalDensity.current) { reach.offset.toDp() }
                LazyColumn(Modifier.fillMaxSize().testTag("journal-feed").nestedScroll(reach).padding(top = offset), state = listState, reverseLayout = !trash, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp, if (trash) Alignment.Top else Alignment.Bottom)) {
                    if (trash) {
                        item { Text(stringResource(R.string.journal_retention)); if(deleted.isNotEmpty()) TextButton(onClick = { emptyArchive = true }) { Text(stringResource(R.string.journal_empty_trash)) } }
                        if (trashOffset > 0) item { TextButton(onClick = { trashOffset = (trashOffset - 100).coerceAtLeast(0) }) { Text(stringResource(R.string.journal_load_newer)) } }
                        if (hasOlderArchive) item { TextButton(onClick = { trashOffset += 100 }) { Text(stringResource(R.string.journal_load_older)) } }
                        items(deleted, key = { it.id }) { entry ->
                            Card(Modifier.fillMaxWidth(), colors = journalCardColors) { Column(Modifier.padding(16.dp)) {
                                Text(dayLabel(entry.day), color = mutedGray); Text(entry.text)
                                Row { TextButton(onClick = { model.restore(entry) }) { Text(stringResource(R.string.journal_restore)) }
                                    IconButton(onClick = { confirmDelete = entry }) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.journal_delete_forever)) } }
                            } }
                        }
                    } else {
                        // Newer-page action belongs below the newest row in reverse layout.
                        if (windowOffset > 0) item { TextButton(onClick = { windowOffset = (windowOffset - 50).coerceAtLeast(0) }) { Text(stringResource(R.string.journal_load_newer)) } }
                        if (entries.isEmpty()) item { Text(stringResource(if (!searchMode) R.string.journal_empty else if (searchText.isBlank()) R.string.journal_search_empty else R.string.journal_search_no_results)) }
                        itemsIndexed(newestFirst, key = { _, entry -> entry.id }) { index, entry ->
                            Column(Modifier.fillMaxWidth().animateItem(fadeInSpec = tween(200), placementSpec = tween(260), fadeOutSpec = null)) {
                            if (index == newestFirst.lastIndex || newestFirst[index + 1].day != entry.day) Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                                Text(dayLabel(entry.day), style = MaterialTheme.typography.labelSmall, color = mutedGray)
                            }
                            // The remembered gesture state must read the latest row revision after edits.
                            val currentEntry by rememberUpdatedState(entry)
                            val canDelete by rememberUpdatedState(editing == null && !capturing)
                            // Undo restores the same item ID with a new revision. Its
                            // saved swipe state must not dismiss the restored entry again.
                            val dismiss = key(entry.revision) {
                                rememberSwipeToDismissBoxState(confirmValueChange = { value ->
                                    value == SwipeToDismissBoxValue.Settled || canDelete
                                })
                            }
                            LaunchedEffect(dismiss, guides.current, canDelete) {
                                if (canDelete && guides.current == JournalGuide.DELETE) {
                                    snapshotFlow { dismiss.dismissDirection }
                                        .first { it != SwipeToDismissBoxValue.Settled }
                                    guides.performed(JournalGuide.DELETE)
                                }
                            }
                            LaunchedEffect(dismiss, dismiss.settledValue) {
                                if (dismiss.settledValue != SwipeToDismissBoxValue.Settled) {
                                    model.delete(currentEntry, done = { undo = it }, failed = { scope.launch { dismiss.reset() } })
                                }
                            }
                            SwipeToDismissBox(state = dismiss, modifier = Modifier.clipToBounds(), enableDismissFromStartToEnd = editing == null && !capturing, enableDismissFromEndToStart = editing == null && !capturing,
                                backgroundContent = {}) {
                                Box {
                                Card(Modifier.fillMaxWidth().semantics {
                                    customActions = listOf(CustomAccessibilityAction(resources.getString(R.string.journal_delete)) {
                                        if (editing == null && !capturing) { model.delete(entry) { undo = it }; true } else false
                                    })
                                }, colors = journalCardColors) { Column(Modifier.padding(16.dp)) {
                                    if (editing?.id == entry.id) {
                                        OutlinedTextField(value = localEdit, onValueChange = { localEdit = it; model.changeEdit(text = it) }, modifier = Modifier.fillMaxWidth().testTag("journal-edit"), minLines = 2,
                                            colors = composerColors.copy(focusedTextColor = cardText, unfocusedTextColor = cardText))
                                        Row { IconButton(onClick = { if (localEdit.isBlank() && editDraft?.image == null) confirmDelete = entry else model.finishEdit(true) }) { Icon(Icons.Outlined.Check, stringResource(R.string.journal_save)) }
                                            IconButton(onClick = { model.finishEdit(false) }) { Icon(Icons.Outlined.Close, stringResource(R.string.journal_cancel)) } }
                                    } else if (capturing && entry.id == liveEntryId) {
                                        Text(committedText)
                                        if (provisionalText.isNotBlank()) Text(provisionalText, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, color = mutedGray)
                                    } else Text(entry.text.ifBlank { " " }, Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(enabled = !capturing && !busy) {
                                        if (editing != null && changed()) { pendingEdit = entry; confirmLeave = true }
                                        else { if (editing != null) model.finishEdit(false); guideEditRequest = entry.id; model.beginEdit(entry) }
                                    })
                                    val image = if (editing?.id == entry.id) editDraft?.image else entry.image
                                    image?.let { id -> JournalImage(model.media, id, Modifier.fillMaxWidth().heightIn(max = 220.dp).clickable(enabled = !capturing && !busy && (editing == null || editing?.id == entry.id)) { imageTarget = entry; imagePreview = true }) }
                                    entry.needsReview?.let { Text(stringResource(R.string.journal_needs_review), style = MaterialTheme.typography.labelSmall) }
                                    val timestamp = if (editing?.id == entry.id) editDraft?.createdAt ?: entry.createdAt else entry.createdAt
                                    val shownDay = if (editing?.id == entry.id) editDraft?.day ?: entry.day else entry.day
                                    val created = Instant.ofEpochMilli(timestamp).atZone(ZoneId.of(entry.zone))
                                    Text(if (created.toLocalDate().toEpochDay() == shownDay) created.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))
                                        else created.format(DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM, FormatStyle.SHORT)),
                                        style = MaterialTheme.typography.labelSmall, color = mutedGray,
                                        modifier = Modifier.align(Alignment.End).testTag("journal-time-${entry.id}").clickable(enabled = !capturing && !busy && (editing == null || editing?.id == entry.id)) {
                                            dateRequest = JournalDateRequest(timestamp, entry.zone, entry)
                                        }.padding(top = 8.dp, bottom = 4.dp))


                                } }
                                if (entry.id == guideEntryId && pageDay == day && editing == null && !capturing && !busy && !settings && !options && !imagePreview && dateRequest == null) {
                                    guides.current?.takeIf { it == JournalGuide.EDIT || it == JournalGuide.DELETE }?.let {
                                        // The text area opens editing; an image tap opens preview.
                                        JournalGestureGuide(it, if (it == JournalGuide.EDIT) Modifier.align(Alignment.TopCenter).padding(top = 20.dp) else Modifier.align(Alignment.Center))
                                    }
                                }
                                }
                            }
                            }
                        }
                        if (hasOlder) item { TextButton(onClick = { if(limit < 150) limit += 50 else windowOffset += 50 }) { Text(stringResource(R.string.journal_load_older)) } }
                    }
                }
            }
            }
            undo?.let { item -> Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.journal_deleted), Modifier.weight(1f))
                TextButton(onClick = { model.restore(item); undo = null }) { Text(stringResource(R.string.journal_undo)) }
            } }
            if (!trash && !searchMode) {
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
                                    guides.performed(JournalGuide.DATE)
                                }
                            })
                    }) {
                    TextButton(onClick = {
                        val zone = ZoneId.systemDefault()
                        dateRequest = JournalDateRequest(LocalDate.ofEpochDay(day).atStartOfDay(zone).toInstant().toEpochMilli(), zone.id)
                    }, enabled = navigationEnabled, modifier = Modifier.align(Alignment.Center).testTag("journal-date").semantics {
                        customActions = listOf(
                            CustomAccessibilityAction(resources.getString(R.string.journal_previous)) { if (navigationEnabled) { selectDay(day - 1); true } else false },
                            CustomAccessibilityAction(resources.getString(R.string.journal_next)) { if (navigationEnabled) { selectDay(day + 1); true } else false })
                    }) {
                        AnimatedContent(targetState = day, modifier = Modifier.clipToBounds(), transitionSpec = {
                            val direction = if ((targetState > initialState) != rtl) AnimatedContentTransitionScope.SlideDirection.Left else AnimatedContentTransitionScope.SlideDirection.Right
                            (slideIntoContainer(direction, tween(320)) + fadeIn(tween(240))) togetherWith
                                (slideOutOfContainer(direction, tween(320)) + fadeOut(tween(240)))
                        }, label = "journal-date-slide") { date ->
                            Text(if (date == LocalDate.now().toEpochDay()) stringResource(R.string.journal_today) else dayLabel(date))
                        }
                    }
                    if (guides.current == JournalGuide.DATE && navigationEnabled && !settings && !options && dateRequest == null && !imagePreview) {
                        JournalGestureGuide(JournalGuide.DATE, Modifier.align(Alignment.Center))
                    }

                }
            }
            if (!trash) Column(Modifier.padding(horizontal = if(searchMode) 16.dp else 12.dp)) {
                if (capturing) {
                    Text("${durationSeconds / 60}:${(durationSeconds % 60).toString().padStart(2, '0')}")
                    Text(liveText, maxLines = 5)
                    val captureStatus = when(captureState) {
                        JournalSpeech.State.STARTING -> R.string.journal_starting
                        JournalSpeech.State.FINALIZING -> R.string.journal_finalizing
                        JournalSpeech.State.PAUSED -> R.string.journal_paused
                        else -> null
                    }
                    captureStatus?.let { Text(stringResource(it), modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
                    if (recoveryPending) TextButton(onClick = { model.retryRecovery() }) { Text(stringResource(R.string.journal_retry)) }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        LinearProgressIndicator(
                            progress = { audioLevel?.let { ((it + 2f) / 12f).coerceIn(0f, 1f) } ?: 0f },
                            modifier = Modifier.weight(1f).padding(end = 8.dp)
                        )
                        IconButton(onClick = { if(captureState == JournalSpeech.State.PAUSED) model.speech.resume() else model.speech.stop(true) }, enabled = captureState != JournalSpeech.State.FINALIZING) {
                            Icon(if(captureState == JournalSpeech.State.PAUSED) Icons.Outlined.PlayArrow else Icons.Outlined.Pause, stringResource(if(captureState == JournalSpeech.State.PAUSED) R.string.journal_resume else R.string.journal_pause))
                        }
                        IconButton(onClick = { model.speech.stop() }, modifier = Modifier.size(48.dp)) { Icon(Icons.Outlined.Stop, stringResource(R.string.journal_stop)) }
                    }
                } else {
                if (!searchMode) draft?.image?.let { id -> JournalImage(model.media, id, Modifier.height(90.dp).clickable(enabled = editing == null && !busy) { imageTarget = null; imagePreview = true }) }
                AnimatedContent(targetState=searchMode, transitionSpec={
                    (fadeIn(tween(180))+expandHorizontally(tween(220),expandFrom=Alignment.Start)) togetherWith
                        (fadeOut(tween(120))+shrinkHorizontally(tween(220),shrinkTowards=Alignment.Start))
                }, label="journal-search-mode") { mode ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(if(mode) 8.dp else 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    val composerFocus=remember{FocusRequester()}
                    LaunchedEffect(searchMode){if(mode && searchMode)composerFocus.requestFocus()}
                    if(!mode) IconButton(onClick={searchMode=true},enabled=editing==null && !busy && !searchMode,modifier=Modifier.testTag("journal-search-toggle")) {
                        Icon(Icons.Outlined.Search,stringResource(R.string.journal_search_title),tint=cardText)
                    }
                    if(mode) BasicTextField(
                        value=searchText,onValueChange={searchText=it},singleLine=true,
                        enabled=editing==null && !busy && draft!=null && searchMode,
                        modifier=Modifier.weight(1f).heightIn(min=40.dp).focusRequester(composerFocus).testTag("journal-composer")
                            .semantics { contentDescription=resources.getString(R.string.journal_search) }
                            .border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.55f),CircleShape)
                            .padding(horizontal=16.dp,vertical=8.dp),
                        textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
                        cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox={innerTextField->
                            Box(contentAlignment=Alignment.CenterStart){
                                if(searchText.isEmpty())Text(stringResource(R.string.journal_search),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                innerTextField()
                            }
                        },
                    ) else
                    OutlinedTextField(if (mode) searchText else localText, onValueChange = {
                        if (mode) searchText = it else { localText = it; model.change(text = it) }
                    }, modifier = Modifier.weight(1f).heightIn(max = 180.dp).focusRequester(composerFocus).testTag("journal-composer").semantics { contentDescription = resources.getString(if (mode) R.string.journal_search else R.string.journal_input) }, enabled = editing == null && !busy && draft != null && mode == searchMode,
                        colors = composerColors, shape = RoundedCornerShape(24.dp),
                        singleLine = mode,
                        placeholder = { Text(stringResource(if (mode) R.string.journal_search else listOf(R.string.journal_prompt_0, R.string.journal_prompt_1, R.string.journal_prompt_2, R.string.journal_prompt_3)[draft?.prompt ?: 0])) },
                        trailingIcon = if (mode) null else { { IconButton(onClick = { imageTarget = null; model.prepareImagePicker(null); picker.launch("image/*") }, enabled = editing == null && !busy && !searchMode) { Icon(Icons.Outlined.AddPhotoAlternate, stringResource(R.string.journal_add_image), tint = cardText) } } })
                    if(mode) IconButton(onClick=::closeSearch,enabled=searchMode,modifier=Modifier.testTag("journal-search-toggle")) {
                        Box(Modifier.size(40.dp).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=if(searchMode).55f else .25f),CircleShape),contentAlignment=Alignment.Center){
                            Icon(Icons.Outlined.Close,stringResource(R.string.journal_search_close))
                        }
                    }
                    if (!mode) {
                    val canSubmit = localText.isNotBlank() || draft?.image != null
                    Box(Modifier.size(48.dp).testTag("journal-submit").combinedClickable(enabled = editing == null && !busy && draft != null && mode == searchMode,
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
                }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
    dateRequest?.let { request ->
        JournalDateTimeDialog(request.timestamp, request.zone, request.entry != null,
            onDismiss = { dateRequest = null },
            onConfirm = { value ->
                if (request.entry == null) selectDay(Instant.ofEpochMilli(value).atZone(ZoneId.of(request.zone)).toLocalDate().toEpochDay())
                else {
                    if (editing == null) model.beginEdit(request.entry)
                    model.changeEdit(createdAt = value)
                }
                dateRequest = null
            })
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
            if (!model.speech.recognitionAvailable()) {
                Text(stringResource(R.string.journal_speech_unavailable))
            } else if (model.speech.systemProviderAvailable()) {
                Text(stringResource(if (model.speech.onDeviceAvailable()) R.string.journal_provider_choice_disclosure else R.string.journal_network_disclosure))
                Row(verticalAlignment = Alignment.CenterVertically) { Text(stringResource(R.string.journal_provider_choice), Modifier.weight(1f)); Switch(networkConsent, { networkConsent = it }) }
            }
        }
    }, confirmButton = { TextButton(enabled = model.speech.recognitionAvailable() && language.isNotBlank() && (model.speech.onDeviceAvailable() || networkConsent), onClick = { speechOptions = false; permission.launch(android.Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.journal_start)) } }, dismissButton = { TextButton(onClick = { speechOptions = false }) { Text(stringResource(R.string.journal_cancel)) } })
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
