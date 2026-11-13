package com.paperstar.notes.ui.pen

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.paperstar.notes.data.Bounds
import com.paperstar.notes.data.InkTool
import com.paperstar.notes.data.Stroke
import com.paperstar.notes.data.boundsOf
import com.paperstar.notes.data.lassoSelect
import kotlin.math.hypot
import kotlin.math.max

enum class PenTool { PEN, HIGHLIGHTER, ERASER, LASSO }

val InkColors = listOf(0xFF1D1A2CL, 0xFF2B55D9L, 0xFFD93A4AL, 0xFF1E9A5AL)
val HighlightColors = listOf(0xFFFFE45CL, 0xFFFF9EC7L, 0xFF8EEA9CL, 0xFF8ED2FFL)
private val Sizes = mapOf(
    PenTool.PEN to listOf(3f, 6f, 11f),
    PenTool.HIGHLIGHTER to listOf(22f, 34f, 48f),
    PenTool.ERASER to listOf(18f, 36f, 64f),
)

private enum class Drag { NONE, STROKE, ERASE, LASSO, MOVE, RESIZE }

/**
 * Everything the pen editor remembers while it's open: chosen tool, the stroke being drawn,
 * the lasso and its selection, undo/redo, and zoom. Strokes themselves live in the repository;
 * every finished change goes out through [write].
 */
class PenState(private val write: (List<Stroke>) -> Unit) {
    var tool by mutableStateOf(PenTool.PEN)
    var penColor by mutableIntStateOf(0)
    var hlColor by mutableIntStateOf(0)
    var sizeIndex = mutableMapOf(PenTool.PEN to 0, PenTool.HIGHLIGHTER to 1, PenTool.ERASER to 1)
    var sizeVersion by mutableIntStateOf(0)

    /** Once a stylus touches the page, fingers only pan and zoom (palm rejection). */
    var stylusSeen = false

    // live drawing (read only by the overlay layer, so the page underneath doesn't redraw)
    val live = ArrayList<Float>()
    var liveTemplate: Stroke? = null
    val lasso = ArrayList<Float>()
    var overlayVersion by mutableIntStateOf(0)

    // selection
    var selection by mutableStateOf<Set<String>>(emptySet())
    var selBounds by mutableStateOf<Bounds?>(null)
    var dragDx by mutableFloatStateOf(0f)
    var dragDy by mutableFloatStateOf(0f)
    var dragScale by mutableFloatStateOf(1f)

    // undo
    private val undo = ArrayDeque<List<Stroke>>()
    private val redo = ArrayDeque<List<Stroke>>()
    var historyVersion by mutableIntStateOf(0)
    val canUndo get() = historyVersion >= 0 && undo.isNotEmpty()
    val canRedo get() = historyVersion >= 0 && redo.isNotEmpty()

    // view
    var zoom by mutableFloatStateOf(1f)
    var panX by mutableFloatStateOf(0f)
    var panY by mutableFloatStateOf(0f)

    private var drag = Drag.NONE
    private var startX = 0f
    private var startY = 0f
    private var startDist = 1f
    private var erasedBefore: List<Stroke>? = null

    fun size(t: PenTool = tool): Float = Sizes[t]!![sizeIndex[t] ?: 0]
    fun setSize(i: Int) { sizeIndex[tool] = i; sizeVersion++ }
    val currentSizeIndex get() = sizeVersion.let { sizeIndex[tool] ?: 0 }

    fun selectTool(t: PenTool) {
        if (t != PenTool.LASSO) clearSelection()
        tool = t
    }

    fun clearSelection() { selection = emptySet(); selBounds = null; resetDrag() }
    private fun resetDrag() { dragDx = 0f; dragDy = 0f; dragScale = 1f }

    private fun commit(before: List<Stroke>, after: List<Stroke>) {
        undo.addLast(before); if (undo.size > 60) undo.removeFirst()
        redo.clear(); historyVersion++
        write(after)
    }

    fun undo(current: List<Stroke>) {
        val prev = undo.removeLastOrNull() ?: return
        redo.addLast(current); clearSelection(); historyVersion++; write(prev)
    }

    fun redo(current: List<Stroke>) {
        val next = redo.removeLastOrNull() ?: return
        undo.addLast(current); clearSelection(); historyVersion++; write(next)
    }

    /** New page or page switch: undo history belongs to one page. */
    fun resetForPage() {
        undo.clear(); redo.clear(); historyVersion++; clearSelection()
        live.clear(); lasso.clear(); liveTemplate = null; drag = Drag.NONE; overlayVersion++
    }

    private fun handleRadius() = 34f / zoom

