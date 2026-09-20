@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.jeerovan.comfer.notes

import androidx.compose.ui.platform.LocalResources
import com.jeerovan.comfer.R
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

@Composable internal fun NotesEditorToolbar(model:NotesViewModel,onLabels:()->Unit,onImage:()->Unit) {
    val resources=LocalResources.current
    var formatting by remember{mutableStateOf(false)}
    var link by remember{mutableStateOf(false)}
    val uriHandler=LocalUriHandler.current
    val keyboard=LocalSoftwareKeyboardController.current
    Row(Modifier.fillMaxWidth().padding(bottom=8.dp).testTag("notes-editor-toolbar"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).testTag("notes-editor-actions"),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            EditorButtonGroup {
                IconButton(onClick={model.undoEdit()}){Icon(Icons.Outlined.Undo,resources.getString(R.string.module_undo_edit))}
                IconButton(onClick={model.redoEdit()}){Icon(Icons.Outlined.Redo,resources.getString(R.string.module_redo_edit))}
            }
            NotesIconButton(onClick={keyboard?.hide();onLabels()}){Icon(Icons.Outlined.Label,resources.getString(R.string.module_manage_labels))}
            NotesIconButton(onClick={model.insertChecklist()}){Icon(Icons.Outlined.RadioButtonChecked,resources.getString(R.string.module_insert_checklist))}
            NotesIconButton(onClick={keyboard?.hide();formatting=true}){Text("Aa",Modifier.semantics{contentDescription=resources.getString(R.string.module_text_formatting)},style=MaterialTheme.typography.titleMedium)}
            EditorButtonGroup {
                IconButton(onClick={model.command("number")}){Icon(Icons.Outlined.FormatListNumbered,resources.getString(R.string.module_numbered_list))}
                IconButton(onClick={model.command("bullet")}){Icon(Icons.Outlined.FormatListBulleted,resources.getString(R.string.module_bulleted_list))}
            }
            NotesIconButton(onClick={keyboard?.hide();onImage()},enabled=!model.importingImage){Icon(Icons.Outlined.AddPhotoAlternate,if(model.importingImage)resources.getString(R.string.module_adding_image) else resources.getString(R.string.journal_add_image))}
        }
    }
    if(formatting)ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={formatting=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
        Column(Modifier.fillMaxWidth().padding(horizontal=20.dp).padding(bottom=24.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text(resources.getString(R.string.module_text_formatting),style=MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                listOf("title" to R.string.ui_title,"heading" to R.string.module_heading,"subheading" to R.string.module_subheading,"body" to R.string.module_body).forEach{(style,label)->
                    AssistChip(onClick={model.format("paragraph",style)},label={Text(resources.getString(label))})
                }
            }
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                NotesIconButton(onClick={model.format("bold")}){Icon(Icons.Outlined.FormatBold,resources.getString(R.string.font_weight_bold))}
                NotesIconButton(onClick={model.format("italic")}){Icon(Icons.Outlined.FormatItalic,resources.getString(R.string.module_italic))}
                NotesIconButton(onClick={model.format("underline")}){Icon(Icons.Outlined.FormatUnderlined,resources.getString(R.string.module_underline))}
                NotesIconButton(onClick={model.format("strike")}){Icon(Icons.Outlined.StrikethroughS,resources.getString(R.string.module_strikethrough))}
            }
            Text(resources.getString(R.string.module_text_color),style=MaterialTheme.typography.labelLarge)
            FlowRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                NoteFormatting.colors.forEach{color->
                    NotesIconButton(onClick={model.format("color",color)}){
                        Icon(if(color=="default")Icons.Outlined.FormatColorReset else Icons.Outlined.Circle,resources.getString(R.string.module_text_color_1_s,resources.getString(when(color){"default"->R.string.module_default;"red"->R.string.module_red;"orange"->R.string.module_orange;"yellow"->R.string.module_yellow;"green"->R.string.module_green;"blue"->R.string.module_blue;"purple"->R.string.module_purple;"gray"->R.string.module_gray;else->R.string.module_default})),tint=if(color=="default")MaterialTheme.colorScheme.onSurface else noteTextColor(color,MaterialTheme.colorScheme.surface))
                    }
                }
            }
            Row(verticalAlignment=Alignment.CenterVertically){
                NotesIconButton(onClick={formatting=false;link=true}){Icon(Icons.Outlined.Link,resources.getString(R.string.module_url))}
                Text(resources.getString(R.string.module_url))
                val current=model.marks.firstOrNull{it.kind=="url"&&model.canvasSelection.start in it.start..it.end}
                if(current!=null)NotesIconButton(onClick={runCatching{uriHandler.openUri(current.value)}.onFailure{model.error="No browser is available to open this link"}}){Icon(Icons.Outlined.OpenInNew,resources.getString(R.string.module_open_link))}
            }
        }
    }
    if(link){
        var text by remember{mutableStateOf(model.selectedText())}
        var url by remember{mutableStateOf(model.marks.firstOrNull{it.kind=="url"&&model.canvasSelection.start in it.start..it.end}?.value.orEmpty())}
        var invalid by remember{mutableStateOf(false)}
        ModalBottomSheet(containerColor=MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.9f),contentColor=MaterialTheme.colorScheme.onSurface,tonalElevation=0.dp,onDismissRequest={link=false},sheetState=rememberModalBottomSheetState(skipPartiallyExpanded=true)) {
            Column(Modifier.fillMaxWidth().padding(20.dp).imePadding(),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(text,{text=it},Modifier.fillMaxWidth(),label={Text(resources.getString(R.string.module_link_text))})
                OutlinedTextField(url,{url=it;invalid=false},Modifier.fillMaxWidth(),label={Text(resources.getString(R.string.module_url))},singleLine=true,isError=invalid,supportingText={if(invalid)Text(resources.getString(R.string.module_enter_a_valid_http_or_https_url))})
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                    NotesIconButton(onClick={link=false}){Icon(Icons.Outlined.Close,resources.getString(R.string.module_cancel_link))}
                    NotesIconButton(onClick={if(NoteFormatting.validUrl(url.trim())){model.command("url",url.trim(),text);link=false}else invalid=true}){Icon(Icons.Outlined.Check,resources.getString(R.string.module_save_link))}
                }
            }
        }
    }
}
@Composable private fun EditorButtonGroup(content:@Composable RowScope.()->Unit) {
    Box(contentAlignment=Alignment.Center) {
        // Match the single buttons' 40 dp background while preserving 48 dp touch targets.
        Box(Modifier.matchParentSize().padding(vertical=4.dp).background(MaterialTheme.colorScheme.surfaceContainer.copy(alpha=.3f),CircleShape))
        Row(verticalAlignment=Alignment.CenterVertically,content=content)
    }
}
