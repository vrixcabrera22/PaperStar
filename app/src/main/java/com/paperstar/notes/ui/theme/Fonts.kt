package com.paperstar.notes.ui.theme

import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.paperstar.notes.R

/**
 * A font for a note's typed text. [key] is what gets saved, so never change it.
 * [scale] evens out fonts that look smaller than others at the same size (like Caveat).
 */
data class NoteFont(val key: String, val name: String, val family: FontFamily, val scale: Float = 1f)

/** A variable font file, set to regular and bold. */
@OptIn(ExperimentalTextApi::class)
private fun variable(res: Int) = FontFamily(
    Font(res, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(res, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** The bundled free Google Fonts (#2 and the font picker). The first one is the app's normal font. */
val NoteFonts = listOf(
    NoteFont("", "Default", FontFamily.Default),
    NoteFont("dm_sans", "DM Sans", variable(R.font.dm_sans)),
    NoteFont("nunito", "Nunito", variable(R.font.nunito)),
    NoteFont("lora", "Lora", variable(R.font.lora)),
    NoteFont("caveat", "Caveat", variable(R.font.caveat), scale = 1.3f),
    NoteFont("patrick_hand", "Patrick Hand", FontFamily(Font(R.font.patrick_hand)), scale = 1.12f),
    NoteFont("bricolage", "Bricolage Grotesque", variable(R.font.bricolage)),
    NoteFont("courier", "Courier Prime", FontFamily(Font(R.font.courier_prime), Font(R.font.courier_prime_bold, FontWeight.Bold))),
    NoteFont("comfortaa", "Comfortaa", variable(R.font.comfortaa)),
)

fun noteFont(key: String): NoteFont = NoteFonts.firstOrNull { it.key == key } ?: NoteFonts.first()

/** Small, medium and large typed text. */
val TextSizeNames = listOf("Small", "Medium", "Large")
private val TextSizes = listOf(14f, 16f, 20f)

/** The style for a note's typed text: its font at its size. */
fun noteTextStyle(base: TextStyle, fontKey: String, size: Int): TextStyle {
    val f = noteFont(fontKey)
    val px = TextSizes[size.coerceIn(TextSizes.indices)] * f.scale
    return base.copy(fontFamily = f.family, fontSize = px.sp, lineHeight = (px * 1.5f).sp)
}

/** The same font in a smaller place, like a note card, keeping [base]'s size. */
fun previewTextStyle(base: TextStyle, fontKey: String): TextStyle {
    val f = noteFont(fontKey)
    val size: TextUnit = base.fontSize * f.scale
    return base.copy(fontFamily = f.family, fontSize = size, lineHeight = size * 1.45f)
}