    // ---- gestures, in page units ----
    fun start(x: Float, y: Float, pressure: Float, isStylus: Boolean, strokes: List<Stroke>) {
        startX = x; startY = y
        when (tool) {
            PenTool.PEN, PenTool.HIGHLIGHTER -> {
                val hl = tool == PenTool.HIGHLIGHTER
                liveTemplate = Stroke(
                    tool = if (hl) InkTool.HIGHLIGHTER else InkTool.PEN,
                    color = if (hl) HighlightColors[hlColor] else InkColors[penColor],
                    width = size(), pressure = isStylus && !hl, pts = emptyList(),
                )
                live.clear(); live += x; live += y; live += pressure
                drag = Drag.STROKE
            }
            PenTool.ERASER -> {
                erasedBefore = null; drag = Drag.ERASE
                lastEraseX = Float.NaN; lastEraseY = Float.NaN
                erase(x, y, strokes)
            }
            PenTool.LASSO -> {
                val b = selBounds
                drag = when {
                    b != null && hypot(x - b.right, y - b.bottom) < handleRadius() -> {
                        startDist = max(hypot(x - b.left, y - b.top), 1f); Drag.RESIZE
                    }
                    b != null && b.contains(x, y, 12f / zoom) -> Drag.MOVE
                    else -> { clearSelection(); lasso.clear(); lasso += x; lasso += y; Drag.LASSO }
                }
            }
        }
        overlayVersion++
    }

    fun move(x: Float, y: Float, pressure: Float, strokes: List<Stroke>) {
        when (drag) {
            Drag.STROKE -> { live += x; live += y; live += pressure }
            Drag.ERASE -> erase(x, y, strokes)
            Drag.LASSO -> { lasso += x; lasso += y }
            Drag.MOVE -> { dragDx = x - startX; dragDy = y - startY }
            Drag.RESIZE -> {
                val b = selBounds ?: return
                dragScale = (hypot(x - b.left, y - b.top) / startDist).coerceIn(.2f, 6f)
            }
            Drag.NONE -> return
        }
        overlayVersion++
    }

    fun end(strokes: List<Stroke>) {
        when (drag) {
            Drag.STROKE -> {
                val t = liveTemplate
                if (t != null && live.isNotEmpty()) commit(strokes, strokes + t.copy(pts = ArrayList(live)))
                live.clear(); liveTemplate = null
            }
            Drag.ERASE -> erasedBefore?.let { before -> undo.addLast(before); redo.clear(); historyVersion++ }
            Drag.LASSO -> {
                val picked = if (lasso.size >= 6) lassoSelect(strokes, lasso) else emptySet()
                lasso.clear()
                selection = picked
                selBounds = boundsOf(strokes.filter { it.id in picked })
            }
            Drag.MOVE, Drag.RESIZE -> applyTransform(strokes)
            Drag.NONE -> Unit
        }
        drag = Drag.NONE
        overlayVersion++
    }

    /** A second finger landed while drawing with a finger: drop the unfinished stroke. */
    fun cancel() {
        live.clear(); lasso.clear(); liveTemplate = null
        if (drag == Drag.MOVE || drag == Drag.RESIZE) resetDrag()
        drag = Drag.NONE; overlayVersion++
    }

    private var lastEraseX = 0f
    private var lastEraseY = 0f

    private fun erase(x: Float, y: Float, strokes: List<Stroke>) {
        val r = size(PenTool.ERASER) / 2
        // the first touch erases at a point; after that, along the path since the last move
        val fromX = if (lastEraseX.isNaN()) x else lastEraseX
        val fromY = if (lastEraseY.isNaN()) y else lastEraseY
        lastEraseX = x; lastEraseY = y
        val keep = strokes.filterNot { it.hits(fromX, fromY, x, y, r) }
        if (keep.size != strokes.size) {
            if (erasedBefore == null) erasedBefore = strokes
            write(keep)
        }
    }

    private fun applyTransform(strokes: List<Stroke>) {
        val b = selBounds ?: return
        if (dragDx == 0f && dragDy == 0f && dragScale == 1f) return
        val out = strokes.map { if (it.id in selection) it.transformed(b.left, b.top, dragScale, dragDx, dragDy) else it }
        resetDrag()
        commit(strokes, out)
        selBounds = boundsOf(out.filter { it.id in selection })
    }

    // ---- selection actions ----
    fun recolor(color: Long, strokes: List<Stroke>) =
        commit(strokes, strokes.map { if (it.id in selection) it.copy(color = color) else it })

    fun deleteSelection(strokes: List<Stroke>) {
        commit(strokes, strokes.filterNot { it.id in selection }); clearSelection()
    }

    fun duplicateSelection(strokes: List<Stroke>) {
        val copies = strokes.filter { it.id in selection }.map { it.transformed(0f, 0f, 1f, 30f, 30f, newId = true) }
        commit(strokes, strokes + copies)
        selection = copies.map { it.id }.toSet()
        selBounds = boundsOf(copies)
    }
}
