@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.jeerovan.comfer.notes

import android.content.*
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.*
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import com.jeerovan.comfer.ui.rememberThumbReach
import kotlinx.coroutines.*

@Composable internal fun NotesScreen(model:NotesViewModel,close:()->Unit,unlock:((()->Unit)->Unit),locked:Boolean=false) {
    val context=LocalContext.current
    val keyboard=LocalSoftwareKeyboardController.current
    val focusManager=LocalFocusManager.current
    LaunchedEffect(model.ready,model.collection){if(model.ready&&model.collection){focusManager.clearFocus();keyboard?.hide()}}
    var options by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    var labelTargets by remember { mutableStateOf<Set<String>?>(null) }
    var labelEdit by remember { mutableStateOf<NoteLabel?>(null) }
    var recovery by remember { mutableStateOf(false) }
    var preview by remember { mutableStateOf<Note?>(null) }
    var purge by remember { mutableStateOf<List<Note>?>(null) }
    var find by remember { mutableStateOf("") }
    var findOpen by remember { mutableStateOf(false) }
    var matchIndex by remember { mutableIntStateOf(0) }
    var debounced by remember { mutableStateOf("") }
    LaunchedEffect(model.query){delay(300);debounced=model.query}
    var filtered by remember { mutableStateOf(emptyList<Note>()) }
    LaunchedEffect(model.notes,debounced,model.view,model.prefs,model.labels){filtered=withContext(Dispatchers.Default){model.filtered(debounced)}}
    val visible=filtered.filter{(it.deletedAt!=null)==(model.view=="Bin") && (!it.protected||NotesSession.unlocked())}
    fun targets()=model.notes.filter{it.id in model.selected && (!it.protected||NotesSession.unlocked())}
    fun labelsForSelection(){labelTargets=if(model.collection)model.selected else setOfNotNull(model.draft?.note?.id)}
    fun back(){when{model.selected.isNotEmpty()->model.selected=emptySet();!model.collection->model.browse();model.view=="Bin"->model.switchView("Notes");else->close()}}
    if(locked){Surface(Modifier.fillMaxSize()){Column(Modifier.safeDrawingPadding().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){Text("Notes is locked");model.error?.let{Text(it,color=MaterialTheme.colorScheme.error)};Button(onClick={unlock{}}){Text("Unlock")};TextButton(onClick={context.startActivity(Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS))}){Text("Device security settings")}}};return}
    BackHandler(onBack=::back)
    Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.surface.copy(alpha=.8f)) {
        Column(Modifier.safeDrawingPadding().imePadding().fillMaxSize().padding(horizontal=16.dp)) {
            Row(Modifier.heightIn(min=48.dp),verticalAlignment=Alignment.CenterVertically) {
                Text(if(model.collection)model.view else "Notes",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge)
                if(model.collection&&model.view=="Bin") TextButton(onClick={purge=model.notes.filter{it.deletedAt!=null}},enabled=model.notes.any{it.deletedAt!=null}){Text("Empty")}
            }
            model.error?.let{Row(verticalAlignment=Alignment.CenterVertically){Text(it,Modifier.weight(1f),color=MaterialTheme.colorScheme.error);NotesIconButton(onClick={model.error=null}){Icon(Icons.Outlined.Close,"Dismiss error")}}}
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
                    if(visible.isEmpty())Text(if(model.view=="Bin")"Bin is empty" else "No notes",Modifier.align(Alignment.Center),color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
                model.undo?.let{Row(verticalAlignment=Alignment.CenterVertically){Text("Deleted",Modifier.weight(1f));TextButton(onClick={model.undoBatch()}){Text("Undo")}}}
            } else {
                val focus=remember{FocusRequester()}
                LaunchedEffect(model.draft?.id){if(model.draft!=null)focus.requestFocus()}
                LaunchedEffect(model.findTarget,model.findSelection){if(model.findTarget!=null)focus.requestFocus()}
                if(model.recovery)Text("Recovered draft",style=MaterialTheme.typography.labelSmall)
                if(findOpen){
                    val matches=remember(model.editorText,find){NotesSearch.matches(model.editorText,find)}
                    Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(find,{find=it;matchIndex=0},Modifier.weight(1f),placeholder={Text("Find in note")});Text("${if(matches.isEmpty())0 else matchIndex+1}/${matches.size}")
                        NotesIconButton(onClick={if(matches.isNotEmpty()){matchIndex=(matchIndex-1+matches.size)%matches.size;model.selectMatch(matches[matchIndex])}}){Icon(Icons.Outlined.KeyboardArrowUp,"Previous match")}
                        NotesIconButton(onClick={if(matches.isNotEmpty()){matchIndex=(matchIndex+1)%matches.size;model.selectMatch(matches[matchIndex])}}){Icon(Icons.Outlined.KeyboardArrowDown,"Next match")}
                        NotesIconButton(onClick={findOpen=false;model.clearFind()}){Icon(Icons.Outlined.Close,"Close find")}}
                }
                NotesCanvas(model,Modifier.weight(1f).fillMaxWidth().focusRequester(focus))
                Text(model.status,style=MaterialTheme.typography.labelSmall,color=if(model.status=="Couldn't save")MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant)
                if(model.status=="Couldn't save")Row{
                    TextButton(onClick={model.retry()}){Text("Retry")}
                    TextButton(onClick={try{(context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager).setPrimaryClip(ClipData.newPlainText("Note",model.editorText))}catch(_:Exception){model.error="Could not copy this draft. Please retry."}}){Text("Copy draft")}
                    TextButton(onClick={model.saveCopy()}){Text("Save a copy")}
                }
            }
            Row(Modifier.fillMaxWidth().heightIn(min=48.dp).testTag("notes-action-bar").clickable{keyboard?.hide();options=true},verticalAlignment=Alignment.CenterVertically) {
                if(model.collection) {
                    if(model.selected.isNotEmpty()) {
                        NotesIconButton(onClick={model.selected=emptySet()}){Icon(Icons.Outlined.Close,"Clear selection")}
                        if(model.view=="Bin"){
                            NotesIconButton(onClick={model.batch(targets()){it.copy(deletedAt=null)}}){Icon(Icons.Outlined.Restore,"Restore selected")}
                            NotesIconButton(onClick={purge=targets()}){Icon(Icons.Outlined.DeleteForever,"Delete selected permanently")}
                        } else {
                            val allPinned=targets().all{it.pinned}
                            NotesIconButton(onClick={model.batch(targets()){it.copy(pinned=!allPinned)}}){Icon(Icons.Outlined.PushPin,if(allPinned)"Unpin selected" else "Pin selected")}
                            NotesIconButton(onClick={colors=true}){Icon(Icons.Outlined.Palette,"Note color")}
                            NotesIconButton(onClick={labelsForSelection()}){Icon(Icons.Outlined.Label,"Manage labels")}
                            NotesIconButton(onClick={model.batch(targets()){it.copy(deletedAt=System.currentTimeMillis())}}){Icon(Icons.Outlined.Delete,"Move selected to Bin")}
                        }
                    }
                } else {
                    NotesIconButton(onClick={keyboard?.hide();model.browse()}){Icon(Icons.Outlined.ArrowBack,"All notes")}
                    NotesIconButton(onClick={model.undoEdit()}){Icon(Icons.Outlined.Undo,"Undo edit")}
                    NotesIconButton(onClick={model.redoEdit()}){Icon(Icons.Outlined.Redo,"Redo edit")}
                }
                Spacer(Modifier.weight(1f))
                NotesIconButton(onClick={keyboard?.hide();options=true}){Icon(Icons.Outlined.MoreVert,"Notes options",tint=Color.Gray)}
            }
            if(model.collection) Row(Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("notes-search-row"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
                OutlinedTextField(model.query,{model.query=it;model.selected=emptySet()},Modifier.weight(1f).testTag("notes-search"),placeholder={Text(if(model.view=="Bin")"Search Bin" else "Search notes")},singleLine=true,shape=CircleShape,
                    colors=OutlinedTextFieldDefaults.colors(focusedBorderColor=Color.Gray.copy(alpha=.55f),unfocusedBorderColor=Color.Gray.copy(alpha=.55f)))
                if(model.query.isEmpty())NotesIconButton(onClick={model.capture()}){Icon(Icons.Outlined.Add,"New note")}
                else NotesIconButton(onClick={model.query="";model.selected=emptySet()}){Icon(Icons.Outlined.Close,"Clear search")}
            }
        }
    }
    if(options) ModalBottomSheet(onDismissRequest={options=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.padding(horizontal=20.dp).padding(bottom=24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            if(!model.collection){
                TextButton(onClick={options=false;model.insertChecklist()}){Text("Insert checkbox")}
                TextButton(onClick={options=false;findOpen=true}){Text("Find in note")}
                TextButton(onClick={options=false;labelsForSelection()}){Text("Manage labels")}
            }
            Text("Sort",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) { listOf("updated" to "Updated","created" to "Created","title" to "Title","manual" to "Manual").forEach{(sort,label)->FilterChip(model.prefs.sort==sort,{model.updatePreferences(model.prefs.copy(sort=sort))},{Text(label)})} }
            Row(verticalAlignment=Alignment.CenterVertically){Text("Grid view",Modifier.weight(1f));Switch(model.prefs.grid,{model.updatePreferences(model.prefs.copy(grid=it))},Modifier.scale(.7f))}
            Row(verticalAlignment=Alignment.CenterVertically){Text("Protect Notes",Modifier.weight(1f));Switch(model.prefs.moduleLocked,{value->options=false;unlock{model.updatePreferences(model.prefs.copy(moduleLocked=value))}},Modifier.scale(.7f))}
            ListItem(headlineContent={Text(if(model.view=="Bin")"Notes" else "Bin")},supportingContent={Text(if(model.view=="Bin")"Return to your notes" else "Deleted notes are kept for 7 days")},modifier=Modifier.clickable{options=false;if(!model.collection)model.browse();model.switchView(if(model.view=="Bin")"Notes" else "Bin")},colors=ListItemDefaults.colors(containerColor=Color.Transparent))
            if(model.recoveredDrafts.isNotEmpty())TextButton(onClick={options=false;recovery=true}){Text("Recovered drafts (${model.recoveredDrafts.size})")}
        }
    }
    if(colors) ModalBottomSheet(onDismissRequest={colors=false}) {
        Text("Note background",Modifier.padding(20.dp),style=MaterialTheme.typography.titleMedium)
        FlowRow(Modifier.padding(20.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){noteColors.forEach{(id,name)->
            AssistChip(onClick={model.batch(targets()){it.copy(color=id)};colors=false},label={Text(name)},colors=AssistChipDefaults.assistChipColors(containerColor=noteColor(id)))
        }}
    }
    labelTargets?.let{ids->ModalBottomSheet(onDismissRequest={labelTargets=null}) {
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Labels",style=MaterialTheme.typography.titleMedium)
            val notes=if(model.collection)model.notes.filter{it.id in ids} else listOfNotNull(model.draft?.note)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                model.labels.filter{it.tag}.forEach{label->
                    val checked=notes.isNotEmpty()&&notes.all{label.id in it.tags}
                    Surface(shape=MaterialTheme.shapes.large,color=Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface,border=BorderStroke(1.dp,Color.Gray.copy(alpha=.55f))) {
                        Row(verticalAlignment=Alignment.CenterVertically) {
                            Box(Modifier.size(48.dp).semantics{contentDescription="Select label ${label.name}"}
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
                Surface(onClick={labelEdit=NoteLabel(name="",tag=true)},shape=MaterialTheme.shapes.large,color=Color.Transparent,contentColor=MaterialTheme.colorScheme.onSurface,border=BorderStroke(1.dp,Color.Gray.copy(alpha=.55f))) {
                    Box(Modifier.size(48.dp),contentAlignment=Alignment.Center){Icon(Icons.Outlined.Add,"Add label")}
                }
            }
        }
    }}
    labelEdit?.let{label->var name by remember(label){mutableStateOf(label.name)}
        ModalBottomSheet(onDismissRequest={labelEdit=null}){Row(Modifier.padding(20.dp),verticalAlignment=Alignment.CenterVertically){OutlinedTextField(name,{name=it},Modifier.weight(1f),label={Text("Label")});if(label.name.isNotEmpty())NotesIconButton(onClick={model.removeLabel(label);labelEdit=null;labelTargets=null}){Icon(Icons.Outlined.Delete,"Delete label; keep notes")};NotesIconButton(onClick={model.saveLabel(label.copy(name=name));labelEdit=null},enabled=name.isNotBlank()){Icon(Icons.Outlined.Check,"Save name")}}}
    }
    preview?.let{note->AlertDialog(onDismissRequest={preview=null},title={Text(note.content.preview)},text={Column(Modifier.verticalScroll(rememberScrollState())){Text(note.content.asText().text)}},confirmButton={TextButton(onClick={model.batch(listOf(note)){it.copy(deletedAt=null)};preview=null}){Text("Restore")}},dismissButton={NotesIconButton(onClick={preview=null}){Icon(Icons.Outlined.Close,"Close")}})}
    purge?.let{notes->AlertDialog(onDismissRequest={purge=null},title={Text("Permanently delete ${notes.size} notes?")},text={Text("This cannot be undone.")},confirmButton={TextButton(onClick={model.purge(notes);purge=null;model.selected=emptySet()}){Text("Delete")}},dismissButton={NotesIconButton(onClick={purge=null}){Icon(Icons.Outlined.Close,"Cancel")}})}
    if(recovery)ModalBottomSheet(onDismissRequest={recovery=false}){LazyColumn(Modifier.padding(20.dp)){items(model.recoveredDrafts,key={it.id}){draft->TextButton(onClick={model.resumeDraft(draft.id);recovery=false}){Text(draft.note.content.preview.ifBlank{"Untitled draft"})}}}}
}
private fun Set<String>.toggle(id:String)=if(id in this)this-id else this+id
internal val noteColors=listOf("default" to "Default","rose" to "Rose","amber" to "Amber","green" to "Green","blue" to "Blue","violet" to "Violet")
@Composable internal fun noteColor(id:String):Color {
    val base=MaterialTheme.colorScheme.surfaceContainer
    val tint=when(id){"rose"->Color(0xFFE57373);"amber"->Color(0xFFFFCA28);"green"->Color(0xFF66BB6A);"blue"->Color(0xFF42A5F5);"violet"->Color(0xFFAB47BC);else->return base}
    return androidx.compose.ui.graphics.lerp(base,tint,.22f)
}
@Composable private fun NoteCard(note:Note,query:String,labels:List<NoteLabel>,selected:Boolean){
    Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(selected)MaterialTheme.colorScheme.secondaryContainer else noteColor(note.color))){Column(Modifier.padding(12.dp)){
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(note.content.preview.ifBlank{"Untitled"},Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2)
            if(note.pinned)Icon(Icons.Outlined.PushPin,"Pinned",Modifier.size(18.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val text=note.content.asText().text
        val matches=remember(text,query){NotesSearch.matches(text,query)}
        val start=(matches.firstOrNull()?.start?.minus(40)?:0).coerceAtLeast(0);val end=(start+200).coerceAtMost(text.length)
        val display=Regex("(?m)^\\[([ xX])\\] ").replace(text){if(it.groupValues[1]==" ")"☐   " else "☑   "}
        if(text.isNotEmpty())Text(buildAnnotatedString{append(display.substring(start,end));matches.filter{it.start>=start&&it.end<=end}.forEach{addStyle(SpanStyle(background=Color.Yellow.copy(alpha=.25f)),it.start-start,it.end-start)}},maxLines=4)
        if(note.tags.isNotEmpty())FlowRow(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalArrangement=Arrangement.spacedBy(4.dp),modifier=Modifier.padding(top=8.dp)){labels.filter{it.id in note.tags}.forEach{label->Surface(shape=MaterialTheme.shapes.small,color=MaterialTheme.colorScheme.secondaryContainer.copy(alpha=.65f)){Text(label.name,Modifier.padding(horizontal=10.dp,vertical=4.dp),style=MaterialTheme.typography.labelMedium)}}}
    }}
}

@Composable private fun NotesIconButton(onClick:()->Unit,enabled:Boolean=true,content:@Composable ()->Unit) {
    IconButton(onClick=onClick,enabled=enabled) {
        Box(Modifier.size(40.dp).border(1.dp,Color.Gray.copy(alpha=if(enabled).55f else .25f),CircleShape),contentAlignment=Alignment.Center) {
            content()
        }
    }
}
