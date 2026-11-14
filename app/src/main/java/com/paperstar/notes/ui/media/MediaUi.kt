package com.paperstar.notes.ui.media

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AColor
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.paperstar.notes.data.AppData
import com.paperstar.notes.data.Attachment
import com.paperstar.notes.data.FileKind
import com.paperstar.notes.data.Imports
import com.paperstar.notes.data.Repository
import com.paperstar.notes.data.kindOf
import com.paperstar.notes.data.sizeText
import com.paperstar.notes.ui.PsIcon
import com.paperstar.notes.ui.PsIcons
import com.paperstar.notes.ui.theme.Paper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import com.paperstar.notes.data.PageBackground
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope

// ---------- loading saved pictures ----------

private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
    override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
}

private fun decodeScaled(file: File, maxPx: Int): ImageBitmap? {
    val b = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, b)
    if (b.outWidth <= 0) return null
    var sample = 1
    while (maxOf(b.outWidth, b.outHeight) / (sample * 2) >= maxPx) sample *= 2
    return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sample })?.asImageBitmap()
}

/** Loads a saved picture off the main thread, shrunk to about [maxPx], with a memory cache. */
@Composable
fun rememberPicture(file: File, maxPx: Int): ImageBitmap? {
    val key = "${file.path}@$maxPx"
    val img by produceState(cache.get(key), key) {
        if (value == null) value = withContext(Dispatchers.IO) { decodeScaled(file, maxPx)?.also { cache.put(key, it) } }
    }
    return img
}

@Composable
fun SavedPicture(file: File, modifier: Modifier = Modifier, maxPx: Int = 512, scale: ContentScale = ContentScale.Crop) {
    val img = rememberPicture(file, maxPx)
    Box(modifier.background(Paper.colors.accentSoft)) {
        if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = scale)
    }
}

// ---------- pinch to zoom ----------

/** Pinch to zoom, drag to pan, double-tap to zoom in or back out. */
@Composable
fun ZoomBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var zoom by remember { mutableFloatStateOf(1f) }
    var off by remember { mutableStateOf(Offset.Zero) }
    Box(
        modifier
            .pointerInput(Unit) {
                detectTransformGestures { _, pan, z, _ ->
                    zoom = (zoom * z).coerceIn(1f, 6f)
                    off = if (zoom == 1f) Offset.Zero else off + pan
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { if (zoom > 1f) { zoom = 1f; off = Offset.Zero } else zoom = 2.5f })
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.graphicsLayer { scaleX = zoom; scaleY = zoom; translationX = off.x; translationY = off.y }) { content() }
    }
}

// ---------- picture viewer ----------

