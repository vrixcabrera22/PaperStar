package com.paperstar.notes

import com.paperstar.notes.ui.media.safeFileName
import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadTest {
    @Test fun fileNamesAreSafe() {
        assertEquals("Cell structure 2.jpg", safeFileName("Cell structure", " 2.jpg"))
        assertEquals("Quiz 1 2 notes 1.jpg", safeFileName("Quiz 1/2: notes?", " 1.jpg"))
        assertEquals("Paper Star.jpg", safeFileName("  ", ".jpg"))
    }
}
