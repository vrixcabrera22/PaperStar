package com.paperstar.notes.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.Mark
import com.paperstar.notes.data.MarkType
import com.paperstar.notes.data.hasMark
import com.paperstar.notes.data.markColor
import com.paperstar.notes.data.setMark
import com.paperstar.notes.data.shiftMarks
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import androidx.compose.ui.graphics.luminance
import com.paperstar.notes.ui.theme.Paper

/** Highlighter colours. Like real highlighters, they look the same in dark mode, with dark text on top. */
val HighlightColors = listOf(Color(0xFFFFE45C), Color(0xFFFFB8CF), Color(0xFFB5F0B0), Color(0xFFAEDCFF))
private val HighlightNames = listOf("Yellow", "Pink", "Green", "Blue")
private val OnHighlight = Color(0xFF1D1A2C)

/** Text colours, with a lighter version of each for dark mode. */
val TextColorsLight = listOf(Color(0xFFD12F4A), Color(0xFF2F62D6), Color(0xFF23884A), Color(0xFF8A3FD1), Color(0xFFD06A12))
private val TextColorsDark = listOf(Color(0xFFFF8597), Color(0xFF8FB2FF), Color(0xFF7FD69B), Color(0xFFC79BFF), Color(0xFFFFAD66))
private val TextColorNames = listOf("Red", "Blue", "Green", "Purple", "Orange")

@Composable
fun textColors(): List<Color> = if (Paper.colors.bg.luminance() < 0.5f) TextColorsDark else TextColorsLight

fun styledText(text: String, marks: List<Mark>, textColors: List<Color>): AnnotatedString =
    AnnotatedString.Builder(text).apply {
        for (m in marks) {
            if (m.end > text.length) continue
            val style = when (m.type) {
                MarkType.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                MarkType.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                MarkType.UNDERLINE -> SpanStyle(textDecoration = TextDecoration.Underline)
                MarkType.COLOR -> SpanStyle(color = textColors[m.color.coerceIn(textColors.indices)])
                MarkType.HIGHLIGHT -> null
            } ?: continue
            addStyle(style, m.start, m.end)
        }
        // Highlights go last so their dark text wins over a text colour.
        for (m in marks) if (m.type == MarkType.HIGHLIGHT && m.end <= text.length) addStyle(
            SpanStyle(background = HighlightColors[m.color.coerceIn(HighlightColors.indices)], color = OnHighlight), m.start, m.end,
        )
    }.toAnnotatedString()

/** Shows the marks inside a BasicTextField. The letters stay the same, so the cursor mapping is 1:1. */
class MarksTransformation(private val marks: List<Mark>, private val textColors: List<Color>) : VisualTransformation {
    override fun filter(text: AnnotatedString) = TransformedText(styledText(text.text, marks, textColors), OffsetMapping.Identity)
    override fun equals(other: Any?) = other is MarksTransformation && other.marks == marks && other.textColors == textColors
    override fun hashCode() = marks.hashCode() * 31 + textColors.hashCode()
}

/**
 * Typed text with its formatting. [pending] holds styles picked on the toolbar with nothing selected:
 * they apply to the next letters typed, then reset when the cursor moves.
 */
class RichTextState(text: String, marks: List<Mark>) {
    var value by mutableStateOf(TextFieldValue(text))
    var marks by mutableStateOf(marks)
    /** Style → colour to turn on, or null to turn off. */
    var pending by mutableStateOf<Map<MarkType, Int?>>(emptyMap())

    /** Returns true when the text or marks changed and need saving. */
    fun onValueChange(new: TextFieldValue): Boolean {
        val old = value
        value = new
        if (new.text == old.text) {
            if (new.selection != old.selection) pending = emptyMap()
            return false
        }
        var m = shiftMarks(marks, old.text, new.text, new.selection.end)
        val typedFrom = new.selection.end - (new.text.length - old.text.length)
        if (pending.isNotEmpty() && new.text.length > old.text.length && typedFrom >= 0) {
            for ((type, color) in pending) m = setMark(m, typedFrom, new.selection.end, type, color != null, color ?: 0, new.text.length)
        }
        marks = m
        return true
    }

    fun isOn(type: MarkType): Boolean {
        if (type in pending) return pending[type] != null
        val s = value.selection
        return hasMark(marks, s.min, s.max, type)
    }

    fun colorOf(type: MarkType): Int? {
        if (type in pending) return pending[type]
        val s = value.selection
        return markColor(marks, s.min, s.max, type)
    }

    /** Applies a toolbar choice. Returns true when the marks changed and need saving. */
    fun apply(type: MarkType, on: Boolean, color: Int = 0): Boolean {
        val s: TextRange = value.selection
        if (s.collapsed) {
            pending = pending + (type to if (on) color else null)
            return false
        }
        marks = setMark(marks, s.min, s.max, type, on, color, value.text.length)
        return true
    }
}

