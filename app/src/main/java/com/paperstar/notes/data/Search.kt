package com.paperstar.notes.data

/** [foundIn] is set when the match was only in recognized text, like "Handwritten page: …the cell wall". */
data class SearchHit(val note: Note, val folder: Folder, val foundIn: String? = null)

fun searchTerms(query: String): List<String> =
    query.lowercase().split(Regex("\\s+")).map { it.removePrefix("#") }.filter { it.isNotBlank() }

/**
 * Every term must appear somewhere in the note: title, typed text, checklist items, tags,
 * file names, the jar's name, or text recognized in its handwriting, pictures and PDFs.
 * The Secret jar is never searched.
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
        val seen = n.recognized.joinToString("\n") { it.text }.lowercase()
        if (!terms.all { hay.contains(it) || seen.contains(it) }) return@mapNotNull null
        // Show where a recognized match came from, unless the typed text already shows it.
        val inBody = terms.any { n.body.lowercase().contains(it) }
        val rec = if (inBody) null else n.recognized.firstOrNull { r -> terms.any { r.text.lowercase().contains(it) } }
        SearchHit(n, f, rec?.let { it.kind.label + ": " + snippet(it.text.replace('\n', ' '), terms) })
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
