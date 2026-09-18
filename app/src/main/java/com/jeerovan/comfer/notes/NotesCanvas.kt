package com.jeerovan.comfer.notes

import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
    var insertSeen by remember(model.draft?.id){mutableIntStateOf(model.checklistInsert)}
    val focus=remember{FocusRequester()}
    fun update(next:TextFieldValue){value=next;model.updateCanvas(next)}
    fun toggle(start:Int){if(start+2<value.text.length){val c=if(value.text[start+1]==' ')"x" else " ";update(value.copy(text=value.text.replaceRange(start+1,start+2,c)))}}
    LaunchedEffect(model.title,model.body.text){if(value.text.substringBefore('\n')!=model.title||value.text.substringAfter('\n',"")!=model.body.text){val next=text();value=TextFieldValue(next,TextRange(value.selection.start.coerceAtMost(next.length)))}}
    LaunchedEffect(model.findTarget,model.findSelection){val offset=when(model.findTarget){"title"->0;"body"->model.title.length+1;else->return@LaunchedEffect};value=value.copy(selection=TextRange((offset+model.findSelection.start).coerceAtMost(value.text.length),(offset+model.findSelection.end).coerceAtMost(value.text.length)))}
    LaunchedEffect(model.checklistInsert){if(insertSeen!=model.checklistInsert){
        insertSeen=model.checklistInsert
        val boundary=value.text.indexOf('\n')
        if(boundary<0||value.selection.start<=boundary){val next=if(boundary<0)value.text+"\n[ ] " else value.text.substring(0,boundary+1)+"[ ] \n"+value.text.substring(boundary+1);val caret=if(boundary<0)next.length else boundary+5;update(TextFieldValue(next,TextRange(caret)))}
        else {val start=value.text.lastIndexOf('\n',(value.selection.start-1).coerceAtLeast(0))+1;update(TextFieldValue(value.text.substring(0,start)+"[ ] "+value.text.substring(start),TextRange(value.selection.start+4)))}
        focus.requestFocus()
    }}
    val heading=MaterialTheme.typography.headlineMedium
    val body=MaterialTheme.typography.bodyLarge
    val checks=remember(value.text){inlineChecks(value.text)}
    val latestChecks by rememberUpdatedState(checks)
    val toggleCurrent by rememberUpdatedState<(Int)->Unit>(::toggle)
    BoxWithConstraints(modifier){
        val minHeight=maxHeight
        Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            BasicTextField(value,::update,Modifier.fillMaxWidth().heightIn(min=minHeight).focusRequester(focus).testTag("notes-editor")
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
                    val display=StringBuilder(input.text);found.forEach{display.replace(it.start,it.start+4,if(it.checked)"☑   " else "☐   ")}
                    val end=input.text.indexOf('\n').let{if(it<0)input.length else it}
                    TransformedText(buildAnnotatedString{append(display.toString());addStyle(heading.toSpanStyle(),0,end);found.filter{it.checked}.forEach{addStyle(SpanStyle(textDecoration=TextDecoration.LineThrough),it.start+4,it.end)}},OffsetMapping.Identity)
                },decorationBox={inner->Box{if(value.text.isEmpty())Text("Title",style=heading,color=MaterialTheme.colorScheme.onSurfaceVariant);inner()}})
        }
    }
}
