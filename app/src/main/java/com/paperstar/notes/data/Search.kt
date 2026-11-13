package com.paperstar.notes.data

data class SearchHit(val note: Note, val folder: Folder)

fun searchTerms(query: String): List<String> =
    query.lowercase().split(Regex("\\s+")).map { it.removePrefix("#") }.filter { it.isNotBlank() }

/**
 * Every term must appear somewhere in the note: title, typed text, checklist items,
 * tags or the jar's name. The Secret jar is never searched.
 */
fun search(data: AppData, query: String): List<SearchHit> {
    val terms = searchTerms(query)
    if (terms.isEmpty()) return emptyList()
    val jars = data.folders.filterNot { it.secret }.associateBy { it.id }
    return data.notes.mapNotNull { n ->
        val f = jars[n.folderId] ?: return@mapNotNull null
        val hay = buildString {
            append(n.title).append('\n').append(n.body).append('\n').append(f.name).append('\n')
            n.checklist.forEach { append(it.text).append('\n') }
            n.tags.forEach { append(it).append('\n') }
            n.files.forEach { append(it.name).append('\n') }
        }.lowercase()
        if (terms.all { hay.contains(it) }) SearchHit(n, f) else null
    }.sortedByDescending { it.note.updatedAt }
}

/** A piece of [text] around the first matching term, so the match is visible in a two-line preview. */
fun snippet(text: String, terms: List<String>): String {
    val low = text.lowercase()
    val first = terms.mapNotNull { t -> low.indexOf(t).takeIf { it >= 0 } }.minOrNull() ?: return text
    return if (first <= 40) text else "…" + text.substring(first - 30)
}

/** Character ranges of every term match in [text], merged and sorted. */
fun matchRanges(text: String, terms: List<String>): List<IntRange> {
    val low = text.lowercase()
    val ranges = mutableListOf<IntRange>()
    for (t in terms) {
        var i = low.indexOf(t)
        while (i >= 0) {
            ranges += i until i + t.length
            i = low.indexOf(t, i + t.length)
        }
    }
    return ranges.sortedBy { it.first }.fold(mutableListOf()) { acc, r ->
        val last = acc.lastOrNull()
        if (last != null && r.first <= last.last + 1) acc[acc.size - 1] = last.first..maxOf(last.last, r.last) else acc += r
        acc
    }
}
