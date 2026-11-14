package com.paperstar.notes.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import com.google.android.gms.tasks.Task
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.paperstar.notes.data.PAGE_W
import com.paperstar.notes.data.Page
import com.paperstar.notes.data.Paper
import com.paperstar.notes.data.RecKind
import com.paperstar.notes.data.RecTask
import com.paperstar.notes.data.Recognized
import com.paperstar.notes.data.Repository
import com.paperstar.notes.data.pendingRecognition
import com.paperstar.notes.ui.pen.drawPaper
import com.paperstar.notes.ui.pen.drawStrokes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Reads the words in handwritten pages, pictures and PDFs so search can find them (#24).
 * Uses ML Kit's on-device text recognition: nothing leaves the phone and no download is needed.
 * Runs in the background a moment after notes stop changing, one item at a time.
 */
class Recognizer(ctx: Context, private val repo: Repository, private val scope: CoroutineScope) {
    private val files: File = ctx.filesDir
    private val client by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch(Dispatchers.Default) {
            // Our own saves also change the data; they simply find nothing left to read.
            repo.data.debounce(2000).conflate().collect { d ->
                for (t in pendingRecognition(d)) {
                    val text = try { read(t) } catch (e: Exception) { Log.w("Recognizer", "Couldn't read ${t.kind} ${t.id}", e); "" }
                    // An item that can't be read is saved as empty, so it isn't retried until it changes.
                    repo.setRecognized(t.noteId, Recognized(t.id, t.kind, t.sig, text.trim()))
                }
            }
        }
    }

    private suspend fun read(t: RecTask): String = when (t.kind) {
        RecKind.PAGE -> t.page?.let { p -> withBitmap(renderPage(p)) { ocr(it) } } ?: ""
        RecKind.PICTURE -> t.path?.let { decode(File(files, it)) }?.let { b -> withBitmap(b) { ocr(it) } } ?: ""
        RecKind.PDF -> t.path?.let { readPdf(File(files, it)) } ?: ""
    }

    /** Draws the page's ink (and picture) on white, without ruled lines that could confuse the reader. */
    private fun renderPage(p: Page): Bitmap {
        val w = PAGE_PX
        val h = (w * p.height / PAGE_W).toInt().coerceIn(1, w * 4)
        val bg: ImageBitmap? = p.bg?.let { decode(File(files, it))?.asImageBitmap() }
        val img = ImageBitmap(w, h)
        CanvasDrawScope().draw(Density(1f), LayoutDirection.Ltr, Canvas(img), Size(w.toFloat(), h.toFloat())) {
            scale(w / PAGE_W, pivot = Offset.Zero) {
                drawPaper(Paper.BLANK, p.height, bg, p.bg != null)
                drawStrokes(p.strokes)
            }
        }
        bg?.asAndroidBitmap()?.recycle()
        return img.asAndroidBitmap()
    }

    private suspend fun readPdf(f: File): String {
        val fd = ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
        return fd.use {
            PdfRenderer(fd).use { r ->
                buildString {
                    for (i in 0 until minOf(r.pageCount, MAX_PDF_PAGES)) {
                        val bmp = r.openPage(i).use { pg ->
                            val h = (PAGE_PX.toFloat() * pg.height / pg.width).toInt().coerceIn(1, PAGE_PX * 4)
                            Bitmap.createBitmap(PAGE_PX, h, Bitmap.Config.ARGB_8888).also {
                                it.eraseColor(Color.WHITE)
                                pg.render(it, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            }
                        }
                        append(withBitmap(bmp) { ocr(it) }).append('\n')
                    }
                }
            }
        }
    }

    private suspend fun ocr(b: Bitmap): String = client.process(InputImage.fromBitmap(b, 0)).await().text

    private fun decode(f: File): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        if (o.outWidth <= 0) return null
        var sample = 1
        while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= MAX_PICTURE_PX) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private inline fun <T> withBitmap(b: Bitmap, fn: (Bitmap) -> T): T = try { fn(b) } finally { b.recycle() }

    private companion object {
        const val PAGE_PX = 1400
        const val MAX_PICTURE_PX = 2000
        const val MAX_PDF_PAGES = 20
    }
}

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { c ->
    addOnSuccessListener { c.resume(it) }
    addOnFailureListener { c.resumeWithException(it) }
}
