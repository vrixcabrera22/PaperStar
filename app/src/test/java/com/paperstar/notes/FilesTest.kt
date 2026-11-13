package com.paperstar.notes

import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.Attachment
import com.paperstar.notes.data.FileKind
import com.paperstar.notes.data.Folder
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.kindOf
import com.paperstar.notes.data.search
import com.paperstar.notes.data.sizeText
import org.junit.Assert.assertEquals
import org.junit.Test

class FilesTest {
    @Test fun recognisesCommonSchoolFiles() {
        assertEquals(FileKind.PDF, kindOf("handout.pdf", "application/octet-stream"))
        assertEquals(FileKind.PDF, kindOf("download", "application/pdf"))
        assertEquals(FileKind.DOC, kindOf("Report.DOCX", ""))
        assertEquals(FileKind.SLIDES, kindOf("x", "application/vnd.openxmlformats-officedocument.presentationml.presentation"))
        assertEquals(FileKind.SHEET, kindOf("grades.csv", "text/csv"))
        assertEquals(FileKind.IMAGE, kindOf("a", "image/png"))
        assertEquals(FileKind.TEXT, kindOf("notes.txt", ""))
        assertEquals(FileKind.OTHER, kindOf("game.apk", "application/vnd.android.package-archive"))
    }

    @Test fun sizesReadNicely() {
        assertEquals("512 B", sizeText(512))
        assertEquals("2 KB", sizeText(2048))
        assertEquals("1.5 MB", sizeText(1_572_864))
    }

    @Test fun searchFindsFileNames() {
        val f = Folder(id = "j", name = "Biology")
        val n = Note(id = "n", folderId = "j", title = "Week 3",
            files = listOf(Attachment(name = "Lab Activity 3.pdf", mime = "application/pdf", size = 1, path = "attachments/x.pdf")))
        assertEquals(listOf("n"), search(AppData(folders = listOf(f), notes = listOf(n)), "lab activity").map { it.note.id })
    }
}
