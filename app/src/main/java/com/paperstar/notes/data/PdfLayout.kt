package com.paperstar.notes.data

/** Page layout math for "Download as PDF". Pure, so it can be tested. */
object PdfLayout {
    /**
     * How many lines, starting at [from], fit in [space] points. [bottoms] holds each line's bottom
     * edge measured from the top of the text. At least one line is always taken, so a line taller
     * than a whole page can't stop the PDF from finishing.
     */
    fun linesThatFit(bottoms: List<Float>, from: Int, space: Float): Int {
        if (from >= bottoms.size) return 0
        val top = if (from == 0) 0f else bottoms[from - 1]
        var n = 0
        while (from + n < bottoms.size && bottoms[from + n] - top <= space) n++
        return maxOf(n, 1)
    }

    /** Scale that fits a [w] x [h] box inside [maxW] x [maxH] without stretching or enlarging past [maxScale]. */
    fun fit(w: Float, h: Float, maxW: Float, maxH: Float, maxScale: Float = Float.MAX_VALUE): Float =
        if (w <= 0f || h <= 0f) 1f else minOf(maxW / w, maxH / h, maxScale)
}
