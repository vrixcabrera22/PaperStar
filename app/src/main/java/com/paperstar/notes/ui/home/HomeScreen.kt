package com.paperstar.notes.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.Folder
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.Repository
import com.paperstar.notes.data.search
import com.paperstar.notes.data.searchTerms
import com.paperstar.notes.data.snippet
import com.paperstar.notes.ui.EmptyBox
import com.paperstar.notes.ui.NoteCard
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.plural
import com.paperstar.notes.ui.theme.Paper

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    repo: Repository,
    data: AppData,
    drops: Map<String, Int>,
    onOpenJar: (String) -> Unit,
    onOpenNote: (String) -> Unit,
    onOpenBackup: () -> Unit = {},
) {
    val c = Paper.colors
    val haptics = LocalHapticFeedback.current
    var query by rememberSaveable { mutableStateOf("") }
    var showNewJar by remember { mutableStateOf(false) }
    var lucky by remember { mutableStateOf<Pair<Note, Folder>?>(null) }
    val shakes = remember { mutableStateMapOf<String, Int>() }
    val jars = data.folders.filterNot { it.secret }

    fun pullLuckyStar(from: List<Folder>) {
        val ids = from.map { it.id }.toSet()
        val pool = data.notes.filter { it.folderId in ids }
        if (pool.isEmpty()) return
        val n = pool.random()
        lucky = n to data.folder(n.folderId)!!
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize().statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 40.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(22.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LogoStar(Modifier.size(42.dp))
                        Text(
                            "paper star", style = MaterialTheme.typography.headlineLarge, color = c.ink,
                            modifier = Modifier.padding(start = 6.dp).drawBehind {
                                drawRect(c.highlight, topLeft = Offset(0f, size.height * .58f),
                                    size = androidx.compose.ui.geometry.Size(size.width, size.height * .3f))
                            },
                        )
                    }
                    Text("Fold your notes into stars and keep them in jars.", color = c.muted, style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 6.dp))
                }
                Box(
                    Modifier.padding(end = 8.dp).size(44.dp).clip(CircleShape).background(c.accentSoft).clickable(onClick = onOpenBackup),
                    contentAlignment = Alignment.Center,
                ) { PsIcon(PsIcons.Cloud, "Backup", c.accent, Modifier.size(22.dp)) }
                Box(
                    Modifier.size(44.dp).clip(CircleShape).background(c.accentSoft)
                        .clickable { haptics.performHapticFeedback(HapticFeedbackType.LongPress); pullLuckyStar(jars) },
                    contentAlignment = Alignment.Center,
                ) { PsIcon(PsIcons.Star, "Lucky star: show a random note", c.accent, Modifier.size(22.dp)) }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { SearchField(query) { query = it } }

        if (query.isNotBlank()) {
            val terms = searchTerms(query)
            val hits = search(data, query)
            item(span = { GridItemSpan(maxLineSpan) }) {
                if (hits.isEmpty()) EmptyBox("No notes match “${query.trim()}”.", "Search looks at titles, typed text, checklists, tags, jar names, files, and words in handwriting, pictures and PDFs.")
                else Text("${plural(hits.size, "note")} found", color = c.muted, style = MaterialTheme.typography.bodySmall)
            }
            items(hits, key = { it.note.id }, span = { GridItemSpan(maxLineSpan) }) { h ->
                NoteCard(
                    h.note, onClick = { onOpenNote(h.note.id) },
                    jarLabel = h.folder.name to c.lids[h.folder.colorKey % c.lids.size],
                    terms = terms, bodyText = h.foundIn ?: snippet(h.note.body, terms),
                )
            }
        } else {
            items(jars, key = { it.id }) { f ->
                val count = data.notesIn(f.id).size
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .combinedClickable(
                            onClick = { onOpenJar(f.id) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                shakes[f.id] = (shakes[f.id] ?: 0) + 1
                                pullLuckyStar(listOf(f))
                            },
                            onLongClickLabel = "Shake for a lucky star",
                        )
                        .padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    JarArt(
                        jarId = f.id, stars = count, lid = c.lids[f.colorKey % c.lids.size],
                        modifier = Modifier.widthIn(max = 150.dp).fillMaxWidth(),
                        drops = drops[f.id] ?: 0, shake = shakes[f.id] ?: 0,
                        onDropsShown = { repo.consumeDrops(f.id) },
                    )
                    Text(f.name, style = MaterialTheme.typography.titleMedium, color = c.ink, textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp))
                    Text(plural(count, "note"), style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
            }
            item {
                Column(
                    Modifier.clip(RoundedCornerShape(16.dp)).clickable { showNewJar = true }.padding(4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    EmptyJarOutline(Modifier.widthIn(max = 150.dp).fillMaxWidth())
                    Text("New jar", style = MaterialTheme.typography.titleMedium, color = c.muted, modifier = Modifier.padding(top = 6.dp))
                }
            }
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    "Tip: long-press a jar to shake it and pull out a lucky star. Every note is one paper star.",
                    color = c.muted, style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }

    if (showNewJar) NewJarDialog(onDismiss = { showNewJar = false }) { name, color ->
        showNewJar = false
        onOpenJar(repo.addFolder(name, color).id)
    }
    lucky?.let { (n, f) ->
        AlertDialog(
            onDismissRequest = { lucky = null },
            icon = { LogoStar(Modifier.size(40.dp)) },
            title = { Text("A lucky star from ${f.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(n.title.ifBlank { "Untitled" }, fontWeight = FontWeight.Bold, color = c.ink)
                    if (n.body.isNotBlank()) Text(n.body, maxLines = 4, color = c.muted,
                        style = com.paperstar.notes.ui.theme.previewTextStyle(MaterialTheme.typography.bodyMedium, n.font))
                }
            },
            confirmButton = { TextButton(onClick = { lucky = null; onOpenNote(n.id) }) { Text("Open note") } },
            dismissButton = { TextButton(onClick = { lucky = null }) { Text("Put it back") } },
        )
    }
}

