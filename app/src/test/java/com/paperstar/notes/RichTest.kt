package com.paperstar.notes

import com.paperstar.notes.data.Mark
import com.paperstar.notes.data.MarkType.BOLD
import com.paperstar.notes.data.MarkType.COLOR
import com.paperstar.notes.data.MarkType.HIGHLIGHT
import com.paperstar.notes.data.MarkType.ITALIC
import com.paperstar.notes.data.findEdit
import com.paperstar.notes.data.hasMark
import com.paperstar.notes.data.markColor
import com.paperstar.notes.data.setMark
import com.paperstar.notes.data.shiftMarks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RichTest {
    private val bold = listOf(Mark(0, 5, BOLD)) // "Hello" in "Hello world"

    @Test fun typingAfterStyledWordContinuesIt() {
        assertEquals(listOf(Mark(0, 6, BOLD)), shiftMarks(bold, "Hello world", "Hello! world", 6))
    }

    @Test fun typingBeforeStyledWordDoesNot() {
        assertEquals(listOf(Mark(1, 6, BOLD)), shiftMarks(bold, "Hello world", "\"Hello world", 1))
    }

    @Test fun typingInsideAndAfterShifts() {
        assertEquals(listOf(Mark(0, 6, BOLD)), shiftMarks(bold, "Hello world", "Helllo world", 4))
        assertEquals(listOf(Mark(0, 5, BOLD)), shiftMarks(bold, "Hello world", "Hello world!", 12))
        assertEquals(listOf(Mark(2, 7, BOLD)), shiftMarks(listOf(Mark(1, 6, BOLD)), "Hello world", "HHello world", 1))
    }

    @Test fun repeatedLetterUsesCursor() {
        // typing "l" right after "Hell" (caret 5) vs after "Hel" (caret 4) gives the same text
        assertEquals(4, findEdit("Hello", "Helllo", 5).start)
        assertEquals(3, findEdit("Hello", "Helllo", 4).start)
    }

    @Test fun deletingShrinksOrRemoves() {
        assertEquals(listOf(Mark(0, 4, BOLD)), shiftMarks(bold, "Hello world", "Helo world", 2))
        assertEquals(emptyList<Mark>(), shiftMarks(bold, "Hello world", " world", 0))
        assertEquals(listOf(Mark(0, 2, BOLD)), shiftMarks(bold, "Hello world", "Heworld", 2))
    }

    @Test fun replacingSelectionKeepsStyleOfStart() {
        // select "llo w" and type "X"
        assertEquals(listOf(Mark(0, 3, BOLD)), shiftMarks(bold, "Hello world", "HeXorld", 3))
    }

    @Test fun setAndClearMarks() {
        var m = setMark(emptyList(), 0, 5, BOLD, true, length = 11)
        m = setMark(m, 3, 8, BOLD, true, length = 11)
        assertEquals(listOf(Mark(0, 8, BOLD)), m)
        m = setMark(m, 2, 4, BOLD, false, length = 11)
        assertEquals(listOf(Mark(0, 2, BOLD), Mark(4, 8, BOLD)), m)
        m = setMark(m, 0, 11, ITALIC, true, length = 11)
        assertEquals(3, m.size)
    }

    @Test fun newColorReplacesOld() {
        var m = setMark(emptyList(), 0, 10, COLOR, true, color = 1, length = 10)
        m = setMark(m, 2, 4, COLOR, true, color = 3, length = 10)
        assertEquals(listOf(Mark(0, 2, COLOR, 1), Mark(2, 4, COLOR, 3), Mark(4, 10, COLOR, 1)), m)
        assertEquals(3, markColor(m, 2, 4, COLOR))
        assertEquals(1, markColor(m, 1, 1, COLOR))
    }

    @Test fun toolbarState() {
        val m = listOf(Mark(0, 3, HIGHLIGHT), Mark(3, 6, HIGHLIGHT, 2))
        assertTrue(hasMark(m, 0, 6, HIGHLIGHT))
        assertFalse(hasMark(m, 0, 7, HIGHLIGHT))
        assertTrue(hasMark(bold, 5, 5, BOLD))
        assertFalse(hasMark(bold, 0, 0, BOLD))
        assertFalse(hasMark(bold, 6, 6, BOLD))
    }
}
