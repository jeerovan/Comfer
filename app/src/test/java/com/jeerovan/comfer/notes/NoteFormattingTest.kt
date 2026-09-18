package com.jeerovan.comfer.notes

import org.junit.Test
import org.junit.Assert.*
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

class NoteFormattingTest {
    @Test fun oldTextPayloadStillDecodes(){
        val content=Json.decodeFromString<NoteContent>("""{"title":"Old","text":"Content"}""")
        assertEquals("Content",content.text);assertTrue(content.marks.isEmpty());assertTrue(content.images.isEmpty())
    }
    @Test fun partialToggleSplitsBoldButKeepsOtherFormatting(){
        val marks=listOf(NoteMark(0,8,"bold"),NoteMark(0,8,"italic"))
        assertEquals(setOf(NoteMark(0,2,"bold"),NoteMark(6,8,"bold"),NoteMark(0,8,"italic")),NoteFormatting.apply(marks,2,6,"bold").toSet())
    }
    @Test fun replacementRebasesSurroundingSpansAndRemovesDeletedOnes(){
        val marks=listOf(NoteMark(0,2,"bold"),NoteMark(2,4,"italic"),NoteMark(4,6,"underline"))
        assertEquals(listOf(NoteMark(0,2,"bold"),NoteMark(3,5,"underline")),NoteFormatting.rebase(marks,"abcdef","abZef"))
    }
    @Test fun emojiReplacementDoesNotSplitSurrogatePair(){
        val before="A😀B";val after="A😁B"
        assertEquals(NoteFormatting.Change(1,3,3),NoteFormatting.change(before,after))
        assertEquals(listOf(NoteMark(3,4,"bold")),NoteFormatting.rebase(listOf(NoteMark(3,4,"bold")),before,after))
    }
    @Test fun listsStayBelowTitleAndNumberSelectedLines(){
        assertEquals("Title\n[ ] ",NoteFormatting.list("Title",2,2,"checklist").first)
        assertEquals("Title\n1. One\n2. Two",NoteFormatting.list("Title\nOne\nTwo",6,13,"number").first)
        assertEquals("Title\n• One",NoteFormatting.list("Title\n[ ] One",10,10,"bullet").first)
    }
    @Test fun addingListPrefixesPreservesFormattingAcrossLines(){
        val before="Title\nOne\nTwo";val after="Title\n1. One\n2. Two"
        val marks=listOf(NoteMark(6,9,"bold"),NoteMark(10,13,"italic"))
        assertEquals(listOf(NoteMark(9,12,"bold"),NoteMark(16,19,"italic")),NoteFormatting.rebaseList(marks,before,after))
    }
    @Test fun enterContinuesListAndEmptyItemEndsIt(){
        assertEquals("Title\n1. One\n2. ",NoteFormatting.continueList("Title\n1. One","Title\n1. One\n")?.first)
        assertEquals("Title\n",NoteFormatting.continueList("Title\n[ ] ","Title\n[ ] \n")?.first)
    }
    @Test fun onlyWebLinksAreAccepted(){
        assertTrue(NoteFormatting.validUrl("https://example.com/path?q=test"))
        listOf("javascript:alert(1)","file:///secret","content://local/1","https://","not a url").forEach{assertFalse(NoteFormatting.validUrl(it))}
    }
    @Test fun invalidSpansFailValidation(){
        try{NoteContent("Title","Body",marks=listOf(NoteMark(0,500,"bold"))).validate();fail()}catch(_:IllegalArgumentException){}
        try{NoteContent("Title","Body",marks=listOf(NoteMark(0,2,"url","javascript:1"))).validate();fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun imageBoundarySeparatesTypingAboveAndBelow() {
        val image=NoteImage(jpeg="/9j/",width=1,height=1,offset=7)
        assertEquals(7,NoteFormatting.rebaseImages(listOf(image),"Title\n\n","Title\n\nBelow",1).single().offset)
        assertEquals(12,NoteFormatting.rebaseImages(listOf(image),"Title\n\n","Title\n\nAbove",0).single().offset)
        assertEquals(3,NoteFormatting.rebaseImages(listOf(image),"Title\n\n","T\n\n",0).single().offset)
    }
    @Test fun imagePositionsDecodeFromOldContentAndRejectInvalidOffsets() {
        assertNull(Json.decodeFromString<NoteImage>("""{"jpeg":"/9j/","width":1,"height":1}""").offset)
        try {NoteContent("Title",images=listOf(NoteImage(jpeg="/9j/",width=1,height=1,offset=99))).validate();fail()}catch(_:IllegalArgumentException){}
    }
}
