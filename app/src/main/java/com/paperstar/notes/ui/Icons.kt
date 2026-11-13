package com.paperstar.notes.ui

import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Small line icons (24x24, 2px stroke), drawn from SVG path data so no icon library is needed. */
object PsIcons {
    private fun icon(name: String, vararg paths: String): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach {
                addPath(
                    pathData = addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = 2f,
                    strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val Back = icon("back", "M15 18l-6-6 6-6")
    val Search = icon("search", "M11 4a7 7 0 1 0 0 14a7 7 0 1 0 0-14z", "M20 20l-3.5-3.5")
    val Close = icon("close", "M6 6l12 12", "M18 6L6 18")
    val Plus = icon("plus", "M12 5v14", "M5 12h14")
    val More = icon("more", "M12 5.5v.01", "M12 12v.01", "M12 18.5v.01")
    val Pin = icon("pin", "M12 17v5", "M9 3h6l-1 6 4 4v2H6v-2l4-4z")
    val Trash = icon("trash", "M3 6h18", "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6", "M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2")
    val Tag = icon("tag", "M3 12V3h9l9 9-9 9z", "M7.5 7.5v.01")
    val Check = icon("check", "M5 12l5 5 9-10")
    val Pen = icon("pen", "M12 20h9", "M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4z")
    val Highlighter = icon("highlighter", "M9 11l-6 6v3h9l3-3", "M22 12l-4.6 4.6a2 2 0 0 1-2.8 0l-5.2-5.2a2 2 0 0 1 0-2.8L14 4")
    val Eraser = icon("eraser", "M7 21l-4.3-4.3c-1-1-1-2.5 0-3.4l9.6-9.6c1-1 2.5-1 3.4 0l5.6 5.6c1 1 1 2.5 0 3.4L13 21", "M22 21H7", "M5 11l9 9")
    val Lasso = icon("lasso", "M7 22a5 5 0 0 1-2-4", "M3.3 14A6.8 6.8 0 0 1 2 10c0-4.4 4.5-8 10-8s10 3.6 10 8-4.5 8-10 8a12 12 0 0 1-5-1", "M5 14a2 2 0 1 0 0 4a2 2 0 1 0 0-4z")
    val Undo = icon("undo", "M9 14L4 9l5-5", "M4 9h10.5a5.5 5.5 0 0 1 0 11H11")
    val Redo = icon("redo", "M15 14l5-5-5-5", "M20 9H9.5a5.5 5.5 0 0 0 0 11H13")
    val Copy = icon("copy", "M10 8h10a2 2 0 0 1 2 2v10a2 2 0 0 1-2 2H10a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2z", "M4 16a2 2 0 0 1-2-2V4a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2")
    val Image = icon("image", "M5 3h14a2 2 0 0 1 2 2v14a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2z", "M9 7.5a1.5 1.5 0 1 0 0 3a1.5 1.5 0 1 0 0-3z", "M21 15l-5-5L5 21")
    val Camera = icon("camera", "M14.5 4h-5L7 7H4a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h16a2 2 0 0 0 2-2V9a2 2 0 0 0-2-2h-3z", "M12 10a3 3 0 1 0 0 6a3 3 0 1 0 0-6z")
    val Scan = icon("scan", "M3 7V5a2 2 0 0 1 2-2h2", "M17 3h2a2 2 0 0 1 2 2v2", "M21 17v2a2 2 0 0 1-2 2h-2", "M7 21H5a2 2 0 0 1-2-2v-2", "M7 12h10")
    val Clip = icon("clip", "M21.4 11.1l-9.2 9.2a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5")
    val Open = icon("open", "M15 3h6v6", "M10 14L21 3", "M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6")
    val Star = icon("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z")
}

@Composable
fun PsIcon(v: ImageVector, desc: String?, tint: Color, modifier: Modifier = Modifier) =
    Icon(v, contentDescription = desc, tint = tint, modifier = modifier)