/** The formatting bar shown above the keyboard while typing (#2). */
@Composable
fun FormatBar(state: RichTextState, onChange: () -> Unit, onFont: () -> Unit, modifier: Modifier = Modifier) {
    val c = Paper.colors
    var palette by remember { mutableStateOf<MarkType?>(null) }
    val colors = textColors()
    fun toggle(t: MarkType) { palette = null; if (state.apply(t, !state.isOn(t))) onChange() }

    Column(modifier.fillMaxWidth().background(c.surface)) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(c.line))
        palette?.let { type ->
            val swatches = if (type == MarkType.HIGHLIGHT) HighlightColors else colors
            val names = if (type == MarkType.HIGHLIGHT) HighlightNames else TextColorNames
            val current = if (state.isOn(type)) state.colorOf(type) else null
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (type == MarkType.HIGHLIGHT) "Highlight" else "Text colour", style = MaterialTheme.typography.labelMedium, color = c.muted)
                swatches.forEachIndexed { i, col ->
                    val picked = current == i
                    Box(
                        Modifier.size(34.dp).clip(CircleShape).background(col)
                            .border(if (picked) 3.dp else 1.dp, if (picked) c.accent else c.line, CircleShape)
                            .clickable(role = Role.Button) { if (state.apply(type, true, i)) onChange(); palette = null }
                            .semantics { contentDescription = names[i]; selected = picked },
                        contentAlignment = Alignment.Center,
                    ) { if (type == MarkType.COLOR) Text("A", color = if (isDarkColor(col)) Color.White else OnHighlight, style = MaterialTheme.typography.labelMedium) }
                }
                Box(
                    Modifier.size(34.dp).clip(CircleShape).border(1.dp, c.line, CircleShape)
                        .clickable(role = Role.Button) { if (state.apply(type, false)) onChange(); palette = null }
                        .semantics { contentDescription = "No ${if (type == MarkType.HIGHLIGHT) "highlight" else "colour"}"; selected = current == null },
                    contentAlignment = Alignment.Center,
                ) { PsIcon(PsIcons.Close, null, c.muted, Modifier.size(16.dp)) }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically,
        ) {
            FormatButton("Bold", state.isOn(MarkType.BOLD), { toggle(MarkType.BOLD) }) {
                Text("B", color = it, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.ExtraBold))
            }
            FormatButton("Italic", state.isOn(MarkType.ITALIC), { toggle(MarkType.ITALIC) }) {
                Text("I", color = it, style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic, fontWeight = FontWeight.Medium))
            }
            FormatButton("Underline", state.isOn(MarkType.UNDERLINE), { toggle(MarkType.UNDERLINE) }) {
                Text("U", color = it, style = MaterialTheme.typography.titleMedium.copy(textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium))
            }
            Box(Modifier.width(1.dp).height(24.dp).background(c.line))
            FormatButton("Font and size", false, { palette = null; onFont() }) {
                Text("Aa", color = it, style = MaterialTheme.typography.titleMedium)
            }
            val hl = if (state.isOn(MarkType.HIGHLIGHT)) state.colorOf(MarkType.HIGHLIGHT) else null
            FormatButton("Highlight", hl != null || palette == MarkType.HIGHLIGHT,
                { palette = if (palette == MarkType.HIGHLIGHT) null else MarkType.HIGHLIGHT }) { tint ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PsIcon(PsIcons.Highlighter, null, tint, Modifier.size(20.dp))
                    Box(Modifier.padding(top = 2.dp).width(18.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(HighlightColors[hl ?: 0]))
                }
            }
            val tc = if (state.isOn(MarkType.COLOR)) state.colorOf(MarkType.COLOR) else null
            FormatButton("Text colour", tc != null || palette == MarkType.COLOR,
                { palette = if (palette == MarkType.COLOR) null else MarkType.COLOR }) { tint ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("A", color = tint, style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.width(18.dp).height(4.dp).clip(RoundedCornerShape(2.dp))
                        .background(if (tc != null) colors[tc] else c.ink))
                }
            }
        }
    }
}

@Composable
private fun FormatButton(label: String, on: Boolean, onClick: () -> Unit, content: @Composable (Color) -> Unit) {
    val c = Paper.colors
    Box(
        Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(if (on) c.accentSoft else Color.Transparent)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label; selected = on },
        contentAlignment = Alignment.Center,
    ) { content(if (on) c.accent else c.ink) }
}

private fun isDarkColor(c: Color) = (0.299 * c.red + 0.587 * c.green + 0.114 * c.blue) < 0.6
