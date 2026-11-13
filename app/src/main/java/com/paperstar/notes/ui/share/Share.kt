package com.paperstar.notes.ui.share

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.Folder
import com.paperstar.notes.data.Imports
import com.paperstar.notes.data.Repository
import com.paperstar.notes.data.sortedForJar
import com.paperstar.notes.ui.plural
import com.paperstar.notes.ui.theme.Paper
import kotlinx.coroutines.flow.MutableStateFlow

/** Pictures, files or text another app shared into Paper Star. */
data class SharePayload(val uris: List<Uri>, val text: String?, val subject: String?)

object ShareInbox {
    val pending = MutableStateFlow<SharePayload?>(null)

    fun accept(intent: Intent?) {
        intent ?: return
        val uris: List<Uri> = when (intent.action) {
            Intent.ACTION_SEND -> listOfNotNull(
                if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra(Intent.EXTRA_STREAM),
            )
            Intent.ACTION_SEND_MULTIPLE ->
                (if (Build.VERSION.SDK_INT >= 33) intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)).orEmpty()
            else -> return
        }
        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.takeIf { it.isNotBlank() }
        if (uris.isEmpty() && text == null) return
        pending.value = SharePayload(uris, text, intent.getStringExtra(Intent.EXTRA_SUBJECT))
    }
}

/** What could actually be copied out of a share. */
class ShareImport(
    val pictures: List<com.paperstar.notes.data.Picture>,
    val files: List<com.paperstar.notes.data.Attachment>,
    val text: String?,
    val failed: Int,
) {
    val isEmpty get() = pictures.isEmpty() && files.isEmpty() && text == null
}

/** Copies the shared items into app storage: pictures go to Pictures, anything else to Files. */
suspend fun importShare(ctx: Context, payload: SharePayload): ShareImport {
    val pics = mutableListOf<com.paperstar.notes.data.Picture>()
    val files = mutableListOf<com.paperstar.notes.data.Attachment>()
    var failed = 0
    for (u in payload.uris) {
        val mime = try { ctx.contentResolver.getType(u).orEmpty() } catch (e: Exception) { "" }
        val pic = if (mime.startsWith("image/")) Imports.picture(ctx, u) else null
        if (pic != null) pics += pic
        else Imports.file(ctx, u)?.let { files += it } ?: failed++
    }
    return ShareImport(pics, files, payload.text, failed)
}

/** Adds an import to a note; text goes after any typed text already there. */
fun applyShare(repo: Repository, noteId: String, s: ShareImport) {
    if (s.pictures.isNotEmpty()) repo.addPictures(noteId, s.pictures)
    if (s.files.isNotEmpty()) repo.addFiles(noteId, s.files)
    s.text?.let { t -> repo.updateNote(noteId) { n -> n.copy(body = if (n.body.isBlank()) t else n.body + "\n\n" + t) } }
}

/**
 * "Save to Paper Star": pick a jar, then a new or existing note.
 * [onSave] gets the note id (or null with the jar for a new note).
 */
@Composable
fun ShareDialog(data: AppData, payload: SharePayload, onSave: (jar: Folder, noteId: String?) -> Unit, onCancel: () -> Unit) {
    val c = Paper.colors
    var jar by remember { mutableStateOf<Folder?>(null) }
    val what = buildList {
        if (payload.uris.isNotEmpty()) add(plural(payload.uris.size, "item"))
        if (payload.text != null) add("text")
    }.joinToString(" and ")
    AlertDialog(
        onDismissRequest = onCancel,
        title = { Text(if (jar == null) "Save to which jar?" else "Save to which note?") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Saving $what to Paper Star.", color = c.muted, style = MaterialTheme.typography.bodySmall)
                val j = jar
                if (j == null) {
                    data.folders.filterNot { it.secret }.forEach { f ->
                        Choice(f.name, plural(data.notesIn(f.id).size, "note"), c.lids[f.colorKey % c.lids.size]) { jar = f }
                    }
                } else {
                    Choice("New note", "A new star in ${j.name}", c.accent) { onSave(j, null) }
                    data.notesIn(j.id).sortedForJar().take(12).forEach { n ->
                        Choice(n.title.ifBlank { "Untitled" }, null, null) { onSave(j, n.id) }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = { if (jar != null) jar = null else onCancel() }) { Text(if (jar != null) "Back" else "Cancel") }
        },
    )
}

@Composable
private fun Choice(title: String, sub: String?, swatch: androidx.compose.ui.graphics.Color?, onClick: () -> Unit) {
    val c = Paper.colors
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(c.bg).clickable(onClick = onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (swatch != null) {
            Box(Modifier.size(14.dp).clip(RoundedCornerShape(4.dp)).background(swatch))
            Spacer(Modifier.width(10.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, color = c.ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
    }
}
