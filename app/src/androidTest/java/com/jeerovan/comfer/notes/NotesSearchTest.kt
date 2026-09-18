package com.jeerovan.comfer.notes

import org.junit.Assert.*
import org.junit.Test

class NotesSearchTest {
    @Test fun unicodeLiteralMatchesUseOriginalOffsets() {
        for ((text, query, expected) in listOf(
            Triple("Straße", "STRASSE", "Straße"), Triple("un café", "cafe\u0301", "café"),
            Triple("你好世界", "好世", "好世"), Triple("%_<tag>", "%_", "%_"),
            Triple("हिन्दी 中文", "हिन्दी", "हिन्दी"), Triple("a 👩🏽‍💻 b", "👩🏽‍💻", "👩🏽‍💻"))) {
            val range=NotesSearch.matches(text,query).single()
            assertEquals(expected,text.substring(range.start,range.end))
        }
    }
    @Test fun optionalAccentModeAndNavigationCount() {
        assertTrue(NotesSearch.matches("café","cafe").isEmpty())
        assertEquals(1,NotesSearch.matches("café","cafe",true).size)
        assertEquals(3,NotesSearch.matches("one ONE one","one").size)
        assertTrue(NotesSearch.matches("text","").isEmpty())
    }
}
