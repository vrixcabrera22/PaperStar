package com.paperstar.notes

import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.Attachment
import com.paperstar.notes.data.Folder
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.Page
import com.paperstar.notes.data.Picture
import com.paperstar.notes.data.RecKind
import com.paperstar.notes.data.Recognized
import com.paperstar.notes.data.Stroke
import com.paperstar.notes.data.pageSig
import com.paperstar.notes.data.pendingRecognition
import com.paperstar.notes.data.pruneRecognized
import com.paperstar.notes.data.search
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RecognizedTest {
    private val ink = Stroke(id = "s1", color = 0xFF000000, width = 4f, pts = listOf(1f, 1f, .5f, 9f, 9f, .5f))
    private val page = Page(id = "p1", strokes = listOf(ink))
    private val blank = Page(id = "p2")
    private val pic = Picture(id = "pic", path = "pictures/pic.jpg", width = 10, height = 10)
    private val pdf = Attachment(id = "f1", name = "Lab.pdf", mime = "application/pdf", size = 1, path = "attachments/f1.pdf")
    private val doc = Attachment(id = "f2", name = "Essay.docx", mime = "", size = 1, path = "attachments/f2.docx")
    private val bio = Folder(id = "bio", name = "Biology")
    private val secret = Folder(id = "sec", name = "Secret jar", secret = true)

    @Test fun findsWhatStillNeedsReading() {
        val n = Note(id = "n", folderId = "bio", pages = listOf(page, blank), pictures = listOf(pic), files = listOf(pdf, doc))
        val tasks = pendingRecognition(AppData(folders = listOf(bio), notes = listOf(n)))
        assertEquals(listOf("p1" to RecKind.PAGE, "pic" to RecKind.PICTURE, "f1" to RecKind.PDF), tasks.map { it.id to it.kind })
    }

    @Test fun readItemsAreSkippedUntilTheyChange() {
        val read = listOf(Recognized("p1", RecKind.PAGE, pageSig(page), "cell"), Recognized("pic", RecKind.PICTURE, "1", ""))
        val n = Note(id = "n", folderId = "bio", pages = listOf(page), pictures = listOf(pic), recognized = read)
        assertTrue(pendingRecognition(AppData(folders = listOf(bio), notes = listOf(n))).isEmpty())
        val changed = n.copy(pages = listOf(page.copy(strokes = page.strokes + ink.copy(id = "s2"))))
        assertEquals(listOf("p1"), pendingRecognition(AppData(folders = listOf(bio), notes = listOf(changed))).map { it.id })
    }

    @Test fun secretJarIsNeverRead() {
        val n = Note(id = "n", folderId = "sec", pages = listOf(page))
        assertTrue(pendingRecognition(AppData(folders = listOf(secret), notes = listOf(n))).isEmpty())
    }

    @Test fun removedItemsArePruned() {
        val n = Note(id = "n", folderId = "bio", pages = listOf(page),
            recognized = listOf(Recognized("p1", RecKind.PAGE, "x", "a"), Recognized("gone", RecKind.PICTURE, "1", "b")))
        assertEquals(listOf("p1"), pruneRecognized(n).map { it.id })
    }

    @Test fun searchFindsRecognizedTextAndSaysWhere() {
        val n = Note(id = "n", folderId = "bio", title = "Lab", body = "typed words", pages = listOf(page),
            recognized = listOf(Recognized("p1", RecKind.PAGE, pageSig(page), "Prepare an onion skin slide")))
        val data = AppData(folders = listOf(bio), notes = listOf(n))
        val hit = search(data, "onion").single()
        assertEquals("Handwritten page: Prepare an onion skin slide", hit.foundIn)
        // a term in the title and one in the handwriting together still match
        assertEquals(1, search(data, "lab onion").size)
        // typed text matches show the typed text instead
        assertNull(search(data, "typed").single().foundIn)
    }
}
