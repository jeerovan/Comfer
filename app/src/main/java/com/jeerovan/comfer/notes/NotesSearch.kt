package com.jeerovan.comfer.notes

import android.icu.text.BreakIterator
import android.icu.text.Normalizer2
import java.util.Locale

/** Grapheme-aware offset map: normalized matches still address the original UTF-16 editor text. */
object NotesSearch {
    data class Match(val start: Int, val end: Int)
    private data class Folded(val text: String, val starts: List<Int>, val ends: List<Int>)
    private fun fold(text: String, accents: Boolean): Folded {
        val iterator = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(text) }
        val normalizer = Normalizer2.getNFKCCasefoldInstance()
        val output = StringBuilder(); val starts = mutableListOf<Int>(); val ends = mutableListOf<Int>()
        var start = iterator.first(); var end = iterator.next()
        while (end != BreakIterator.DONE) {
            var part = normalizer.normalize(text.substring(start, end))
            if (accents) part = Normalizer2.getNFDInstance().normalize(part).replace(Regex("\\p{M}+"), "")
            output.append(part); repeat(part.length) { starts += start; ends += end }
            start = end; end = iterator.next()
        }
        return Folded(output.toString(), starts, ends)
    }
    fun matches(text: String, query: String, accentInsensitive: Boolean = false): List<Match> {
        if (query.isBlank()) return emptyList()
        val source = fold(text, accentInsensitive); val needle = fold(query, accentInsensitive).text
        if (needle.isEmpty()) return emptyList()
        val results = mutableListOf<Match>(); var offset = 0
        while (offset <= source.text.length - needle.length) {
            val position = source.text.indexOf(needle, offset); if(position < 0) break
            results += Match(source.starts[position], source.ends[position + needle.length - 1])
            offset = position + needle.length
        }
        return results.distinct()
    }
    fun contains(text: String, query: String) = query.isBlank() || Normalizer2.getNFKCCasefoldInstance().normalize(text)
        .contains(Normalizer2.getNFKCCasefoldInstance().normalize(query))
}
