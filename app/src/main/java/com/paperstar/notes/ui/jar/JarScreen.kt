package com.paperstar.notes.ui.jar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.Repository
import com.paperstar.notes.data.sortedForJar
import com.paperstar.notes.ui.EmptyBox
import com.paperstar.notes.ui.NoteCard
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.TagChip
import com.paperstar.notes.ui.home.NewJarDialog
import com.paperstar.notes.ui.plural
import com.paperstar.notes.ui.theme.Paper

@Composable
fun JarScreen(
    repo: Repository,
    data: AppData,
    jarId: String,
    onBack: () -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenPenNote: (String) -> Unit,
) {
    val c = Paper.colors
    val jar = data.folder(jarId) ?: return
    val all = data.notesIn(jarId).sortedForJar()
    val tags = all.flatMap { it.tags }.distinct().sorted()
    var tagFilter by remember(jarId) { mutableStateOf<String?>(null) }
    val shown = if (tagFilter == null) all else all.filter { tagFilter in it.tags }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize().statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 110.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PsIcon(PsIcons.Back, null, c.accent, Modifier.size(20.dp))
                        Text("Jars", color = c.accent, style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.weight(1f))
                    Box {
                        Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                            PsIcon(PsIcons.More, "Jar options", c.ink, Modifier.size(22.dp))
                        }
                        DropdownMenu(menu, onDismissRequest = { menu = false }) {
                            DropdownMenuItem(text = { Text("Rename or recolour") }, onClick = { menu = false; editing = true })
                            DropdownMenuItem(text = { Text("Delete jar", color = c.danger) }, onClick = { menu = false; confirmDelete = true })
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(c.lids[jar.colorKey % c.lids.size]))
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(jar.name, style = MaterialTheme.typography.headlineMedium, color = c.ink)
                        Text("${plural(all.size, "note")} · ${plural(all.size, "star")} in the jar", color = c.muted,
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            if (tags.isNotEmpty()) item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    tags.forEach { t -> TagChip(t, selected = t == tagFilter, onClick = { tagFilter = if (tagFilter == t) null else t }) }
                }
            }
            if (shown.isEmpty()) item {
                EmptyBox(if (tagFilter != null) "No notes tagged #$tagFilter here." else "This jar is empty.",
                    if (tagFilter != null) null else "Tap New note to add your first star.")
            }
            items(shown, key = { it.id }) { n -> NoteCard(n, onClick = { onOpenNote(n.id) }) }
        }

        Row(
            Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ExtendedFloatingActionButton(
                onClick = { onOpenNote(repo.addNote(jarId).id) },
                icon = { PsIcon(PsIcons.Plus, null, c.ink, Modifier.size(20.dp)) },
                text = { Text("Note") },
                containerColor = c.surface, contentColor = c.ink,
            )
            ExtendedFloatingActionButton(
                onClick = {
                    val date = java.text.SimpleDateFormat("MMM d", java.util.Locale.getDefault()).format(java.util.Date())
                    onOpenPenNote(repo.addPenNote(jarId, "${jar.name} notes · $date").id)
                },
                icon = { PsIcon(PsIcons.Pen, null, c.accentInk, Modifier.size(20.dp)) },
                text = { Text("Pen note") },
                containerColor = c.accent, contentColor = c.accentInk,
            )
        }
    }

    if (editing) NewJarDialog(
        title = "Edit jar", initialName = jar.name, initialColor = jar.colorKey, confirm = "Save",
        onDismiss = { editing = false },
    ) { name, color ->
        editing = false
        repo.updateFolder(jarId) { it.copy(name = name.trim().ifEmpty { it.name }, colorKey = color) }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${jar.name}”?") },
        text = { Text("All ${plural(all.size, "note")} inside will be deleted. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; repo.deleteFolder(jarId); onBack() }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}
