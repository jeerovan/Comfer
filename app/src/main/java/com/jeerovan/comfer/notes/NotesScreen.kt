@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.jeerovan.comfer.notes

import com.jeerovan.comfer.localizedModuleMessage
import androidx.compose.ui.platform.LocalResources
import com.jeerovan.comfer.R
import android.content.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.rememberThumbReach
import kotlinx.coroutines.*

@Composable internal fun NotesScreen(model:NotesViewModel,close:()->Unit,unlock:((()->Unit)->Unit),locked:Boolean=false,pickImage:()->Unit={}) {
    val resources=LocalResources.current
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val view=LocalView.current
    val focusManager=LocalFocusManager.current
    LaunchedEffect(model.ready,model.collection){if(model.ready&&model.collection){focusManager.clearFocus();keyboard?.hide()}}
    var options by remember { mutableStateOf(false) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    var labelTargets by remember { mutableStateOf<Set<String>?>(null) }
    var labelEdit by remember { mutableStateOf<NoteLabel?>(null) }
    var recovery by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Note?>(null) }
    var purge by remember { mutableStateOf<List<Note>?>(null) }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(model.query){delay(300);debounced=model.query}
    var filtered by remember { mutableStateOf(emptyList<Note>()) }
    LaunchedEffect(model.notes,debounced,model.view,model.prefs,model.labels){filtered=withContext(Dispatchers.Default){model.filtered(debounced)}}
    val visible=filtered.filter{(it.deletedAt!=null)==(model.view=="Bin") && (!it.protected||NotesSession.unlocked())}
    fun targets()=model.notes.filter{it.id in model.selected && (!it.protected||NotesSession.unlocked())}
    fun labelsForSelection(){labelTargets=if(model.collection)model.selected else setOfNotNull(model.draft?.note?.id)}
    fun closeSearch(){
        // Dismiss before removing the field and its text-input session.
        keyboard?.hide()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager)
            ?.hideSoftInputFromWindow(view.windowToken,0)
        focusManager.clearFocus(force=true)
        searching=false;model.query="";debounced="";model.selected=emptySet()
    }
    LaunchedEffect(model.selected.isNotEmpty()) {
        if(model.selected.isNotEmpty()) {
            focusManager.clearFocus(force=true)
            keyboard?.hide()
        }
    }
    fun back(){when{model.selected.isNotEmpty()->model.selected=emptySet();!model.collection->model.browse();searching->closeSearch();model.view=="Bin"->model.switchView("Notes");else->close()}}
    if(locked){Surface(Modifier.fillMaxSize()){Column(Modifier.safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text(resources.getString(R.string.module_notes_is_locked));model.error?.let{Text(localizedModuleMessage(resources,it),color=MaterialTheme.colorScheme.error)};Button(onClick={unlock{}}){Text(resources.getString(R.string.module_unlock))};TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))}){Text(resources.getString(R.string.journal_device_security))}}};return}
    BackHandler(onBack=::back)
    // A translucent color no longer matches contentColorFor(surface); provide its pair explicitly.
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface.copy(alpha=.8f),contentColor=MaterialTheme.colorScheme.onSurface) {
        Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().padding(horizontal=16.dp)) {
            Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(model.collection&&model.view=="Bin") resources.getString(R.string.module_bin) else resources.getString(R.string.workspace_notes),Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                if(model.collection&&model.view=="Bin") TextButton(onClick={purge=model.notes.filter{it.deletedAt!=null}},enabled=model.notes.any{it.deletedAt!=null}){Text(resources.getString(R.string.module_empty))}
                if(model.collection)NotesIconButton(onClick={keyboard?.hide();options=true}){Icon(Icons.Outlined.MoreVert,resources.getString(R.string.module_notes_options),tint=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
            model.error?.let{Row(verticalAlignment=Alignment.CenterVertically){Text(localizedModuleMessage(resources,it),Modifier.weight(1f),color=MaterialTheme.colorScheme.error);NotesIconButton(onClick={model.error=null}){Icon(Icons.Outlined.Close,resources.getString(R.string.module_dismiss_error))}}}
            if(!model.ready) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){CircularProgressIndicator()}
            else if(model.collection) {
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
                    val reach=rememberThumbReach((maxHeight-360.dp).coerceAtLeast(0.dp),model.view)
                    val padding=with(LocalDensity.current){reach.offset.toDp()}
                    NotesReorderGrid(visible,model.prefs.grid,model.view!="Bin"&&model.query.isBlank(),model::reorder,
                        onOpen={note->if(model.selected.isNotEmpty())model.selected=model.selected.toggle(note.id) else if(note.deletedAt!=null)preview=note else model.open(note,debounced)},
                        onSelect={note->model.selected=model.selected.toggle(note.id)},
                        modifier=Modifier.nestedScroll(reach).fillMaxSize(),topPadding=padding,
                        onDragStart={model.selected=emptySet()},onPosition=model::persistBrowse,
                        initialIndex=model.prefs.scrollIndex,initialOffset=model.prefs.scrollOffset) {note->
                        NoteCard(note,debounced,model.labels,note.id in model.selected)
                    }
                    if(visible.isEmpty())Text(if(model.view=="Bin")resources.getString(R.string.module_bin_is_empty) else resources.getString(R.string.module_no_notes),Modifier.align(Alignment.Center),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                model.undo?.let{Row(verticalAlignment=Alignment.CenterVertically){Text(resources.getString(R.string.tasks_deleted),Modifier.weight(1f));TextButton(onClick={model.undoBatch()}){Text(resources.getString(R.string.tasks_undo))}}}
            } else {
                val focus=remember{FocusRequester()}
                LaunchedEffect(model.draft?.id){if(model.draft!=null)focus.requestFocus()}
                LaunchedEffect(model.findTarget,model.findSelection){if(model.findTarget!=null)focus.requestFocus()}
                if(model.recovery)Text(resources.getString(R.string.module_recovered_draft),style=MaterialTheme.typography.labelSmall)
                NotesCanvas(model,Modifier.weight(1f).fillMaxWidth().focusRequester(focus))
                if(model.status.isNotEmpty()&&model.status!="Saved")Text(when(model.status){"Saving"->resources.getString(R.string.module_saving);"Saved"->resources.getString(R.string.tasks_saved);else->resources.getString(R.string.module_couldn_t_save)},style=MaterialTheme.typography.labelSmall,color=if(model.status=="Couldn't save")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if(model.status=="Couldn't save")Row{
                    TextButton(onClick={model.retry()}){Text(resources.getString(R.string.module_retry))}
                    TextButton(onClick={try{(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText(resources.getString(R.string.module_note),model.editorText))}catch(_:Exception){model.error="Could not copy this draft. Please retry."}}){Text(resources.getString(R.string.module_copy_draft))}
                    TextButton(onClick={model.saveCopy()}){Text(resources.getString(R.string.module_save_a_copy))}
                }
            }
            if(!model.collection)NotesEditorToolbar(model,onLabels={labelsForSelection()},onImage=pickImage)
            else AnimatedContent(
                targetState=model.selected.isNotEmpty(),
                modifier=Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("notes-bottom-actions"),
                contentAlignment=Alignment.BottomCenter,
                transitionSpec={
                    (fadeIn(tween(180))+slideInVertically(tween(220)){it/2}) togetherWith
                        (fadeOut(tween(120))+slideOutVertically(tween(220)){-it/2})
                },
                label="notes-selection-actions",
            ) { selecting ->
            if(selecting)Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("notes-action-bar"),horizontalArrangement=Arrangement.Center,verticalAlignment=Alignment.CenterVertically) {
                NotesIconButton(onClick={model.selected=emptySet()}){Icon(Icons.Outlined.Close,resources.getString(R.string.module_clear_selection))}
                if(model.view=="Bin"){
                    NotesIconButton(onClick={model.batch(targets()){it.copy(deletedAt=null)}}){Icon(Icons.Outlined.Restore,resources.getString(R.string.module_restore_selected))}
                    NotesIconButton(onClick={purge=targets()}){Icon(Icons.Outlined.DeleteForever,resources.getString(R.string.module_delete_selected_permanently))}
                } else {
                    val allPinned=targets().all{it.pinned}
                    NotesIconButton(onClick={model.batch(targets()){it.copy(pinned=!allPinned)}}){Icon(Icons.Outlined.PushPin,if(allPinned)resources.getString(R.string.module_unpin_selected) else resources.getString(R.string.module_pin_selected))}
                    NotesIconButton(onClick={colors=true}){Icon(Icons.Outlined.Palette,resources.getString(R.string.module_note_color))}
                    NotesIconButton(onClick={labelsForSelection()}){Icon(Icons.Outlined.Label,resources.getString(R.string.module_manage_labels))}
                    NotesIconButton(onClick={model.batch(targets()){it.copy(deletedAt=System.currentTimeMillis())}}){Icon(Icons.Outlined.Delete,resources.getString(R.string.module_move_selected_to_bin))}
                }
            }
            else Row(Modifier.fillMaxWidth().testTag("notes-search-row"),horizontalArrangement=Arrangement.spacedBy(8.dp,Alignment.CenterHorizontally),verticalAlignment=Alignment.CenterVertically) {
                if(searching) {
                    val searchFocus=remember{FocusRequester()}
                    LaunchedEffect(Unit){searchFocus.requestFocus()}
                    BasicTextField(
                        value=model.query,
                        onValueChange={model.query=it;model.selected=emptySet()},
                        modifier=Modifier.weight(1f).heightIn(min=40.dp).testTag("notes-search").focusRequester(searchFocus)
                            .background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.3f),CircleShape)
                            .padding(horizontal=16.dp,vertical=8.dp),
                        singleLine=true,
                        textStyle=MaterialTheme.typography.bodyMedium.copy(color=MaterialTheme.colorScheme.onSurface),
                        cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox={innerTextField->
                            Box(contentAlignment=Alignment.CenterStart) {
                                if(model.query.isEmpty())Text(if(model.view=="Bin")resources.getString(R.string.module_search_bin) else resources.getString(R.string.module_search_notes),style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                innerTextField()
                            }
                        },
                    )
                    NotesIconButton(onClick=::closeSearch){Icon(Icons.Outlined.Close,resources.getString(R.string.tasks_close_search))}
                } else {
                    NotesIconButton(onClick={searching=true}){Icon(Icons.Outlined.Search,resources.getString(R.string.module_search_notes))}
                    NotesIconButton(onClick={model.capture()}){Icon(Icons.Outlined.Add,resources.getString(R.string.module_new_note))}
                }
            }
            }
        }
    }
    if(options&&model.collection) ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={options=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.padding(horizontal=20.dp).padding(bottom=24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(resources.getString(R.string.tasks_sort),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("updated" to R.string.module_updated,"created" to R.string.module_created,"title" to R.string.ui_title,"manual" to R.string.module_manual).forEach{(sort,label)->FilterChip(model.prefs.sort==sort,{model.updatePreferences(model.prefs.copy(sort=sort))},{Text(resources.getString(label))})} }
            Row(verticalAlignment=Alignment.CenterVertically){Text(resources.getString(R.string.module_grid_view),Modifier.weight(1f));Switch(model.prefs.grid,{model.updatePreferences(model.prefs.copy(grid=it))},Modifier.scale(.7f))}
            Row(verticalAlignment=Alignment.CenterVertically){Text(resources.getString(R.string.module_protect_notes),Modifier.weight(1f));Switch(model.prefs.moduleLocked,{value->options=false;unlock{model.updatePreferences(model.prefs.copy(moduleLocked=value))}},Modifier.scale(.7f))}
            ListItem(headlineContent={Text(if(model.view=="Bin") resources.getString(R.string.workspace_notes) else resources.getString(R.string.module_bin))},supportingContent={Text(if(model.view=="Bin")resources.getString(R.string.module_return_to_your_notes) else resources.getString(R.string.module_deleted_notes_are_kept_for_7_days))},modifier=Modifier.clickable{options=false;if(!model.collection)model.browse();model.switchView(if(model.view=="Bin")"Notes" else "Bin")},colors=ListItemDefaults.colors(containerColor=Color.Transparent))
            if(model.recoveredDrafts.isNotEmpty())TextButton(onClick={options=false;recovery=true}){Text(resources.getString(R.string.module_recovered_drafts_1_d,model.recoveredDrafts.size))}
        }
    }
    if(colors) ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={colors=false}) {
        Text(resources.getString(R.string.module_note_background),Modifier.padding(20.dp),style=MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){noteColors.forEach{(id,name)->
            AssistChip(onClick={model.batch(targets()){it.copy(color=id)};colors=false},label={Text(resources.getString(name))},colors=AssistChipDefaults.assistChipColors(containerColor=noteColor(id),labelColor=MaterialTheme.colorScheme.onSurface))
        }}
    }
    labelTargets?.let{ids->ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={labelTargets=null}) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(resources.getString(R.string.module_labels),style=MaterialTheme.typography.titleMedium)
            val notes=if(model.collection)model.notes.filter{it.id in ids} else listOfNotNull(model.draft?.note)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                model.labels.filter{it.tag}.forEach{label->
                    val checked=notes.isNotEmpty()&&notes.all{label.id in it.tags}
                    Surface(shape=MaterialTheme.shapes.large,color=Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.55f))) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).semantics{contentDescription=resources.getString(R.string.module_select_label_1_s,label.name)}
                                .toggleable(value=checked,role=Role.Checkbox,onValueChange={
                                    if(model.collection)model.toggleLabel(notes,label)
                                    else model.editMetadata{it.copy(tags=it.tags.toggle(label.id))}
                                }),contentAlignment=Alignment.Center) {
                                Icon(if(checked)Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,null)
                            }
                            Box(Modifier.widthIn(max=220.dp).heightIn(min=48.dp).clickable{labelEdit=label}.padding(end=16.dp),contentAlignment=Alignment.CenterStart) {
                                Text(label.name,style=MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
                Surface(onClick={labelEdit=NoteLabel(name="",tag=true)},shape=MaterialTheme.shapes.large,color=Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.55f))) {
                    Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Add,resources.getString(R.string.module_add_label))}
                }
            }
        }
    }}
    labelEdit?.let{label->var name by remember(label){mutableStateOf(label.name)}
        ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={labelEdit=null}){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(name,{name=it},Modifier.weight(1f),label={Text(resources.getString(R.string.module_label))});if(label.name.isNotEmpty())NotesIconButton(onClick={model.removeLabel(label);labelEdit=null;labelTargets=null}){Icon(Icons.Outlined.Delete,resources.getString(R.string.module_delete_label_keep_notes))};NotesIconButton(onClick={model.saveLabel(label.copy(name=name));labelEdit=null},enabled=name.isNotBlank()){Icon(Icons.Outlined.Check,resources.getString(R.string.module_save_name))}}}
    }
    preview?.let{note->AlertDialog(onDismissRequest={preview=null},title={Text(note.content.preview)},text={Column(Modifier.verticalScroll(rememberScrollState())){Text(note.content.asText().text)}},confirmButton={TextButton(onClick={model.batch(listOf(note)){it.copy(deletedAt=null)};preview=null}){Text(resources.getString(R.string.journal_restore))}},dismissButton={NotesIconButton(onClick={preview=null}){Icon(Icons.Outlined.Close,resources.getString(R.string.journal_close))}})}
    purge?.let{notes->AlertDialog(onDismissRequest={purge=null},title={Text(resources.getString(R.string.module_permanently_delete_1_d_notes,notes.size))},text={Text(resources.getString(R.string.module_this_cannot_be_undone))},confirmButton={TextButton(onClick={model.purge(notes);purge=null;model.selected=emptySet()}){Text(resources.getString(R.string.ui_delete))}},dismissButton={NotesIconButton(onClick={purge=null}){Icon(Icons.Outlined.Close,resources.getString(R.string.tasks_cancel))}})}
    if(recovery)ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={recovery=false}){LazyColumn(Modifier.padding(20.dp)){items(model.recoveredDrafts,key={it.id}){draft->TextButton(onClick={model.resumeDraft(draft.id);recovery=false}){Text(draft.note.content.preview.ifBlank{resources.getString(R.string.module_untitled_draft)})}}}}
}
private fun Set<String>.toggle(id:String)=if(id in this)this-id else this+id
internal val noteColors=listOf("default" to R.string.module_default,"rose" to R.string.module_rose,"amber" to R.string.module_amber,"green" to R.string.module_green,"blue" to R.string.module_blue,"violet" to R.string.module_violet)
@Composable internal fun noteColor(id:String):Color {
    val base=MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
    val tint=when(id){"rose"->Color(0xFFE57373);"amber"->Color(0xFFFFCA28);"green"->Color(0xFF66BB6A);"blue"->Color(0xFF42A5F5);"violet"->Color(0xFFAB47BC);else->return base}
    return androidx.compose.ui.graphics.lerp(base,tint,.22f)
}
@Composable private fun NoteCard(note:Note,query:String,labels:List<NoteLabel>,selected:Boolean){
    val resources=LocalResources.current
    val colors=MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth().semantics{this.selected=selected},border=if(selected)BorderStroke(2.dp,Color(0xFF9E9E9E)) else null,colors=CardDefaults.cardColors(containerColor=noteColor(note.color),contentColor=colors.onSurface)){Column(Modifier.padding(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(note.content.preview.ifBlank{resources.getString(R.string.module_untitled)},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2)
            if(note.pinned)Icon(Icons.Outlined.PushPin,resources.getString(R.string.module_pinned),Modifier.size(18.dp),tint=colors.onSurfaceVariant)
        }
        val text=note.content.asText().text
        val matches=remember(text,query){NotesSearch.matches(text,query)}
        val start=(matches.firstOrNull()?.start?.minus(40)?:0).coerceAtLeast(0);val end=(start+200).coerceAtMost(text.length)
        val display=Regex("(?m)^\\[([ xX])\\] ").replace(text){if(it.groupValues[1]==" ")"○   " else "●   "}
        if(text.isNotEmpty())Text(buildAnnotatedString{append(display.substring(start,end));matches.filter{it.start>=start&&it.end<=end}.forEach{addStyle(SpanStyle(background=colors.tertiaryContainer,color=colors.onTertiaryContainer),it.start-start,it.end-start)}},maxLines=4)
        if(note.tags.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.padding(top=8.dp)){labels.filter{it.id in note.tags}.forEach{label->Surface(shape=MaterialTheme.shapes.small,color=Color.Transparent,contentColor=colors.onSurfaceVariant,border=BorderStroke(1.dp,colors.outlineVariant)){Text(label.name,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium)}}}
    }}
}

@Composable internal fun NotesIconButton(onClick:()->Unit,enabled:Boolean=true,content:@Composable ()->Unit) {
    com.jeerovan.comfer.ui.ModuleIconButton(onClick=onClick,enabled=enabled,content=content)
}
