package com.paperstar.notes.ui.note

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.paperstar.notes.data.Attachment
import com.paperstar.notes.data.Imports
import com.paperstar.notes.data.Note
import com.paperstar.notes.data.Repository
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.media.FileRow
import com.paperstar.notes.ui.media.SavedPicture
import com.paperstar.notes.ui.media.openExternally
import com.paperstar.notes.ui.theme.Paper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) { if (c is Activity) return c; c = c.baseContext }
    return null
}

/** Buttons to add pictures, photos, scans and files, plus the pictures grid and the files list. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NoteAttachments(repo: Repository, note: Note, onOpenPicture: (String) -> Unit, onOpenFile: (String) -> Unit) {
    val c = Paper.colors
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var galleryOffer by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var removing by remember { mutableStateOf<Attachment?>(null) }
    val noteId = note.id

    fun toast(msg: String) = Toast.makeText(ctx, msg, Toast.LENGTH_SHORT).show()

    fun importPictures(uris: List<Uri>, offerGalleryCleanup: Boolean) {
        if (uris.isEmpty()) return
        busy = true
        scope.launch {
            val pics = uris.mapNotNull { Imports.picture(ctx, it) }
            repo.addPictures(noteId, pics)
            busy = false
            if (pics.size < uris.size) toast("${uris.size - pics.size} couldn't be opened as pictures")
            if (offerGalleryCleanup && pics.isNotEmpty()) galleryOffer = Imports.galleryItems(ctx, uris)
        }
    }

    // #17: Android shows its own "Allow Paper Star to delete…" confirmation.
    val deleteFromGallery = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        toast(if (r.resultCode == Activity.RESULT_OK) "Removed from your gallery" else "Kept in your gallery")
    }
    val pickPictures = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        importPictures(uris, offerGalleryCleanup = true)
    }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) scope.launch {
            busy = true
            Imports.picture(ctx, u)?.let { repo.addPictures(noteId, listOf(it)) }
            Imports.clearCameraTemp(ctx)
            busy = false
        }
    }
    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            busy = true
            scope.launch {
                val files = uris.mapNotNull { Imports.file(ctx, it) }
                repo.addFiles(noteId, files)
                busy = false
                if (files.size < uris.size) toast("${uris.size - files.size} file(s) couldn't be copied")
            }
        }
    }
    // #14: one scanned page becomes a picture, several pages become one PDF.
    val scan = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val result = GmsDocumentScanningResult.fromActivityResultIntent(r.data) ?: return@rememberLauncherForActivityResult
        val pages = result.pages.orEmpty()
        busy = true
        scope.launch {
            if (pages.size == 1) {
                Imports.picture(ctx, pages[0].imageUri)?.let { repo.addPictures(noteId, listOf(it)) }
            } else {
                val pdf = result.pdf
                val stamp = SimpleDateFormat("MMM d, h.mm a", Locale.getDefault()).format(Date())
                if (pdf != null) Imports.file(ctx, pdf.uri)?.let { repo.addFiles(noteId, listOf(it.copy(name = "Scan $stamp.pdf", mime = "application/pdf"))) }
            }
            busy = false
            toast(if (pages.size == 1) "Scan added as a picture" else "Scan added as a ${pages.size}-page PDF")
        }
    }

    fun startScan() {
        val activity = ctx.findActivity() ?: return
        val options = GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(30)
            .setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG, GmsDocumentScannerOptions.RESULT_FORMAT_PDF)
            .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
            .build()
        GmsDocumentScanning.getClient(options).getStartScanIntent(activity)
            .addOnSuccessListener { scan.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { toast("The scanner isn't available on this phone. It needs Google Play services.") }
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val m = Modifier.weight(1f)
            AddButton(PsIcons.Image, "Pictures", m) { pickPictures.launch(arrayOf("image/*")) }
            AddButton(PsIcons.Camera, "Camera", m) {
                val u = Imports.cameraTarget(ctx); cameraUri = u
                try { takePhoto.launch(u) } catch (e: Exception) { toast("No camera app found") }
            }
            AddButton(PsIcons.Scan, "Scan", m) { startScan() }
            AddButton(PsIcons.Clip, "File", m) { pickFiles.launch(arrayOf("*/*")) }
        }
        if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = c.accent, trackColor = c.accentSoft)

        if (note.pictures.isNotEmpty()) {
            SectionTitle("Pictures", "${note.pictures.size}")
            BoxWithConstraints {
                val cell = (maxWidth - 12.dp) / 3
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    note.pictures.forEach { p ->
                        SavedPicture(
                            repo.fileOf(p.path),
                            Modifier.width(cell).aspectRatio(1f).clip(RoundedCornerShape(10.dp)).clickable { onOpenPicture(p.id) },
                        )
                    }
                }
            }
        }
        if (note.files.isNotEmpty()) {
            SectionTitle("Files", "${note.files.size}")
            note.files.forEach { f ->
                FileRow(
                    f,
                    onOpen = { if (f.mime == "application/pdf" || f.name.endsWith(".pdf", true)) onOpenFile(f.id) else openExternally(ctx, f) },
                    onRemove = { removing = f },
                )
            }
        }
    }

    if (galleryOffer.isNotEmpty() && Build.VERSION.SDK_INT >= 30) {
        val n = galleryOffer.size
        AlertDialog(
            onDismissRequest = { galleryOffer = emptyList() },
            title = { Text("Remove from your gallery?") },
            text = {
                Text(
                    if (n == 1) "This picture is now saved in this note. Remove the original from your gallery so it doesn't pile up? Android will ask you to confirm."
                    else "These $n pictures are now saved in this note. Remove the originals from your gallery so they don't pile up? Android will ask you to confirm.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val req = MediaStore.createDeleteRequest(ctx.contentResolver, galleryOffer)
                    galleryOffer = emptyList()
                    deleteFromGallery.launch(IntentSenderRequest.Builder(req.intentSender).build())
                }) { Text("Remove") }
            },
            dismissButton = { TextButton(onClick = { galleryOffer = emptyList() }) { Text("Keep") } },
        )
    }
    removing?.let { f ->
        AlertDialog(
            onDismissRequest = { removing = null },
            title = { Text("Remove “${f.name}”?") },
            text = { Text("It will be deleted from this note. This can't be undone.") },
            confirmButton = { TextButton(onClick = { removing = null; repo.removeFile(noteId, f.id) }) { Text("Remove", color = c.danger) } },
            dismissButton = { TextButton(onClick = { removing = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddButton(icon: ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    val c = Paper.colors
    Column(
        modifier.clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick).padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PsIcon(icon, null, c.accent, Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun SectionTitle(title: String, count: String) {
    val c = Paper.colors
    Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(top = 8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = c.ink)
        Text("  $count", style = MaterialTheme.typography.bodySmall, color = c.muted)
    }
}
