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
    val Star = icon("star", "M12 3l2.7 5.6 6.1.9-4.4 4.3 1 6.1L12 17l-5.4 2.9 1-6.1-4.4-4.3 6.1-.9z")
}

@Composable
fun PsIcon(v: ImageVector, desc: String?, tint: Color, modifier: Modifier = Modifier) =
    Icon(v, contentDescription = desc, tint = tint, modifier = modifier)