@Composable
fun PictureViewer(repo: Repository, data: AppData, noteId: String, picId: String, onBack: () -> Unit, onWriteOn: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val note = data.note(noteId) ?: return
    val pic = note.pictures.firstOrNull { it.id == picId } ?: return
    var confirm by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    val density = LocalDensity.current
    // The viewer is always dark, like a photo app.
    Column(Modifier.fillMaxSize().background(Color(0xFF0B0A10)).statusBarsPadding().navigationBarsPadding()) {
        ViewerBar(note.title.ifBlank { "Untitled" }, onBack) {
            DarkButton("Write on it") {
                if (busy == null) {
                    busy = "Getting the picture ready…"
                    scope.launch {
                        val bg = Imports.backgroundFromPicture(ctx, repo.fileOf(pic.path))
                        busy = null
                        if (bg == null) Toast.makeText(ctx, "This picture couldn't be opened.", Toast.LENGTH_SHORT).show()
                        else onWriteOn(repo.addPicturePages(noteId, listOf(bg)))
                    }
                }
            }
            DarkButton("Remove") { confirm = true }
        }
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val maxPx = with(density) { maxOf(maxWidth, maxHeight).roundToPx() } * 2
            val img = rememberPicture(repo.fileOf(pic.path), maxPx)
            ZoomBox(Modifier.fillMaxSize()) {
                if (img != null) Image(img, "Picture", Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
                else CircularProgressIndicator(color = Color.White)
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove this picture?") },
        text = { Text("It will be deleted from this note. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirm = false; onBack(); repo.removePicture(noteId, picId) }) { Text("Remove", color = Paper.colors.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    busy?.let { BusyDialog(it) }
}

// ---------- file viewer ----------

/** PDFs open here, page by page. Tap a page to zoom into it. Other files go to another app. */
@Composable
fun FileViewer(repo: Repository, data: AppData, noteId: String, fileId: String, onBack: () -> Unit, onWriteOn: (Int) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf<String?>(null) }
    val note = data.note(noteId) ?: return
    val att = note.files.firstOrNull { it.id == fileId } ?: return
    var confirm by remember { mutableStateOf(false) }
    var zoomPage by remember { mutableStateOf<Int?>(null) }
    val file = repo.fileOf(att.path)
    val pdf = remember(file.path) { PdfDoc.open(file) }
    DisposableEffect(pdf) { onDispose { pdf?.close() } }

    Column(Modifier.fillMaxSize().background(Color(0xFF0B0A10)).statusBarsPadding().navigationBarsPadding()) {
        ViewerBar(att.name, onBack = { if (zoomPage != null) zoomPage = null else onBack() }) {
            if (pdf != null) DarkButton(if (zoomPage != null) "Write on page" else "Write on it") {
                if (busy == null) {
                    // One zoomed page, or every page (up to a limit, so a whole textbook doesn't fill the phone).
                    val single = zoomPage
                    val pages = single?.let { listOf(it) } ?: (0 until minOf(pdf.pageCount, MAX_WRITE_PAGES)).toList()
                    busy = "Getting the pages ready…"
                    scope.launch {
                        val bgs = mutableListOf<PageBackground>()
                        for ((n, i) in pages.withIndex()) {
                            busy = if (pages.size == 1) "Getting the page ready…" else "Getting page ${n + 1} of ${pages.size} ready…"
                            val bmp = pdf.renderBitmap(i, PDF_WRITE_WIDTH) ?: continue
                            Imports.saveBackground(ctx, bmp)?.let { bgs += it }
                            bmp.recycle()
                        }
                        busy = null
                        if (bgs.isEmpty()) {
                            Toast.makeText(ctx, "The pages couldn't be opened.", Toast.LENGTH_SHORT).show()
                        } else {
                            if (single == null && pdf.pageCount > MAX_WRITE_PAGES) Toast.makeText(ctx,
                                "Added the first $MAX_WRITE_PAGES pages. Zoom into a later page and tap Write on page to add it.", Toast.LENGTH_LONG).show()
                            onWriteOn(repo.addPicturePages(noteId, bgs))
                        }
                    }
                }
            }
            DarkButton("Open in…") { openExternally(ctx, att) }
            DarkButton("Remove") { confirm = true }
        }
        if (pdf == null) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("This PDF couldn't be opened. It may be damaged or password-protected.", color = Color.White,
                    modifier = Modifier.padding(24.dp))
            }
        } else BoxWithConstraints(Modifier.weight(1f).fillMaxWidth()) {
            val widthPx = with(LocalDensity.current) { maxWidth.roundToPx() }
            val zp = zoomPage
            if (zp != null) {
                val bmp by produceState<ImageBitmap?>(null, zp) { value = pdf.render(zp, widthPx * 2) }
                ZoomBox(Modifier.fillMaxSize()) {
                    bmp?.let { Image(it, "Page ${zp + 1}", Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth) }
                }
            } else LazyColumn(
                Modifier.fillMaxSize(), contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(pdf.pageCount) { i ->
                    val bmp by produceState<ImageBitmap?>(null, i) { value = pdf.render(i, widthPx) }
                    Box(
                        Modifier.fillMaxWidth().aspectRatio(pdf.aspect(i)).background(Color.White).clickable { zoomPage = i },
                        contentAlignment = Alignment.Center,
                    ) {
                        bmp?.let { Image(it, "Page ${i + 1}", Modifier.fillMaxSize()) } ?: CircularProgressIndicator()
                    }
                }
                item {
                    Text("${pdf.pageCount} page${if (pdf.pageCount == 1) "" else "s"} · tap a page to zoom", color = Color(0xFFA29DB9),
                        style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(8.dp))
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Remove “${att.name}”?") },
        text = { Text("It will be deleted from this note. This can't be undone.") },
        confirmButton = { TextButton(onClick = { confirm = false; onBack(); repo.removeFile(noteId, fileId) }) { Text("Remove", color = Paper.colors.danger) } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    busy?.let { BusyDialog(it) }
}

private const val MAX_WRITE_PAGES = 40
private const val PDF_WRITE_WIDTH = 1600

/** A small "working on it" dialog that can't be dismissed. */
@Composable
private fun BusyDialog(text: String) = AlertDialog(
    onDismissRequest = {},
    text = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(28.dp))
            Text(text, modifier = Modifier.padding(start = 16.dp))
        }
    },
    confirmButton = {},
)

/** Thin wrapper around Android's built-in PDF renderer. One page renders at a time. */
private class PdfDoc(private val fd: ParcelFileDescriptor, private val r: PdfRenderer) {
    private val lock = Mutex()
    private val sizes = List(r.pageCount) { i -> r.openPage(i).use { it.width to it.height } }
    val pageCount get() = sizes.size
    fun aspect(i: Int) = sizes[i].first.toFloat() / sizes[i].second

    suspend fun render(i: Int, widthPx: Int): ImageBitmap? = renderBitmap(i, widthPx)?.asImageBitmap()

    suspend fun renderBitmap(i: Int, widthPx: Int): Bitmap? = withContext(Dispatchers.IO) {
        lock.withLock {
            try {
                r.openPage(i).use { p ->
                    val w = widthPx.coerceIn(200, 3000)
                    val h = (w / aspect(i)).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { eraseColor(AColor.WHITE) }
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    fun close() { try { r.close(); fd.close() } catch (_: Exception) {} }

    companion object {
        fun open(f: File): PdfDoc? = try {
            val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
            PdfDoc(fd, PdfRenderer(fd))
        } catch (e: Exception) {
            null
        }
    }
}

fun openExternally(ctx: Context, att: Attachment) {
    val uri = Imports.shareUri(ctx, att.path)
    val intent = Intent(Intent.ACTION_VIEW).setDataAndType(uri, att.mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        ctx.startActivity(Intent.createChooser(intent, "Open ${att.name}"))
    } catch (e: ActivityNotFoundException) {
        val app = when (kindOf(att.name, att.mime)) {
            FileKind.DOC -> "Google Docs or WPS Office"
            FileKind.SLIDES -> "Google Slides or WPS Office"
            FileKind.SHEET -> "Google Sheets or WPS Office"
            else -> "an app that can open this file"
        }
        Toast.makeText(ctx, "No app can open this file. Try installing $app.", Toast.LENGTH_LONG).show()
    }
}

// ---------- pieces used by the note screen ----------

private val kindColors = mapOf(
    FileKind.PDF to Color(0xFFD93A4A), FileKind.DOC to Color(0xFF2B6CD9), FileKind.SLIDES to Color(0xFFE0702B),
    FileKind.SHEET to Color(0xFF1E9A5A), FileKind.IMAGE to Color(0xFF8A63D2), FileKind.TEXT to Color(0xFF5B6B82),
    FileKind.OTHER to Color(0xFF6C6882),
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FileRow(att: Attachment, onOpen: () -> Unit, onRemove: () -> Unit) {
    val c = Paper.colors
    val kind = kindOf(att.name, att.mime)
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(c.surface).border(1.dp, c.line, RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onRemove, onLongClickLabel = "Remove file").padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.width(38.dp).height(46.dp).clip(RoundedCornerShape(6.dp)).background(kindColors[kind]!!),
            contentAlignment = Alignment.BottomCenter,
        ) { Text(kind.label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(bottom = 5.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(att.name, color = c.ink, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sizeText(att.size) + if (kind == FileKind.PDF) " · opens here" else " · opens in another app",
                color = c.muted, style = MaterialTheme.typography.bodySmall)
        }
        Box(Modifier.size(36.dp).clip(CircleShape).clickable(onClick = onRemove), contentAlignment = Alignment.Center) {
            PsIcon(PsIcons.Close, "Remove ${att.name}", c.muted, Modifier.size(16.dp))
        }
    }
}

@Composable
private fun ViewerBar(title: String, onBack: () -> Unit, actions: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            PsIcon(PsIcons.Back, "Back", Color.White, Modifier.size(22.dp))
        }
        Text(title, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { actions() }
    }
}

@Composable
private fun DarkButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(999.dp)).border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(999.dp))
            .background(Color(0x14FFFFFF)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    ) { Text(label, color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium) }
}
