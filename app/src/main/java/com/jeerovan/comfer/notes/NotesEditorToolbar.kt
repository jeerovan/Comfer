@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.jeerovan.comfer.notes

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable internal fun NotesEditorToolbar(model:NotesViewModel,onBack:()->Unit,onLabels:()->Unit,onImage:()->Unit) {
    var formatting by remember{mutableStateOf(false)}
    var link by remember{mutableStateOf(false)}
    val uriHandler=LocalUriHandler.current
    val keyboard=LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("notes-editor-toolbar"),verticalAlignment=Alignment.CenterVertically) {
        NotesIconButton(onClick=onBack){Icon(Icons.Outlined.ArrowBack,"All notes")}
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("notes-editor-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            EditorButtonGroup {
                IconButton(onClick={model.undoEdit()}){Icon(Icons.Outlined.Undo,"Undo edit")}
                IconButton(onClick={model.redoEdit()}){Icon(Icons.Outlined.Redo,"Redo edit")}
            }
            NotesIconButton(onClick={keyboard?.hide();onLabels()}){Icon(Icons.Outlined.Label,"Manage labels")}
            NotesIconButton(onClick={model.insertChecklist()}){Icon(Icons.Outlined.RadioButtonChecked,"Insert checklist")}
            NotesIconButton(onClick={keyboard?.hide();formatting=true}){Text("Aa",Modifier.semantics{contentDescription="Text formatting"},style=MaterialTheme.typography.titleMedium)}
            EditorButtonGroup {
                IconButton(onClick={model.command("number")}){Icon(Icons.Outlined.FormatListNumbered,"Numbered list")}
                IconButton(onClick={model.command("bullet")}){Icon(Icons.Outlined.FormatListBulleted,"Bulleted list")}
            }
            NotesIconButton(onClick={keyboard?.hide();onImage()},enabled=!model.importingImage){Icon(Icons.Outlined.AddPhotoAlternate,if(model.importingImage)"Adding image" else "Add image")}
        }
    }
    if(formatting)ModalBottomSheet(onDismissRequest={formatting=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("Text formatting",style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("title" to "Title","heading" to "Heading","subheading" to "Subheading","body" to "Body").forEach{(style,label)->
                    AssistChip(onClick={model.format("paragraph",style)},label={Text(label)})
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                NotesIconButton(onClick={model.format("bold")}){Icon(Icons.Outlined.FormatBold,"Bold")}
                NotesIconButton(onClick={model.format("italic")}){Icon(Icons.Outlined.FormatItalic,"Italic")}
                NotesIconButton(onClick={model.format("underline")}){Icon(Icons.Outlined.FormatUnderlined,"Underline")}
                NotesIconButton(onClick={model.format("strike")}){Icon(Icons.Outlined.StrikethroughS,"Strikethrough")}
            }
            Text("Text color",style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                NoteFormatting.colors.forEach{color->
                    NotesIconButton(onClick={model.format("color",color)}){
                        Icon(if(color=="default")Icons.Outlined.FormatColorReset else Icons.Outlined.Circle,"Text color $color",tint=if(color=="default")MaterialTheme.colorScheme.onSurface else noteTextColor(color,MaterialTheme.colorScheme.surface))
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                NotesIconButton(onClick={formatting=false;link=true}){Icon(Icons.Outlined.Link,"URL")}
                Text("URL")
                val current=model.marks.firstOrNull{it.kind=="url"&&model.canvasSelection.start in it.start..it.end}
                if(current!=null)NotesIconButton(onClick={runCatching{uriHandler.openUri(current.value)}.onFailure{model.error="No browser is available to open this link"}}){Icon(Icons.Outlined.OpenInNew,"Open link")}
            }
        }
    }
    if(link){
        var text by remember{mutableStateOf(model.selectedText())}
        var url by remember{mutableStateOf(model.marks.firstOrNull{it.kind=="url"&&model.canvasSelection.start in it.start..it.end}?.value.orEmpty())}
        var invalid by remember{mutableStateOf(false)}
        ModalBottomSheet(onDismissRequest={link=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text("Link text")})
                OutlinedTextField(url,{url=it;invalid=false},Modifier.fillMaxWidth(),label={Text("URL")},singleLine=true,isError=invalid,supportingText={if(invalid)Text("Enter a valid http or https URL")})
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    NotesIconButton(onClick={link=false}){Icon(Icons.Outlined.Close,"Cancel link")}
                    NotesIconButton(onClick={if(NoteFormatting.validUrl(url.trim())){model.command("url",url.trim(),text);link=false}else invalid=true}){Icon(Icons.Outlined.Check,"Save link")}
                }
            }
        }
    }
}
@Composable private fun EditorButtonGroup(content:@Composable RowScope.()->Unit) {
    Box(contentAlignment=Alignment.Center) {
        // Match the single buttons' 40 dp outline while preserving 48 dp touch targets.
        Box(Modifier.matchParentSize().padding(vertical=4.dp).border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.55f),CircleShape))
        Row(verticalAlignment=Alignment.CenterVertically,content=content)
    }
}
