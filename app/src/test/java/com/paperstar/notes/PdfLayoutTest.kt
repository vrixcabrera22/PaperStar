package com.paperstar.notes

import com.paperstar.notes.data.PdfLayout
import org.junit.Assert.assertEquals
import org.junit.Test

class PdfLayoutTest {
    private val lines = listOf(20f, 40f, 60f, 80f, 100f) // five 20-point lines

    @Test fun takesWhatFits() {
        assertEquals(2, PdfLayout.linesThatFit(lines, 0, 45f))
        assertEquals(3, PdfLayout.linesThatFit(lines, 2, 1000f)) // the rest
        assertEquals(1, PdfLayout.linesThatFit(lines, 1, 20f))
    }

    @Test fun alwaysTakesOneLineAndStopsAtTheEnd() {
        assertEquals(1, PdfLayout.linesThatFit(lines, 0, 5f))
        assertEquals(0, PdfLayout.linesThatFit(lines, 5, 100f))
    }

    @Test fun fitsWithoutStretching() {
        assertEquals(0.5f, PdfLayout.fit(1000f, 1414f, 500f, 900f), 0.001f) // limited by width
        assertEquals(0.5f, PdfLayout.fit(2000f, 1000f, 1200f, 500f), 0.001f) // limited by height
        assertEquals(1f, PdfLayout.fit(100f, 100f, 500f, 500f, maxScale = 1f), 0.001f) // small pictures stay small
    }
}
