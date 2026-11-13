package com.paperstar.notes.ui.home

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBounce
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import com.paperstar.notes.ui.theme.Paper
import com.paperstar.notes.ui.theme.StarColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

const val MAX_STARS = 45
private const val W = 120f
private const val H = 150f
/** Big enough that neighbours overlap a little, like a real pile of paper stars. */
private const val STAR_R = 10.5f

/** Dashed empty jar used for the "New jar" button. */
@Composable
fun EmptyJarOutline(modifier: Modifier = Modifier) {
    val c = Paper.colors
    Canvas(modifier.aspectRatio(W / H)) {
        val k = size.width / W
        scale(k, pivot = Offset.Zero) {
            val dash = Stroke(2.5f, pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(7f, 6f)))
            drawPath(Path().apply { addRoundRect(RoundRect(12f, 26f, 108f, 145f, CornerRadius(23f))) }, c.line, style = dash)
            drawRoundRect(c.line, Offset(26f, 6f), Size(68f, 14f), CornerRadius(5f), style = dash)
            val p = 4f; val cx = 60f; val cy = 86f
            drawLine(c.accent, Offset(cx - 12f, cy), Offset(cx + 12f, cy), strokeWidth = p, cap = StrokeCap.Round)
            drawLine(c.accent, Offset(cx, cy - 12f), Offset(cx, cy + 12f), strokeWidth = p, cap = StrokeCap.Round)
        }
    }
}

private data class StarSpot(val x: Float, val y: Float, val color: Color, val rot: Float)

/** Same layout every time for a given jar: rows of five, piled from the bottom up. */
private fun spots(seed: String, n: Int): List<StarSpot> {
    val r = Random(seed.hashCode())
    return List(n) { i ->
        val row = i / 5; val col = i % 5
        val x = 28f + col * 16f + (if (row % 2 == 1) 6f else 0f) + (r.nextFloat() - .5f) * 3f
        val y = 129f - row * 12.5f + (r.nextFloat() - .5f) * 3f
        StarSpot(x, y, StarColors[r.nextInt(StarColors.size)], r.nextFloat() * 72f)
    }
}

fun starPath(cx: Float, cy: Float, radius: Float): Path = Path().apply {
    for (i in 0 until 10) {
        val a = -PI / 2 + i * PI / 5
        val rr = if (i % 2 == 1) radius * .5f else radius
        val x = cx + cos(a).toFloat() * rr; val y = cy + sin(a).toFloat() * rr
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

/** A puffy folded-paper star: the fat round stroke in the same colour makes the points soft. */
fun DrawScope.paperStar(cx: Float, cy: Float, radius: Float, color: Color, rotation: Float) {
    rotate(rotation, pivot = Offset(cx, cy)) {
        val p = starPath(cx, cy, radius)
        drawPath(p, color)
        drawPath(p, color, style = Stroke(width = radius * .42f, join = StrokeJoin.Round))
        drawPath(p, Color(0x40462D5A), style = Stroke(width = .8f, join = StrokeJoin.Round))
    }
}

/**
 * A glass jar holding one paper star per note.
 * @param drops how many of the newest stars should fall in (played once, then [onDropsShown]).
 * @param shake bump this number to make the jar shake.
 */
@Composable
fun JarArt(
    jarId: String,
    stars: Int,
    lid: Color,
    modifier: Modifier = Modifier,
    drops: Int = 0,
    shake: Int = 0,
    onDropsShown: () -> Unit = {},
) {
    val c = Paper.colors
    val n = min(stars, MAX_STARS)
    val d = if (stars > MAX_STARS) 0 else min(drops, n)
    val layout = remember(jarId, n) { spots(jarId, n) }
    val fall = remember(jarId) { Animatable(1f) }
    val tilt = remember(jarId) { Animatable(0f) }

    LaunchedEffect(jarId, d) {
        if (d > 0) {
            fall.snapTo(0f)
            fall.animateTo(1f, tween(durationMillis = 1000 + 350 * (d - 1), easing = LinearEasing))
            tilt.animateTo(0f, keyframes { durationMillis = 450; -4f at 120; 3f at 280; 0f at 450 })
            onDropsShown()
        }
    }
    LaunchedEffect(shake) {
        if (shake > 0) tilt.animateTo(0f, keyframes {
            durationMillis = 650; -9f at 80; 8f at 180; -7f at 280; 6f at 380; -3f at 500; 0f at 650
        })
    }

    Canvas(
        modifier
            .aspectRatio(W / H)
            .graphicsLayer { rotationZ = tilt.value; transformOrigin = TransformOrigin(.5f, .92f) }
    ) {
        val k = size.width / W
        scale(k, pivot = Offset.Zero) {
            val body = RoundRect(12f, 26f, 108f, 145f, CornerRadius(23f))
            drawPath(Path().apply { addRoundRect(body) }, c.glass)

            // settled stars, clipped to the inside of the jar
            val inside = Path().apply { addRoundRect(RoundRect(14f, 28f, 106f, 143f, CornerRadius(21f))) }
            clipPath(inside) {
                layout.forEachIndexed { i, s -> if (i < n - d) paperStar(s.x, s.y, STAR_R, s.color, s.rot) }
            }
            // falling stars: drawn outside the clip so they're visible above the jar
            val total = 1000f + 350f * (d - 1).coerceAtLeast(0)
            for (j in 0 until d) {
                val s = layout[n - d + j]
                val t = ((fall.value * total - j * 350f) / 1000f).coerceIn(0f, 1f)
                val e = EaseOutBounce.transform(t)
                translate(top = -150f * (1 - e)) { paperStar(s.x, s.y, STAR_R, s.color, s.rot - 140f * (1 - e)) }
            }

            drawPath(Path().apply { addRoundRect(body) }, c.glassLine, style = Stroke(2.5f))
            drawPath(Path().apply { moveTo(24f, 48f); quadraticTo(20f, 78f, 24f, 112f) }, c.glassShine,
                style = Stroke(4f, cap = StrokeCap.Round))
            // neck and lid
            drawRoundRect(c.glass, Offset(22f, 18f), Size(76f, 12f), CornerRadius(5f))
            drawRoundRect(c.glassLine, Offset(22f, 18f), Size(76f, 12f), CornerRadius(5f), style = Stroke(2.5f))
            drawRoundRect(lid, Offset(26f, 4f), Size(68f, 16f), CornerRadius(5f))
            drawRoundRect(c.glassLine, Offset(26f, 4f), Size(68f, 16f), CornerRadius(5f), style = Stroke(2f))
        }
    }
}
