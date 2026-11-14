package com.paperstar.notes.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.matchRanges
import com.paperstar.notes.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun whenText(t: Long, now: Long = System.currentTimeMillis()): String {
    val d = ((now - t) / 86_400_000L).toInt()
    return when {
        d <= 0 -> "today"
        d == 1 -> "yesterday"
        d < 7 -> "$d days ago"
        else -> SimpleDateFormat("MMM d", Locale.getDefault()).format(Date(t))
    }
}

fun plural(n: Int, word: String) = "$n $word${if (n == 1) "" else "s"}"

fun highlighted(text: String, terms: List<String>, bg: Color): AnnotatedString = buildAnnotatedString {
    append(text)
    if (terms.isNotEmpty()) matchRanges(text, terms).forEach {
        addStyle(SpanStyle(background = bg, color = Color(0xFF1D1A2C)), it.first, it.last + 1)
    }
}

@Composable
fun TagChip(tag: String, selected: Boolean = false, onClick: (() -> Unit)? = null, onRemove: (() -> Unit)? = null) {
    val c = Paper.colors
    Row(
        Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) c.accent else c.accentSoft)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 10.dp, end = if (onRemove != null) 4.dp else 10.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("#$tag", style = MaterialTheme.typography.labelMedium, color = if (selected) c.accentInk else c.accent)
        if (onRemove != null) {
            Box(Modifier.padding(start = 2.dp).size(20.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
                PsIcon(PsIcons.Close, "Remove tag $tag", c.accent, Modifier.size(12.dp))
            }
        }
    }
}

/** The note card used in jar lists and search results. */
@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    jarLabel: Pair<String, Color>? = null,
    terms: List<String> = emptyList(),
    bodyText: String = note.body,
) {
    val c = Paper.colors
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (jarLabel != null) Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(jarLabel.second))
            Spacer(Modifier.width(6.dp))
            Text(jarLabel.first, style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (note.pinned) {
                PsIcon(PsIcons.Pin, "Pinned", c.accent, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
            }
            Text(highlighted(note.title.ifBlank { "Untitled" }, terms, c.highlight), style = MaterialTheme.typography.titleMedium,
                color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (bodyText.isNotBlank()) Text(highlighted(bodyText, terms, c.highlight),
            style = com.paperstar.notes.ui.theme.previewTextStyle(MaterialTheme.typography.bodyMedium, note.font),
            color = c.muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (note.pages.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            note.pages.take(4).forEach { p ->
                com.paperstar.notes.ui.pen.PageThumb(p, Modifier.width(40.dp).clip(RoundedCornerShape(4.dp)).border(1.dp, c.line, RoundedCornerShape(4.dp)))
            }
            Text(plural(note.pages.size, "page"), style = MaterialTheme.typography.labelMedium, color = c.muted)
        }
        if (note.pictures.isNotEmpty() || note.files.isNotEmpty()) {
            val ctx = androidx.compose.ui.platform.LocalContext.current
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                note.pictures.take(4).forEach { p ->
                    com.paperstar.notes.ui.media.SavedPicture(java.io.File(ctx.filesDir, p.path),
                        Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)), maxPx = 160)
                }
                val extra = buildList {
                    if (note.pictures.size > 4) add("+${note.pictures.size - 4}")
                    if (note.files.isNotEmpty()) add(plural(note.files.size, "file"))
                }.joinToString(" · ")
                if (extra.isNotEmpty()) Text(extra, style = MaterialTheme.typography.labelMedium, color = c.muted)
            }
        }
        if (note.checklist.isNotEmpty()) {
            val done = note.checklist.count { it.done }
            Text("$done of ${note.checklist.size} done", style = MaterialTheme.typography.labelMedium,
                color = if (done == note.checklist.size) c.accent else c.muted)
        }
        if (note.tags.isNotEmpty()) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            note.tags.take(4).forEach { TagChip(it) }
        }
        Text("Edited ${whenText(note.updatedAt)}", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}

@Composable
fun EmptyBox(text: String, sub: String? = null) {
    val c = Paper.colors
    Column(
        Modifier.fillMaxWidth().border(2.dp, c.line, RoundedCornerShape(14.dp)).padding(vertical = 36.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text, color = c.muted, style = MaterialTheme.typography.bodyLarge)
        if (sub != null) Text(sub, color = c.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
    }
}
