package com.jeerovan.comfer.notes

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.*
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextDecoration

/** Task lines coexist with prose; the plain-text markers also survive copy and backup. */
internal data class InlineCheck(val start:Int,val end:Int,val checked:Boolean,val label:String)
internal fun inlineChecks(text:String):List<InlineCheck> = Regex("(?m)^\\[([ xX])\\] (.*)$").findAll(text).filter{it.range.first>text.indexOf('\n')&&text.contains('\n')}.map{
    InlineCheck(it.range.first,it.range.last+1,it.groupValues[1]!=" ",it.groupValues[2])
}.toList()

@Composable internal fun NotesCanvas(model:NotesViewModel,modifier:Modifier=Modifier) {
    fun text()=model.title+if(model.body.text.isNotEmpty())"\n"+model.body.text else ""
    var value by remember(model.draft?.id){mutableStateOf(TextFieldValue(text()))}
    var layout by remember{mutableStateOf<TextLayoutResult?>(null)}
    var commandSeen by remember(model.draft?.id){mutableIntStateOf(model.canvasCommand?.sequence?:0)}
    var preview by remember{mutableStateOf<NoteImage?>(null)}
    val focus=remember{FocusRequester()}
    fun update(next:TextFieldValue,listEdit:Boolean=false){
        val continued=if(next.text!=value.text)NoteFormatting.continueList(value.text,next.text)else null
        val updated=continued?.let{TextFieldValue(it.first,TextRange(it.second))}?:next
        value=updated;model.updateCanvas(updated,listEdit)
    }
    fun toggle(start:Int){if(start+2<value.text.length){val c=if(value.text[start+1]==' ')"x" else " ";update(value.copy(text=value.text.replaceRange(start+1,start+2,c)))}}
    LaunchedEffect(model.title,model.body.text){if(value.text.substringBefore('\n')!=model.title||value.text.substringAfter('\n',"")!=model.body.text){val next=text();value=TextFieldValue(next,TextRange(value.selection.start.coerceAtMost(next.length)))}}
    LaunchedEffect(model.findTarget,model.findSelection){val offset=when(model.findTarget){"title"->0;"body"->model.title.length+1;else->return@LaunchedEffect};value=value.copy(selection=TextRange((offset+model.findSelection.start).coerceAtMost(value.text.length),(offset+model.findSelection.end).coerceAtMost(value.text.length)))}
    LaunchedEffect(model.canvasCommand){model.canvasCommand?.let{command->if(commandSeen!=command.sequence){
        commandSeen=command.sequence
        if(command.kind=="url"){
            val start=value.selection.min;val end=value.selection.max
            val label=command.text.ifBlank{command.value}
            val updated=value.text.replaceRange(start,end,label)
            val sameText=updated==value.text
            update(TextFieldValue(updated,TextRange(start+label.length)))
            model.link(start,start+label.length,command.value,recordUndo=sameText)
        }else{
            val next=NoteFormatting.list(value.text,value.selection.min,value.selection.max,command.kind)
            update(TextFieldValue(next.first,TextRange(next.second)),listEdit=true)
        }
        focus.requestFocus()
    }}}
    // Observe formatting in composition so toolbar-only edits invalidate text layout immediately.
    val marks=model.marks
    val heading=MaterialTheme.typography.headlineMedium
    val body=MaterialTheme.typography.bodyLarge
    val subheadingStyle=MaterialTheme.typography.headlineSmall
    val smallHeadingStyle=MaterialTheme.typography.titleMedium
    val linkColor=MaterialTheme.colorScheme.primary
    val checks=remember(value.text){inlineChecks(value.text)}
    val latestChecks by rememberUpdatedState(checks)
    val toggleCurrent by rememberUpdatedState<(Int)->Unit>(::toggle)
    BoxWithConstraints(modifier){
        val minHeight=maxHeight
        LazyColumn(Modifier.fillMaxSize()) {
            item(key="note-text") {
            BasicTextField(value,{update(it)},Modifier.fillMaxWidth().heightIn(min=if(model.images.isEmpty())minHeight else 160.dp).focusRequester(focus).testTag("notes-editor")
                .semantics{customActions=checks.mapIndexed{index,item->CustomAccessibilityAction("${if(item.checked)"Uncheck" else "Check"} item ${index+1}: ${item.label}"){toggle(item.start);true}}}
                .pointerInput(Unit){awaitEachGesture{
                    val down=awaitFirstDown(requireUnconsumed=false,pass=PointerEventPass.Initial)
                    val result=layout ?: return@awaitEachGesture
                    val index=result.getOffsetForPosition(down.position)
                    val check=latestChecks.firstOrNull{index in it.start..it.start+3} ?: return@awaitEachGesture
                    val bounds=result.getBoundingBox(check.start)
                    if(down.position.y<bounds.top||down.position.y>bounds.bottom||down.position.x>result.getBoundingBox(check.start+3).right)return@awaitEachGesture
                    down.consume()
                    val up=waitForUpOrCancellation(pass=PointerEventPass.Initial)
                    if(up!=null){up.consume();toggleCurrent(check.start)}
                }},
                textStyle=body.copy(color=MaterialTheme.colorScheme.onSurface,lineHeight=androidx.compose.ui.unit.TextUnit.Unspecified),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                onTextLayout={layout=it},visualTransformation=VisualTransformation{input->
                    val found=inlineChecks(input.text)
                    val display=StringBuilder(input.text);found.forEach{display.replace(it.start,it.start+4,if(it.checked)"●   " else "○   ")}
                    val end=input.text.indexOf('\n').let{if(it<0)input.length else it}
                    TransformedText(buildAnnotatedString{
                        append(display.toString());addStyle(heading.toSpanStyle(),0,end)
                        marks.sortedBy{if(it.kind=="paragraph")0 else 1}.forEach{mark->
                            val style=when(mark.kind){
                                "paragraph"->when(mark.value){"title"->heading.toSpanStyle();"heading"->subheadingStyle.toSpanStyle();"subheading"->smallHeadingStyle.toSpanStyle();else->body.toSpanStyle()}
                                "bold"->SpanStyle(fontWeight=FontWeight.Bold)
                                "italic"->SpanStyle(fontStyle=FontStyle.Italic)
                                "underline"->SpanStyle(textDecoration=TextDecoration.Underline)
                                "strike"->SpanStyle(textDecoration=TextDecoration.LineThrough)
                                "color"->SpanStyle(color=noteTextColor(mark.value))
                                "url"->SpanStyle(color=linkColor,textDecoration=TextDecoration.Underline)
                                else->SpanStyle()
                            }
                            if(mark.start<input.length)addStyle(style,mark.start,mark.end.coerceAtMost(input.length))
                        }
                        val strikes=marks.filter{it.kind=="strike"}+found.filter{it.checked}.map{NoteMark(it.start+4,it.end,"strike")}
                        strikes.forEach{if(it.start<input.length)addStyle(SpanStyle(textDecoration=TextDecoration.LineThrough),it.start,it.end.coerceAtMost(input.length))}
                        marks.filter{it.kind=="underline"||it.kind=="url"}.forEach{under->strikes.forEach{strike->
                            val from=maxOf(under.start,strike.start);val to=minOf(under.end,strike.end,input.length)
                            if(to>from)addStyle(SpanStyle(textDecoration=TextDecoration.Underline+TextDecoration.LineThrough),from,to)
                        }}
                    },OffsetMapping.Identity)
                },decorationBox={inner->Box{if(value.text.isEmpty())Text("Title",style=heading,color=MaterialTheme.colorScheme.onSurfaceVariant);inner()}})
            }
            items(model.images,key={it.id}){image->
                NoteImageView(image,Modifier.fillMaxWidth().padding(vertical=8.dp).clickable{preview=image})
            }
        }
    }
    preview?.let{image->AlertDialog(onDismissRequest={preview=null},text={NoteImageView(image,Modifier.fillMaxWidth())},
        confirmButton={NotesIconButton(onClick={model.removeImage(image.id);preview=null}){Icon(Icons.Outlined.Delete,"Remove image")}},
        dismissButton={NotesIconButton(onClick={preview=null}){Icon(Icons.Outlined.Close,"Close image")}})}
}

@Composable internal fun NoteImageView(image:NoteImage,modifier:Modifier=Modifier) {
    val result by produceState<Pair<android.graphics.Bitmap?,Boolean>>(null to true,image.id,image.jpeg){value=withContext(Dispatchers.Default){NotesImages.decode(image)} to false}
    result.first?.let{Image(it.asImageBitmap(),"Note image",modifier.aspectRatio(image.width.toFloat()/image.height),contentScale=ContentScale.Fit)}
        ?:Text(if(result.second)"Loading image…" else "Image unavailable",modifier)
}
internal fun noteTextColor(value:String):Color=when(value){
    "red"->Color(0xFFD94B4B);"orange"->Color(0xFFBF7C20);"green"->Color(0xFF388E5B);"blue"->Color(0xFF427ED0);"purple"->Color(0xFF9966CC);else->Color.Unspecified
}
