package com.paperstar.notes

import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.CheckItem
import com.paperstar.notes.data.Folder
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.matchRanges
import com.paperstar.notes.data.normalizeTag
import com.paperstar.notes.data.search
import com.paperstar.notes.data.snippet
import com.paperstar.notes.data.sortedForJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchTest {
    private val bio = Folder(id = "bio", name = "Biology")
    private val secret = Folder(id = "sec", name = "Secret jar", secret = true)
    private val data = AppData(
        folders = listOf(bio, secret),
        notes = listOf(
            Note(id = "a", folderId = "bio", title = "Cell structure", body = "Mitochondria is the powerhouse", tags = listOf("exam"), updatedAt = 2),
            Note(id = "b", folderId = "bio", title = "Quiz", checklist = listOf(CheckItem(text = "Review diagram")), updatedAt = 3),
            Note(id = "c", folderId = "sec", title = "Wi-Fi password", body = "exam", updatedAt = 4),
        ),
    )

    @Test fun findsByTitleBodyChecklistAndTag() {
        assertEquals(listOf("a"), search(data, "powerhouse").map { it.note.id })
        assertEquals(listOf("b"), search(data, "diagram").map { it.note.id })
        assertEquals(listOf("a"), search(data, "#exam").map { it.note.id })
    }

    @Test fun everyTermMustMatch() {
        assertEquals(listOf("a"), search(data, "cell exam").map { it.note.id })
        assertTrue(search(data, "cell quiz").isEmpty())
    }

    @Test fun jarNameMatchesAndNewestFirst() {
        assertEquals(listOf("b", "a"), search(data, "biology").map { it.note.id })
    }

    @Test fun secretJarIsNeverSearched() {
        assertTrue(search(data, "password").isEmpty())
    }

    @Test fun snippetKeepsMatchVisible() {
        val text = "x".repeat(80) + " cell wall"
        assertTrue(snippet(text, listOf("cell")).startsWith("…"))
        assertEquals("short cell", snippet("short cell", listOf("cell")))
    }

    @Test fun rangesAreMergedAndCaseInsensitive() {
        assertEquals(listOf(0..3, 9..12), matchRanges("Quiz and quiz", listOf("quiz")))
        assertEquals(listOf(0..4), matchRanges("abcde", listOf("abc", "cde")))
    }

    @Test fun tagsAreNormalized() {
        assertEquals("final-exam", normalizeTag("  #Final Exam "))
    }

    @Test fun pinnedNotesComeFirst() {
        val notes = listOf(Note(id = "1", folderId = "x", updatedAt = 9), Note(id = "2", folderId = "x", pinned = true, updatedAt = 1))
        assertEquals(listOf("2", "1"), notes.sortedForJar().map { it.id })
    }
}
