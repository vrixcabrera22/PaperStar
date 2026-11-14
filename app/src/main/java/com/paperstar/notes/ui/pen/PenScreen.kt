package com.paperstar.notes.ui.pen

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke as DrawStroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.InkTool
import com.paperstar.notes.ui.media.rememberPicture
import com.paperstar.notes.data.PAGE_W
import com.paperstar.notes.data.Paper
import com.paperstar.notes.data.Repository
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.theme.Paper as PaperTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PenScreen(repo: Repository, data: AppData, noteId: String, startPage: Int, onDone: () -> Unit) {
    val c = PaperTheme.colors
    val note = data.note(noteId) ?: return
    if (note.pages.isEmpty()) {
        LaunchedEffect(noteId) { repo.insertPage(noteId, -1, Paper.LINED) }
        return
    }
    var index by remember(noteId) { mutableIntStateOf(startPage.coerceIn(0, note.pages.lastIndex)) }
    if (index > note.pages.lastIndex) index = note.pages.lastIndex
    val page = note.pages[index]
    val strokesNow = rememberUpdatedState(page.strokes)
    val pageIdNow = rememberUpdatedState(page.id)
    val state = remember(noteId) { PenState { s -> repo.updatePage(noteId, pageIdNow.value) { it.copy(strokes = s) } } }
    LaunchedEffect(page.id) { state.resetForPage() }
    var deleteArmed by remember(page.id) { mutableStateOf(false) }
    val bgImg = page.bg?.let { rememberPicture(repo.fileOf(it), 2400) }

    Column(Modifier.fillMaxSize().background(c.bg).statusBarsPadding().navigationBarsPadding()) {
        // ---- top bar ----
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.clip(RoundedCornerShape(999.dp)).background(c.accent).clickable(onClick = onDone)
                    .padding(horizontal = 18.dp, vertical = 9.dp),
            ) { Text("Done", color = c.accentInk, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.weight(1f))
            ToolButton(PsIcons.Back, "Previous page", enabled = index > 0) { index-- }
            Text("Page ${index + 1} of ${note.pages.size}", color = c.muted, style = MaterialTheme.typography.bodySmall)
            ToolButton(PsIcons.Back, "Next page", enabled = index < note.pages.lastIndex, flip = true) { index++ }
            Spacer(Modifier.weight(1f))
            ToolButton(PsIcons.Undo, "Undo", enabled = state.canUndo) { state.undo(strokesNow.value) }
            ToolButton(PsIcons.Redo, "Redo", enabled = state.canRedo) { state.redo(strokesNow.value) }
        }

        // ---- paper ----
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
            val density = LocalDensity.current
            val pad = with(density) { 12.dp.toPx() }
            val availW = constraints.maxWidth - pad * 2
            val availH = constraints.maxHeight - pad * 2
            val fitW = minOf(availW, availH * PAGE_W / page.height)
            val fitH = fitW * page.height / PAGE_W
            val ox = (constraints.maxWidth - fitW) / 2
            val oy = (constraints.maxHeight - fitH) / 2
            val k = fitW / PAGE_W

            fun toPage(p: Offset) = Offset(
                (p.x - ox - state.panX) / (state.zoom * k),
                (p.y - oy - state.panY) / (state.zoom * k),
            )

            Box(
                Modifier.fillMaxSize().pointerInput(noteId, fitW) {
                    awaitEachGesture {
                        val first = awaitFirstDown(requireUnconsumed = false)
                        val stylus = first.type == PointerType.Stylus || first.type == PointerType.Eraser
                        if (stylus) state.stylusSeen = true
                        val fingerOnly = first.type == PointerType.Touch
                        var navigating = fingerOnly && state.stylusSeen
                        if (!navigating) {
                            val p = toPage(first.position)
                            state.start(p.x, p.y, if (stylus) first.pressure else .5f, stylus, strokesNow.value)
                        }
                        first.consume()
                        while (true) {
                            val ev = awaitPointerEvent()
                            val down = ev.changes.filter { it.pressed }
                            if (down.isEmpty()) { if (!navigating) state.end(strokesNow.value); break }
                            if (!navigating && fingerOnly && down.size >= 2) { state.cancel(); navigating = true }
                            if (navigating) {
                                val zoomBy = ev.calculateZoom(); val pan = ev.calculatePan(); val centroid = ev.calculateCentroid()
                                val newZoom = (state.zoom * zoomBy).coerceIn(1f, 5f)
                                if (centroid != Offset.Unspecified) {
                                    val f = newZoom / state.zoom
                                    state.panX = centroid.x - ox - (centroid.x - ox - state.panX) * f + pan.x
                                    state.panY = centroid.y - oy - (centroid.y - oy - state.panY) * f + pan.y
                                }
                                state.zoom = newZoom
                                // keep at least part of the page on screen
                                val pw = fitW * newZoom; val ph = fitH * newZoom
                                state.panX = state.panX.coerceIn(-(pw - fitW / 3) - ox + pad, constraints.maxWidth - ox - fitW / 3)
                                state.panY = state.panY.coerceIn(-(ph - fitH / 3) - oy + pad, constraints.maxHeight - oy - fitH / 3)
                                if (newZoom == 1f && zoomBy < 1f) { state.panX = 0f; state.panY = 0f }
                            } else {
                                val ch = ev.changes.firstOrNull { it.id == first.id }
                                if (ch != null) {
                                    val pr = if (stylus) ch.pressure else .5f
                                    ch.historical.forEach { h -> val p = toPage(h.position); state.move(p.x, p.y, pr, strokesNow.value) }
                                    val p = toPage(ch.position); state.move(p.x, p.y, pr, strokesNow.value)
                                }
                            }
                            ev.changes.forEach { it.consume() }
                        }
                    }
                },
            ) {
                Box(
                    Modifier
                        .offset { IntOffset(ox.roundToInt(), oy.roundToInt()) }
                        .size(with(density) { fitW.toDp() }, with(density) { fitH.toDp() })
                        .graphicsLayer {
                            translationX = state.panX; translationY = state.panY
                            scaleX = state.zoom; scaleY = state.zoom
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .shadow(6.dp, RoundedCornerShape(4.dp))
                        .clip(RoundedCornerShape(4.dp)),
                ) {
                    // finished strokes: only redraws when the page changes
                    val skip = state.selection
                    Canvas(Modifier.fillMaxSize()) {
                        scale(k, pivot = Offset.Zero) { drawPaper(page.paper, page.height, bgImg, page.bg != null); drawStrokes(page.strokes, skip) }
                    }
                    // live layer: the stroke being drawn, the lasso, the selection
                    Canvas(Modifier.fillMaxSize()) {
                        state.overlayVersion // redraw trigger
                        scale(k, pivot = Offset.Zero) {
                            state.liveTemplate?.let { t -> if (state.live.isNotEmpty()) drawInk(t.copy(pts = state.live.toList())) }
                            if (state.lasso.size >= 4) {
                                val path = Path().apply {
                                    moveTo(state.lasso[0], state.lasso[1])
                                    for (i in 1 until state.lasso.size / 2) lineTo(state.lasso[i * 2], state.lasso[i * 2 + 1])
                                }
                                drawPath(path, c.accent, style = DrawStroke(3f / state.zoom, pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f))))
                            }
                            val b = state.selBounds
                            if (b != null && state.selection.isNotEmpty()) {
                                val sel = page.strokes.filter { it.id in state.selection }
                                for (s in sel) if (s.tool == InkTool.HIGHLIGHTER) drawInk(s, b.left, b.top, state.dragScale, state.dragDx, state.dragDy)
                                for (s in sel) if (s.tool == InkTool.PEN) drawInk(s, b.left, b.top, state.dragScale, state.dragDx, state.dragDy)
                                val l = b.left + state.dragDx; val t = b.top + state.dragDy
                                val w = b.width * state.dragScale; val h = b.height * state.dragScale
                                val sw = 2.5f / state.zoom
                                drawRect(c.accent.copy(alpha = .08f), Offset(l, t), Size(w, h))
                                drawRect(c.accent, Offset(l, t), Size(w, h), style = DrawStroke(sw, pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))))
                                drawCircle(Color.White, 16f / state.zoom, Offset(l + w, t + h))
                                drawCircle(c.accent, 16f / state.zoom, Offset(l + w, t + h), style = DrawStroke(4f / state.zoom))
                            }
                        }
                    }
                }
                if (state.zoom > 1.01f) Box(
                    Modifier.align(Alignment.TopEnd).padding(10.dp).clip(RoundedCornerShape(999.dp)).background(c.surface)
                        .border(1.dp, c.line, RoundedCornerShape(999.dp))
                        .clickable { state.zoom = 1f; state.panX = 0f; state.panY = 0f }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                ) { Text("${(state.zoom * 100).roundToInt()}% · Fit page", style = MaterialTheme.typography.labelMedium, color = c.ink) }
            }
        }

        // ---- tools ----
        val selecting = state.tool == PenTool.LASSO && state.selection.isNotEmpty()
        FlowRow(
            Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            ToolGroup {
                ToolButton(PsIcons.Pen, "Pen", selected = state.tool == PenTool.PEN) { state.selectTool(PenTool.PEN) }
                ToolButton(PsIcons.Highlighter, "Highlighter", selected = state.tool == PenTool.HIGHLIGHTER) { state.selectTool(PenTool.HIGHLIGHTER) }
                ToolButton(PsIcons.Eraser, "Eraser", selected = state.tool == PenTool.ERASER) { state.selectTool(PenTool.ERASER) }
                ToolButton(PsIcons.Lasso, "Lasso: circle strokes to move, resize or recolour", selected = state.tool == PenTool.LASSO) { state.selectTool(PenTool.LASSO) }
            }
            when {
                selecting -> {
                    ToolGroup {
                        InkColors.forEach { col ->
                            ColorDot(Color(col.toInt()), "Recolour", false) { state.recolor(col, strokesNow.value) }
                        }
                    }
                    ToolGroup {
                        ToolButton(PsIcons.Copy, "Duplicate selection") { state.duplicateSelection(strokesNow.value) }
                        ToolButton(PsIcons.Trash, "Delete selection") { state.deleteSelection(strokesNow.value) }
                        TextTool("Done") { state.clearSelection() }
                    }
                }
                state.tool == PenTool.LASSO -> ToolGroup {
                    Text("Draw a loop around strokes to select them", color = c.muted, style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 12.dp))
                }
                else -> {
                    if (state.tool != PenTool.ERASER) ToolGroup {
                        val hl = state.tool == PenTool.HIGHLIGHTER
                        (if (hl) HighlightColors else InkColors).forEachIndexed { i, col ->
                            ColorDot(Color(col.toInt()), "Colour ${i + 1}", i == (if (hl) state.hlColor else state.penColor)) {
                                if (hl) state.hlColor = i else state.penColor = i
                            }
                        }
                    }
                    ToolGroup {
                        val dots = when (state.tool) { PenTool.ERASER -> listOf(10, 16, 22); PenTool.HIGHLIGHTER -> listOf(10, 15, 20); else -> listOf(5, 9, 14) }
                        dots.forEachIndexed { i, px ->
                            Box(
                                Modifier.size(40.dp).clip(RoundedCornerShape(9.dp))
                                    .background(if (i == state.currentSizeIndex) c.accentSoft else Color.Transparent)
                                    .clickable { state.setSize(i) },
                                contentAlignment = Alignment.Center,
                            ) { Box(Modifier.size(px.dp).clip(CircleShape).background(if (i == state.currentSizeIndex) c.accent else c.ink)) }
                        }
                    }
                }
            }
            if (!selecting) ToolGroup {
                // a page with a picture behind it has no paper style to change
                if (page.bg == null) TextTool(when (page.paper) { Paper.LINED -> "Lined"; Paper.GRID -> "Grid"; Paper.BLANK -> "Blank" }) {
                    val next = Paper.entries[(page.paper.ordinal + 1) % Paper.entries.size]
                    repo.updatePage(noteId, page.id) { it.copy(paper = next) }
                }
                TextTool("+ Page") { index = repo.insertPage(noteId, index, if (page.bg != null) Paper.LINED else page.paper) }
                if (deleteArmed) TextTool("Delete?", danger = true) {
                    deleteArmed = false
                    if (note.pages.size == 1) { repo.deletePage(noteId, page.id); onDone() }
                    else { repo.deletePage(noteId, page.id); if (index > 0) index-- }
                } else ToolButton(PsIcons.Trash, "Delete page") { deleteArmed = true }
            }
        }
    }
}

