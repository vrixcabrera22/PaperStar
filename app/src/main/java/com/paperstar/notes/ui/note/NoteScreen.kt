package com.paperstar.notes.ui.note

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.CheckItem
import com.paperstar.notes.data.Repository
import com.paperstar.notes.data.normalizeTag
import com.paperstar.notes.data.PAGE_H
import com.paperstar.notes.data.PAGE_W
import com.paperstar.notes.data.Paper as PaperStyle
import com.paperstar.notes.ui.pen.PageThumb
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.Color
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.TagChip
import com.paperstar.notes.ui.theme.Paper
import com.paperstar.notes.ui.theme.noteTextStyle
import com.paperstar.notes.ui.whenText

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteScreen(
    repo: Repository,
    data: AppData,
    noteId: String,
    onBack: () -> Unit,
    onOpenPen: (Int) -> Unit,
    onOpenPicture: (String) -> Unit,
    onOpenFile: (String) -> Unit,
) {
    val c = Paper.colors
    val note = data.note(noteId) ?: return
    val jar = data.folder(note.folderId)
    // Local copies of the text so typing never fights with saving.
    var title by remember(noteId) { mutableStateOf(note.title) }
    val rich = remember(noteId) { RichTextState(note.body, note.marks) }
    var bodyFocused by remember { mutableStateOf(false) }
    var fontPicker by remember { mutableStateOf(false) }
    fun saveBody() = repo.updateNote(noteId) { n -> n.copy(body = rich.value.text, marks = rich.marks) }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var addingTag by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable(onClick = onBack).padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PsIcon(PsIcons.Back, null, c.accent, Modifier.size(20.dp))
                Text(jar?.name ?: "Back", color = c.accent, style = MaterialTheme.typography.titleMedium, maxLines = 1)
            }
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(if (note.pinned) c.accentSoft else c.bg).clickable { repo.togglePin(noteId) },
                contentAlignment = Alignment.Center,
            ) { PsIcon(PsIcons.Pin, if (note.pinned) "Unpin note" else "Pin note to the top", if (note.pinned) c.accent else c.muted, Modifier.size(20.dp)) }
            Box {
                Box(Modifier.size(40.dp).clip(CircleShape).clickable { menu = true }, contentAlignment = Alignment.Center) {
                    PsIcon(PsIcons.More, "Note options", c.ink, Modifier.size(22.dp))
                }
                DropdownMenu(menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Delete note", color = c.danger) }, onClick = { menu = false; confirmDelete = true })
                }
            }
        }

        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                if (title.isEmpty()) Text("Note title", style = MaterialTheme.typography.headlineMedium, color = c.muted)
                BasicTextField(
                    title, { title = it; repo.updateNote(noteId) { n -> n.copy(title = it) } },
                    textStyle = MaterialTheme.typography.headlineMedium.copy(color = c.ink),
                    cursorBrush = SolidColor(c.accent), modifier = Modifier.fillMaxWidth(),
                )
            }
            Text(if (note.pinned) "Pinned · edited ${whenText(note.updatedAt)}" else "Edited ${whenText(note.updatedAt)}",
                style = MaterialTheme.typography.bodySmall, color = c.muted)

            // tags
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                note.tags.forEach { t -> TagChip(t, onRemove = { repo.updateNote(noteId) { n -> n.copy(tags = n.tags - t) } }) }
                Row(
                    Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, c.line, RoundedCornerShape(999.dp))
                        .clickable { addingTag = true }.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PsIcon(PsIcons.Tag, null, c.muted, Modifier.size(14.dp))
                    Text(" Add tag", style = MaterialTheme.typography.labelMedium, color = c.muted)
                }
            }

            // handwriting
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.accent)
                    .clickable { if (note.pages.isEmpty()) onOpenPen(repo.insertPage(noteId, -1, PaperStyle.LINED)) else onOpenPen(note.pages.lastIndex) }
                    .padding(vertical = 13.dp),
                horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
            ) {
                PsIcon(PsIcons.Pen, null, c.accentInk, Modifier.size(20.dp))
                Text("  Write with pen", color = c.accentInk, style = MaterialTheme.typography.titleMedium)
            }
            if (note.pages.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                    Text("Handwritten", style = MaterialTheme.typography.titleMedium, color = c.ink)
                    Text("  ${note.pages.size} page${if (note.pages.size == 1) "" else "s"}", style = MaterialTheme.typography.bodySmall, color = c.muted)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    note.pages.forEachIndexed { i, p ->
                        Box(
                            Modifier.width(96.dp).clip(RoundedCornerShape(8.dp)).border(1.dp, c.line, RoundedCornerShape(8.dp))
                                .clickable { onOpenPen(i) },
                        ) {
                            PageThumb(p, Modifier.fillMaxWidth())
                            Text("${i + 1}", style = MaterialTheme.typography.labelMedium, color = Color(0xFF6C6882),
                                modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp))
                        }
                    }
                    Box(
                        Modifier.width(96.dp).aspectRatio(PAGE_W / PAGE_H).clip(RoundedCornerShape(8.dp))
                            .border(2.dp, c.accent, RoundedCornerShape(8.dp)).background(c.accentSoft)
                            .clickable { onOpenPen(repo.insertPage(noteId, note.pages.lastIndex, note.pages.last().paper)) },
                        contentAlignment = Alignment.Center,
                    ) { Text("+ New page", color = c.accent, style = MaterialTheme.typography.labelMedium) }
                }
            }

            // pictures, scans and files
            NoteAttachments(repo, note, onOpenPicture, onOpenFile)

            // typed text
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text("Typed", style = MaterialTheme.typography.titleMedium, color = c.ink, modifier = Modifier.weight(1f))
                FontChip(note.font, note.textSize) { fontPicker = true }
            }
            val bodyStyle = noteTextStyle(MaterialTheme.typography.bodyLarge.copy(color = c.ink), note.font, note.textSize)
            Box(
                Modifier.fillMaxWidth().heightIn(min = 120.dp).clip(RoundedCornerShape(14.dp)).background(c.surface)
                    .border(1.dp, c.line, RoundedCornerShape(14.dp)).padding(14.dp),
            ) {
                if (rich.value.text.isEmpty()) Text("Type here…", style = bodyStyle.copy(color = c.muted))
                BasicTextField(
                    rich.value, { if (rich.onValueChange(it)) saveBody() },
                    textStyle = bodyStyle,
                    visualTransformation = MarksTransformation(rich.marks, textColors()),
                    cursorBrush = SolidColor(c.accent),
                    modifier = Modifier.fillMaxWidth().onFocusChanged { bodyFocused = it.isFocused },
                )
            }

            // checklist
            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
                Text("Checklist", style = MaterialTheme.typography.titleMedium, color = c.ink)
                if (note.checklist.isNotEmpty()) Text("  ${note.checklist.count { it.done }} of ${note.checklist.size} done",
                    style = MaterialTheme.typography.bodySmall, color = c.muted)
            }
            note.checklist.forEach { item -> CheckRow(repo, noteId, item) }
            Row(
                Modifier.clip(RoundedCornerShape(10.dp)).clickable {
                    repo.updateNote(noteId) { n -> n.copy(checklist = n.checklist + CheckItem()) }
                }.padding(vertical = 8.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PsIcon(PsIcons.Plus, null, c.accent, Modifier.size(18.dp))
                Text(" Add item", color = c.accent, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.size(40.dp))
        }
        if (bodyFocused) FormatBar(rich, ::saveBody, onFont = { fontPicker = true }, modifier = Modifier.navigationBarsPadding())
    }

    if (addingTag) {
        var raw by remember { mutableStateOf("") }
        val existing = data.notes.flatMap { it.tags }.distinct().filterNot { it in note.tags }.sorted()
        fun add(t: String) {
            val tag = normalizeTag(t)
            if (tag.isNotEmpty() && tag !in note.tags) repo.updateNote(noteId) { n -> n.copy(tags = n.tags + tag) }
            addingTag = false
        }
        AlertDialog(
            onDismissRequest = { addingTag = false },
            title = { Text("Add a tag") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(raw, { raw = it.take(30) }, singleLine = true, placeholder = { Text("e.g. exam") },
                        prefix = { Text("#") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), keyboardActions = KeyboardActions(onDone = { add(raw) }))
                    if (existing.isNotEmpty()) {
                        Text("Tags you've used", style = MaterialTheme.typography.labelMedium, color = c.muted)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            existing.forEach { t -> TagChip(t, onClick = { add(t) }) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { add(raw) }) { Text("Add") } },
            dismissButton = { TextButton(onClick = { addingTag = false }) { Text("Cancel") } },
        )
    }
    if (fontPicker) FontPickerDialog(
        note.font, note.textSize,
        onFont = { k -> repo.updateNote(noteId) { n -> n.copy(font = k) } },
        onSize = { sz -> repo.updateNote(noteId) { n -> n.copy(textSize = sz) } },
        onDismiss = { fontPicker = false },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete “${note.title.ifBlank { "Untitled" }}”?") },
        text = { Text("Its star leaves the jar. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; onBack(); repo.deleteNote(noteId) }) { Text("Delete", color = c.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun CheckRow(repo: Repository, noteId: String, item: CheckItem) {
    val c = Paper.colors
    var text by remember(item.id) { mutableStateOf(item.text) }
    fun change(fn: (CheckItem) -> CheckItem) = repo.updateNote(noteId) { n ->
        n.copy(checklist = n.checklist.map { if (it.id == item.id) fn(it) else it })
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(item.done, { checked -> change { it.copy(done = checked) } },
            colors = CheckboxDefaults.colors(checkedColor = c.accent, checkmarkColor = c.accentInk))
        Box(Modifier.weight(1f)) {
            if (text.isEmpty()) Text("List item", style = MaterialTheme.typography.bodyLarge, color = c.muted)
            BasicTextField(
                text, { text = it; change { ci -> ci.copy(text = it) } },
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = if (item.done) c.muted else c.ink,
                    textDecoration = if (item.done) TextDecoration.LineThrough else TextDecoration.None,
                ),
                cursorBrush = SolidColor(c.accent), singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            Modifier.size(36.dp).clip(CircleShape).clickable {
                repo.updateNote(noteId) { n -> n.copy(checklist = n.checklist.filterNot { it.id == item.id }) }
            },
            contentAlignment = Alignment.Center,
        ) { PsIcon(PsIcons.Close, "Remove item", c.muted, Modifier.size(16.dp)) }
    }
}
