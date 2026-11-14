package com.paperstar.notes.data

import kotlinx.serialization.Serializable

/** Kinds of formatting for typed text (#2). */
enum class MarkType { BOLD, ITALIC, UNDERLINE, HIGHLIGHT, COLOR }

/**
 * One formatted range of a note's typed text, from [start] (inclusive) to [end] (exclusive).
 * [color] picks from the highlight or text colour list, so it can look right in light and dark mode.
 */
@Serializable
data class Mark(val start: Int, val end: Int, val type: MarkType, val color: Int = 0)

/** Sorts, clamps to [length], drops empty ranges, and joins touching ranges of the same style. */
fun normalizeMarks(marks: List<Mark>, length: Int): List<Mark> {
    val out = mutableListOf<Mark>()
    marks.map { it.copy(start = it.start.coerceIn(0, length), end = it.end.coerceIn(0, length)) }
        .filter { it.end > it.start }
        .groupBy { it.type to it.color }
        .forEach { (_, group) ->
            var cur: Mark? = null
            for (m in group.sortedBy { it.start }) {
                val c = cur
                cur = when {
                    c == null -> m
                    m.start <= c.end -> c.copy(end = maxOf(c.end, m.end))
                    else -> { out += c; m }
                }
            }
            cur?.let { out += it }
        }
    return out.sortedWith(compareBy({ it.start }, { it.type }))
}

/**
 * Moves marks to follow an edit from [old] to [new]. [cursor] is the caret after the edit and
 * tells us where typed text went (needed when the same letter repeats, like typing "l" after "l").
 * Text typed right after a styled word continues that style, like in Word or Google Docs.
 */
fun shiftMarks(marks: List<Mark>, old: String, new: String, cursor: Int): List<Mark> {
    val e = findEdit(old, new, cursor)
    val delta = e.newEnd - e.oldEnd
    val shifted = marks.map { m ->
        val start = when {
            m.start < e.start -> m.start
            m.start >= e.oldEnd -> m.start + delta
            else -> e.newEnd
        }
        val end = when {
            m.end < e.start -> m.end
            m.end >= e.oldEnd -> m.end + delta
            m.start < e.start -> e.newEnd
            else -> e.start
        }
        m.copy(start = start, end = end)
    }
    return normalizeMarks(shifted, new.length)
}

/** The changed part of an edit: old[start, oldEnd) was replaced by new[start, newEnd). */
data class Edit(val start: Int, val oldEnd: Int, val newEnd: Int)

fun findEdit(old: String, new: String, cursor: Int): Edit {
    val c = cursor.coerceIn(0, new.length)
    var s = 0
    val maxS = minOf(old.length, new.length - c)
    while (s < maxS && old[old.length - 1 - s] == new[new.length - 1 - s]) s++
    var p = 0
    val maxP = minOf(old.length - s, new.length - s)
    while (p < maxP && old[p] == new[p]) p++
    return Edit(p, old.length - s, new.length - s)
}

/** Turns [type] on or off for [start, end). Turning a colour on replaces any other colour there. */
fun setMark(marks: List<Mark>, start: Int, end: Int, type: MarkType, on: Boolean, color: Int = 0, length: Int): List<Mark> {
    if (end <= start) return marks
    val out = mutableListOf<Mark>()
    for (m in marks) {
        if (m.type != type || m.end <= start || m.start >= end) { out += m; continue }
        if (m.start < start) out += m.copy(end = start)
        if (m.end > end) out += m.copy(start = end)
    }
    if (on) out += Mark(start, end, type, color)
    return normalizeMarks(out, length)
}

/**
 * Whether [type] is on for the selection [start, end), so the toolbar can show it as pressed.
 * With no selection, it checks the letter before the caret, which is the style typing will continue.
 */
fun hasMark(marks: List<Mark>, start: Int, end: Int, type: MarkType): Boolean {
    val ms = marks.filter { it.type == type }
    if (start == end) return ms.any { it.start < start && it.end >= start }
    var pos = start
    for (m in ms.sortedBy { it.start }) {
        if (m.start > pos) break
        if (m.end > pos) pos = m.end
        if (pos >= end) return true
    }
    return false
}

/** The colour of [type] at the selection, or null when there is none. */
fun markColor(marks: List<Mark>, start: Int, end: Int, type: MarkType): Int? {
    val at = if (start == end) start - 1 else start
    return marks.firstOrNull { it.type == type && it.start <= at && at < it.end }?.color
}