@Composable
private fun ToolGroup(content: @Composable () -> Unit) {
    val c = PaperTheme.colors
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp)).padding(3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) { content() }
}

@Composable
private fun ToolButton(icon: ImageVector, desc: String, selected: Boolean = false, enabled: Boolean = true, flip: Boolean = false, onClick: () -> Unit) {
    val c = PaperTheme.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).background(if (selected) c.accentSoft else Color.Transparent)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        PsIcon(icon, desc, when { !enabled -> c.line; selected -> c.accent; else -> c.ink },
            Modifier.size(21.dp).graphicsLayer { if (flip) rotationZ = 180f })
    }
}

@Composable
private fun TextTool(label: String, danger: Boolean = false, onClick: () -> Unit) {
    val c = PaperTheme.colors
    Box(
        Modifier.clip(RoundedCornerShape(9.dp)).background(if (danger) c.danger else Color.Transparent).clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp),
    ) { Text(label, color = if (danger) Color.White else c.ink, style = MaterialTheme.typography.labelMedium) }
}

@Composable
private fun ColorDot(color: Color, desc: String, selected: Boolean, onClick: () -> Unit) {
    val c = PaperTheme.colors
    Box(
        Modifier.size(40.dp).clip(RoundedCornerShape(9.dp)).clickable(onClickLabel = desc, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape)
                .then(if (selected) Modifier.border(3.dp, c.accent, CircleShape) else Modifier.border(1.dp, Color(0x26000000), CircleShape))
                .padding(if (selected) 4.dp else 0.dp).clip(CircleShape).background(color),
        )
    }
}
