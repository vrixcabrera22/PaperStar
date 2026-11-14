package com.paperstar.notes.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.theme.NoteFonts
import com.paperstar.notes.ui.theme.Paper
import com.paperstar.notes.ui.theme.TextSizeNames
import com.paperstar.notes.ui.theme.noteFont

/** The "Aa" chip above the typed text, showing the note's font and size. */
@Composable
fun FontChip(fontKey: String, size: Int, onClick: () -> Unit) {
    val c = Paper.colors
    val f = noteFont(fontKey)
    Row(
        Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, c.line, RoundedCornerShape(999.dp))
            .clickable(role = Role.Button, onClickLabel = "Change font", onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Aa", fontFamily = f.family, fontSize = (16 * f.scale).sp, color = c.accent)
        Text("  ${f.name} · ${TextSizeNames[size.coerceIn(TextSizeNames.indices)]}",
            style = MaterialTheme.typography.labelMedium, color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Font and size for a note's typed text. Changes apply right away. */
@Composable
fun FontPickerDialog(fontKey: String, size: Int, onFont: (String) -> Unit, onSize: (Int) -> Unit, onDismiss: () -> Unit) {
    val c = Paper.colors
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Font") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextSizeNames.forEachIndexed { i, name ->
                        val on = i == size
                        Column(
                            Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                                .background(if (on) c.accentSoft else c.surface)
                                .border(if (on) 2.dp else 1.dp, if (on) c.accent else c.line, RoundedCornerShape(12.dp))
                                .clickable(role = Role.RadioButton) { onSize(i) }
                                .semantics { selected = on }
                                .height(68.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text("A", fontSize = listOf(14, 18, 24)[i].sp, color = if (on) c.accent else c.ink,
                                fontFamily = noteFont(fontKey).family)
                            Text(name, style = MaterialTheme.typography.labelMedium, color = if (on) c.accent else c.muted)
                        }
                    }
                }
                LazyColumn(Modifier.heightIn(max = 380.dp)) {
                    items(NoteFonts, key = { it.key }) { f ->
                        val on = f.key == noteFont(fontKey).key
                        Row(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                                .background(if (on) c.accentSoft else androidx.compose.ui.graphics.Color.Transparent)
                                .clickable(role = Role.RadioButton) { onFont(f.key) }
                                .semantics { selected = on }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(f.name, fontFamily = f.family, fontSize = (19 * f.scale).sp, color = c.ink, maxLines = 1)
                                Text("Mitochondria = powerhouse", fontFamily = f.family, fontSize = (13 * f.scale).sp,
                                    color = c.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            if (on) { Spacer(Modifier.size(8.dp)); PsIcon(PsIcons.Check, "Selected", c.accent, Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}
