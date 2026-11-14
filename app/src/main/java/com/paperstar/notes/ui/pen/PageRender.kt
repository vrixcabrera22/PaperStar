package com.paperstar.notes.ui.pen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import com.paperstar.notes.ui.media.rememberPicture
import java.io.File
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import com.paperstar.notes.data.InkTool
import com.paperstar.notes.data.PAGE_H
import com.paperstar.notes.data.PAGE_W
import com.paperstar.notes.data.Page
import com.paperstar.notes.data.Paper
import com.paperstar.notes.data.Stroke

/** Paper is paper: white in light and dark mode. */
val PaperWhite = Color(0xFFFFFEFB)
private val RuleBlue = Color(0xFFC7D5EC)
private val MarginPink = Color(0xFFF0A3AE)
private val GridBlue = Color(0xFFDCE4F2)
const val HIGHLIGHT_ALPHA = .4f

/**
 * Draws the paper in page units (call inside a scale so 1 unit = 1 page unit).
 * A page with a picture behind it (#11) draws the picture instead of lines.
 */
fun DrawScope.drawPaper(paper: Paper, height: Float = PAGE_H, bg: ImageBitmap? = null, hasBg: Boolean = false) {
    drawRect(PaperWhite, size = androidx.compose.ui.geometry.Size(PAGE_W, height))
    if (hasBg) {
        if (bg != null) drawImage(bg, dstSize = IntSize(PAGE_W.roundToInt(), height.roundToInt()), filterQuality = FilterQuality.High)
        return
    }
    when (paper) {
        Paper.LINED -> {
            var y = 160f
            while (y < height - 40f) { drawLine(RuleBlue, Offset(0f, y), Offset(PAGE_W, y), 1.6f); y += 56f }
            drawLine(MarginPink, Offset(110f, 0f), Offset(110f, height), 1.6f)
        }
        Paper.GRID -> {
            var v = 50f
            while (v < PAGE_W) { drawLine(GridBlue, Offset(v, 0f), Offset(v, height), 1.6f); v += 50f }
            v = 50f
            while (v < height) { drawLine(GridBlue, Offset(0f, v), Offset(PAGE_W, v), 1.6f); v += 50f }
        }
        Paper.BLANK -> Unit
    }
}

/** Draws one stroke in page units, optionally moved/resized (for the lasso preview). */
fun DrawScope.drawInk(
    s: Stroke,
    ax: Float = 0f, ay: Float = 0f, scale: Float = 1f, dx: Float = 0f, dy: Float = 0f,
) {
    if (s.count == 0) return
    fun px(i: Int) = ax + (s.x(i) - ax) * scale + dx
    fun py(i: Int) = ay + (s.y(i) - ay) * scale + dy
    val color = Color(s.color.toInt()).let { if (s.tool == InkTool.HIGHLIGHTER) it.copy(alpha = HIGHLIGHT_ALPHA) else it }
    val w = s.width * scale
    if (s.count == 1) {
        drawCircle(color, radius = w / 2, center = Offset(px(0), py(0)))
        return
    }
    if (s.pressure && s.tool == InkTool.PEN) {
        for (i in 1 until s.count) {
            drawLine(color, Offset(px(i - 1), py(i - 1)), Offset(px(i), py(i)),
                strokeWidth = (w * (.35f + s.p(i) * 1.1f)).coerceAtLeast(.8f), cap = StrokeCap.Round)
        }
        return
    }
    val path = Path().apply {
        moveTo(px(0), py(0))
        for (i in 1 until s.count - 1) quadraticTo(px(i), py(i), (px(i) + px(i + 1)) / 2, (py(i) + py(i + 1)) / 2)
        lineTo(px(s.count - 1), py(s.count - 1))
    }
    drawPath(path, color, style = DrawStroke(width = w, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** Highlighters go under the ink, like a real highlighter under pen. */
fun DrawScope.drawStrokes(strokes: List<Stroke>, skip: Set<String> = emptySet()) {
    for (s in strokes) if (s.tool == InkTool.HIGHLIGHTER && s.id !in skip) drawInk(s)
    for (s in strokes) if (s.tool == InkTool.PEN && s.id !in skip) drawInk(s)
}

/** A small preview of a handwritten page. */
@Composable
fun PageThumb(page: Page, modifier: Modifier = Modifier) {
    val bg = page.bg?.let { rememberPicture(File(LocalContext.current.filesDir, it), 400) }
    Canvas(modifier.aspectRatio(PAGE_W / page.height)) {
        scale(size.width / PAGE_W, pivot = Offset.Zero) {
            drawPaper(page.paper, page.height, bg, page.bg != null)
            drawStrokes(page.strokes)
        }
    }
}