@Composable
fun LogoStar(modifier: Modifier) {
    androidx.compose.foundation.Canvas(modifier) {
        val k = size.minDimension / 40f
        scale(k, pivot = Offset.Zero) {
            paperStar(20f, 21f, 14f, androidx.compose.ui.graphics.Color(0xFFFFE3A8), 8f)
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    val c = Paper.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(c.surface)
            .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PsIcon(PsIcons.Search, null, c.muted, Modifier.size(20.dp))
        Box(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 14.dp)) {
            if (value.isEmpty()) Text("Search notes or #tags", color = c.muted, style = MaterialTheme.typography.bodyLarge)
            BasicTextField(
                value = value, onValueChange = onChange, singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = c.ink),
                cursorBrush = SolidColor(c.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) Box(
            Modifier.size(32.dp).clip(CircleShape).background(c.accentSoft).clickable { onChange("") },
            contentAlignment = Alignment.Center,
        ) { PsIcon(PsIcons.Close, "Clear search", c.accent, Modifier.size(16.dp)) }
    }
}

@Composable
fun NewJarDialog(
    title: String = "New jar",
    initialName: String = "",
    initialColor: Int = 0,
    confirm: String = "Create",
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    val c = Paper.colors
    var name by remember { mutableStateOf(initialName) }
    var color by remember { mutableIntStateOf(initialColor) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text("Name") },
                    placeholder = { Text("e.g. Chemistry") })
                Text("Lid colour", style = MaterialTheme.typography.labelMedium, color = c.muted)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    c.lids.forEachIndexed { i, lid ->
                        Box(
                            Modifier.size(38.dp).clip(RoundedCornerShape(10.dp)).background(lid)
                                .border(2.dp, if (i == color) c.ink else lid, RoundedCornerShape(10.dp))
                                .clickable { color = i },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(name, color) }) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
