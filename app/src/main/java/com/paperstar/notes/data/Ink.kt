package com.paperstar.notes.data

import kotlinx.serialization.Serializable
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Page coordinates: every page is 1000 x 1414 units (A4 ratio), whatever the screen size. */
const val PAGE_W = 1000f
const val PAGE_H = 1414f

enum class Paper { LINED, GRID, BLANK }

enum class InkTool { PEN, HIGHLIGHTER }

/**
 * One pen or highlighter stroke. Points are stored flat as x, y, pressure triples
 * to keep the saved file small.
 */
@Serializable
data class Stroke(
    val id: String = newId(),
    val tool: InkTool = InkTool.PEN,
    /** ARGB colour. */
    val color: Long,
    val width: Float,
    /** True when drawn with a stylus, so width follows pressure. */
    val pressure: Boolean = false,
    val pts: List<Float>,
) {
    val count: Int get() = pts.size / 3
    fun x(i: Int) = pts[i * 3]
    fun y(i: Int) = pts[i * 3 + 1]
    fun p(i: Int) = pts[i * 3 + 2]

    /** Is any part of the stroke within [r] page units of (px, py)? */
    fun hits(px: Float, py: Float, r: Float): Boolean = hits(px, py, px, py, r)

    /**
     * Does the eraser, sweeping from (ax, ay) to (bx, by), come within [r] of any part of this stroke?
     * Checks whole segments, so fast strokes and fast eraser moves can't slip between points.
     */
    fun hits(ax: Float, ay: Float, bx: Float, by: Float, r: Float): Boolean {
        val reach = r + width / 2
        if (count == 1) return segDist(x(0), y(0), ax, ay, bx, by) < reach
        for (i in 1 until count) {
            if (segSegDist(x(i - 1), y(i - 1), x(i), y(i), ax, ay, bx, by) < reach) return true
        }
        return false
    }

    /** Scale around (ax, ay), then move by (dx, dy). Width scales too. */
    fun transformed(ax: Float, ay: Float, scale: Float, dx: Float, dy: Float, newId: Boolean = false): Stroke {
        val out = ArrayList<Float>(pts.size)
        for (i in 0 until count) {
            out += ax + (x(i) - ax) * scale + dx
            out += ay + (y(i) - ay) * scale + dy
            out += p(i)
        }
        return copy(id = if (newId) newId() else id, pts = out, width = width * scale)
    }
}

@Serializable
data class Page(
    val id: String = newId(),
    val paper: Paper = Paper.LINED,
    val strokes: List<Stroke> = emptyList(),
    /** Page height in page units. Plain pages are A4; picture and PDF pages take the picture's shape. */
    val height: Float = PAGE_H,
    /** A picture to write on (#11), relative to the files folder. Null for plain paper. */
    val bg: String? = null,
)

/** The height of a 1000-unit-wide page for a picture of [w] x [h] pixels, kept within sensible limits. */
fun pageHeightFor(w: Int, h: Int): Float =
    if (w <= 0 || h <= 0) PAGE_H else (PAGE_W * h / w).coerceIn(PAGE_W / 4, PAGE_W * 4)

data class Bounds(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width get() = right - left
    val height get() = bottom - top
    fun contains(x: Float, y: Float, margin: Float = 0f) =
        x >= left - margin && x <= right + margin && y >= top - margin && y <= bottom + margin
}

fun boundsOf(strokes: Collection<Stroke>): Bounds? {
    if (strokes.isEmpty()) return null
    var l = Float.MAX_VALUE; var t = Float.MAX_VALUE; var r = -Float.MAX_VALUE; var b = -Float.MAX_VALUE
    for (s in strokes) {
        val h = s.width / 2
        for (i in 0 until s.count) {
            l = min(l, s.x(i) - h); t = min(t, s.y(i) - h); r = max(r, s.x(i) + h); b = max(b, s.y(i) + h)
        }
    }
    return Bounds(l, t, r, b)
}

/** Distance from point (px, py) to the segment (ax, ay)–(bx, by). */
fun segDist(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val len2 = dx * dx + dy * dy
    val t = if (len2 == 0f) 0f else (((px - ax) * dx + (py - ay) * dy) / len2).coerceIn(0f, 1f)
    return hypot(px - (ax + t * dx), py - (ay + t * dy))
}

private fun cross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float) = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)

/** Shortest distance between two segments (0 when they cross). */
fun segSegDist(
    ax: Float, ay: Float, bx: Float, by: Float,
    cx: Float, cy: Float, dx: Float, dy: Float,
): Float {
    val d1 = cross(cx, cy, dx, dy, ax, ay); val d2 = cross(cx, cy, dx, dy, bx, by)
    val d3 = cross(ax, ay, bx, by, cx, cy); val d4 = cross(ax, ay, bx, by, dx, dy)
    if (((d1 > 0 && d2 < 0) || (d1 < 0 && d2 > 0)) && ((d3 > 0 && d4 < 0) || (d3 < 0 && d4 > 0))) return 0f
    return minOf(
        minOf(segDist(ax, ay, cx, cy, dx, dy), segDist(bx, by, cx, cy, dx, dy)),
        minOf(segDist(cx, cy, ax, ay, bx, by), segDist(dx, dy, ax, ay, bx, by)),
    )
}

/** Ray casting. [poly] is flat x, y pairs. */
fun pointInPolygon(x: Float, y: Float, poly: List<Float>): Boolean {
    val n = poly.size / 2
    if (n < 3) return false
    var inside = false
    var j = n - 1
    for (i in 0 until n) {
        val xi = poly[i * 2]; val yi = poly[i * 2 + 1]
        val xj = poly[j * 2]; val yj = poly[j * 2 + 1]
        if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) inside = !inside
        j = i
    }
    return inside
}

/** A stroke is picked up by the lasso when at least half of its points are inside the loop. */
fun lassoSelect(strokes: List<Stroke>, poly: List<Float>): Set<String> =
    strokes.filter { s ->
        val inside = (0 until s.count).count { pointInPolygon(s.x(it), s.y(it), poly) }
        s.count > 0 && inside * 2 >= s.count
    }.map { it.id }.toSet()

/** A saved picture to put behind a page, and the page height that fits it. */
data class PageBackground(val path: String, val height: Float)
