package com.jeerovan.comfer.notes

import kotlinx.serialization.Serializable
import java.net.URI
import java.util.UUID

@Serializable data class NoteMark(val start:Int,val end:Int,val kind:String,val value:String="")
@Serializable data class NoteImage(val id:String=UUID.randomUUID().toString(),val jpeg:String,val width:Int,val height:Int)
internal data class NoteCanvasCommand(val sequence:Int,val kind:String,val value:String="",val text:String="")

/** UTF-16 ranges match Android/Compose selection offsets; plain text is never parsed as HTML. */
object NoteFormatting {
    val kinds=setOf("paragraph","bold","italic","underline","strike","color","url")
    val paragraphs=setOf("title","heading","subheading","body")
    val colors=setOf("default","red","orange","green","blue","purple")
    fun validUrl(value:String):Boolean=runCatching { val uri=URI(value);uri.scheme?.lowercase() in setOf("https","http")&&!uri.host.isNullOrBlank() }.getOrDefault(false)
    fun canvas(content:NoteContent)=content.title+if(content.text.isNotEmpty())"\n"+content.text else ""
    data class Change(val start:Int,val oldEnd:Int,val newEnd:Int)
    fun change(before:String,after:String):Change {
        var start=0
        while(start<minOf(before.length,after.length)&&before[start]==after[start])start++
        if(start>0&&start<before.length&&before[start].isLowSurrogate()&&before[start-1].isHighSurrogate())start--
        var oldEnd=before.length;var newEnd=after.length
        while(oldEnd>start&&newEnd>start&&before[oldEnd-1]==after[newEnd-1]){oldEnd--;newEnd--}
        if(oldEnd>start&&oldEnd<before.length&&before[oldEnd].isLowSurrogate()){oldEnd++;newEnd++}
        return Change(start,oldEnd,newEnd)
    }
    fun rebase(marks:List<NoteMark>,before:String,after:String):List<NoteMark> {
        if(before==after)return marks
        val c=change(before,after);val delta=c.newEnd-c.oldEnd
        return marks.mapNotNull { m->
            if(c.oldEnd>c.start&&m.start>=c.start&&m.end<=c.oldEnd)return@mapNotNull null
            val start=when{m.start>=c.oldEnd->m.start+delta;m.start>=c.start->c.start;else->m.start}
            val end=when{m.end>c.oldEnd->m.end+delta;m.end>c.start->c.newEnd;else->m.end}
            if(end>start) m.copy(start=start.coerceAtLeast(0),end=end.coerceAtMost(after.length)) else null
        }.filter{it.end>it.start}
    }
    fun apply(marks:List<NoteMark>,start:Int,end:Int,kind:String,value:String="",toggle:Boolean=true):List<NoteMark> {
        if(end<=start)return marks
        val same=marks.filter{it.kind==kind&&it.value==value}.sortedBy{it.start}
        var covered=start
        same.forEach{if(it.start<=covered&&it.end>covered)covered=it.end}
        val remove=toggle&&covered>=end
        val result=marks.flatMap { m->
            if(m.kind!=kind||m.end<=start||m.start>=end)listOf(m)
            else listOfNotNull(m.takeIf{it.start<start}?.copy(end=start),m.takeIf{it.end>end}?.copy(start=end))
        }.toMutableList()
        if(!remove&&!(kind=="color"&&value=="default"))result+=NoteMark(start,end,kind,value)
        return normalize(result)
    }
    fun normalize(marks:List<NoteMark>):List<NoteMark> = marks.groupBy{it.kind to it.value}.values.flatMap { group->
        val result=mutableListOf<NoteMark>()
        group.sortedBy{it.start}.forEach{m->val last=result.lastOrNull();if(last!=null&&last.end>=m.start)result[result.lastIndex]=last.copy(end=maxOf(last.end,m.end))else result+=m}
        result
    }
    fun paragraphRange(text:String,start:Int,end:Int):IntRange {
        val first=text.lastIndexOf('\n',(start-1).coerceAtLeast(-1))+1
        val last=text.indexOf('\n',(end-1).coerceAtLeast(start).coerceAtMost(text.length)).let{if(it<0)text.length else it}
        return first until last
    }
    private val prefix=Regex("^(?:\\[[ xX]\\] |• |\\d+\\. )")
    fun list(text:String,start:Int,end:Int,kind:String):Pair<String,Int> {
        val titleEnd=text.indexOf('\n')
        val marker=when(kind){"checklist"->"[ ] ";"number"->"1. ";else->"• "}
        if(titleEnd<0||start<=titleEnd){
            val at=if(titleEnd<0)text.length else titleEnd
            val insertion="\n"+marker
            return text.substring(0,at)+insertion+text.substring(at) to at+insertion.length
        }
        val range=paragraphRange(text,start,end)
        val lines=text.substring(range.first,range.last+1).split('\n')
        val rewritten=lines.mapIndexed { index,line->
            val mark=if(kind=="number")"${index+1}. " else marker
            mark+line.replaceFirst(prefix,"")
        }.joinToString("\n")
        val result=text.replaceRange(range.first,range.last+1,rewritten)
        return result to (start+marker.length).coerceAtMost(range.first+rewritten.length)
    }
    fun rebaseList(marks:List<NoteMark>,before:String,after:String):List<NoteMark> {
        val oldLines=before.split('\n');val newLines=after.split('\n')
        if(oldLines.size!=newLines.size)return rebase(marks,before,after)
        var text=before;var result=marks
        for(index in oldLines.lastIndex downTo 1) {
            val old=oldLines[index];val new=newLines[index]
            if(old==new)continue
            val oldPrefix=prefix.find(old)?.value.orEmpty();val newPrefix=prefix.find(new)?.value.orEmpty()
            if(old.removePrefix(oldPrefix)!=new.removePrefix(newPrefix))return rebase(marks,before,after)
            val start=oldLines.take(index).sumOf{it.length+1}
            val next=text.replaceRange(start,start+oldPrefix.length,newPrefix)
            result=rebase(result,text,next);text=next
        }
        return if(text==after)result else rebase(marks,before,after)
    }
    fun continueList(before:String,after:String):Pair<String,Int>? {
        val c=change(before,after)
        if(c.oldEnd!=c.start||c.newEnd!=c.start+1||after.getOrNull(c.start)!='\n')return null
        val lineStart=before.lastIndexOf('\n',c.start-1)+1
        if(lineStart==0)return null
        val line=before.substring(lineStart,c.start)
        val match=prefix.find(line)?:return null
        if(line==match.value)return after.removeRange(lineStart,c.newEnd) to lineStart
        val next=when{match.value.startsWith("[")->"[ ] ";match.value=="• "->"• ";else->"${match.value.substringBefore('.').toLongOrNull()?.plus(1)?:1}. "}
        return after.substring(0,c.newEnd)+next+after.substring(c.newEnd) to c.newEnd+next.length
    }
}
