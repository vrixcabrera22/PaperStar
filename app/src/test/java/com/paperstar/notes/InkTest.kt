package com.paperstar.notes

import com.paperstar.notes.data.Stroke
import com.paperstar.notes.data.boundsOf
import com.paperstar.notes.data.lassoSelect
import com.paperstar.notes.data.pointInPolygon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class InkTest {
    private fun line(id: String, vararg xy: Float) =
        Stroke(id = id, color = 0xFF000000, width = 4f, pts = xy.toList().chunked(2).flatMap { listOf(it[0], it[1], .5f) })

    private val square = listOf(0f, 0f, 100f, 0f, 100f, 100f, 0f, 100f)

    @Test fun pointInsideAndOutsideSquare() {
        assertTrue(pointInPolygon(50f, 50f, square))
        assertFalse(pointInPolygon(150f, 50f, square))
        assertFalse(pointInPolygon(50f, 50f, listOf(0f, 0f, 10f, 10f))) // not a loop
    }

    @Test fun lassoNeedsHalfTheStrokeInside() {
        val inside = line("in", 10f, 10f, 20f, 20f, 30f, 30f)
        val half = line("half", 80f, 50f, 90f, 50f, 110f, 50f, 120f, 50f)
        val outside = line("out", 200f, 200f, 210f, 210f)
        assertEquals(setOf("in", "half"), lassoSelect(listOf(inside, half, outside), square))
    }

    @Test fun moveAndResizeKeepShape() {
        val s = line("a", 10f, 10f, 20f, 30f)
        val moved = s.transformed(0f, 0f, 1f, 5f, -5f)
        assertEquals(listOf(15f, 5f, .5f, 25f, 25f, .5f), moved.pts)
        val big = s.transformed(10f, 10f, 2f, 0f, 0f)
        assertEquals(listOf(10f, 10f, .5f, 30f, 50f, .5f), big.pts)
        assertEquals(8f, big.width)
        assertEquals("a", big.id)
        assertTrue(s.transformed(0f, 0f, 1f, 0f, 0f, newId = true).id != "a")
    }

    @Test fun boundsIncludeHalfTheWidth() {
        val b = boundsOf(listOf(line("a", 10f, 10f, 20f, 30f)))!!
        assertEquals(8f, b.left); assertEquals(8f, b.top); assertEquals(22f, b.right); assertEquals(32f, b.bottom)
    }

    @Test fun eraserHitsBetweenSparsePoints() {
        val s = line("a", 0f, 100f, 300f, 100f) // only two points, far apart
        assertTrue(s.hits(150f, 105f, 9f)) // touching the middle of the segment
        assertTrue(s.hits(150f, 40f, 150f, 160f, 5f)) // eraser swipe crosses the line between its own points
        assertFalse(s.hits(150f, 40f, 150f, 80f, 5f)) // swipe stops short
    }

    @Test fun eraserHitsNearPoints() {
        val s = line("a", 100f, 100f)
        assertTrue(s.hits(110f, 100f, 9f))
        assertFalse(s.hits(130f, 100f, 9f))
    }
}
